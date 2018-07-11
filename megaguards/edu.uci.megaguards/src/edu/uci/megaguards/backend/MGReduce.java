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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.analysis.exception.TypeException;
import edu.uci.megaguards.analysis.parallel.ParallelFunctions;
import edu.uci.megaguards.analysis.parallel.reduction.ReductionWorkload;
import edu.uci.megaguards.ast.MGTree;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.MGArgs;
import edu.uci.megaguards.fallback.MGFallbackHandler;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.MGStorage;
import edu.uci.megaguards.unbox.StaticUnboxer;

public abstract class MGReduce<T extends Node, R> extends MGRoot<T, R> {

    protected final MGFallbackHandler<?> fallback;

    public MGReduce(MGTree<T, R> baseTree, MGFallbackHandler<?> fallback, Type type) {
        super(baseTree, type);
        this.fallback = fallback;
    }

    public MGReduce(MGReduce<T, R> mapnode, Type type) {
        this(mapnode.baseTree, mapnode.fallback, type);
    }

    public boolean isFailed() {
        return false;
    }

    public MGFallbackHandler<?> getFallback() {
        return fallback;
    }

    public abstract Object reduce(SourceSection s, R mappingFunction, FrameDescriptor fd, Object iterable, Object initializer, boolean hasInitializer, int iterableLen);

    public abstract Object reduce(R mappingFunction, Object iterable, Object initializer, boolean hasInitializer, int iterableLen);

    public static class Uninitialized<T extends Node, R> extends MGReduce<T, R> {

        public Uninitialized(MGTree<T, R> baseTree, MGFallbackHandler<?> fallback) {
            super(baseTree, fallback, Type.UNINITIALIZED);
        }

        public Uninitialized(MGReduce<T, R> fornode) {
            super(fornode, Type.UNINITIALIZED);
        }

        @Override
        public Object reduce(R mappingFunction, Object iterable, Object initializer, boolean hasInitializer, int iterableLen) {
            throw new IllegalStateException();
        }

        public void translateTruffleReduceNode(VirtualFrame frame, MGNodeOptions options, MGGlobalEnv env, MGLog log, R function, Object iterable, Object initializer, boolean hasInitializer,
                        long[] range) {
            long s = System.currentTimeMillis();
            Object result = baseTree.create(env, frame, log).buildReduceTree(this, function, iterable, initializer, hasInitializer /*- initializer instanceof PNone*/, range, options);
            if (!MGOptions.Backend.allowInAccurateMathFunctions) {
                checkMathFunctions(env);
            }
            env.mergePrivateParameters();
            env.setResultsSize(1);
            env.setResult(result, 0);
            log.setOptionValue("TranslationTime", (System.currentTimeMillis() - s));
        }

        @Override
        public Object reduce(SourceSection s, R mappingFunction, FrameDescriptor fd, Object iterable, Object initializer, boolean hasInitializer, int iterableLen) {
            final long startTime = System.currentTimeMillis();
            final MGGlobalEnv env = new MGGlobalEnv(ParallelFunctions.Reduce.tag);
            final MGLog log = new MGLog(s);
            final int start = 0;
            final int stop = iterableLen;
            final int step = 1;
            final long[] range = new long[]{start, stop, step};
            final MGNodeOptions options = MGNodeOptions.getOptions(mappingFunction.hashCode());
            try {
                VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{}, fd);
                translateTruffleReduceNode(frame, options, env, log, mappingFunction, iterable, initializer, hasInitializer, range);
                processBoxedData(log);
                translateBounds(env, log);
                final FinalizedVariableValues finalizedValues = new FinalizedVariableValues(env);
                boundCheck(finalizedValues, false, log);
                MGParallel parallelInvoke = MGParallel.createReduce(s, options, rootNode, coreComputeNode, env, finalizedValues, log);
                final DirectCallNode call = parallelInvoke.createCallNode();
                call.call(new Object[]{start, stop, step, log});
                CompilerDirectives.transferToInterpreterAndInvalidate();
                env.clearValues();
                replace(new Ready<>(this, mappingFunction.hashCode(), fd, env, s, finalizedValues, parallelInvoke, call), "MegaGuard Opt");
            } catch (MGException e) {
                fallback.handleException(e);
                throw e;
            }
            log.setOptionValue("TotalTime", System.currentTimeMillis() - startTime);
            log.setOptionValue("TotalKernelExecutions", 1);
            MGLog.addLog(log);

            if (MGOptions.logging)
                log.printLog();

            fallback.resetLimit();
            return env.getResult(0);
        }

    }

    public static class Ready<T extends Node, R> extends MGReduce<T, R> {

        @Child protected MGParallel invoke;
        @Child protected DirectCallNode callNode;
        protected final MGGlobalEnv env;
        protected final SourceSection source;
        protected final FinalizedVariableValues finalizedValues;
        protected final int originMappingFunction;
        protected final FrameDescriptor fd;
        private final MGStorage[] list;
        private final Object[] values;

        public Ready(MGReduce<T, R> baseCall, int hashCode, FrameDescriptor fd, MGGlobalEnv env, SourceSection source, FinalizedVariableValues finalizedValues, MGParallel invoke,
                        DirectCallNode callNode) {
            super(baseCall, Type.OPENCL);
            this.invoke = invoke;
            this.callNode = callNode;
            this.env = env;
            this.source = source;
            this.finalizedValues = finalizedValues;
            this.originMappingFunction = hashCode;
            this.fd = fd;
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
        private void megaguard(VirtualFrame frame, MGArgs args) {
            for (int i = 0; i < list.length; i++) {
                values[i] = list[i].getValueNode().getUnboxed(frame, args.getArgValue(list[i].getName()));
            }

            for (int i = 0; i < list.length; i++) {
                list[i].updateValue(values[i]);
            }
        }

        @Override
        public Object reduce(R mappingFunction, Object iterable, Object initializer, boolean hasInitializer, int iterableLen) {
            if (mappingFunction.hashCode() != originMappingFunction) {
                throw TypeException.INSTANCE.message("Guard Failed! (map function miss-match)");
            }
            long startTime = System.currentTimeMillis();
            final int start = 0;
            final int stop = iterableLen;
            final int step = 1;
            final long[] range = new long[]{start, stop, step};
            StaticUnboxer ret = null;
            VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{}, fd);
            final MGArgs args = new MGArgs().addArg(ParallelFunctions.Map.iterableList, iterable);
            args.addArg(ParallelFunctions.Reduce.initialFlag, hasInitializer ? 1 : 0);
            args.addArg(ParallelFunctions.Reduce.initializerValue, initializer);
            megaguard(frame, args);
            env.setRanges(range, 0);
            MGLog log = new MGLog(source);
            try {
                processBoxedData(log);
                reloadGlobalLoopInfos();
                boundCheck(finalizedValues, true, log);
                callNode.call(new Object[]{start, stop, step, log});
                ret = (StaticUnboxer) ((ReductionWorkload) env.getResult(0)).getBoxed();
            } catch (MGException e) {
                fallback.handleException(e);
                throw e;
            }
            finalizedValues.reset();
            env.clearValues();
            log.setOptionValue("TotalTime", System.currentTimeMillis() - startTime);
            log.setOptionValue("TotalKernelExecutions", 1);
            MGLog.addLog(log);

            if (MGOptions.logging)
                log.printLog();

            if (MGOptions.Backend.Debug > 0) {
                log.println("Total iterations: " + log.getOptionValueLong("TotalParallelLoops") + "  outter loop offloaded at: " + start);
            }

            fallback.resetLimit();
            return ret.getFirstValue();
        }

        @Override
        public Object reduce(SourceSection s, R mappingFunction, FrameDescriptor f, Object iterable, Object initializer, boolean hasInitializer, int iterableLen) {
            return replace(new Uninitialized<>(this)).reduce(s, mappingFunction, f, iterable, initializer, hasInitializer, iterableLen);
        }

    }

}
