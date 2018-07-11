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

import static org.jocl.CL.CL_MEM_READ_WRITE;
import static org.jocl.CL.CL_TRUE;
import static org.jocl.CL.clCreateBuffer;
import static org.jocl.CL.clEnqueueReadBuffer;
import static org.jocl.CL.clEnqueueWriteBuffer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.parallel.reduction.ReductionWorkload;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;
import edu.uci.megaguards.unbox.Unboxer;

public class OpenCLData {

    private enum STATE {
        INIT,
        BUFFERED,
        LOADED
    }

    private final static HashMap<Integer, OpenCLData> allData = new HashMap<>();

    private final DataType dataType;

    private final long dataSize;
    private final int dataTypeSize;

    private final int hashCode;
    private final int[] dims;
    private final int numDims;
    private final int dataPointerLen;
    private final int numElements;
    private boolean writeOnly;

    private final boolean reduceResult;

    private final HashMap<OpenCLDevice, OnDevice> onDeviceData;
    private final HashSet<String> varNames;
    private String lastVarName;

    @TruffleBoundary
    protected OpenCLData(MGArray array) {
        this.lastVarName = array.getName();
        this.dataType = DataType.values()[array.getDataType().ordinal()];
        this.dataTypeSize = getTypeSize(array);

        final Unboxer boxed = (array.getValue() instanceof ParallelWorkload) ? ((ParallelWorkload) array.getValue()).getBoxed() : (Unboxer) array.getValue();
        this.reduceResult = array.getValue() instanceof ReductionWorkload;
        this.dataSize = boxed.getInfo().sizeEstimate(dataTypeSize);
        this.hashCode = boxed.getValue().hashCode();
        this.dims = boxed.getInfo().getDimSizes();
        this.numDims = boxed.getInfo().getDim();
        this.dataPointerLen = boxed.getInfo().getSize(0, numDims - 1);
        this.numElements = boxed.getInfo().getSize(0, numDims);
        this.writeOnly = array.isWriteOnly();
        this.onDeviceData = new HashMap<>();
        this.varNames = new HashSet<>();
    }

    public DataType getDataType() {
        return dataType;
    }

    public void addName(String name) {
        this.varNames.add(name != null ? name : "<None>");
        this.lastVarName = name;
    }

    public long getDataSize() {
        return dataSize;
    }

    public int getHashCode() {
        return hashCode;
    }

    @TruffleBoundary
    public OnDevice getOnDeviceData(OpenCLDevice device) {
        return onDeviceData.get(device);
    }

    @TruffleBoundary
    public void invalidateOtherDeviceData(OpenCLDevice device) {
        for (Entry<OpenCLDevice, OnDevice> d : onDeviceData.entrySet()) {
            if (d.getKey() == device)
                continue;
            if (d.getValue().state == STATE.LOADED)
                d.getValue().state = STATE.BUFFERED;
        }
    }

    @TruffleBoundary
    private void invalidateDevicesData() {
        for (Entry<OpenCLDevice, OnDevice> entry : onDeviceData.entrySet()) {
            if (entry.getValue().state == STATE.LOADED)
                entry.getValue().state = STATE.BUFFERED;
        }
    }

    @TruffleBoundary
    private void internalClean() {
        for (Entry<OpenCLDevice, OnDevice> d : onDeviceData.entrySet()) {
            if (d.getValue().state != STATE.INIT) {
                d.getValue().clean();
            }
        }
    }

    @TruffleBoundary
    public static void clean() {
        for (Entry<Integer, OpenCLData> d : allData.entrySet()) {
            d.getValue().internalClean();
        }
    }

    public static Pointer getValuePointer(MGObject storage) {
        switch (storage.getDataType()) {
            case Bool:
                return Pointer.to(new int[]{((boolean) storage.getValue()) ? 1 : 0});
            case Double:
                return Pointer.to(new double[]{((double) storage.getValue())});
            case Int:
                return Pointer.to(new int[]{((int) storage.getValue())});
            case Long:
                return Pointer.to(new long[]{((long) storage.getValue())});
        }
        return null;
    }

    public static int getTypeSize(MGStorage storage) {
        int s = 0;
        switch (storage.getDataType()) {
            case Bool:
                s = Sizeof.cl_int;
                break;
            case BoolArray:
                s = Sizeof.cl_int;
                break;
            case Double:
                s = Sizeof.cl_double;
                break;
            case DoubleArray:
                s = Sizeof.cl_double;
                break;
            case Int:
                s = Sizeof.cl_int;
                break;
            case IntArray:
                s = Sizeof.cl_int;
                break;
            case Long:
                s = Sizeof.cl_long;
                break;
            case LongArray:
                s = Sizeof.cl_long;
                break;
            case None:
                s = Sizeof.cl_int;
                break;
        }
        return s;
    }

    @TruffleBoundary
    public static OpenCLData initData(OpenCLDevice device, MGArray array) {
        Unboxer boxed = null;
        if (array.getValue() instanceof ParallelWorkload)
            boxed = ((ParallelWorkload) array.getValue()).getBoxed();
        else
            boxed = (Unboxer) array.getValue();

        boolean changed = boxed.isChanged();
        int hashCode = boxed.getValue().hashCode();
        OpenCLData d = allData.get(hashCode);
        if (d == null) {
            d = new OpenCLData(array);
            allData.put(hashCode, d);
        } else {
            d.writeOnly = array.isWriteOnly();
        }
        d.addName(array.getName());
        if (changed || MGOptions.Backend.disableDataManagementOptimization) {
            d.invalidateDevicesData();
        }

        if (!d.onDeviceData.containsKey(device)) {
            d.onDeviceData.put(device, d.new OnDevice(device, d));
        }
        return d;
    }

    public static OpenCLData getData(MGArray array) {
        Unboxer boxed = null;
        if (array.getValue() instanceof ParallelWorkload)
            boxed = ((ParallelWorkload) array.getValue()).getBoxed();
        else
            boxed = (Unboxer) array.getValue();
        int hashCode = boxed.getValue().hashCode();
        return allData.get(hashCode);
    }

    @TruffleBoundary
    public static OpenCLData getData(int hashCode) {
        return allData.get(hashCode);
    }

    public class OnDevice {

        private final OpenCLData data;
        private final OpenCLDevice device;
        private Pointer[] dataPointer;
        private org.jocl.cl_mem dataOnDevice;
        private STATE state;
        private int usedCount;

        public OnDevice(OpenCLDevice device, OpenCLData data) {
            this.data = data;
            this.device = device;
            this.dataPointer = null;
            this.dataOnDevice = null;
            this.state = STATE.INIT;
            this.usedCount = 0;
        }

        public boolean isLoaded() {
            return this.state == STATE.LOADED;
        }

        @TruffleBoundary
        private boolean convertToPointer(Object o) {
            boolean success = true;
            this.dataPointer = new Pointer[this.data.dataPointerLen];
            if (numDims == 1) {
                switch (dataType) {
                    case LongArray:
                        this.dataPointer[0] = Pointer.to((long[]) o);
                        break;
                    case DoubleArray:
                        this.dataPointer[0] = Pointer.to((double[]) o);
                        break;
                    case IntArray:
                        this.dataPointer[0] = Pointer.to((int[]) o);
                        break;
                }
            } else {
                // Assume 2D object
                switch (dataType) {
                    case LongArray:
                        long[][] l = (long[][]) o;
                        for (int i = 0; i < this.data.dataPointerLen; i++) {
                            this.dataPointer[i] = Pointer.to(l[i]);
                        }
                        break;
                    case DoubleArray:
                        double[][] d = (double[][]) o;
                        for (int i = 0; i < this.data.dataPointerLen; i++) {
                            this.dataPointer[i] = Pointer.to(d[i]);
                        }
                        break;
                    case IntArray:
                        int[][] n = (int[][]) o;
                        for (int i = 0; i < this.data.dataPointerLen; i++) {
                            this.dataPointer[i] = Pointer.to(n[i]);
                        }
                        break;
                }

            }
            return success;
        }

        @TruffleBoundary
        private boolean createBuffer() {
            this.dataOnDevice = clCreateBuffer(device.getContext(),
                            CL_MEM_READ_WRITE, // | CL_MEM_COPY_HOST_PTR,
                            this.data.dataTypeSize * this.data.numElements,
                            // this.dataPointer[i], null); // in case we use
                            // CL_MEM_COPY_HOST_PTR
                            null, null);
            // CL.clFlush(device.getCommandQueue());
            // CL.clFinish(device.getCommandQueue());
            return true;
        }

        @TruffleBoundary
        public boolean put(Object o) {
            return put(o, false);
        }

        @TruffleBoundary
        public boolean put(Object o, boolean force) {
            boolean success = true;
            this.usedCount++;
            String msg = String.format("put(%s: %d) ", data.lastVarName, data.hashCode);
            if (force && state == STATE.LOADED && !data.writeOnly) {
                state = STATE.BUFFERED;
            }
            if (state == STATE.INIT) {
                convertToPointer(o);
                createBuffer();
                device.addData(data);
                state = (this.data.writeOnly) ? STATE.LOADED : STATE.BUFFERED;
                msg += String.format("(buffer created size: %d byte) ", data.dataSize);
            }

            if (state == STATE.BUFFERED) {
                for (int i = 0; i < this.data.dataPointerLen; i++) {
                    final int offset = this.data.dataTypeSize * i * this.data.dims[this.data.numDims - 1];
                    final int length = this.data.dataTypeSize * this.data.dims[this.data.numDims - 1];
                    success = success && CL.CL_SUCCESS == clEnqueueWriteBuffer(device.getCommandQueue(),
                                    this.dataOnDevice, CL_TRUE,
                                    offset, length,
                                    this.dataPointer[i], 0, null, null);
                }
                // CL.clFlush(device.getCommandQueue());
                // CL.clFinish(device.getCommandQueue());
                state = STATE.LOADED;
                msg += "(data loaded) ";
            }

            // if (state == STATE.LOADED)
            // success = true;

            if (MGOptions.Backend.Debug > 2) {
                MGLog.printlnTagged(msg + "done.");
            }

            return success;
        }

        @TruffleBoundary
        public boolean get() {
            boolean success = true;
            final int l = reduceResult ? 1 : this.data.dims[this.data.numDims - 1];
            for (int i = 0; i < this.data.dataPointerLen; i++) {
                final int offset = this.data.dataTypeSize * i * l;
                final int length = this.data.dataTypeSize * l;
                success = success && CL.CL_SUCCESS == clEnqueueReadBuffer(device.getCommandQueue(), this.dataOnDevice, CL_TRUE,
                                offset, length,
                                this.dataPointer[i], 0, null, null);
            }

            if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto)
                invalidateOtherDeviceData(device);
            // CL.clFlush(device.getCommandQueue());
            // CL.clFinish(device.getCommandQueue());
            return success;
        }

        public int getUsedCount() {
            return usedCount;
        }

        @TruffleBoundary
        public void clean() {
            this.state = STATE.INIT;
            if (this.dataOnDevice != null) {
                CL.clReleaseMemObject(this.dataOnDevice);
            }
            device.delData(data);
            this.usedCount = 0;
            this.dataOnDevice = null;
            this.dataPointer = null;
        }

        public org.jocl.cl_mem getCLMem() {
            return dataOnDevice;
        }
    }
}
