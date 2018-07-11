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

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.log.MGLog;

public class OpenCLAutoDevice {

    private static final HashMap<String, HashMap<Long, OpenCLAutoDevice>> SELECTIONS = new HashMap<>();

    private static final int TRIES = MGOptions.Backend.AutoTries;

    public static boolean deviceLocked = false;
    public static Mode lockedMode = Mode.TRYCPU;

    private static final double SPEEDUPDIFF = ((double) MGOptions.Backend.AutoDiff) / 100;

    @TruffleBoundary
    public static OpenCLDevice getDevice(String kernel, MGLog log) {
        final long totalParallelLoops = log.getOptionValueLong("TotalParallelLoops");
        reportIterationCount(kernel, log);
        return SELECTIONS.get(kernel).get(totalParallelLoops).getDevice(log);
    }

    @TruffleBoundary
    public static void reportIterationCount(String kernel, MGLog log) {
        final long totalParallelLoops = log.getOptionValueLong("TotalParallelLoops");
        if (!SELECTIONS.containsKey(kernel)) {
            SELECTIONS.put(kernel, new HashMap<>());
        }

        if (!SELECTIONS.get(kernel).containsKey(totalParallelLoops)) {
            SELECTIONS.get(kernel).put(totalParallelLoops, new OpenCLAutoDevice());
        }

    }

    public static void reportKernelTime(String kernel, MGLog log, long time) {
        final long totalParallelLoops = log.getOptionValueLong("TotalParallelLoops");
        final HashMap<Long, OpenCLAutoDevice> iterationDevice = SELECTIONS.get(kernel);
        final OpenCLAutoDevice auto = iterationDevice.get(totalParallelLoops);
        auto.setKernelTime(time);
    }

    public static OpenCLDevice getLockedDeviceAndUnlock() {
        deviceLocked = false;
        switch (lockedMode) {
            case GPU:
            case TRYGPU:
                return OpenCLMGR.MGR.getBestGPU();

            case CPU:
            case TRYCPU:
                return OpenCLMGR.MGR.getBestCPU();
        }

        return null;
    }

    enum Mode {
        TRYGPU,
        TRYCPU,
        GPU,
        CPU,
    }

    private Mode mode;
    private long timeGPU;
    private long timeCPU;
    private int triesGPU;
    private int triesCPU;

    @TruffleBoundary
    public OpenCLAutoDevice() {
        timeGPU = Long.MAX_VALUE;
        timeCPU = Long.MAX_VALUE;
        mode = Mode.TRYCPU;
        triesGPU = TRIES;
        triesCPU = TRIES;
    }

    private void setSelection(MGLog log) {
        if (mode == Mode.GPU || mode == Mode.CPU)
            return;

        if (timeGPU != Long.MAX_VALUE && timeCPU != Long.MAX_VALUE) {
            final double speedup = (double) timeGPU / (double) timeCPU;
            String speedupDevice = (speedup >= 1) ? String.format("CPU: %.3fx", speedup) : String.format("GPU: %.3fx", 1 / speedup);
            String msg = String.format("Iter: %-16d\tGPU: %-10d (%-2d)\tCPU: %-10d (%-2d)\t%-12s", log.getOptionValueLong("TotalParallelLoops"), timeGPU, triesGPU, timeCPU, triesCPU, speedupDevice);

            if (triesGPU <= 0 && triesCPU <= 0) {
                mode = (speedup > 1) ? Mode.CPU : Mode.GPU;
                msg += "\tDevice: " + mode + "\n";
            } else if (triesGPU == TRIES - 1 || triesCPU == TRIES - 1) {
                msg += "\t(1st Try)\n";
            } else if (triesGPU > 0 || triesCPU > 0) {
                msg += "\t(Retry)\n";
            } else if (speedup > (1.0 + SPEEDUPDIFF)) {
                mode = Mode.CPU;
                msg += "\tDevice: " + mode + "\n";
            } else if (speedup < (SPEEDUPDIFF - 1.0)) {
                mode = Mode.GPU;
                msg += "\tDevice: " + mode + "\n";
            } else {
                msg += "\t(Retry)\n";
            }
            if (MGOptions.Backend.Debug > 0) {
                log.print(msg);
            }
        }
    }

    public OpenCLDevice getDevice(MGLog log) {
        if (!deviceLocked) {
            setSelection(log);
            lockedMode = Mode.values()[mode.ordinal()];
        } else {
            mode = Mode.values()[lockedMode.ordinal()];
        }
        if (OpenCLMGR.MGR.getNumDevices() == 1) {
            if (OpenCLMGR.MGR.getBestGPU() == null) {
                return OpenCLMGR.MGR.getBestCPU();
            } else {
                return OpenCLMGR.MGR.getBestGPU();
            }
        }
        OpenCLDevice device = OpenCLMGR.MGR.getBestGPU();
        switch (mode) {
            case GPU:
                device = OpenCLMGR.MGR.getBestGPU();
                log.setOptionValue("FinalExecutionMode", device.getDeviceName());
                break;
            case TRYGPU:
                device = OpenCLMGR.MGR.getBestGPU();
                break;

            case CPU:
                device = OpenCLMGR.MGR.getBestCPU();
                log.setOptionValue("FinalExecutionMode", device.getDeviceName());
                break;

            case TRYCPU:
                device = OpenCLMGR.MGR.getBestCPU();
                break;
        }

        return device;
    }

    public void setKernelTime(long time) {
        if (mode == Mode.TRYGPU) {
            if (MGOptions.Backend.AutoMethod == 0)
                timeGPU = Math.min(timeGPU, time);
            else
                timeGPU = (timeGPU != Long.MAX_VALUE) ? (timeGPU + time) / 2 : time;
            triesGPU--;
            mode = Mode.TRYCPU;
            return;
        }

        if (mode == Mode.TRYCPU) {
            if (MGOptions.Backend.AutoMethod == 0)
                timeCPU = Math.min(timeCPU, time);
            else
                timeCPU = (timeCPU != Long.MAX_VALUE) ? (timeCPU + time) / 2 : time;
            triesCPU--;
            mode = Mode.TRYGPU;
            return;
        }
    }

    @TruffleBoundary
    public static void clean() {
        SELECTIONS.clear();
    }

}
