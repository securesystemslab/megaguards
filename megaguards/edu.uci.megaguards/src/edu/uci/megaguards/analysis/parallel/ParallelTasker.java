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
package edu.uci.megaguards.analysis.parallel;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.backend.MGParallel;
import edu.uci.megaguards.log.MGLog;

public class ParallelTasker extends Thread {

    private static Compilation compiling = Compilation.getInstance();

    private MGParallel parallelNode;
    private final MGGlobalEnv env;
    private final SourceSection sourceSection;
    private final MGNodeOptions options;
    private final MGNode rootNode;
    private final MGNode coreComputeNode;
    private final FinalizedVariableValues finalizedValues;
    private final MGLog log;
    private Exception err;
    private boolean isReady;
    private boolean isFailed;

    @TruffleBoundary
    public ParallelTasker(SourceSection sourceSection, MGNodeOptions options, MGNode rootNode, MGNode coreComputeNode, MGGlobalEnv env, FinalizedVariableValues finalizedValues, MGLog log) {
        this.sourceSection = sourceSection;
        this.options = options;
        this.rootNode = rootNode;
        this.coreComputeNode = coreComputeNode;
        this.env = env;
        this.finalizedValues = finalizedValues;

        this.log = log;
        this.parallelNode = null;
        this.err = null;
        this.isReady = false;
        this.isFailed = false;
    }

    public void prepare() {
        try {
            long startTime = System.currentTimeMillis();
            parallelNode = MGParallel.createLoop(sourceSection, options, rootNode, coreComputeNode, env, finalizedValues, log);
            log.setOptionValue("TotalTime", log.getOptionValueLong("TotalTime") + (System.currentTimeMillis() - startTime));
            this.isReady = true;
        } catch (Exception e) {
            this.err = e;
            this.isFailed = true;
            log.printException(e);
        }
    }

    @TruffleBoundary
    @Override
    public void run() {
        synchronized (compiling) {
            if (compiling.isCompilationInProgress()) {
                try {
                    compiling.wait();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
            try {
                compiling.setCompilationInProgress();
                prepare();
                compiling.releaseCompilationInProgress();
            } catch (Exception e) {
                this.err = e;
                this.isFailed = true;
            }
            compiling.notify();
        }
    }

    @TruffleBoundary
    public synchronized void execute(int offset) throws Exception {
        long startTime = System.currentTimeMillis();
        log.setOptionValue("OffloadOffset", offset);
        parallelNode.getExecuter().execute(offset);
        log.setOptionValue("TotalTime", log.getOptionValueLong("TotalTime") + (System.currentTimeMillis() - startTime));

        log.setOptionValue("TotalKernelExecutions", 1);
    }

    @TruffleBoundary
    public synchronized void executeReduction() throws Exception {
        long startTime = System.currentTimeMillis();
        parallelNode.getExecuter().executeReduction();
        log.setOptionValue("TotalTime", log.getOptionValueLong("TotalTime") + (System.currentTimeMillis() - startTime));

        log.setOptionValue("TotalKernelExecutions", 1);
    }

    @TruffleBoundary
    public boolean isCompiled() {
        return parallelNode.getExecuter().isReady();
    }

    public MGParallel getParallelNode() {
        return parallelNode;
    }

    public Exception getErr() {
        return err;
    }

    @TruffleBoundary
    public synchronized boolean isReady() {
        return isReady && !this.isAlive();
    }

    public boolean isFailed() {
        return isFailed;
    }

    private static class Compilation {

        private static final Compilation INSTANCE = new Compilation();

        public static Compilation getInstance() {
            return INSTANCE;
        }

        private boolean compiling = false;

        public synchronized void setCompilationInProgress() {
            compiling = true;
        }

        public synchronized void releaseCompilationInProgress() {
            compiling = false;
        }

        public synchronized boolean isCompilationInProgress() {
            return compiling;
        }

    }
}
