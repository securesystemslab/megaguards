/*
 * Copyright (c) 2018, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uci.megaguards.backend.parallel.opencl;

import static org.jocl.CL.CL_CONTEXT_PLATFORM;
import static org.jocl.CL.CL_DEVICE_EXTENSIONS;
import static org.jocl.CL.CL_DEVICE_GLOBAL_MEM_SIZE;
import static org.jocl.CL.CL_DEVICE_LOCAL_MEM_SIZE;
import static org.jocl.CL.CL_DEVICE_LOCAL_MEM_TYPE;
import static org.jocl.CL.CL_DEVICE_MAX_CLOCK_FREQUENCY;
import static org.jocl.CL.CL_DEVICE_MAX_COMPUTE_UNITS;
import static org.jocl.CL.CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE;
import static org.jocl.CL.CL_DEVICE_MAX_MEM_ALLOC_SIZE;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_GROUP_SIZE;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS;
import static org.jocl.CL.CL_DEVICE_MAX_WORK_ITEM_SIZES;
import static org.jocl.CL.CL_DEVICE_NAME;
import static org.jocl.CL.CL_DEVICE_QUEUE_PROPERTIES;
import static org.jocl.CL.CL_DEVICE_SINGLE_FP_CONFIG;
import static org.jocl.CL.CL_DEVICE_TYPE;
import static org.jocl.CL.CL_DEVICE_TYPE_ACCELERATOR;
import static org.jocl.CL.CL_DEVICE_TYPE_CPU;
import static org.jocl.CL.CL_DEVICE_TYPE_DEFAULT;
import static org.jocl.CL.CL_DEVICE_TYPE_GPU;
import static org.jocl.CL.CL_DEVICE_VENDOR;
import static org.jocl.CL.CL_DEVICE_VERSION;
import static org.jocl.CL.CL_DEVICE_OPENCL_C_VERSION;
import static org.jocl.CL.CL_DRIVER_VERSION;
import static org.jocl.CL.CL_PLATFORM_ICD_SUFFIX_KHR;
import static org.jocl.CL.CL_PLATFORM_EXTENSIONS;
import static org.jocl.CL.CL_PLATFORM_NAME;
import static org.jocl.CL.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
import static org.jocl.CL.CL_QUEUE_PROFILING_ENABLE;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import edu.uci.megaguards.MGOptions;

@SuppressWarnings("deprecation")
public class OpenCLDevice {

    public enum Type {
        GPU,
        CPU,
        ACCELERATOR,
        DEFAULT,
        Unknown
    }

    public final HashMap<String, org.jocl.cl_kernel> kernels = new HashMap<>();

    private final org.jocl.cl_platform_id platform;
    private final org.jocl.cl_device_id device;
    private final org.jocl.cl_context context;
    private final org.jocl.cl_context_properties contextProperties;
    private final org.jocl.cl_command_queue commandQueue;

    private final float openCLVer;

    private final Type deviceType;
    private final String platformName;
    private final String platformSuffix;
    private final String deviceName;
    private final String deviceVendor;
    private final String deviceCompiler;
    private final String driverVersion;
    private final int maxComputeUnits;
    private final long maxWorkItemDimensions;
    private final long maxWorkItemSizes[];
    private final long maxWorkGroupSize;
    private final long maxClockFrequency;
    private final long maxMemAllocSize;
    private final long globalMemSize;
    private final int localMemType;
    private final long localMemSize;
    private final long maxConstantBufferSize;
    private final long queueProperties;
    private final long singleFpConfig;
    private final HashSet<String> extensions;

    private final String summary;

    private long memoryUtilzation;

    private final HashSet<OpenCLData> data;

    private class DataComparator implements Comparator<OpenCLData> {
        private final OpenCLDevice device;

        public DataComparator(OpenCLDevice device) {
            this.device = device;
        }

        @TruffleBoundary
        public int compare(OpenCLData o1, OpenCLData o2) {
            int left = o1.getOnDeviceData(device).getUsedCount();
            int right = o2.getOnDeviceData(device).getUsedCount();
            // return left - right; // descending order
            return right - left; // ascending order
        }

    }

    @TruffleBoundary
    public OpenCLDevice(org.jocl.cl_platform_id platform, org.jocl.cl_device_id device) {
        this.data = new HashSet<>();
        this.extensions = new HashSet<>();
        this.platform = platform;
        this.device = device;
        this.contextProperties = new org.jocl.cl_context_properties();
        this.contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        this.context = org.jocl.CL.clCreateContext(contextProperties, 1, new org.jocl.cl_device_id[]{device}, null, null, null);
        // #clCreateCommandQueue on OpenCL 1.2 (deprecated for OpenCL 2.0)
        this.commandQueue = org.jocl.CL.clCreateCommandQueue(context, device, 0, null);

        this.memoryUtilzation = 0;
        this.openCLVer = processOpenCLVer();

        this.platformName = getString(platform, CL_PLATFORM_NAME);
        this.platformSuffix = getString(platform, CL_PLATFORM_ICD_SUFFIX_KHR);
        String ext[] = getString(platform, CL_PLATFORM_EXTENSIONS).split(" ");
        for (String e : ext) {
            this.extensions.add(e);
        }
        ext = getString(device, CL_DEVICE_EXTENSIONS).split(" ");
        for (String e : ext) {
            this.extensions.add(e);
        }
        boolean amd = this.openCLVer > 1.1 && this.extensions.contains("cl_khr_icd") && this.platformSuffix.equals("AMD");
        final String deviceBoardName = !amd ? "" : getString(device, 0x4038); // CL_DEVICE_BOARD_NAME_AMD:
                                                                              // 0x4038
        this.deviceName = (getString(device, CL_DEVICE_NAME)) + (deviceBoardName.length() > 0 ? " " + deviceBoardName : "");
        this.deviceVendor = getString(device, CL_DEVICE_VENDOR);
        this.deviceCompiler = getString(device, CL_DEVICE_VERSION);
        this.driverVersion = getString(device, CL_DRIVER_VERSION);
        long dt = getLong(device, CL_DEVICE_TYPE);
        if ((dt & CL_DEVICE_TYPE_CPU) != 0)
            this.deviceType = Type.CPU;
        else if ((dt & CL_DEVICE_TYPE_GPU) != 0)
            this.deviceType = Type.GPU;
        else if ((dt & CL_DEVICE_TYPE_ACCELERATOR) != 0)
            this.deviceType = Type.ACCELERATOR;
        else if ((dt & CL_DEVICE_TYPE_DEFAULT) != 0)
            this.deviceType = Type.DEFAULT;
        else
            this.deviceType = Type.Unknown;

        this.maxComputeUnits = getInt(device, CL_DEVICE_MAX_COMPUTE_UNITS);
        this.maxWorkItemDimensions = getLong(device, CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS);
        this.maxWorkItemSizes = getSizes(device, CL_DEVICE_MAX_WORK_ITEM_SIZES, 3);
        this.maxWorkGroupSize = getSize(device, CL_DEVICE_MAX_WORK_GROUP_SIZE);
        this.maxClockFrequency = getLong(device, CL_DEVICE_MAX_CLOCK_FREQUENCY);
        this.maxMemAllocSize = getLong(device, CL_DEVICE_MAX_MEM_ALLOC_SIZE);
        this.globalMemSize = getLong(device, CL_DEVICE_GLOBAL_MEM_SIZE);
        this.localMemType = getInt(device, CL_DEVICE_LOCAL_MEM_TYPE);
        this.localMemSize = getLong(device, CL_DEVICE_LOCAL_MEM_SIZE);
        this.maxConstantBufferSize = getLong(device, CL_DEVICE_MAX_CONSTANT_BUFFER_SIZE);
        // #CL_DEVICE_QUEUE_PROPERTIES on OpenCL 1.2 (deprecated for OpenCL 2.0)
        this.queueProperties = getLong(device, CL_DEVICE_QUEUE_PROPERTIES);
        this.singleFpConfig = getLong(device, CL_DEVICE_SINGLE_FP_CONFIG);

        this.summary = description();
    }

    private String description() {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        printStream.printf("Platform Name: \t\t\t\t\t\t%s\n", platformName);
        printStream.printf("Device Name: \t\t\t\t\t\t%s\n", deviceName);
        printStream.printf("Vendor: \t\t\t\t\t\t%s\n", deviceVendor);
        printStream.printf("Device OpenCL C version: \t\t\t\t%s\n", deviceCompiler);
        printStream.printf("Driver version: \t\t\t\t\t%s\n", driverVersion);
        String s = "";
        switch (deviceType) {
            case CPU:
                s = "CL_DEVICE_TYPE_CPU";
                break;
            case GPU:
                s = "CL_DEVICE_TYPE_GPU";
                break;
            case ACCELERATOR:
                s = "CL_DEVICE_TYPE_ACCELERATOR";
                break;
            case DEFAULT:
                s = "CL_DEVICE_TYPE_DEFAULT";
                break;
        }

        printStream.printf("Device Type:\t\t\t\t\t\t%s\n", s);
        printStream.printf("Max compute units:\t\t\t\t\t%d\n", maxComputeUnits);
        printStream.printf("Max work items dimensions:\t\t\t\t%d\n", maxWorkItemDimensions);
        printStream.printf("  Max work items[%d]:\t\t\t\t\t%d\n", 0, maxWorkItemSizes[0]);
        printStream.printf("  Max work items[%d]:\t\t\t\t\t%d\n", 1, maxWorkItemSizes[1]);
        printStream.printf("  Max work items[%d]:\t\t\t\t\t%d\n", 2, maxWorkItemSizes[2]);
        printStream.printf("Max work group size:\t\t\t\t\t%d\n", maxWorkGroupSize);
        printStream.printf("Max clock frequency:\t\t\t\t\t%d MHz\n", maxClockFrequency);
        printStream.printf("Max memory allocation:\t\t\t\t\t%d Byte (%s)\n", maxMemAllocSize, humanReadableByteCount(maxMemAllocSize, false));
        printStream.printf("Global memory size:\t\t\t\t\t%d Byte (%s)\n", globalMemSize, humanReadableByteCount(globalMemSize, false));
        printStream.printf("Local memory type:\t\t\t\t\t%s\n", localMemType == 1 ? "local" : "global");
        printStream.printf("Local memory size:\t\t\t\t\t%d Byte (%s)\n", localMemSize, humanReadableByteCount(localMemSize, false));
        printStream.printf("Constant buffer size:\t\t\t\t\t%d Byte (%s)\n", maxConstantBufferSize, humanReadableByteCount(maxConstantBufferSize, false));
        printStream.printf("Queue on Host properties:\n");
        printStream.printf("  Out-of-Order: \t\t\t\t\t%s\n", ((queueProperties & CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE) != 0) ? "Yes" : "No");
        printStream.printf("  Profiling: \t\t\t\t\t\t%s\n", ((queueProperties & CL_QUEUE_PROFILING_ENABLE) != 0) ? "Yes" : "No");
        printStream.printf("Single-precision floating-point capability:\n");
        printStream.printf("  Denorms:\t\t\t\t\t\t%s\n", ((singleFpConfig & CL.CL_FP_DENORM) != 0) ? "Yes" : "No");
        printStream.printf("  Quiet NaNs:\t\t\t\t\t\t%s\n", ((singleFpConfig & CL.CL_FP_INF_NAN) != 0) ? "Yes" : "No");
        printStream.printf("  Round to zero rounding mode:\t\t\t\t%s\n", ((singleFpConfig & CL.CL_FP_ROUND_TO_ZERO) != 0) ? "Yes" : "No");
        printStream.printf("  Round to nearest even:\t\t\t\t%s\n", ((singleFpConfig & CL.CL_FP_ROUND_TO_NEAREST) != 0) ? "Yes" : "No");
        printStream.printf("  Round to +ve and -ve infinity rounding modes:\t\t%s\n", ((singleFpConfig & CL.CL_FP_ROUND_TO_INF) != 0) ? "Yes" : "No");
        printStream.printf("  Software basic floating-point operations:\t\t%s\n", ((singleFpConfig & CL.CL_FP_SOFT_FLOAT) != 0) ? "Yes" : "No");
        printStream.printf("  IEEE754-2008 fused multiply-add:\t\t\t%s\n", ((singleFpConfig & CL.CL_FP_FMA) != 0) ? "Yes" : "No");
        printStream.printf("  IEEE754 divide and sqrt are correctly rounded:\t%s\n", ((singleFpConfig & CL.CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT) != 0) ? "Yes" : "No");
        printStream.flush();
        return byteArray.toString();
    }

    public String descriptionJSON(JSONObjectBuilder jsonOut) {
        jsonOut.add("Platform Name", platformName);
        jsonOut.add("Device Name", deviceName);
        jsonOut.add("Vendor", deviceVendor);
        jsonOut.add("Device OpenCL C version", deviceCompiler);
        jsonOut.add("Driver version", driverVersion);
        String s = "";
        switch (deviceType) {
            case CPU:
                s = "CL_DEVICE_TYPE_CPU";
                break;
            case GPU:
                s = "CL_DEVICE_TYPE_GPU";
                break;
            case ACCELERATOR:
                s = "CL_DEVICE_TYPE_ACCELERATOR";
                break;
            case DEFAULT:
                s = "CL_DEVICE_TYPE_DEFAULT";
                break;
        }

        jsonOut.add("Device Type", s);
        jsonOut.add("Max compute units", maxComputeUnits);
        jsonOut.add("Max work items dimensions", maxWorkItemDimensions);
        jsonOut.add("Max work items 0", maxWorkItemSizes[0]);
        jsonOut.add("Max work items 1", maxWorkItemSizes[1]);
        jsonOut.add("Max work items 2", maxWorkItemSizes[2]);
        jsonOut.add("Max work group size", maxWorkGroupSize);
        jsonOut.add("Max clock frequency (MHz)", maxClockFrequency);
        jsonOut.add("Max memory allocation (Byte)", maxMemAllocSize);
        jsonOut.add("Max memory allocation (h)", humanReadableByteCount(maxMemAllocSize, false));
        jsonOut.add("Global memory size (Byte)", globalMemSize);
        jsonOut.add("Global memory size (h)", humanReadableByteCount(globalMemSize, false));
        jsonOut.add("Local memory type", localMemType == 1 ? "local" : "global");
        jsonOut.add("Local memory size (Byte)", localMemSize);
        jsonOut.add("Local memory size (h)", humanReadableByteCount(localMemSize, false));
        jsonOut.add("Constant buffer size (Byte)", maxConstantBufferSize);
        jsonOut.add("Constant buffer size (h)", humanReadableByteCount(maxConstantBufferSize, false));
        JSONObjectBuilder jsonProp = JSONHelper.object();
        jsonProp.add("Out-of-Order", ((queueProperties & CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE) != 0) ? "Yes" : "No");
        jsonProp.add("Profiling", ((queueProperties & CL_QUEUE_PROFILING_ENABLE) != 0) ? "Yes" : "No");
        jsonOut.add("Queue on Host properties", jsonProp);
        JSONObjectBuilder jsonCapa = JSONHelper.object();
        jsonCapa.add("Denorms", ((singleFpConfig & CL.CL_FP_DENORM) != 0) ? "Yes" : "No");
        jsonCapa.add("Quiet NaNs", ((singleFpConfig & CL.CL_FP_INF_NAN) != 0) ? "Yes" : "No");
        jsonCapa.add("Round to zero rounding mode", ((singleFpConfig & CL.CL_FP_ROUND_TO_ZERO) != 0) ? "Yes" : "No");
        jsonCapa.add("Round to nearest even", ((singleFpConfig & CL.CL_FP_ROUND_TO_NEAREST) != 0) ? "Yes" : "No");
        jsonCapa.add("Round to +ve and -ve infinity rounding modes", ((singleFpConfig & CL.CL_FP_ROUND_TO_INF) != 0) ? "Yes" : "No");
        jsonCapa.add("Software basic floating-point operations", ((singleFpConfig & CL.CL_FP_SOFT_FLOAT) != 0) ? "Yes" : "No");
        jsonCapa.add("IEEE754-2008 fused multiply-add", ((singleFpConfig & CL.CL_FP_FMA) != 0) ? "Yes" : "No");
        jsonCapa.add("IEEE754 divide and sqrt are correctly rounded", ((singleFpConfig & CL.CL_FP_CORRECTLY_ROUNDED_DIVIDE_SQRT) != 0) ? "Yes" : "No");
        jsonOut.add("Single-precision floating-point capability", jsonCapa);
        return deviceName;
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @TruffleBoundary
    private float processOpenCLVer() {
        float ver = 0.0f;
        String verStr = getString(device, CL_DEVICE_OPENCL_C_VERSION);
        String v[] = verStr.split(" ");

        for (int i = v.length - 1; i >= 0; i++) {
            try {
                ver = Float.parseFloat(v[i]);
                break;
            } catch (NumberFormatException e) {
            }
        }
        return ver;
    }

    @TruffleBoundary
    private static int getInt(org.jocl.cl_device_id device, int paramName) {
        return getInts(device, paramName, 1)[0];
    }

    @TruffleBoundary
    private static int[] getInts(org.jocl.cl_device_id device, int paramName, int numValues) {
        int values[] = new int[numValues];
        org.jocl.CL.clGetDeviceInfo(device, paramName, Sizeof.cl_int * numValues, Pointer.to(values), null);
        return values;
    }

    @TruffleBoundary
    private static long getLong(org.jocl.cl_device_id device, int paramName) {
        return getLongs(device, paramName, 1)[0];
    }

    @TruffleBoundary
    private static long[] getLongs(org.jocl.cl_device_id device, int paramName, int numValues) {
        long values[] = new long[numValues];
        org.jocl.CL.clGetDeviceInfo(device, paramName, Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }

    @TruffleBoundary
    private static String getString(org.jocl.cl_device_id device, int paramName) {
        long size[] = new long[1];
        org.jocl.CL.clGetDeviceInfo(device, paramName, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        org.jocl.CL.clGetDeviceInfo(device, paramName, buffer.length, Pointer.to(buffer), null);

        return new String(buffer, 0, buffer.length - 1);
    }

    @TruffleBoundary
    private static String getString(org.jocl.cl_platform_id platform, int paramName) {
        long size[] = new long[1];
        org.jocl.CL.clGetPlatformInfo(platform, paramName, 0, null, size);

        byte buffer[] = new byte[(int) size[0]];
        org.jocl.CL.clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);

        return new String(buffer, 0, buffer.length - 1);
    }

    @TruffleBoundary
    private static long getSize(org.jocl.cl_device_id device, int paramName) {
        return getSizes(device, paramName, 1)[0];
    }

    @TruffleBoundary
    static long[] getSizes(org.jocl.cl_device_id device, int paramName, int numValues) {
        ByteBuffer buffer = ByteBuffer.allocate(numValues * Sizeof.size_t).order(ByteOrder.nativeOrder());
        org.jocl.CL.clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues, Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4)
            for (int i = 0; i < numValues; i++)
                values[i] = buffer.getInt(i * Sizeof.size_t);

        else
            for (int i = 0; i < numValues; i++)
                values[i] = buffer.getLong(i * Sizeof.size_t);

        return values;
    }

    private long getMemoryProportion() {
        return (long) (globalMemSize * MGOptions.Backend.deviceMemoryPortion);
    }

    public long getMemoryAvailable() {
        return (long) (globalMemSize * MGOptions.Backend.deviceMemoryPortion) - memoryUtilzation;
    }

    @TruffleBoundary
    public void addData(OpenCLData d) {
        data.add(d);
        memoryUtilzation += d.getDataSize();
    }

    @TruffleBoundary
    public void delData(OpenCLData d) {
        data.remove(d);
        memoryUtilzation -= d.getDataSize();
    }

    @TruffleBoundary
    public boolean ensureMemoryAllocation(ArrayList<OpenCLData> readOnly, ArrayList<OpenCLData> writes, long totalDataSize) {
        boolean success = true;
        long localTotalDataSize = totalDataSize;
        final HashSet<Integer> dataHashes = new HashSet<>();
        for (OpenCLData d : readOnly) {
            if (d.getOnDeviceData(this).isLoaded()) {
                localTotalDataSize -= d.getDataSize();
            }
            dataHashes.add(d.getHashCode());
        }

        for (OpenCLData d : writes) {
            if (d.getOnDeviceData(this).isLoaded()) {
                localTotalDataSize -= d.getDataSize();
            }
            dataHashes.add(d.getHashCode());
        }

        if (localTotalDataSize > getMemoryAvailable()) {
            if (localTotalDataSize > getMemoryProportion()) {
                success = false;
            } else {
                final ArrayList<OpenCLData> d = new ArrayList<>(data);
                Collections.sort(d, new DataComparator(this));
                for (int i = 0; i < d.size() && localTotalDataSize > getMemoryAvailable(); i++) {
                    OpenCLData o = d.get(i);
                    if (dataHashes.contains(o.getHashCode()))
                        continue;
                    o.getOnDeviceData(this).clean();
                }
            }
        }
        return success;
    }

    @TruffleBoundary
    public void clean() {
        for (Entry<String, org.jocl.cl_kernel> k : kernels.entrySet()) {
            org.jocl.CL.clReleaseKernel(k.getValue());
        }
        org.jocl.CL.clReleaseCommandQueue(commandQueue);
        org.jocl.CL.clReleaseContext(context);
    }

    public org.jocl.cl_context getContext() {
        return context;
    }

    public org.jocl.cl_command_queue getCommandQueue() {
        return commandQueue;
    }

    public float getOpenCLVersion() {
        return openCLVer;
    }

    public org.jocl.cl_platform_id getPlatform() {
        return platform;
    }

    public org.jocl.cl_device_id getDevice() {
        return device;
    }

    public Type getDeviceType() {
        return deviceType;
    }

    public String getPlatformName() {
        return platformName;
    }

    // public String getDeviceName() {
    // return deviceName;
    // }

    @TruffleBoundary
    public String getDeviceName() {
        return String.format("OpenCL %s: %s", deviceType, deviceName);
    }

    public String getDeviceVendor() {
        return deviceVendor;
    }

    public String getDeviceCompiler() {
        return deviceCompiler;
    }

    public String getDriverVersion() {
        return driverVersion;
    }

    public int getMaxComputeUnits() {
        return maxComputeUnits;
    }

    public long getMaxWorkItemDimensions() {
        return maxWorkItemDimensions;
    }

    public long[] getMaxWorkItemSizes() {
        return maxWorkItemSizes;
    }

    public long getMaxWorkGroupSize() {
        return maxWorkGroupSize;
    }

    public long getMaxClockFrequency() {
        return maxClockFrequency;
    }

    public long getMaxMemAllocSize() {
        return maxMemAllocSize;
    }

    public long getGlobalMemSize() {
        return globalMemSize;
    }

    public int getLocalMemType() {
        return localMemType;
    }

    public long getLocalMemSize() {
        return localMemSize;
    }

    public long getMaxConstantBufferSize() {
        return maxConstantBufferSize;
    }

    public long getQueueProperties() {
        return queueProperties;
    }

    public long getSingleFpConfig() {
        return singleFpConfig;
    }

    public String getSummary() {
        return summary;
    }

}
