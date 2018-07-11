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
package edu.uci.megaguards.backend;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.BoundInvalidateException;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.ast.MGTree;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.fallback.MGFallbackHandler;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.MGStorage;

public abstract class MGFor<T extends Node, R> extends MGRoot<T, R> {

    protected final MGFallbackHandler<?> fallback;

    public MGFor(MGTree<T, R> baseTree, MGFallbackHandler<?> fallback, Type type) {
        super(baseTree, type);
        this.fallback = fallback;
    }

    public MGFor(MGFor<T, R> fornode, Type type) {
        this(fornode.baseTree, fornode.fallback, type);
    }

    public boolean isFailed() {
        return false;
    }

    public MGFallbackHandler<?> getFallback() {
        return fallback;
    }

    public abstract MGFor<T, R> forLoop(VirtualFrame frame, SourceSection s, String fn, T iv, T body, int start, int stop, int step);

    public abstract void forLoop(VirtualFrame frame, int start, int stop, int step);

    public static class Uninitialized<T extends Node, R> extends MGFor<T, R> {

        public Uninitialized(MGTree<T, R> baseTree, MGFallbackHandler<?> fallback) {
            super(baseTree, fallback, Type.UNINITIALIZED);
        }

        public Uninitialized(MGFor<T, R> fornode) {
            super(fornode, Type.UNINITIALIZED);
        }

        @Override
        public void forLoop(VirtualFrame frame, int start, int stop, int step) {
            throw new IllegalStateException();
        }

        public void translateTruffleForNode(VirtualFrame frame, MGNodeOptions options, MGGlobalEnv env, MGLog log, T iv, T body, long[] range) throws MGException {
            final long s = System.currentTimeMillis();
            baseTree.create(env, frame, log).buildForLoopTree(this, iv, body, range, options);
            env.mergePrivateParameters();
            env.setRootNode(rootNode);
            log.setOptionValue("TranslationTime", (System.currentTimeMillis() - s));
        }

        @Override
        public Ready<T, R> forLoop(VirtualFrame frame, SourceSection s, String fn, T iv, T body, int start, int stop, int step) {
            final long startTime = System.currentTimeMillis();
            final MGGlobalEnv env = new MGGlobalEnv(fn);
            final MGLog log = new MGLog(s);
            final FinalizedVariableValues finalizedValues = new FinalizedVariableValues(env);
            DirectCallNode newCall = null;
            MGInvoke invoke = null;
            Type t = Type.TRUFFLE;
            final MGNodeOptions options = MGNodeOptions.getOptions(body.hashCode());
            String logKey = "TotalTruffleExecutions";
            try {
                translateTruffleForNode(frame, options, env, log, iv, body, new long[]{start, stop, step});
                processBoxedData(log);
                translateBounds(env, log);
                boundCheck(finalizedValues, false, log);
                boolean isDone = false;
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (MGOptions.Backend.target != ExecutionMode.Truffle) {
                    if (env.isOuterBreak() || env.isIllegalForOpenCL()) {
                        try {
                            MGParallel.dataDependenceAnalysis(options, env, coreComputeNode, finalizedValues, log);
                        } catch (MGException e) {
                            // pass through to use Truffle back-end
                        }

                    } else {
                        try {
                            if (!MGOptions.Backend.allowInAccurateMathFunctions) {
                                checkMathFunctions(env);
                            }
                            MGParallel parallelInvoke = MGParallel.createLoop(s, options, rootNode, coreComputeNode, env, finalizedValues, log);
                            final DirectCallNode call = parallelInvoke.createCallNode();
                            call.call(new Object[]{start, stop, step, log});
                            newCall = call;
                            invoke = parallelInvoke;
                            logKey = "TotalKernelExecutions";
                            if (MGOptions.Backend.Debug > 0) {
                                log.println("Total iterations: " + log.getOptionValueLong("TotalParallelLoops"));
                            }
                            t = Type.OPENCL;
                            isDone = true;
                        } catch (MGException e) {
                            // pass through to use Truffle back-end
                        }
                    }
                }

                if (!isDone) {
                    final MGTruffle truffleInvoke = MGTruffle.createLoop(env, log);
                    final DirectCallNode call = truffleInvoke.createCallNode();
                    call.call(new Object[]{start, stop, step, log});
                    newCall = call;
                    invoke = truffleInvoke;
                }

                CompilerDirectives.transferToInterpreterAndInvalidate();
                env.clearValues();
            } catch (MGException e) {
                fallback.handleException(e);
                throw e;
            }

            log.setOptionValue("TotalTime", System.currentTimeMillis() - startTime);
            log.setOptionValue(logKey, 1);
            MGLog.addLog(log);

            if (MGOptions.logging)
                log.printLog();

            if (MGOptions.Backend.Debug > 0) {
                log.println("Total iterations: " + log.getOptionValueLong("TotalParallelLoops"));
            }

            fallback.resetLimit();

            return new Ready<>(this, env, s, logKey, finalizedValues, invoke, newCall, t);
        }

    }

    public static class Ready<T extends Node, R> extends MGFor<T, R> {

        @Child protected MGInvoke invoke;
        @Child protected DirectCallNode callNode;
        protected final MGGlobalEnv env;
        protected final SourceSection source;
        protected final FinalizedVariableValues finalizedValues;
        protected final String logKey;
        private final MGStorage[] list;
        private final Object[] values;

        public Ready(MGFor<T, R> baseCall, MGGlobalEnv env, SourceSection source, String logKey, FinalizedVariableValues finalizedValues, MGInvoke invoke, DirectCallNode callNode, Type type) {
            super(baseCall, type);
            this.invoke = invoke;
            this.callNode = callNode;
            this.env = env;
            this.source = source;
            this.finalizedValues = finalizedValues;
            this.logKey = logKey;
            list = env.getStorageList();
            values = new Object[list.length];
        }

        @Override
        public boolean isUninitialized() {
            return false;
        }

        private void reloadGlobalLoopInfos() {
            finalizedValues.reloadGlobalLoopInfos();
        }

        @ExplodeLoop
        private void megaguard(VirtualFrame frame) {
            for (int i = 0; i < list.length; i++) {
                values[i] = list[i].getValueNode().getUnboxed(frame);
            }

            for (int i = 0; i < list.length; i++) {
                list[i].updateValue(values[i]);
            }
        }

        public void execute(long startTime, int start, int stop, int step, MGLog log) {
            try {
                callNode.call(new Object[]{start, stop, step, log});
            } catch (MGException e) {
                fallback.handleException(e);
                throw e;
            }
            finalizedValues.reset();
            env.clearValues();
            log.setOptionValue("TotalTime", System.currentTimeMillis() - startTime);
            log.setOptionValue(logKey, 1);
            MGLog.addLog(log);

            if (MGOptions.logging)
                log.printLog();

            if (MGOptions.Backend.Debug > 0) {
                log.println("Total iterations: " + log.getOptionValueLong("TotalParallelLoops"));
            }

            fallback.resetLimit();

        }

        @Override
        public void forLoop(VirtualFrame frame, int start, int stop, int step) {
            long startTime = System.currentTimeMillis();
            megaguard(frame);
            env.reloadConstantLongValues();
            // guard();
            env.setRanges(new long[]{start, stop, step}, 0);
            MGLog log = new MGLog(source);
            try {
                processBoxedData(log);
                reloadGlobalLoopInfos();
                try {
                    boundCheck(finalizedValues, true, log);
                } catch (BoundInvalidateException b) {
                    boundCheck(finalizedValues, false, log);
                    if (type == Type.OPENCL) {
                        invoke.invalidate(log);
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        final MGInvoke truffleInvoke = invoke.invalidate(log);
                        replace(new Ready<>(this, env, source, logKey, finalizedValues, truffleInvoke, truffleInvoke.createCallNode(), type)).execute(startTime, start, stop, step, log);
                        return;
                    }
                }
            } catch (MGException e) {
                fallback.handleException(e);
                fallback.setReason(e.getClass().getSimpleName());
                throw e;
            }
            execute(startTime, start, stop, step, log);
        }

        @Override
        public MGFor<T, R> forLoop(VirtualFrame frame, SourceSection s, String fn, T iv, T body, int start, int stop, int step) {
            return new Uninitialized<>(this).forLoop(frame, s, fn, iv, body, start, stop, step);
        }

    }

}
