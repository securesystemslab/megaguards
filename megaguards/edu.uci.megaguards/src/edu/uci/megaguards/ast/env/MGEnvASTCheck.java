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

import java.util.HashSet;

import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeAssign;
import edu.uci.megaguards.ast.node.MGNodeAssignComplex;
import edu.uci.megaguards.ast.node.MGNodeBinOp;
import edu.uci.megaguards.ast.node.MGNodeBlock;
import edu.uci.megaguards.ast.node.MGNodeBreak;
import edu.uci.megaguards.ast.node.MGNodeBreakElse;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction;
import edu.uci.megaguards.ast.node.MGNodeEmpty;
import edu.uci.megaguards.ast.node.MGNodeFor;
import edu.uci.megaguards.ast.node.MGNodeFunctionCall;
import edu.uci.megaguards.ast.node.MGNodeIf;
import edu.uci.megaguards.ast.node.MGNodeJumpFrom;
import edu.uci.megaguards.ast.node.MGNodeJumpTo;
import edu.uci.megaguards.ast.node.MGNodeMathFunction;
import edu.uci.megaguards.ast.node.MGNodeOperand;
import edu.uci.megaguards.ast.node.MGNodeOperandComplex;
import edu.uci.megaguards.ast.node.MGNodeReturn;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalBarrier;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalSize;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGroupID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGroupSize;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalBarrier;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalSize;
import edu.uci.megaguards.ast.node.MGNodeUnaryOp;
import edu.uci.megaguards.ast.node.MGNodeWhile;
import edu.uci.megaguards.ast.node.MGVisitorIF;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;

public class MGEnvASTCheck implements MGVisitorIF<Object> {

    private final HashSet<String> varTable;
    private boolean start;
    private final HashSet<String> atomicWrites;
    private boolean reading;
    private MGNode skipNode;

    private static final MGEnvASTCheck CHECKAST = new MGEnvASTCheck();

    private MGEnvASTCheck() {
        varTable = new HashSet<>();
        atomicWrites = new HashSet<>();
        reading = true;
        skipNode = null;
    }

    public static void checkAST(MGGlobalEnv env, MGNodeFor forNode) {
        CHECKAST.start = false;
        CHECKAST.atomicWrites.clear();
        CHECKAST.reading = true;
        CHECKAST.varTable.addAll(env.getLocalVarTable().keySet());
        CHECKAST.varTable.remove(forNode.getLoopInfo().getInductionVariable().getName());
        CHECKAST.skipNode = forNode;

        final MGNode root = CHECKAST.getRoot(forNode);
        CHECKAST.visitor(root);

        env.setAtomicWrite(CHECKAST.atomicWrites);
        CHECKAST.atomicWrites.clear();
        CHECKAST.skipNode = null;
        CHECKAST.varTable.clear();
    }

    private MGNode getRoot(MGNode node) {
        if (node.getParent() == null)
            return node;
        return getRoot(node.getParent());
    }

    private Object visitor(MGNode node) {
        return node.accept(this);
    }

    public Object visitOperand(MGNodeOperand node) {
        final MGObject v = node.getValue();
        if (v instanceof MGStorage) {
            final MGStorage s = (MGStorage) v;
            final String name = s.getName();
            if (reading) {
                if (varTable.contains(name)) {
                    atomicWrites.add(name);
                }
            } else {
                if (start)
                    varTable.remove(name);
            }
        }
        return null;
    }

    public Object visitAssign(MGNodeAssign node) {
        visitor(node.getRight());
        boolean t = reading;
        reading = false;
        visitor(node.getLeft());
        reading = t;
        return null;
    }

    public Object visitUnaryOp(MGNodeUnaryOp node) {
        visitor(node.getChild());
        return null;
    }

    public Object visitBinOp(MGNodeBinOp node) {
        visitor(node.getLeft());
        visitor(node.getRight());
        return null;
    }

    public Object visitBlock(MGNodeBlock node) {
        for (MGNode n : node.getChildren()) {
            if (n == skipNode) {
                start = true;
                continue;
            }

            visitor(n);
        }
        return null;
    }

    public Object visitBreakElse(MGNodeBreakElse node) {
        visitor(node.getForBody());
        visitor(node.getOrelse());
        return null;
    }

    public Object visitBuiltinFunction(MGNodeBuiltinFunction node) {
        for (MGNode n : node.getNodes()) {
            visitor(n);
        }
        return null;
    }

    public Object visitFor(MGNodeFor node) {
        final LoopInfo info = node.getLoopInfo();
        varTable.remove(info.getInductionVariable().getName());

        if (info.getStartNode() != null)
            visitor(info.getStartNode());
        if (info.getStopNode() != null)
            visitor(info.getStopNode());
        if (info.getStepNode() != null)
            visitor(info.getStepNode());

        if (node == skipNode) {
            start = true;
            return null;
        }

        visitor(node.getForBody());
        return null;
    }

    public Object visitWhile(MGNodeWhile node) {
        visitor(node.getCond());
        visitor(node.getBody());
        return null;
    }

    public Object visitIf(MGNodeIf node) {
        visitor(node.getCond());
        visitor(node.getThen());
        if (node.getOrelse() != null)
            visitor(node.getOrelse());
        return null;
    }

    public Object visitMathFunction(MGNodeMathFunction node) {
        for (MGNode n : node.getNodes()) {
            visitor(n);
        }

        return null;
    }

    public Object visitFunctionCall(MGNodeFunctionCall node) {
        for (MGNode n : node.getArgs()) {
            visitor(n);
        }
        return null;
    }

    public Object visitReturn(MGNodeReturn node) {
        visitor(node.getRight());
        return null;
    }

    public Object visitAssignComplex(MGNodeAssignComplex node) {
        return null;
    }

    public Object visitOperandComplex(MGNodeOperandComplex node) {
        return null;
    }

    public Object visitEmpty(MGNodeEmpty node) {
        return null;
    }

    public Object visitBreak(MGNodeBreak node) {
        return null;
    }

    public Object visitJumpFrom(MGNodeJumpFrom node) {
        return null;
    }

    public Object visitJumpTo(MGNodeJumpTo node) {
        return null;
    }

    public Object visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) {
        return null;
    }

    public Object visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) {
        return null;
    }

    public Object visitParallelNodeLocalID(ParallelNodeLocalID node) {
        return null;
    }

    public Object visitParallelNodeLocalSize(ParallelNodeLocalSize node) {
        return null;
    }

    public Object visitParallelNodeGroupID(ParallelNodeGroupID node) {
        return null;
    }

    public Object visitParallelNodeGroupSize(ParallelNodeGroupSize node) {
        return null;
    }

    public Object visitParallelNodeGlobalID(ParallelNodeGlobalID node) {
        return null;
    }

    public Object visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) {
        return null;
    }

}
