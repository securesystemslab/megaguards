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

import static org.jocl.CL.CL_DEVICE_TYPE_ALL;
import static org.jocl.CL.CL_PLATFORM_NAME;
import static org.jocl.CL.clGetDeviceIDs;
import static org.jocl.CL.clGetDeviceInfo;
import static org.jocl.CL.clGetPlatformIDs;
import static org.jocl.CL.clGetPlatformInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLDevice.Type;
import edu.uci.megaguards.log.MGLog;

public class OpenCLMGR {

    public static final ArrayList<OpenCLDevice> GPUs = new ArrayList<>();
    public static final ArrayList<OpenCLDevice> CPUs = new ArrayList<>();

    public static final OpenCLMGR MGR = new OpenCLMGR();

    private final int numPlatforms[];
    private final org.jocl.cl_platform_id platforms[];
    private final int numDevices;

    @TruffleBoundary
    public OpenCLMGR() {
        CL.setExceptionsEnabled(MGOptions.Backend.oclExceptions);

        numPlatforms = new int[1];
        clGetPlatformIDs(0, null, numPlatforms);

        if (MGOptions.Backend.Debug > 7 || MGOptions.Backend.clinfo)
            MGLog.getPrintErr().println("Number of platforms: " + numPlatforms[0]);

        platforms = new org.jocl.cl_platform_id[numPlatforms[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        for (int i = 0; i < platforms.length; i++) {
            String platformName = getString(platforms[i], CL_PLATFORM_NAME);

            int devices[] = new int[1];
            clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, 0, null, devices);

            if (MGOptions.Backend.Debug > 7 || MGOptions.Backend.clinfo)
                MGLog.getPrintErr().println("Number of devices in " + platformName + " platform: " + devices[0]);

            org.jocl.cl_device_id devicesArray[] = new org.jocl.cl_device_id[devices[0]];
            clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_ALL, devices[0], devicesArray, null);

            for (org.jocl.cl_device_id deviceOrigin : devicesArray) {
                org.jocl.cl_device_id device = deviceOrigin;
                if (MGOptions.Backend.oclCPUNumCores > 0) {
                    long dt = getLong(device, CL.CL_DEVICE_TYPE);
                    if ((dt & CL.CL_DEVICE_TYPE_CPU) != 0) {
                        org.jocl.cl_device_partition_property subprop = new org.jocl.cl_device_partition_property();
                        // subprop.addProperty(CL.CL_DEVICE_PARTITION_BY_COUNTS, platforms[i]);
                        // subprop.addProperty(MGOptions.Parallel.oclCPUNumCores, platforms[i]);
                        // subprop.addProperty(0, platforms[i]);
                        subprop.addProperty(CL.CL_DEVICE_PARTITION_BY_COUNTS, MGOptions.Backend.oclCPUNumCores);
                        subprop.addProperty(CL.CL_DEVICE_PARTITION_BY_COUNTS_LIST_END, 0);

                        org.jocl.cl_device_id subdevicesArray[] = new org.jocl.cl_device_id[16];
                        CL.clCreateSubDevices(device, subprop, 1, subdevicesArray, null);
                        device = subdevicesArray[0];
                    }
                }

                OpenCLDevice d = new OpenCLDevice(platforms[i], device);
                if (d.getDeviceType() == Type.CPU)
                    CPUs.add(d);
                else if (d.getDeviceType() == Type.GPU)
                    GPUs.add(d);

                if (MGOptions.Backend.Debug > 7 || MGOptions.Backend.clinfo)
                    MGLog.getPrintErr().println(d.getSummary());
            }
        }

        numDevices = GPUs.size() + CPUs.size();
    }

    @TruffleBoundary
    private static long getLong(org.jocl.cl_device_id device, int paramName) {
        return getLongs(device, paramName, 1)[0];
    }

    @TruffleBoundary
    private static long[] getLongs(org.jocl.cl_device_id device, int paramName, int numValues) {
        long values[] = new long[numValues];
        CL.clGetDeviceInfo(device, paramName, Sizeof.cl_long * numValues, Pointer.to(values), null);
        return values;
    }

    @TruffleBoundary
    private static String getString(org.jocl.cl_platform_id platform, int paramName) {
        long size[] = new long[1];
        clGetPlatformInfo(platform, paramName, 0, null, size);
        byte buffer[] = new byte[(int) size[0]];
        clGetPlatformInfo(platform, paramName, buffer.length, Pointer.to(buffer), null);
        return new String(buffer, 0, buffer.length - 1);
    }

    @TruffleBoundary
    static long[] getSizes(org.jocl.cl_device_id device, int paramName, int numValues) {
        ByteBuffer buffer = ByteBuffer.allocate(numValues * Sizeof.size_t).order(ByteOrder.nativeOrder());
        clGetDeviceInfo(device, paramName, Sizeof.size_t * numValues, Pointer.to(buffer), null);
        long values[] = new long[numValues];
        if (Sizeof.size_t == 4)
            for (int i = 0; i < numValues; i++)
                values[i] = buffer.getInt(i * Sizeof.size_t);

        else
            for (int i = 0; i < numValues; i++)
                values[i] = buffer.getLong(i * Sizeof.size_t);

        return values;
    }

    public int[] getNumPlatforms() {
        return numPlatforms;
    }

    public org.jocl.cl_platform_id[] getPlatforms() {
        return platforms;
    }

    public boolean isValidAutoDevice() {
        return isValidAutoDevice(true);
    }

    @TruffleBoundary
    public boolean isValidAutoDevice(boolean warn) {
        if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto) {
            if (GPUs.size() > 0 && CPUs.size() > 0) {
                return true;
            } else {
                if (GPUs.size() > 0) {
                    MGOptions.Backend.target = ExecutionMode.OpenCLGPU;
                    if (warn)
                        MGLog.printlnErrTagged("Only OpenCL GPU device found");
                } else if (CPUs.size() > 0) {
                    MGOptions.Backend.target = ExecutionMode.OpenCLCPU;
                    if (warn)
                        MGLog.printlnErrTagged("Only OpenCL CPU device found");
                } else {
                    MGOptions.Backend.target = ExecutionMode.Truffle;
                    if (warn)
                        MGLog.printlnErrTagged("No valid OpenCL GPU or CPU devices found, will use Truffle instead");
                }
            }
        }
        return false;
    }

    public boolean isValidGPUDevice() {
        return isValidGPUDevice(true);
    }

    @TruffleBoundary
    public boolean isValidGPUDevice(boolean warn) {
        if (MGOptions.Backend.target == ExecutionMode.OpenCLGPU) {
            if (GPUs.size() > 0) {
                return true;
            } else {
                MGOptions.Backend.target = ExecutionMode.OpenCLCPU;
                if (CPUs.size() == 0) {
                    MGOptions.Backend.target = ExecutionMode.Truffle;
                    if (warn)
                        MGLog.printlnErrTagged("No valid OpenCL GPU device found, will use Truffle instead");
                } else {
                    if (warn)
                        MGLog.printlnErrTagged("No valid OpenCL GPU device found, will use CPU device instead");
                }
            }
        }
        return false;
    }

    public boolean isValidCPUDevice() {
        return isValidCPUDevice(true);
    }

    @TruffleBoundary
    public boolean isValidCPUDevice(boolean warn) {
        if (MGOptions.Backend.target == ExecutionMode.OpenCLCPU) {
            if (CPUs.size() > 0) {
                return true;
            } else {
                MGOptions.Backend.target = ExecutionMode.OpenCLGPU;
                if (GPUs.size() == 0) {
                    MGOptions.Backend.target = ExecutionMode.Truffle;
                    if (warn)
                        MGLog.printlnErrTagged("No valid OpenCL CPU device found, will use Truffle instead");
                } else {
                    if (warn)
                        MGLog.printlnErrTagged("No valid OpenCL CPU device found, will use GPU device instead");
                }
            }
        }
        return false;
    }

    public int getNumDevices() {
        return numDevices;
    }

    @TruffleBoundary
    public OpenCLDevice getBestGPU() {
        // TODO: calculate best GPU device
        if (GPUs.size() == 0)
            return null;
        return GPUs.get(0);
    }

    @TruffleBoundary
    public OpenCLDevice getBestCPU() {
        // TODO: calculate best CPU device
        if (CPUs.size() == 0)
            return null;
        return CPUs.get(0);
    }

    public OpenCLDevice getDevice() {
        OpenCLDevice device = null;
        if (getNumDevices() == 0)
            return null;

        if (MGOptions.Backend.target == ExecutionMode.OpenCLGPU) {
            device = getBestGPU();
        } else {
            device = getBestCPU();
        }

        return device;
    }

    @TruffleBoundary
    public void clean() {
        for (OpenCLDevice d : GPUs) {
            d.clean();
        }
        GPUs.clear();

        for (OpenCLDevice d : CPUs) {
            d.clean();
        }
        CPUs.clear();
    }

}
