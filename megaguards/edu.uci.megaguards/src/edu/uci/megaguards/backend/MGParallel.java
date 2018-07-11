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

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.parallel.DataDependence;
import edu.uci.megaguards.analysis.parallel.Optimization;
import edu.uci.megaguards.analysis.parallel.ParallelFunctions;
import edu.uci.megaguards.analysis.parallel.exception.DataDependenceException;
import edu.uci.megaguards.analysis.parallel.polyhedral.AthenaPetTest;
import edu.uci.megaguards.analysis.parallel.profile.ParallelNodeProfile;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeFunctionCall;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLExecuter;
import edu.uci.megaguards.log.MGLog;

public class MGParallel extends MGInvoke {

    private final OpenCLExecuter executer;
    private final boolean isReduce;

    public MGParallel(MGBaseEnv env, OpenCLExecuter executer, boolean isreduce) {
        super(env);
        this.executer = executer;
        this.isReduce = isreduce;
    }

    public MGParallel(MGBaseEnv env, OpenCLExecuter executer) {
        this(env, executer, false);
    }

    @Override
    public MGInvoke invalidate(MGLog log) {
        executer.setLog(log);
        executer.invalidateSource();
        executer.init();
        return this;
    }

    @TruffleBoundary
    public static void checkReduction(MGNodeOptions options, MGNode coreComputeNode) {
        if (options != null && options.isReduceOn())
            return;

        ParallelFunctions.Reduce.checkWhitelist((MGNodeFunctionCall) coreComputeNode);
    }

    public static void checkRecursion(MGGlobalEnv env) {
        if (env.isRecursionExists()) {
            throw DataDependenceException.INSTANCE.message("Recursion detected");
        }
    }

    @TruffleBoundary
    public static DataDependence dataDependenceAnalysis(MGNodeOptions options, MGGlobalEnv env, MGNode coreComputeNode, FinalizedVariableValues finalizedValues, MGLog log) {
        DataDependence checkDDep = null;
        if (!(options != null && options.isDDOff())) {
            long s = System.currentTimeMillis();
            checkDDep = new AthenaPetTest(coreComputeNode, env.getExistingLoopInfos().get(0), env, finalizedValues);
            boolean ddResult = checkDDep.testDependence("for", log.getSourceSection());
            ddResult = (ddResult) ? checkDDep.testArrayReferences(env) : ddResult;
            if (!ddResult)
                throw DataDependenceException.INSTANCE.message(checkDDep.getReason());
            log.setOptionValue("DependenceTime", (System.currentTimeMillis() - s));
            log.setOptionValue("DependenceCount", 1);
        }
        return checkDDep;
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    public static MGNode maximizeThreads(MGNodeOptions options, MGGlobalEnv env, MGNode rootNode, FinalizedVariableValues finalizedValues, MGLog log) {
        Optimization optimize = new Optimization(rootNode, env, options);
        final MGNode opt = optimize.optimizeLoop(finalizedValues);
        return opt;
    }

    @TruffleBoundary
    public static ParallelNodeProfile profileParallelNode(MGGlobalEnv env, FinalizedVariableValues finalizedValues, MGLog log) {
        ParallelNodeProfile profile = new ParallelNodeProfile(log, env.getMGRootNode(), finalizedValues);
        long s = System.currentTimeMillis();
        profile.profile();
        log.setOptionValue("ProfileTime", (System.currentTimeMillis() - s));
        return profile;

    }

    @SuppressWarnings("fallthrough")
    @TruffleBoundary
    public static OpenCLExecuter getOpenCLExecuter(SourceSection sourceSection, MGGlobalEnv env, MGNode rn, MGLog log) {
        OpenCLExecuter executer = null;

        ExecutionMode mode = MGOptions.Backend.target;
        switch (mode) {
            case OpenCLAuto:
            case OpenCLCPU:
            case OpenCLGPU:
                executer = new OpenCLExecuter(sourceSection, env, rn, log);
                break;
        }

        return executer;
    }

    public static MGParallel createLoop(SourceSection sourceSection, MGNodeOptions options, MGNode rootNode, MGNode coreComputeNode, MGGlobalEnv env, FinalizedVariableValues finalizedValues,
                    MGLog log) {
        checkRecursion(env);
        dataDependenceAnalysis(options, env, coreComputeNode, finalizedValues, log);
        MGNode opt = maximizeThreads(options, env, rootNode, finalizedValues, log);
        final OpenCLExecuter executer = getOpenCLExecuter(sourceSection, env, opt, log);
        return new MGParallel(env, executer);
    }

    public static MGParallel createInternalLoop(MGNodeOptions options, SourceSection sourceSection, MGNode rootNode, MGGlobalEnv env, MGLog log) {
        final MGNode opt = maximizeThreads(options, env, rootNode, env.getFinalizedValues(), log);
        final OpenCLExecuter executer = getOpenCLExecuter(sourceSection, env, opt, log);
        return new MGParallel(env, executer);
    }

    public static MGParallel createInternalMapOfMapReduce(SourceSection sourceSection, MGNode map, MGGlobalEnv env, MGLog log) {
        final OpenCLExecuter executer = getOpenCLExecuter(sourceSection, env, map, log);
        return new MGParallel(env, executer);
    }

    public static MGParallel createInternalReduce(SourceSection sourceSection, MGNode reduce, MGGlobalEnv env, MGLog log) {
        final OpenCLExecuter executer = getOpenCLExecuter(sourceSection, env, reduce, log);
        return new MGParallel(env, executer, true);
    }

    public static MGParallel createMap(SourceSection sourceSection, MGNodeOptions options, MGNode rootNode, MGNode coreComputeNode, MGGlobalEnv env, FinalizedVariableValues finalizedValues,
                    MGLog log) {
        dataDependenceAnalysis(options, env, coreComputeNode, finalizedValues, log);
        final OpenCLExecuter executer = getOpenCLExecuter(sourceSection, env, rootNode, log);
        return new MGParallel(env, executer);
    }

    public static MGParallel createReduce(SourceSection sourceSection, MGNodeOptions options, MGNode rootNode, MGNode coreComputeNode, MGGlobalEnv env, FinalizedVariableValues finalizedValues,
                    MGLog log) {
        checkReduction(options, coreComputeNode);
        dataDependenceAnalysis(options, env, coreComputeNode, finalizedValues, log);
        final OpenCLExecuter executer = getOpenCLExecuter(sourceSection, env, rootNode, log);
        final ArrayList<String[]> swapping = new ArrayList<>(1);
        swapping.add(new String[]{ParallelFunctions.Reduce.iterableList, ParallelFunctions.Reduce.result, ParallelFunctions.Reduce.result2});
        executer.setSwapping(swapping);
        return new MGParallel(env, executer, true);
    }

    public OpenCLExecuter getExecuter() {
        return executer;
    }

    @SuppressWarnings("unused")
    @Override
    public Object execute(VirtualFrame frame) {
        final int start = (int) frame.getArguments()[0];
        final int stop = (int) frame.getArguments()[1];
        final int step = (int) frame.getArguments()[2];
        final MGLog log = (MGLog) frame.getArguments()[3];
        long startTime = System.currentTimeMillis();
        log.setOptionValue("OffloadOffset", start);
        executer.setLog(log);
        executer.init();
        if (!isReduce) {
            executer.execute(start);
        } else {
            executer.executeReduction();
        }
        executer.reset();
        log.setOptionValue("TotalTime", log.getOptionValueLong("TotalTime") + (System.currentTimeMillis() - startTime));
        log.setOptionValue("TotalKernelExecutions", 1);
        return null;
    }

}
