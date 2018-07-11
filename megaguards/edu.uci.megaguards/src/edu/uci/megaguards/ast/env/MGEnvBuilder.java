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

import edu.uci.megaguards.analysis.exception.CoverageException;
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
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGLiteral;
import edu.uci.megaguards.object.MGStorage;

public class MGEnvBuilder implements MGVisitorIF<Object> {

    private static final MGEnvBuilder BUILDENV = new MGEnvBuilder();

    private MGBaseEnv hostEnv;
    private MGGlobalEnv globalEnv;
    private MGGlobalEnv localEnv;
    private boolean reading;
    private boolean processingAssignment;
    private boolean functionBody;

    private MGEnvBuilder() {
        this.hostEnv = null;
        this.globalEnv = null;
        this.localEnv = null;
        this.processingAssignment = false;
        this.reading = false;
        this.functionBody = false;
    }

    public static MGGlobalEnv createInternalEnv(MGBaseEnv hostEnv, MGGlobalEnv env, MGNodeFor root) {
        MGGlobalEnv localEnv = env.createInternalEnv();
        BUILDENV.hostEnv = hostEnv;
        BUILDENV.globalEnv = env;
        BUILDENV.localEnv = localEnv;
        final LoopInfo info = root.getLoopInfo();
        BUILDENV.reading = true;
        if (info.getStartNode() != null)
            BUILDENV.visitor(info.getStartNode());
        if (info.getStepNode() != null)
            BUILDENV.visitor(info.getStepNode());
        if (info.getStopNode() != null)
            BUILDENV.visitor(info.getStopNode());
        BUILDENV.reading = false;
        BUILDENV.processingAssignment = false;

        localEnv.addLoopInfo(info);
        localEnv.setGlobalLoopInfo(info, 0);
        final MGStorage inductionVar = info.getInductionVariable();
        localEnv.setIteratorVar(inductionVar, 0);
        localEnv.registerVar(inductionVar);
        BUILDENV.visitor(root.getForBody());
        localEnv.mergePrivateParameters();
        localEnv.setRootNode(root.getForBody());
        return localEnv;
    }

    public void visitor(MGNode root) {
        root.accept(this);
    }

    public Object visitOperandComplex(MGNodeOperandComplex node) {
        throw CoverageException.INSTANCE.message("Should not come here");
    }

    public Object visitOperand(MGNodeOperand node) {
        if (node.getValue() instanceof MGLiteral)
            return null;

        final MGStorage s = (MGStorage) node.getValue();
        if (processingAssignment && MGArray.isArray(s.getDataType())) {
            throw CoverageException.INSTANCE.message(String.format("Illegal OpenCL assignment statement for variable '%s'", s.getName()));
        }
        final String name = s.getName();
        if (globalEnv.getParameters().containsKey(name)) {
            localEnv.registerParameter(globalEnv.getParameters().get(name));
            if (s instanceof MGArray) {
                localEnv.getArrayReadWrite().addArrayAccess(node, reading);
                for (int i = 0; i < ((MGArray) s).getArrayInfo().getDim(); i++) {
                    localEnv.registerParameter(globalEnv.getParameters().get(name + MGBaseEnv.DIMSIZE + i));
                }
                boolean t = reading;
                reading = true;
                for (int i = 0; i < ((MGArray) s).getIndicesLen(); i++)
                    visitor(((MGArray) s).getIndices()[i]);
                reading = t;
            } else
                return null;

        } else if (hostEnv instanceof MGPrivateEnv && ((MGPrivateEnv) hostEnv).getFunction().getHashParameters().containsKey(name)) {
            MGStorage p = ((MGPrivateEnv) hostEnv).getFunction().getHashParameters().get(name);
            localEnv.registerParameter(p);
            if (s instanceof MGArray) {
                localEnv.getArrayReadWrite().addArrayAccess(node, reading);
                for (int i = 0; i < ((MGArray) s).getArrayInfo().getDim(); i++) {
                    localEnv.registerParameter(((MGPrivateEnv) hostEnv).getFunction().getHashParameters().get(name + MGBaseEnv.DIMSIZE + i));
                }
                boolean t = reading;
                reading = true;
                for (int i = 0; i < ((MGArray) s).getIndicesLen(); i++)
                    visitor(((MGArray) s).getIndices()[i]);
                reading = t;
            } else
                return null;
        } else if (!functionBody && s instanceof MGArray) {
            localEnv.registerParameter(s.getOrigin());
            localEnv.registerVar(s.getOrigin());

            for (int i = 0; i < ((MGArray) s).getArrayInfo().getDim(); i++) {
                final MGStorage dimSize = hostEnv.getVar(name + MGBaseEnv.DIMSIZE + i);
                localEnv.registerParameter(dimSize.getOrigin());
                localEnv.registerVar(dimSize.getOrigin());
            }

        }

        if (functionBody)
            return null;

        if (reading) {
            if (localEnv.getVar(name) == null) {
                if (s.getDataType() == DataType.Int || s.getDataType() == DataType.Long) {
                    final LoopInfo info = globalEnv.getGlobalLoopInfos()[0];
                    if (info.getInductionVariable().getName().equals(name)) {
                        long midRange = (info.getRange()[1] - info.getRange()[0]) / info.getRange()[2];
                        localEnv.getConstantIntVars().put(name, midRange);
                    }
                }
                localEnv.registerParameter(s.getOrigin());
                localEnv.registerVar(s.getOrigin());

                if (s instanceof MGArray) {
                    for (int i = 0; i < ((MGArray) s).getArrayInfo().getDim(); i++) {
                        final MGStorage dimSize = hostEnv.getVar(name + MGBaseEnv.DIMSIZE + i);
                        localEnv.registerParameter(dimSize.getOrigin());
                        localEnv.registerVar(dimSize.getOrigin());
                    }
                }
            }

            if (s instanceof MGArray) {
                for (int i = 0; i < ((MGArray) s).getIndicesLen(); i++) {
                    visitor(((MGArray) s).getIndices()[i]);
                }
            }

        } else {
            localEnv.registerVar(s.getOrigin());
        }
        return null;
    }

    public Object visitAssignComplex(MGNodeAssignComplex node) {
        boolean t = reading;
        reading = true;
        visitor(node.getReal().getRight());
        visitor(node.getImag().getRight());
        reading = false;
        visitor(node.getReal().getLeft());
        visitor(node.getImag().getLeft());
        reading = t;
        return null;
    }

    public Object visitAssign(MGNodeAssign node) {
        boolean t = reading;
        reading = true;
        processingAssignment = true;
        visitor(node.getRight());
        reading = false;
        visitor(node.getLeft());
        processingAssignment = false;
        reading = t;
        return null;
    }

    public Object visitUnaryOp(MGNodeUnaryOp node) {
        boolean t = reading;
        reading = true;
        visitor(node.getChild());
        reading = t;
        return null;
    }

    public Object visitBinOp(MGNodeBinOp node) {
        boolean t = reading;
        reading = true;
        visitor(node.getLeft());
        visitor(node.getRight());
        reading = t;
        return null;
    }

    public Object visitBlock(MGNodeBlock node) {
        for (MGNode n : node.getChildren())
            visitor(n);
        return null;
    }

    public Object visitBreak(MGNodeBreak node) {
        return null;
    }

    public Object visitBreakElse(MGNodeBreakElse node) {
        visitor(node.getForBody());
        visitor(node.getOrelse());
        return null;
    }

    public Object visitBuiltinFunction(MGNodeBuiltinFunction node) {
        boolean t = reading;
        reading = true;
        for (MGNode n : node.getNodes())
            visitor(n);
        reading = t;
        return null;
    }

    public Object visitFor(MGNodeFor node) {
        final LoopInfo info = node.getLoopInfo();
        localEnv.addLoopInfo(info);
        localEnv.registerVar(info.getInductionVariable());
        boolean t = reading;
        reading = true;
        if (info.getStartNode() != null)
            visitor(info.getStartNode());
        if (info.getStepNode() != null)
            visitor(info.getStepNode());
        if (info.getStopNode() != null)
            visitor(info.getStopNode());
        reading = t;
        visitor(node.getForBody());
        return null;
    }

    public Object visitWhile(MGNodeWhile node) {
        boolean t = reading;
        reading = true;
        visitor(node.getCond());
        reading = t;
        visitor(node.getBody());
        return null;
    }

    public Object visitIf(MGNodeIf node) {
        boolean t = reading;
        reading = true;
        visitor(node.getCond());
        reading = t;
        visitor(node.getThen());
        if (node.getOrelse() != null)
            visitor(node.getOrelse());
        return null;
    }

    public Object visitMathFunction(MGNodeMathFunction node) {
        boolean t = reading;
        reading = true;
        for (MGNode n : node.getNodes())
            visitor(n);
        reading = t;
        return null;
    }

    public Object visitEmpty(MGNodeEmpty node) {
        return null;
    }

    public Object visitFunctionCall(MGNodeFunctionCall node) {
        boolean t = reading;
        boolean t2 = processingAssignment;
        reading = true;
        processingAssignment = false;
        for (MGNode n : node.getArgs())
            visitor(n);
        reading = t;
        processingAssignment = t2;
        t = functionBody;
        functionBody = true;
        visitor(node.getFunctionNode().getBody());
        functionBody = t;
        localEnv.addFunction(node.getFunctionNode().getPrivateEnv());
        return null;
    }

    public Object visitReturn(MGNodeReturn node) {
        boolean t = reading;
        reading = true;
        visitor(node.getRight());
        reading = t;
        return null;
    }

    public Object visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeLocalID(ParallelNodeLocalID node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeLocalSize(ParallelNodeLocalSize node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeGroupID(ParallelNodeGroupID node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeGroupSize(ParallelNodeGroupSize node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeGlobalID(ParallelNodeGlobalID node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) {
        // TODO need to be implemented
        throw CoverageException.INSTANCE.message("Should implement it");
    }

    public Object visitJumpFrom(MGNodeJumpFrom node) {
        return null;
    }

    public Object visitJumpTo(MGNodeJumpTo node) {
        return null;
    }

}
