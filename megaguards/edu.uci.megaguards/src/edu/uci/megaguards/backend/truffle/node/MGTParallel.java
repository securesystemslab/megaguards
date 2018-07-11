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
package edu.uci.megaguards.backend.truffle.node;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.MGObjectTracker;
import edu.uci.megaguards.backend.MGParallel;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLAutoDevice;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLDevice;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLMGR;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGStorage;

public final class MGTParallel extends MGTNode<Object> {

    @Child protected MGParallel loop;
    protected final MGGlobalEnv loopEnv;
    @Child protected MGTNode<Integer> start;
    @Child protected MGTNode<Integer> stop;
    @Child protected MGTNode<Integer> step;
    @Child protected DirectCallNode callNode;
    @Children protected final MGTNode<?>[] valueNodes;
    protected final MGStorage[] local;
    protected final Object[] values;
    protected final SourceSection sourceSection;

    public MGTParallel(MGParallel funcRoot, MGGlobalEnv loopEnv, MGTNode<Integer> start, MGTNode<Integer> stop, MGTNode<Integer> step, MGStorage[] local, MGTNode<?>[] valueNodes, MGLog log) {
        super(DataType.None);
        this.loop = funcRoot;
        this.loopEnv = loopEnv;
        this.start = start;
        this.stop = stop;
        this.step = step;
        this.local = local;
        this.valueNodes = valueNodes;
        this.values = local == null ? null : new Object[local.length];
        this.sourceSection = log.getSourceSection();
        this.callNode = funcRoot.createCallNode();
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame) {
        long startTime = System.currentTimeMillis();
        final int s = start.execute(frame);
        final int t = stop.execute(frame);
        if (s == t)
            return null;
        final int e = step.execute(frame);
        loopEnv.getRanges()[0][0] = s;
        loopEnv.getRanges()[0][1] = t;
        loopEnv.getRanges()[0][2] = e;
        final MGLog log = new MGLog(sourceSection);
        for (int i = 0; i < valueNodes.length; i++) {
            values[i] = valueNodes[i].execute(frame);
        }
        for (int i = 0; i < valueNodes.length; i++) {
            local[i].setValue(values[i]);
        }
        callNode.call(new Object[]{s, t, e, log});
        log.setOptionValue("TotalTime", System.currentTimeMillis() - startTime);
        log.setOptionValue("TotalKernelExecutions", 1);
        MGLog.addLog(log);

        if (MGOptions.logging)
            log.printLog();

        if (MGOptions.Backend.Debug > 0) {
            log.println("Total iterations: " + log.getOptionValueLong("TotalParallelLoops") + "  outter loop offloaded at: " + start);
        }

        return null;
    }

    public static final class OpenCLGetData extends MGTControl {

        public static final MGLog sharedDTLog = new MGLog(null);

        private final MGObjectTracker changesTracker;

        public OpenCLGetData(MGObjectTracker changesTracker) {
            super(DataType.None);
            this.changesTracker = changesTracker;
        }

        @TruffleBoundary
        private void processOpenCL() {
            final OpenCLDevice device;
            if (OpenCLMGR.MGR.isValidAutoDevice()) {
                device = OpenCLAutoDevice.getLockedDeviceAndUnlock();
            } else {
                if (MGOptions.Backend.target == ExecutionMode.OpenCLCPU) {
                    device = OpenCLMGR.MGR.getBestCPU();
                } else {
                    device = OpenCLMGR.MGR.getBestGPU();
                }
            }

            if (device == null)
                throw new IllegalStateException("Device is NULL!");

            long st = System.currentTimeMillis();

            changesTracker.getAllOpenCLData();

            sharedDTLog.setOptionValue("DataTransferTime", sharedDTLog.getOptionValueLong("DataTransferTime") + (System.currentTimeMillis() - st));
            changesTracker.reset();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            processOpenCL();
            return null;
        }

    }

}
