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
package edu.uci.megaguards.analysis.bounds;

import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeArray;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeBinOp;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeBuiltinFunction;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeEither;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeFunctionCall;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeLimit;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeLiteral;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeMathFunction;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeRange;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeVariable;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.env.MGPrivateEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeAssign;
import edu.uci.megaguards.ast.node.MGNodeAssignComplex;
import edu.uci.megaguards.ast.node.MGNodeBinOp;
import edu.uci.megaguards.ast.node.MGNodeBinOp.BinOpType;
import edu.uci.megaguards.ast.node.MGNodeBlock;
import edu.uci.megaguards.ast.node.MGNodeBreak;
import edu.uci.megaguards.ast.node.MGNodeBreakElse;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction.BuiltinFunctionType;
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
import edu.uci.megaguards.ast.node.MGNodeUnaryOp.UnaryOpType;
import edu.uci.megaguards.ast.node.MGNodeUserFunction;
import edu.uci.megaguards.ast.node.MGNodeWhile;
import edu.uci.megaguards.ast.node.MGVisitorIF;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGLiteral;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;

public class BoundNodeVisitor implements MGVisitorIF<MGBoundNode> {

    private MGBaseEnv env;
    private final HashMap<Integer, MGBoundNode> functions;
    private MGLog log;

    public BoundNodeVisitor(MGBaseEnv env, MGLog log) {
        this.env = env;
        this.log = log;
        this.functions = new HashMap<>();
    }

    @TruffleBoundary
    public void processLoopInfo(LoopInfo info) {
        final long[] range = info.getRange();

        final MGBoundNode stopBound;
        final MGBoundNode startBound;
        final MGBoundNode stepBound;

        if (!info.isSimpleRange()) {
            if (info.getStartNode() == null) {
                startBound = new MGBoundNodeLiteral(range[0], DataType.Int);
            } else {
                startBound = visitor(info.getStartNode());
            }
            if (info.getStopNode() == null) {
                stopBound = new MGBoundNodeLiteral(range[1], DataType.Int);
            } else {
                stopBound = visitor(info.getStopNode());
            }
            if (info.getStepNode() == null) {
                stepBound = new MGBoundNodeLiteral(range[2], DataType.Int);
            } else {
                stepBound = visitor(info.getStepNode());
            }
        } else {
            startBound = new MGBoundNodeLiteral(range[0], DataType.Int);
            stopBound = new MGBoundNodeLiteral(range[1], DataType.Int);
            stepBound = new MGBoundNodeLiteral(range[2], DataType.Int);
        }
        info.setBounds(new MGBoundNodeRange(startBound, stopBound, stepBound));
    }

    @TruffleBoundary
    public void processAllLoopInfos() {
        for (LoopInfo info : env.getExistingLoopInfos()) {
            if (info != null) {
                processLoopInfo(info);
            }
        }

    }

    private void processSingleAccess(MGNode aa) {
        MGArray arrayValue = (MGArray) ((MGNodeOperand) aa).getValue();
        int dims = arrayValue.getArrayInfo().getDim();
        MGBoundNode[] bounds = new MGBoundNode[dims];
        MGNode[] indices = arrayValue.getIndices();
        for (int i = 0; i < dims; i++) {
            bounds[i] = visitor(indices[i]);
        }
        arrayValue.setBounds(bounds);

    }

    @TruffleBoundary
    public void processBounds() {
        for (MGNode aa : env.getArrayaccesses()) {
            processSingleAccess(aa);
        }

        for (MGNode of : env.getOverflowCheck()) {
            of.setBound(visitor(of));
        }

        if (env.isGlobalEnv()) {
            final MGGlobalEnv holder = (MGGlobalEnv) env;
            for (MGPrivateEnv p : holder.getPrivateEnvironments().values()) {
                env = p;
                BoundNodeVisitor v = new BoundNodeVisitor(p, log);
                v.processAllLoopInfos();
                v.processBounds();
            }
            env = holder;
        }
    }

    @TruffleBoundary
    private MGBoundNode visitor(MGNode root) {
        try {
            return root.accept(this);
        } catch (CoverageException c) {
            throw c;
        } catch (Exception e) {
            log.printException(e);
        }
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    private MGBoundNode accessArray(MGArray arrayValue) {
        int dims = arrayValue.getArrayInfo().getDim();
        final MGBoundNode[] bounds = new MGBoundNode[dims];
        MGNode[] indices = arrayValue.getIndices();
        for (int i = 0; i < dims; i++) {
            bounds[i] = visitor(indices[i]);
        }
        arrayValue.setBounds(bounds);
        return new MGBoundNodeArray(arrayValue);
    }

    @TruffleBoundary
    public MGBoundNode visitOperandComplex(MGNodeOperandComplex node) {
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitOperand(MGNodeOperand node) {
        MGObject var = node.getValue();

        if (var instanceof MGArray && ((MGArray) var).getIndicesLen() > 0) {
            return accessArray((MGArray) var);
        } else if (var instanceof MGStorage) {
            if (((MGStorage) var).getDefUseIndex() != -1) {
                return visitor(env.getDefUse().get(var.getName()).get(((MGStorage) var).getDefUseIndex()));
            }
            return new MGBoundNodeVariable(var.getName(), var.getDataType());
        }

        assert (var instanceof MGLiteral);
        long val = 0;
        if (var.getValue() instanceof Integer)
            val = (int) var.getValue();
        else if (var.getValue() instanceof Long)
            val = ((Long) var.getValue());
        else if (var.getValue() instanceof Double)
            val = ((Double) var.getValue()).intValue();
        else if (var.getValue() instanceof Boolean)
            val = (((boolean) var.getValue()) ? 1 : 0);
        return new MGBoundNodeLiteral(val, var.getDataType());
    }

    @TruffleBoundary
    public MGBoundNode visitAssignComplex(MGNodeAssignComplex node) {
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitAssign(MGNodeAssign node) {
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitUnaryOp(MGNodeUnaryOp node) {
        if (node.getType() == UnaryOpType.Cast) {
            return visitor(node.getChild());
        }
        return null;
    }

    @TruffleBoundary
    private static MGBoundNode arith(MGBoundNode left, BinOpType op, MGBoundNode right, DataType type) {
        if (left instanceof MGBoundNodeLimit || right instanceof MGBoundNodeLimit) {
            MGBoundNode min = null;
            MGBoundNode max = null;
            if (left instanceof MGBoundNodeLimit && right instanceof MGBoundNodeLimit) {
                min = new MGBoundNodeBinOp(((MGBoundNodeLimit) left).getMin(), op, ((MGBoundNodeLimit) right).getMin(), type);
                max = new MGBoundNodeBinOp(((MGBoundNodeLimit) left).getMax(), op, ((MGBoundNodeLimit) right).getMax(), type);
            } else if (left instanceof MGBoundNodeLimit) {
                min = new MGBoundNodeBinOp(((MGBoundNodeLimit) left).getMin(), op, right, type);
                max = new MGBoundNodeBinOp(((MGBoundNodeLimit) left).getMax(), op, right, type);
            } else {
                min = new MGBoundNodeBinOp(left, op, ((MGBoundNodeLimit) right).getMin(), type);
                max = new MGBoundNodeBinOp(left, op, ((MGBoundNodeLimit) right).getMax(), type);
            }
            return new MGBoundNodeLimit(min, max, type);
        } else {
            return new MGBoundNodeBinOp(left, op, right, type);
        }
    }

    @TruffleBoundary
    public MGBoundNode visitBinOp(MGNodeBinOp node) {
        BinOpType op = node.getType();
        MGBoundNode var = null;
        final MGBoundNode left = visitor(node.getLeft());
        final MGBoundNode right = visitor(node.getRight());

        if (left instanceof MGBoundNodeLiteral && right instanceof MGBoundNodeLiteral) {
            long leftLiteral = ((MGBoundNodeLiteral) left).getLiteral();
            long rightLiteral = ((MGBoundNodeLiteral) right).getLiteral();
            switch (op) {
                case ADD:
                    var = new MGBoundNodeLiteral(leftLiteral + rightLiteral, node.getExpectedType());
                    break;
                case SUB:
                    var = new MGBoundNodeLiteral(leftLiteral - rightLiteral, node.getExpectedType());
                    break;
                case MUL:
                    var = new MGBoundNodeLiteral(leftLiteral * rightLiteral, node.getExpectedType());
                    break;
                case DIV:
                    var = new MGBoundNodeLiteral(leftLiteral / rightLiteral, node.getExpectedType());
                    break;
                case MOD:
                    var = new MGBoundNodeLiteral(leftLiteral % rightLiteral, node.getExpectedType());
                    break;
                case POW:
                    var = new MGBoundNodeLiteral((long) Math.pow(leftLiteral, rightLiteral), node.getExpectedType());
                    break;
            }
        } else {
            var = arith(left, op, right, node.getExpectedType());
        }

        assert var != null;
        return var;
    }

    @TruffleBoundary
    public MGBoundNode visitBuiltinFunction(MGNodeBuiltinFunction node) {
        if (node.getType() == BuiltinFunctionType.MIN || node.getType() == BuiltinFunctionType.MAX) {
            final MGBoundNode arg1 = visitor(node.getNodes().get(0));
            final MGBoundNode arg2 = visitor(node.getNodes().get(1));
            return new MGBoundNodeBuiltinFunction(node.getType(), arg1, arg2, node.getExpectedType());
        }
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitFor(MGNodeFor node) {
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitIf(MGNodeIf node) {
        final MGBoundNode argThen = visitor(node.getThen());
        final MGBoundNode argElse = visitor(node.getOrelse());
        return new MGBoundNodeEither(argThen, argElse, node.getExpectedType());
    }

    @TruffleBoundary
    public MGBoundNode visitMathFunction(MGNodeMathFunction node) {
        final MGBoundNode arg = visitor(node.getNodes().get(0));
        return new MGBoundNodeMathFunction(node.getType(), arg, node.getExpectedType());
    }

    public MGBoundNode visitEmpty(MGNodeEmpty node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitFunctionCall(MGNodeFunctionCall node) {
        if (!functions.containsKey(node.getFunctionNode().hashCode())) {
            ArrayList<MGBoundNode> args = new ArrayList<>();
            MGNodeUserFunction functionNode = node.getFunctionNode();
            for (MGNode arg : functionNode.getCalls().get(0).getArgs()) {
                args.add(visitor(arg));
            }
            for (int i = 1; i < functionNode.getCalls().size(); i++) {
                for (int j = 0; j < args.size(); j++) {
                    MGNode arg = functionNode.getCalls().get(i).getArgs().get(j);
                    args.set(j, new MGBoundNodeEither(args.get(j), visitor(arg), node.getExpectedType()));
                }
            }
            for (int i = 0; i < functionNode.getParameters().size(); i++) {
                MGStorage s = functionNode.getParameters().get(i);
                if (s instanceof MGArray) {
                    // ((MGArray) s).setBounds(null);
                }
            }
            MGBoundNode ret = visitOperand(functionNode.getReturnVar());
            functions.put(node.getFunctionNode().hashCode(), new MGBoundNodeFunctionCall(ret, args, node.getExpectedType()));
        }
        MGBoundNode call = functions.get(node.getFunctionNode().hashCode());
        if (call == null) {
            throw CoverageException.INSTANCE.message("Call value cannot be verified for array bounds");
        }
        return call;
    }

    public MGBoundNode visitReturn(MGNodeReturn node) {
        return visitor(node.getRight());
    }

    @TruffleBoundary
    public MGBoundNode visitBlock(MGNodeBlock node) {
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitBreak(MGNodeBreak node) {
        assert false : "There is something uncovered!";
        return null;
    }

    @TruffleBoundary
    public MGBoundNode visitBreakElse(MGNodeBreakElse node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitJumpFrom(MGNodeJumpFrom node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitJumpTo(MGNodeJumpTo node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitWhile(MGNodeWhile node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeLocalID(ParallelNodeLocalID node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeLocalSize(ParallelNodeLocalSize node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeGroupID(ParallelNodeGroupID node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeGroupSize(ParallelNodeGroupSize node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeGlobalID(ParallelNodeGlobalID node) {
        assert false : "There is something uncovered!";
        return null;
    }

    public MGBoundNode visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) {
        assert false : "There is something uncovered!";
        return null;
    }

}
