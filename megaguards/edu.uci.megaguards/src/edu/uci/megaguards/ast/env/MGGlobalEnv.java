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
package edu.uci.megaguards.ast.env;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.analysis.bounds.BoundNodeVisitor;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.LoopException;
import edu.uci.megaguards.analysis.parallel.graph.CycleDetection;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGStorage;

public class MGGlobalEnv extends MGBaseEnv {

    private String reductionSwitcher;

    // Global Env
    private int levels;
    private MGStorage[] iterVar;
    private LoopInfo[] globalLoopInfos;

    private boolean runtimeBoundCheck;
    protected boolean requireDouble;

    protected final HashSet<String> atomicWrites;

    private MGNode rootNode;

    private final CycleDetection cycles;
    private int recursionExist;
    private final HashMap<String, MGPrivateEnv> functions;

    protected boolean outerBreak;

    protected boolean illegalForOpenCL;

    private final HashMap<String, HashSet<String>> argsDefUse;

    private final HashMap<String, Integer> changedSet;
    private int changedIdx;

    protected BoundNodeVisitor variableBounds;

    protected FinalizedVariableValues finalizedValues;

    protected Object[] result;

    public MGGlobalEnv(String functionName) {
        super(functionName, "");
        this.reductionSwitcher = null;

        this.levels = 1;
        this.iterVar = new MGStorage[3];
        this.globalLoopInfos = new LoopInfo[3];

        this.rootNode = null;

        this.runtimeBoundCheck = false;
        this.requireDouble = false;
        this.atomicWrites = new HashSet<>();

        this.functions = new HashMap<>();
        this.cycles = new CycleDetection();
        this.recursionExist = -1;

        this.outerBreak = false;

        this.illegalForOpenCL = false;

        this.argsDefUse = new HashMap<>();
        this.changedSet = new HashMap<>();
        this.changedIdx = 0;
        this.variableBounds = null;
        this.result = null;
        this.finalizedValues = null;
    }

    public MGGlobalEnv(MGGlobalEnv env, String functionName) {
        super(env, functionName);
        this.reductionSwitcher = null;
        this.levels = 1;
        this.iterVar = new MGStorage[3];
        this.globalLoopInfos = new LoopInfo[3];

        this.rootNode = null;

        this.runtimeBoundCheck = false;
        this.requireDouble = false;
        this.atomicWrites = new HashSet<>();

        this.outerBreak = false;

        this.functions = new HashMap<>();
        this.cycles = new CycleDetection();
        this.recursionExist = -1;

        this.argsDefUse = env.argsDefUse;
        this.changedSet = env.changedSet;
        this.changedIdx = env.changedIdx;

        this.arrays.putAll(env.arrays);

        this.variableBounds = null;
        this.result = null;
        this.finalizedValues = env.finalizedValues;
    }

    private static class InternalEnv extends MGGlobalEnv {

        @SuppressWarnings("unused") private final MGGlobalEnv refEnv;

        protected InternalEnv(MGGlobalEnv env) {
            super(env, "_" + idPrefix++);
            this.refEnv = env;
            this.variableBounds = env.variableBounds;
        }
    }

    @TruffleBoundary
    public MGGlobalEnv createInternalEnv() {
        return new InternalEnv(this);
    }

    @Override
    public boolean isGlobalEnv() {
        return true;
    }

    @Override
    public MGGlobalEnv getGlobalEnv() {
        return this;
    }

    public void setOuterBreak(boolean b) {
        this.outerBreak = b;
    }

    public boolean isOuterBreak() {
        return outerBreak;
    }

    public void setIllegalForOpenCL(boolean b) {
        this.illegalForOpenCL = b || illegalForOpenCL;
    }

    public boolean isIllegalForOpenCL() {
        return illegalForOpenCL;
    }

    public void setRequireDouble(boolean d) {
        this.requireDouble = d;
    }

    public boolean isRequireDouble() {
        return requireDouble;
    }

    public FinalizedVariableValues getFinalizedValues() {
        return finalizedValues;
    }

    public void setFinalizedValues(FinalizedVariableValues finalizedValues) {
        this.finalizedValues = finalizedValues;
    }

    public void setAtomicWrite(HashSet<String> aw) {
        this.atomicWrites.addAll(aw);
    }

    public boolean hasAtomicWrite() {
        return this.atomicWrites.size() != 0;
    }

    public HashSet<String> getAtomicWrites() {
        return this.atomicWrites;
    }

    @TruffleBoundary
    public long[][] getRanges() {
        final long[][] ranges = new long[levels][0];
        for (int i = 0; i < levels; i++)
            ranges[i] = globalLoopInfos[i].getRange();

        return ranges;
    }

    public int getIterationLevels() {
        return levels;
    }

    public MGStorage[] getIteratorVar() {
        return this.iterVar;
    }

    public void increaseLevel() {
        this.levels++;
    }

    public void setRanges(long[] range, int level) {
        this.globalLoopInfos[level].setRange(range);
    }

    public void setGlobalLoopInfo(LoopInfo info, int level) {
        globalLoopInfos[level] = info;
    }

    public LoopInfo[] getGlobalLoopInfos() {
        return globalLoopInfos;
    }

    public MGNode getMGRootNode() {
        return rootNode;
    }

    public void setRootNode(MGNode kernelNode) {
        this.rootNode = kernelNode;
    }

    public boolean isRuntimeBoundCheck() {
        return runtimeBoundCheck;
    }

    public String getReductionSwitcher() {
        return reductionSwitcher;
    }

    public void setReductionSwitcher(String reductionSwitcher) {
        this.reductionSwitcher = reductionSwitcher;
    }

    public void addArgDefUse(String param, String arg) {
        if (!changedSet.containsKey(param))
            changedSet.put(param, changedIdx++);

        if (!argsDefUse.containsKey(param)) {
            argsDefUse.put(param, new HashSet<>());
        }

        if (argsDefUse.containsKey(arg)) {
            argsDefUse.get(param).addAll(argsDefUse.get(arg));
        } else {
            argsDefUse.get(param).add(arg);
        }
    }

    public HashSet<String> getReverseArgDefUse(String arg) {
        final HashSet<String> params = new HashSet<>();
        for (String param : argsDefUse.keySet()) {
            if (argsDefUse.get(param).contains(arg)) {
                params.add(param);
            }
        }
        return params;
    }

    private boolean updateReadWrite() {
        for (Entry<String, MGStorage> entry : this.parameters.entrySet()) {
            if (entry.getValue().getValue() instanceof ParallelWorkload) {
                continue;
            }
            if (entry.getValue().getOrigin() instanceof MGArray) {
                final MGArray array = (MGArray) entry.getValue().getOrigin();
                if (!array.isReadOnly()) {
                    continue;
                }
                final String name = array.getName();
                if (getArrayReadWrite().getReadWrites().containsKey(name)) {
                    array.setReadWrite();
                    continue;
                }
                final HashSet<String> params = getReverseArgDefUse(name);
                for (MGPrivateEnv pEnv : getPrivateEnvironments().values()) {
                    if (pEnv.getArrayReadWrite().getReadWrites().containsKey(name)) {
                        array.setReadWrite();
                        break;
                    }
                    for (String param : params) {
                        if (pEnv.getArrayReadWrite().getReadWrites().containsKey(param)) {
                            array.setReadWrite();
                            break;
                        }
                    }
                    if (!array.isReadOnly()) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    public void mergeArgsDefUses() {
        boolean converged = false;
        while (!converged) {
            converged = true;
            for (String param : argsDefUse.keySet()) {
                final HashSet<String> args = argsDefUse.get(param);
                final ArrayList<String> argsList = new ArrayList<>(args);
                for (String arg : argsList) {
                    if (arg.contentEquals(param)) {
                        args.remove(arg);
                        converged = false;
                    } else if (argsDefUse.containsKey(arg)) {
                        args.addAll(argsDefUse.get(arg));
                        args.remove(arg);
                        converged = false;
                    }
                }
            }
        }
        updateReadWrite();
    }

    @TruffleBoundary
    public MGStorage setIteratorVar(MGStorage inductionVar, int level) {
        iterVar[level] = inductionVar;
        return iterVar[level];
    }

    @TruffleBoundary
    public MGStorage setIteratorVar(String name, int level) {
        iterVar[level] = registerIntVar(name);
        iterVar[level].setDefine();
        return iterVar[level];
    }

    public CycleDetection getCycles() {
        return cycles;
    }

    @TruffleBoundary
    public void mergePrivateParameters() {
        for (MGBaseEnv p : this.functions.values()) {
            this.parameters.putAll(p.getParameters());
            this.arrays.putAll(p.arrays);
        }
        for (String param : parameters.keySet()) {
            if (!changedSet.containsKey(param))
                changedSet.put(param, changedIdx++);
        }
    }

    public int sizeOfChangeList() {
        return changedIdx;
    }

    @TruffleBoundary
    public MGPrivateEnv getPrivateEnvironment(String fn, String tg) {
        return functions.get(fn + tg);
    }

    public void addFunction(MGPrivateEnv penv) {
        final String fid = penv.functionID;
        final String functionTypeTag = penv.typeTag;
        final String fn = fid + functionTypeTag;
        if (!functions.containsKey(fn)) {
            functions.put(fn, penv);
            arrayReadWrite.mergeReadWrite(penv.arrayReadWrite);
        }
    }

    public HashMap<String, MGPrivateEnv> getPrivateEnvironments() {
        return functions;
    }

    public boolean isRecursionExists() {
        if (recursionExist == -1)
            recursionExist = getCycles().isCyclic() ? 1 : 0;

        return recursionExist == 1;
    }

    @TruffleBoundary
    public MGPrivateEnv createPrivateEnvironment(MGBaseEnv currentEnv, String fid, String functionTypeTag) throws LoopException {
        final String fn = fid + functionTypeTag;
        if (fn.contentEquals(getFunctionID()) || fn.contentEquals(currentEnv.getFunctionID())) {
            throw LoopException.INSTANCE.message(String.format("Recusive call has been detected between '%s' and '%s'.", fid, currentEnv.getFunctionID()));
        }
        if (!functions.containsKey(fn)) {
            functions.put(fn, new MGPrivateEnv(fid, functionTypeTag, this));
        }
        cycles.addEdge(currentEnv.getFunctionID(), fid);
        return functions.get(fn);
    }

    public BoundNodeVisitor getVariableBounds() {
        return variableBounds;
    }

    public void setVariableBounds(BoundNodeVisitor variableBounds) {
        this.variableBounds = variableBounds;
    }

    public void setResultsSize(int size) {
        this.result = new Object[size];
    }

    public Object[] getResults() {
        return result;
    }

    public Object getResult(int idx) {
        return result[idx];
    }

    public void setResult(Object result, int idx) {
        this.result[idx] = result;
    }

}
