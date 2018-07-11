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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.BoundNodeVisitor;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.BoundException;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.ast.MGTree;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeMathFunction.MathFunctionType;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.unbox.Boxed;

public abstract class MGRoot<T extends Node, R> extends Node {

    protected static enum Type {
        TRUFFLE,
        OPENCL,
        UNINITIALIZED
    }

    protected final MGTree<T, R> baseTree;

    protected MGNode coreComputeNode;
    protected MGNode rootNode;

    protected final Type type;

    public MGRoot(MGTree<T, R> baseTree, Type type) {
        this.baseTree = baseTree;
        this.type = type;
    }

    public MGRoot(MGRoot<T, R> baseCall, Type type) {
        this(baseCall.baseTree, type);
    }

    public boolean isUninitialized() {
        return true;
    }

    public void setCoreComputeNode(MGNode coreComputeNode) {
        if (coreComputeNode != null)
            this.coreComputeNode = coreComputeNode;
    }

    public void setMGRootNode(MGNode rootNode) {
        if (rootNode != null)
            this.rootNode = rootNode;
    }

    public MGNode getCoreComputeNode() {
        return coreComputeNode;
    }

    public MGNode getMGRootNode() {
        return rootNode;
    }

    @TruffleBoundary
    protected void checkMathFunctions(MGGlobalEnv env) {
        for (MathFunctionType f : env.getUsedMathFunctions())
            if (MGOptions.Backend.MathFunctionBlackList.contains(f))
                throw CoverageException.INSTANCE.message(String.format("Using blacklisted math function '%s'.", f));
    }

    @TruffleBoundary
    protected void translateBounds(MGGlobalEnv env, MGLog log) throws CoverageException {
        long s = System.currentTimeMillis();
        BoundNodeVisitor variableBounds = new BoundNodeVisitor(env, log);
        variableBounds.processAllLoopInfos();
        variableBounds.processBounds();
        env.setVariableBounds(variableBounds);
        log.setOptionValue("BoundCheckTime", (System.currentTimeMillis() - s));

    }

    @TruffleBoundary
    protected void processBoxedData(MGLog log) throws MGException {
        long s = System.currentTimeMillis();
        Boxed.UnboxAll(log);
        log.setOptionValue("UnboxTime", (System.currentTimeMillis() - s));
    }

    protected void boundCheck(FinalizedVariableValues finalizedValues, boolean justVerify, MGLog log) throws BoundException {
        if (MGOptions.boundCheck) {
            long s = System.currentTimeMillis();
            finalizedValues.boundCheck(justVerify);
            log.setOptionValue("BoundCheckTime", log.getOptionValueLong("BoundCheckTime") + (System.currentTimeMillis() - s));
        }
    }

}
