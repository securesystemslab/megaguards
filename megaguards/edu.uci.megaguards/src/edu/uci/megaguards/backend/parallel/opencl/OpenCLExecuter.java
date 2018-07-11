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

import static org.jocl.CL.clBuildProgram;
import static org.jocl.CL.clCreateKernel;
import static org.jocl.CL.clCreateProgramWithSource;
import static org.jocl.CL.clEnqueueNDRangeKernel;
import static org.jocl.CL.clSetKernelArg;
import static org.jocl.CL.clWaitForEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.jocl.CL;
import org.jocl.CLException;
import org.jocl.Pointer;
import org.jocl.Sizeof;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.exception.BoundException;
import edu.uci.megaguards.analysis.parallel.ParallelFunctions;
import edu.uci.megaguards.analysis.parallel.exception.CompilationException;
import edu.uci.megaguards.analysis.parallel.reduction.ReductionWorkload;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.env.MGPrivateEnv;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeUserFunction;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.MGObjectTracker;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.backend.parallel.ParallelWorkload.LoadType;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.ArrayInfo;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGLongArray;
import edu.uci.megaguards.object.MGStorage;
import edu.uci.megaguards.unbox.StaticUnboxer;
import edu.uci.megaguards.unbox.Unboxer;

public class OpenCLExecuter {

    protected final MGGlobalEnv env;
    protected int levels;
    protected MGStorage[] iterVar;
    protected MGNode kernelBody;
    protected ArrayList<MGNodeUserFunction> localFunctions;
    protected String kernelFile;
    protected String kernelName;
    protected Map<String, MGStorage> parameters;
    protected String[] orderedParameters;

    protected String generatedSrc;

    protected static final HashMap<Integer, String> generatedSrcs = new HashMap<>();

    protected long[][] ranges;
    protected boolean ready;
    protected boolean initialized;
    protected MGObjectTracker changesTracker;
    protected boolean lockDevice;
    protected ArrayList<String[]> swapping;
    protected final String reductionSwitcher;

    protected MGLog log;

    private final long[] boundFlagVal;
    private final long[] ofFlagVal;
    private final MGLongArray boundFlag;
    private final MGLongArray ofFlag;
    private OpenCLData deviceBoundFlag;
    private OpenCLData deviceOFFlag;
    private final ArrayList<OpenCLData> readOnly;
    private final HashMap<Integer, OpenCLData> write;
    private final HashMap<MGArray, OpenCLData> workloads;
    private final HashMap<Integer, Object> dataHashes;
    private final HashMap<String, String> swapParameter;
    private long totalDataSize;
    private OpenCLDevice device;

    private long[] localSize;
    private long[] groupSize;
    private long[] globalSize;

    @TruffleBoundary
    public OpenCLExecuter(SourceSection source, MGGlobalEnv env, MGNode rootNode, MGLog log) {

        this.env = env;
        levels = env.getIterationLevels();
        iterVar = env.getIteratorVar();
        kernelBody = rootNode == null ? env.getMGRootNode() : rootNode;
        parameters = env.getParameters();
        orderedParameters = env.getOrderedParameters();
        String namePostfix = "";
        if (source != null) {
            int bstart = source.getSource().getName().lastIndexOf("/");
            int bend = source.getSource().getName().length();
            namePostfix += source.getSource().getName().substring(bstart + 1, bend).replaceAll("[^\\w\\s]", "");
            namePostfix += "_" + source.getStartLine();
        }

        kernelName = "Parallel_" + "_" + namePostfix;
        kernelFile = null;

        generatedSrc = generatedSrcs.get(kernelBody.hashCode());
        if (generatedSrc == null) {
            this.localFunctions = new ArrayList<>();
            for (Entry<String, MGPrivateEnv> entry : env.getPrivateEnvironments().entrySet()) {
                MGPrivateEnv e = entry.getValue();
                this.localFunctions.add(e.getFunction());
            }
        }

        ranges = env.getRanges();
        this.changesTracker = null;
        this.lockDevice = OpenCLAutoDevice.deviceLocked;

        ready = false;
        initialized = false;

        reductionSwitcher = env.getReductionSwitcher();
        this.log = log;

        this.localSize = new long[]{1};
        this.groupSize = new long[]{1};
        this.globalSize = new long[]{1};
        this.readOnly = new ArrayList<>();
        this.write = new HashMap<>();
        this.workloads = new HashMap<>();
        this.dataHashes = new HashMap<>();
        this.swapParameter = new HashMap<>();
        this.totalDataSize = 0;

        this.boundFlagVal = new long[]{0};

        this.boundFlag = new MGLongArray(OpenCLTranslator.BOUNDFLAG,
                        new StaticUnboxer.LongArray(boundFlagVal,
                                        new ArrayInfo(long[].class, 1),
                                        boundFlagVal.hashCode(),
                                        true),
                        null);

        this.ofFlagVal = new long[]{0};

        this.ofFlag = new MGLongArray(OpenCLTranslator.OVERFLOWFLAG,
                        new StaticUnboxer.LongArray(ofFlagVal,
                                        new ArrayInfo(long[].class, 1),
                                        ofFlagVal.hashCode(),
                                        true),
                        null);
        init();
        // log.setOptionValue("GeneratedCode", this.generatedSrc);
    }

    @TruffleBoundary
    public void init() {
        ranges = env.getRanges();
        long totalIterations = 1;
        for (int i = 0; i < levels; i++)
            totalIterations *= (ranges[0][1] - ranges[0][0]);
        log.setOptionValue("TotalParallelLoops", totalIterations);

        if (MGOptions.Backend.Debug > 3)
            MGLog.printlnTagged("totalIterations: " + totalIterations);

        if (!initialized) {
            initialized = true;
            if (generatedSrc == null) {
                long st = System.currentTimeMillis();
                OpenCLTranslator translator = new OpenCLTranslator(env, parameters, orderedParameters, localFunctions, kernelName, kernelFile, log);
                this.generatedSrc = translator.generateSrc(kernelBody);
                log.setOptionValue("Recycled", false);
                log.setOptionValue("CodeGenerationTime", (System.currentTimeMillis() - st));
                log.setOptionValue("GeneratedCode", this.generatedSrc);
                log.setOptionValue("TotalGeneratedKernels", 1);
                generatedSrcs.put(kernelBody.hashCode(), generatedSrc);
                log.printGeneratedCode(this.generatedSrc);
            }

            if (OpenCLMGR.MGR.isValidAutoDevice()) {
                this.device = OpenCLAutoDevice.getDevice(generatedSrc, log);
                if (lockDevice) {
                    OpenCLAutoDevice.deviceLocked = true;
                }
            } else {
                if (MGOptions.Backend.target == ExecutionMode.OpenCLCPU) {
                    this.device = OpenCLMGR.MGR.getBestCPU();
                    log.setOptionValue("FinalExecutionMode", device.getDeviceName());
                } else {
                    this.device = OpenCLMGR.MGR.getBestGPU();
                    log.setOptionValue("FinalExecutionMode", device.getDeviceName());
                }
            }
            if (!isReady()) {
                compile();
            }
            log.setOptionValue("ExecutionMode", device.getDeviceName());
            this.deviceBoundFlag = OpenCLData.initData(device, boundFlag);
            this.deviceOFFlag = OpenCLData.initData(device, ofFlag);

        }

        if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto) {
            OpenCLAutoDevice.reportIterationCount(generatedSrc, log);
        }
    }

    @TruffleBoundary
    public void reset() {
        this.initialized = false;
        this.readOnly.clear();
        this.write.clear();
        this.dataHashes.clear();
        this.swapParameter.clear();
        this.totalDataSize = 0;
        this.boundFlagVal[0] = 0;
        this.ofFlagVal[0] = 0;
    }

    private long getWorkloadSize(ParallelWorkload load) {
        long size = 1;
        final int[] dim = load.getDim();
        switch (load.getType()) {
            case GlobalSize:
                for (int d : dim)
                    size *= globalSize[d];
                break;
            case GroupSize:
                for (int d : dim)
                    size *= groupSize[d];
                break;
            case LocalSize:
                for (int d : dim)
                    size *= localSize[d];
                break;

        }
        return size;
    }

    @TruffleBoundary
    private void populateWorkloadStorage() {
        for (MGArray array : this.workloads.keySet()) {
            ParallelWorkload load = (ParallelWorkload) array.getValue();
            if (load.getType() == LoadType.LocalSize)
                continue;

            final boolean c = load.ensureCapacity(getWorkloadSize(load));

            final OpenCLData d = OpenCLData.initData(device, array);
            if (c || !d.getOnDeviceData(device).isLoaded()) {
                d.getOnDeviceData(device).put(load.getBoxed().getValue());
            }
            this.workloads.replace(array, d);
        }
    }

    @TruffleBoundary
    public void prepareData() {
        for (Entry<String, MGStorage> entry : this.parameters.entrySet()) {
            Object value = null;
            value = entry.getValue().getValue();
            if (value == null) {
                throw CompilationException.INSTANCE.message(String.format("Variable '%s' is NULL!", entry.getKey()));
            }
            final int valueHashCode = value.hashCode();
            if (value instanceof ParallelWorkload) {
                if (((ParallelWorkload) value).getType() == LoadType.LocalSize)
                    continue;
                this.workloads.put((MGArray) entry.getValue(), null);
                continue;
            }
            if (entry.getValue() instanceof MGArray && !(value instanceof Unboxer)) {
                value = env.getArrayUnboxer(valueHashCode);
                if (value == null) {
                    throw CompilationException.INSTANCE.message(String.format("Variable '%s' storage not found!", entry.getKey()));
                }
                entry.getValue().setValue(value);
            }
            if (value instanceof Unboxer) {
                Unboxer boxed = ((Unboxer) value);
                value = boxed.getValue();
                final int hashCode = value.hashCode();
                if (entry.getValue().getOrigin() instanceof MGArray) {
                    final MGArray array = (MGArray) entry.getValue().getOrigin();

                    if (changesTracker != null) {
                        changesTracker.setDevice(device);
                        @SuppressWarnings("unused")
                        final String name = array.getName();
                        final int index = changesTracker.getIndex(hashCode);
                        final boolean changed = changesTracker.updateDataForOpenCL(index);

                        if (changed) {
                            boxed.setChanged(true);
                        }
                    }

                    OpenCLData d = OpenCLData.initData(device, array);

                    if (entry.getValue().getOrigin().isReadOnly()) {
                        this.readOnly.add(d);
                    } else {
                        this.write.put(d.getHashCode(), d);
                    }
                    this.dataHashes.put(hashCode, value);
                    this.totalDataSize += d.getDataSize();
                }
            }

        }
    }

    @TruffleBoundary
    private boolean loadReadOnly() {
        boolean success = true;
        long st = System.currentTimeMillis();
        for (OpenCLData d : this.readOnly) {
            Object value = dataHashes.get(d.getHashCode());
            success = success && (value != null);
            success = success && d.getOnDeviceData(device).put(value, MGOptions.Backend.disableDataManagementOptimization);
        }
        log.setOptionValue("DataTransferTime", log.getOptionValueLong("DataTransferTime") + (System.currentTimeMillis() - st));
        return success;
    }

    @TruffleBoundary
    private boolean loadWrites() {
        boolean success = true;
        long st = System.currentTimeMillis();
        for (OpenCLData d : this.write.values()) {
            Object value = dataHashes.get(d.getHashCode());
            success = success && (value != null);
            success = success && d.getOnDeviceData(device).put(value, MGOptions.Backend.disableDataManagementOptimization);
        }

        log.setOptionValue("DataTransferTime", log.getOptionValueLong("DataTransferTime") + (System.currentTimeMillis() - st));
        return success;
    }

    @TruffleBoundary
    private void preExecution() {

        prepareData();

        boolean success = device.ensureMemoryAllocation(readOnly, new ArrayList<>(write.values()), totalDataSize);
        if (!success) {
            throw CompilationException.INSTANCE.message("Data too large for '" + device.getDeviceName() + "' memory.");
        }
        success = success && loadReadOnly();
        if (!success) {
            throw CompilationException.INSTANCE.message("Failed to load read only data.");
        }

        boolean allReadOnlyLoaded = true;
        for (int i = 0; i < this.readOnly.size() && allReadOnlyLoaded; i++) {
            allReadOnlyLoaded = allReadOnlyLoaded && this.readOnly.get(i).getOnDeviceData(device).isLoaded();
        }
        if (!allReadOnlyLoaded)
            loadReadOnly();

        loadWrites();
    }

    @TruffleBoundary
    public boolean isReady() {
        if (device.kernels.containsKey(generatedSrc)) {
            ready = true;
        } else {
            ready = false;
        }
        return ready;
    }

    @TruffleBoundary
    protected void internalCompile() throws CompilationException {
        org.jocl.cl_program program = clCreateProgramWithSource(device.getContext(), 1, new String[]{generatedSrc}, null, null);
        int[] errcode_ret = new int[1];
        boolean success = CL.CL_SUCCESS == clBuildProgram(program, 0, null, "", null, errcode_ret);
        org.jocl.cl_kernel kernel = clCreateKernel(program, kernelName, errcode_ret);
        success = success && errcode_ret[0] == CL.CL_SUCCESS;
        if (!success) {
            throw CompilationException.INSTANCE.message(String.format("Failed to compile the kernel '%s'", kernelName));
        }
        device.kernels.put(generatedSrc, kernel);

    }

    @TruffleBoundary
    public void compile() {
        long start = System.currentTimeMillis();
        if (!device.kernels.containsKey(generatedSrc))
            internalCompile();
        log.setOptionValue("CompilationTime", (System.currentTimeMillis() - start));
        ready = true;
    }

    @TruffleBoundary
    private boolean setKernelArgs() {
        int argv = 0;
        boolean success = deviceBoundFlag.getOnDeviceData(device).put(((Unboxer) boundFlag.getValue()).getValue());
        success = success && deviceOFFlag.getOnDeviceData(device).put(((Unboxer) ofFlag.getValue()).getValue());
        org.jocl.cl_kernel kernel = device.kernels.get(generatedSrc);
        for (int i = 0; i < this.orderedParameters.length; i++) {
            String argName = this.orderedParameters[i];
            if (!swapParameter.isEmpty() && swapParameter.containsKey(argName)) {
                argName = swapParameter.get(argName);
            }
            MGStorage s = this.parameters.get(argName);
            Object o = s.getValue();
            if (o instanceof Unboxer) {
                OpenCLData d = OpenCLData.getData(((Unboxer) o).getValue().hashCode());
                success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, Sizeof.cl_mem, Pointer.to(d.getOnDeviceData(device).getCLMem()));
            } else if (o instanceof ParallelWorkload) {
                if (((ParallelWorkload) o).getType() != LoadType.LocalSize) {
                    OpenCLData d = OpenCLData.getData(((ParallelWorkload) o).getBoxed().getValue().hashCode());
                    success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, Sizeof.cl_mem, Pointer.to(d.getOnDeviceData(device).getCLMem()));
                } else {
                    final long size = getWorkloadSize((ParallelWorkload) o);
                    int ssize = OpenCLData.getTypeSize(s);
                    success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, ssize * size, null);
                }
            } else {
                success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, OpenCLData.getTypeSize(s), OpenCLData.getValuePointer(s));
            }
        }
        for (int i = 0; i < levels; i++) {
            // offset
            success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, Sizeof.cl_uint, Pointer.to(new int[]{((Long) ranges[i][0]).intValue()}));
            // step
            success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, Sizeof.cl_uint, Pointer.to(new int[]{((Long) ranges[i][2]).intValue()}));
        }

        success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, Sizeof.cl_mem, Pointer.to(deviceBoundFlag.getOnDeviceData(device).getCLMem()));
        success = success && CL.CL_SUCCESS == clSetKernelArg(kernel, argv++, Sizeof.cl_mem, Pointer.to(deviceOFFlag.getOnDeviceData(device).getCLMem()));
        return success;
    }

    @TruffleBoundary
    private void getWrites() {
        boolean success = true;
        long st = System.currentTimeMillis();
        if (changesTracker != null) {
            for (int hashCode : this.write.keySet()) {
                final int index = changesTracker.getIndex(hashCode);
                changesTracker.setOpenCLChanged(index);
            }
        } else {
            for (OpenCLData d : this.write.values()) {
                success = success && d.getOnDeviceData(device).get();
            }
        }
        for (Entry<MGArray, OpenCLData> entry : this.workloads.entrySet()) {
            final MGArray array = entry.getKey();
            final OpenCLData d = entry.getValue();
            if (!array.isReadOnly()) {
                success = success && d.getOnDeviceData(device).get();
            }
        }
        log.setOptionValue("DataTransferTime", log.getOptionValueLong("DataTransferTime") + (System.currentTimeMillis() - st));

    }

    @TruffleBoundary
    private void discardWrites() {
        for (OpenCLData d : this.write.values()) {
            d.getOnDeviceData(device).clean();
        }
    }

    @TruffleBoundary
    private void postExecution() throws BoundException {
        deviceBoundFlag.getOnDeviceData(device).get();
        deviceOFFlag.getOnDeviceData(device).get();
        boolean success = ((long[]) ((Unboxer) boundFlag.getValue()).getValue())[0] == 0;
        success = success && ((long[]) ((Unboxer) ofFlag.getValue()).getValue())[0] == 0;
        if (success) {
            deviceBoundFlag.getOnDeviceData(device).clean();
            deviceOFFlag.getOnDeviceData(device).clean();
            getWrites();
        } else {
            discardWrites();
            throw BoundException.INSTANCE.message("Runtime bound violation or overflowed (Execution discarded)!");
        }
    }

    @TruffleBoundary
    public synchronized void execute(int offset) throws BoundException, CompilationException {
        ranges[0][0] = offset;

        if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto) {
            if (MGOptions.Backend.Debug > 0) {
                log.println("Adaptive execution using '" + device.getDeviceName() + "'.");
            }
        }
        preExecution();
        org.jocl.cl_kernel kernel = device.kernels.get(generatedSrc);
        globalSize = OpenCLUtil.getGlobalWorkSize(ranges, levels);
        populateWorkloadStorage();
        boolean success = false;
        while (!success) {
            localSize = OpenCLUtil.createLocalSize(kernel, device, globalSize, levels, log);
            setKernelArgs();
            final int lastLevel = levels - 1;
            if (env.getGlobalLoopInfos()[lastLevel].isReductionOpt()) {
                final long v = Math.max(Math.min(globalSize[lastLevel], localSize[lastLevel]), 1);
                globalSize[lastLevel] = v > 2 && v % 2 == 1 ? v - 1 : v;
                localSize[lastLevel] = globalSize[lastLevel];
            }
            try {
                success = internalKernalExecution(kernel, null, globalSize, localSize);
                if (!success) {
                    OpenCLUtil.reportLocalSizeFailure(kernel, device, localSize);
                }
            } catch (CLException e) {
                OpenCLUtil.reportLocalSizeFailure(kernel, device, localSize);

            }
        }
        // CL.clFinish(device.getCommandQueue());
        // CL.clFlush(device.getCommandQueue());
        postExecution();

        internalClean();
    }

    @TruffleBoundary
    public synchronized boolean internalKernalExecution(org.jocl.cl_kernel kernel, long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize) {

        long nano = System.nanoTime();
        long st = System.currentTimeMillis();
        org.jocl.cl_event event = new org.jocl.cl_event();

        boolean success = CL.CL_SUCCESS == clEnqueueNDRangeKernel(
                        device.getCommandQueue(),
                        kernel, levels, globalWorkOffset,
                        globalWorkSize, localWorkSize, 0, null, event);

        success = success && CL.CL_SUCCESS == clWaitForEvents(1, new org.jocl.cl_event[]{event});
        log.setOptionValue("CoreExecutionTime", (System.currentTimeMillis() - st));
        if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto) {
            OpenCLAutoDevice.reportKernelTime(generatedSrc, log, (System.nanoTime() - nano));
        }
        return success;
    }

    @TruffleBoundary
    private void internalClean() {
        this.dataHashes.clear();
        this.readOnly.clear();
        this.write.clear();
        this.workloads.clear();
    }

    @TruffleBoundary
    public static synchronized void clean(boolean totalClean) {
        OpenCLData.clean();
        if (totalClean) {
            OpenCLAutoDevice.clean();
            OpenCLMGR.MGR.clean();
        }
    }

    @TruffleBoundary
    public void executeReduction() {
        long s = System.currentTimeMillis();
        if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto) {
            if (MGOptions.Backend.Debug > 0) {
                log.println("Adaptive execution using '" + device.getDeviceName() + "'.");
            }
        }

        preExecution();
        long st = System.currentTimeMillis();
        org.jocl.cl_kernel kernel = device.kernels.get(generatedSrc);
        OpenCLUtil.setWorkloadSizes(device, ranges, levels, globalSize, localSize, groupSize);
        populateWorkloadStorage();
        // globalSize = OpenCLUtil.getGlobalWorkSize(ranges, levels);
        // localSize = OpenCLUtil.createLocalSize(kernel, device, globalSize, levels, log);
        setKernelArgs();
        internalKernalExecution(kernel, null, globalSize, localSize);
        boolean result1 = true;
        if (groupSize[0] > 1) {
            int n = ((Long) groupSize[0]).intValue();
            String inputLength = ParallelFunctions.Reduce.listSize;
            this.parameters.get(inputLength).updateValue(n);
            this.ranges[0][1] = n;
            final int i11, i12, i21, i22;
            if (reductionSwitcher != null) {
                this.parameters.get(reductionSwitcher).updateValue(0);
                i11 = i22 = 1;
                i12 = i21 = 2;
            } else {
                i11 = 0;
                i12 = i21 = 1;
                i22 = 2;
            }
            OpenCLUtil.setWorkloadSizes(device, ranges, levels, globalSize, localSize, groupSize);
            for (String[] swp : swapping) {
                this.swapParameter.put(swp[i11], swp[i12]);
                this.swapParameter.put(swp[i21], swp[i22]);
            }
            setKernelArgs();
            this.swapParameter.clear();
            internalKernalExecution(kernel, null, globalSize, localSize);
            result1 = false;
        }
        log.setOptionValue("CoreExecutionTime", (System.currentTimeMillis() - st));

        postExecution();
        final int idx = result1 ? 1 : 2;
        for (int i = 0; i < swapping.size(); i++) {
            final String var = swapping.get(i)[idx];
            final ReductionWorkload l = (ReductionWorkload) this.parameters.get(var).getValue();
            final StaticUnboxer boxed = (StaticUnboxer) l.getBoxed();
            env.setResult(boxed.getFirstValue(), i);
        }

        if (MGOptions.Backend.target == ExecutionMode.OpenCLAuto) {
            OpenCLAutoDevice.reportKernelTime(generatedSrc, log, (System.currentTimeMillis() - s));
        }
        internalClean();
    }

    public void setChangesTracker(MGObjectTracker changesTracker) {
        this.changesTracker = changesTracker;
    }

    public void setSwapping(ArrayList<String[]> swapping) {
        this.swapping = swapping;
    }

    public void setLockDevice() {
        this.lockDevice = true;
    }

    @TruffleBoundary
    public static synchronized void cleanUp(boolean totalClean) {
        clean(totalClean);
    }

    public void setLog(MGLog l) {
        this.log = l;
    }

    public void invalidateSource() {
        generatedSrc = null;
    }

}
