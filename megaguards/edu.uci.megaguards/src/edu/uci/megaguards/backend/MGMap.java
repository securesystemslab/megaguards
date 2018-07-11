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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.analysis.exception.TypeException;
import edu.uci.megaguards.analysis.parallel.ParallelFunctions;
import edu.uci.megaguards.analysis.parallel.reduction.MapWorkload;
import edu.uci.megaguards.ast.MGTree;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.MGArgs;
import edu.uci.megaguards.fallback.MGFallbackHandler;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGStorage;

public abstract class MGMap<T extends Node, R> extends MGRoot<T, R> {

    protected final MGFallbackHandler<?> fallback;

    public MGMap(MGTree<T, R> baseTree, MGFallbackHandler<?> fallback, Type type) {
        super(baseTree, type);
        this.fallback = fallback;
    }

    public MGMap(MGMap<T, R> mapnode, Type type) {
        this(mapnode.baseTree, mapnode.fallback, type);
    }

    public boolean isFailed() {
        return false;
    }

    public MGFallbackHandler<?> getFallback() {
        return fallback;
    }

    public abstract Object map(SourceSection s, R mappingFunction, FrameDescriptor fd, int iterableLen, Object iterable, Object... otherIterable);

    public abstract Object map(R mappingFunction, int iterableLen, Object iterable, Object... otherIterable);

    public static class Uninitialized<T extends Node, R> extends MGMap<T, R> {

        public Uninitialized(MGTree<T, R> baseTree, MGFallbackHandler<?> fallback) {
            super(baseTree, fallback, Type.UNINITIALIZED);
        }

        public Uninitialized(MGMap<T, R> fornode) {
            super(fornode, Type.UNINITIALIZED);
        }

        @Override
        public Object map(R mappingFunction, int iterableLen, Object iterable, Object... otherIterable) {
            throw new IllegalStateException();
        }

        public MapWorkload<T> translateTruffleMapNode(VirtualFrame frame, MGNodeOptions options, MGGlobalEnv env, MGLog log, R function, long[] range, Object iterable, Object... otherIterable)
                        throws MGException {
            long s = System.currentTimeMillis();
            MapWorkload<T> result = baseTree.create(env, frame, log).buildMapTree(this, function, range, options, iterable, otherIterable);
            if (!MGOptions.Backend.allowInAccurateMathFunctions) {
                checkMathFunctions(env);
            }
            env.mergePrivateParameters();
            log.setOptionValue("TranslationTime", (System.currentTimeMillis() - s));
            return result;
        }

        @Override
        public Object map(SourceSection s, R mappingFunction, FrameDescriptor fd, int iterableLen, Object iterable, Object... otherIterable) {
            final long startTime = System.currentTimeMillis();
            final MGGlobalEnv env = new MGGlobalEnv("$MAP$");
            final MGLog log = new MGLog(s);
            final int start = 0;
            final int stop = iterableLen;
            final int step = 1;
            final long[] range = new long[]{start, stop, step};
            final MGNodeOptions options = MGNodeOptions.getOptions(mappingFunction.hashCode());
            Object ret = null;
            try {
                VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{}, fd);
                MapWorkload<T> result = translateTruffleMapNode(frame, options, env, log, mappingFunction, range, iterable, otherIterable);
                processBoxedData(log);
                translateBounds(env, log);
                final FinalizedVariableValues finalizedValues = new FinalizedVariableValues(env);
                boundCheck(finalizedValues, false, log);
                MGParallel parallelInvoke = MGParallel.createMap(s, options, rootNode, coreComputeNode, env, finalizedValues, log);
                final DirectCallNode call = parallelInvoke.createCallNode();
                call.call(new Object[]{start, stop, step, log});
                CompilerDirectives.transferToInterpreterAndInvalidate();
                ret = result.boxedResult();
                replace(new Ready<>(this, mappingFunction.hashCode(), fd, env, s, finalizedValues, parallelInvoke, call, result), "MegaGuard Opt");
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
            return ret;
        }

    }

    public static class Ready<T extends Node, R> extends MGMap<T, R> {

        @Child protected MGParallel invoke;
        @Child protected DirectCallNode callNode;
        protected final MGGlobalEnv env;
        protected final SourceSection source;
        protected final FinalizedVariableValues finalizedValues;
        protected final int originMappingFunction;
        protected final FrameDescriptor fd;
        private final MGStorage[] list;
        private final Object[] values;

        private final MapWorkload<T> result;

        public Ready(MGMap<T, R> baseCall, int hashCode, FrameDescriptor fd, MGGlobalEnv env, SourceSection source, FinalizedVariableValues finalizedValues, MGParallel invoke,
                        DirectCallNode callNode, MapWorkload<T> result) {
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

            this.result = result;
        }

        @Override
        public boolean isUninitialized() {
            return false;
        }

        @TruffleBoundary
        private void verifyEqualIterableLength(MGArgs args) {
            int length = -1;
            for (MGStorage s : list) {
                if (args.getArgValue(s.getName()) != null) {
                    if (length == -1) {
                        length = ((MGArray) s).getArrayInfo().getSize(0);
                    } else if (length != ((MGArray) s).getArrayInfo().getSize(0)) {
                        throw CoverageException.INSTANCE.message("Inconsistent arrays length");
                    }

                }
            }
        }

        @ExplodeLoop
        private void megaguard(VirtualFrame frame, MGArgs args) {
            for (int i = 0; i < list.length; i++) {
                values[i] = list[i].getValueNode().getUnboxed(frame, args.getArgValue(list[i].getName()));
            }

            for (int i = 0; i < list.length; i++) {
                list[i].updateValue(values[i]);
            }

            verifyEqualIterableLength(args);
        }

        private void reloadGlobalLoopInfos() {
            finalizedValues.reloadGlobalLoopInfos();
        }

        @Override
        public Object map(R mappingFunction, int iterableLen, Object iterable, Object... otherIterable) {
            if (mappingFunction.hashCode() != originMappingFunction) {
                throw TypeException.INSTANCE.message("Guard Failed! (map function miss-match)");
            }
            long startTime = System.currentTimeMillis();
            final int start = 0;
            final int stop = iterableLen;
            final int step = 1;
            final long[] range = new long[]{start, stop, step};
            Object ret = null;
            VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{}, fd);
            final MGArgs args = new MGArgs().addArg(ParallelFunctions.Map.iterableList, iterable);
            for (int i = 0; i < otherIterable.length; i++) {
                args.addArg(ParallelFunctions.Map.iterableList + i, otherIterable[i]);
            }
            megaguard(frame, args);
            env.setRanges(range, 0);
            MGLog log = new MGLog(source);
            try {
                processBoxedData(log);
                reloadGlobalLoopInfos();
                boundCheck(finalizedValues, true, log);
                callNode.call(new Object[]{start, stop, step, log});
                ret = result.boxedResult();
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
            return ret;
        }

        @Override
        public Object map(SourceSection s, R mappingFunction, FrameDescriptor f, int iterableLen, Object iterable, Object... otherIterable) {
            return replace(new Uninitialized<>(this)).map(s, mappingFunction, f, iterableLen, iterable, otherIterable);
        }

    }

}
