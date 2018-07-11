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
package edu.uci.megaguards.analysis.parallel.polyhedral;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Stack;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.analysis.parallel.DataDependence;
import edu.uci.megaguards.analysis.parallel.polyhedral.AthenaPet.AthenaPetObject;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeAssign;
import edu.uci.megaguards.ast.node.MGNodeAssignComplex;
import edu.uci.megaguards.ast.node.MGNodeBinOp;
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
import edu.uci.megaguards.ast.node.MGNodeWhile;
import edu.uci.megaguards.ast.node.MGVisitorIF;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGStorage;
import edu.uci.megaguards.unbox.Unboxer;

public class AthenaPetTest extends DataDependence implements MGVisitorIF<AthenaPetObject> {

    private static final String DUMMY_VAR = "DUMMY$VAR$";
    private static int postfix = 0;

    private AthenaPet ap;
    private HashSet<String> local;
    private boolean replaceConst;

    private boolean subscriptProcessing;

    private ArrayList<AthenaPetObject> functions;
    private Stack<HashMap<String, MGNode>> functionArgs;
    private Stack<MGNode> functionReturn;

    private final long minLoop;

    public AthenaPetTest(MGNode forBody, LoopInfo info, MGGlobalEnv env, FinalizedVariableValues finalizedValues) {
        super(forBody, info, env, finalizedValues);
        this.ap = new AthenaPet();
        this.local = new HashSet<>();
        this.replaceConst = true;
        this.subscriptProcessing = false;
        this.functions = new ArrayList<>();
        this.functionArgs = new Stack<>();
        this.functionReturn = new Stack<>();
        long min = finalizedValues.getMinValue();
        this.minLoop = min < 0 ? min * -1 : min;
    }

    private static final int[] binaryOpMap = {
                    AthenaPetJNI.pet_op_add,
                    AthenaPetJNI.pet_op_sub,
                    AthenaPetJNI.pet_op_mul,
                    AthenaPetJNI.pet_op_div,

                    AthenaPetJNI.pet_op_mul, // pow
                    AthenaPetJNI.pet_op_mod,

                    AthenaPetJNI.pet_op_lor,
                    AthenaPetJNI.pet_op_land,

                    AthenaPetJNI.pet_op_shl,
                    AthenaPetJNI.pet_op_shr,
                    AthenaPetJNI.pet_op_and,
                    AthenaPetJNI.pet_op_or,
                    AthenaPetJNI.pet_op_xor,

                    AthenaPetJNI.pet_op_eq,
                    AthenaPetJNI.pet_op_ne,
                    AthenaPetJNI.pet_op_lt,
                    AthenaPetJNI.pet_op_le,
                    AthenaPetJNI.pet_op_gt,
                    AthenaPetJNI.pet_op_ge
    };

    @TruffleBoundary
    @Override
    public boolean testDependence(String name, SourceSection source) {

        String iv_s = loopInfo.getInductionVariable().getName();
        String forTag = name + s(source);
        loops.put(forTag, null);
        local.add(iv_s);
        return testDependence(forTag);
    }

    @TruffleBoundary
    private void setReason(HashMap<String, String> flowDepStmt) {
        this.ddreason = "[AthenaPet]: Flow dependence exist:\n";
        for (Entry<String, String> f : flowDepStmt.entrySet()) {
            this.ddreason += "\t\t" + f.getKey() + " -> " + f.getValue() + "\n";
        }
    }

    private boolean testDependence(String forTag) {
        final long[] range = loopInfo.getRange();
        AthenaPetObject iv = ap.var(loopInfo.getInductionVariable());
        AthenaPetObject lhs_cond = ap.var(loopInfo.getInductionVariable());
        AthenaPetObject rhs_cond = ap.literal(range[1] + minLoop);
        AthenaPetObject cond = ap.binaryOp(lhs_cond, AthenaPetJNI.pet_op_lt, rhs_cond);
        AthenaPetObject inc = ap.literal(range[2]);
        AthenaPetObject init = ap.literal(range[0] + minLoop);
        AthenaPetObject body = ap.statement(visitor(forBody), s("body"));
        AthenaPetObject block = ap.block(this.functions.size() + 1);
        for (AthenaPetObject s : this.functions) {
            block = ap.addChild(block, s);
        }
        this.functions.clear();
        block = ap.addChild(block, body);

        AthenaPetObject forloop = ap.forLoop(iv, init, cond, inc, block, forTag);

        forloop = addConstantValues(forloop);
        for (String l : local) {
            if (!constants.containsKey(l)) {
                if (!l.contains(MGBaseEnv.REPLACETAG)) {
                    ap.addLocalVar(l);
                }
            }
        }

        HashMap<String, String> flowDepStmt = new HashMap<>();
        ap.computeDependences(forloop, loops, flowDepStmt);
        setReason(flowDepStmt);
        return !loops.containsKey(forTag);

    }

    @TruffleBoundary
    @Override
    public boolean testDependence(MGNode node) {

        String iv_s = loopInfo.getInductionVariable().getName();
        String forTag = s(node);
        loops.put(forTag, null);
        local.add(iv_s);
        boolean r = testDependence(forTag);
        return r;
    }

    @TruffleBoundary
    private AthenaPetObject visitor(MGNode root) {
        try {
            return root.accept(this);
        } catch (Exception e) {
            MGLog.printException(e, null);
        }
        return null;

    }

    @TruffleBoundary
    private AthenaPetObject accessArray(MGArray arrayValue) {
        return accessArray(arrayValue, arrayValue);
    }

    @TruffleBoundary
    private AthenaPetObject accessArray(MGArray arrayValue, MGArray arrayIndices) {
        final int dims = arrayIndices.getArrayInfo().getDim();
        final MGNode[] indices = arrayIndices.getIndices();
        final int indicesLen = arrayIndices.getIndicesLen();
        if (subscriptProcessing) {
            final long[] bounds = finalizedValues.getArrayValuesBound().get(((Unboxer) arrayValue.getOrigin().getValue()).getValue().hashCode());
            if (bounds != null && bounds[2] == 1 && indicesLen == 1) {
                // this array has unique values, so use it's subscripts instead.
                return visitor(indices[0]);
            } else {
                // Athena Pet doesn't support 'a[ b[i] ]' expressions and will assume 'b[i] = 0'
                return ap.literal(0);
            }
        }
        subscriptProcessing = true;
        AthenaPetObject var = ap.var(arrayValue);
        if (dims > 2) {
            AthenaPetObject index = null;
            AthenaPetObject slice = null;
            int[] dimSizes = new int[dims];
            dimSizes[0] = dimSizes[dims - 1] = 1; // we don't need these values
            for (int i = dims - 2; i > 0; i--)
                dimSizes[i] = arrayValue.getArrayInfo().getSize(i) * dimSizes[i + 1];

            if (dims == indicesLen) {
                // Returning single value
                index = ap.binaryOp(visitor(indices[0]), AthenaPetJNI.pet_op_mul, ap.literal(dimSizes[0 + 1]));
                for (int i = 1; i < dims - 2; i++) {
                    slice = ap.binaryOp(visitor(indices[i]), AthenaPetJNI.pet_op_mul, ap.literal(dimSizes[i + 1]));
                    index = ap.binaryOp(index, AthenaPetJNI.pet_op_add, slice);
                }

                index = ap.binaryOp(index, AthenaPetJNI.pet_op_add, visitor(indices[dims - 2]));
                var = ap.subscriptAccess(var, index);
                var = ap.subscriptAccess(var, visitor(indices[dims - 1]));
            } else {
                // Returning single dimension.
                index = ap.binaryOp(visitor(indices[0]), AthenaPetJNI.pet_op_mul, ap.literal(dimSizes[0 + 1]));
                for (int i = 1; i < dims - 2; i++) {
                    slice = ap.binaryOp(visitor(indices[i]), AthenaPetJNI.pet_op_mul, ap.literal(dimSizes[i + 1]));
                    index = ap.binaryOp(index, AthenaPetJNI.pet_op_add, slice);
                }

                index = ap.binaryOp(index, AthenaPetJNI.pet_op_add, visitor(indices[dims - 2]));
                var = ap.subscriptAccess(var, index);
            }
        } else {
            for (int i = 0; i < indicesLen; i++) {
                var = ap.subscriptAccess(var, visitor(indices[i]));
            }
        }
        subscriptProcessing = false;
        return var;
    }

    @TruffleBoundary
    private AthenaPetObject addConstantValues(AthenaPetObject forloop) {
        AthenaPetObject domain = forloop;

        AthenaPetObject[] constantsList = new AthenaPetObject[constants.size()];
        int i = 0;
        for (Entry<String, Long> c : constants.entrySet()) {
            if (!c.getKey().contains(MGBaseEnv.REPLACETAG)) {
                ap.addLocalVar(c.getKey());

                constantsList[i] = ap.constant(ap.var(c.getKey(), 0, 0), ap.literal(c.getValue().intValue()));
                constantsList[i] = ap.statement(constantsList[i], s("const_" + c.getKey()));
                i++;
            }
        }
        if (i > 0) {
            domain = ap.block(i + 1);
            for (int j = 0; j < i; j++) {
                domain = ap.addChild(domain, constantsList[j]);
            }
            domain = ap.addChild(domain, forloop);
        }

        return domain;
    }

    @TruffleBoundary
    public AthenaPetObject visitOperandComplex(MGNodeOperandComplex node) {
        return null;
    }

    private MGNodeOperand getRootArg(MGNodeOperand node) {
        if (this.functionArgs.size() > 0 && this.functionArgs.peek().containsKey(node.getValue().getName())) {
            return getRootArg((MGNodeOperand) this.functionArgs.peek().get(node.getValue().getName()));
        }
        return node;
    }

    @TruffleBoundary
    public AthenaPetObject visitOperand(MGNodeOperand node) {
        if (!(node.getValue() instanceof MGArray) && this.functionArgs.size() > 0 && this.functionArgs.peek().containsKey(node.getValue().getName()))
            return visitor(this.functionArgs.peek().get(node.getValue().getName()));

        if (replaceConst && constants.containsKey(node.getValue().getName()))
            return ap.literal(constants.get(node.getValue().getName()).intValue());
        if (node.getValue() instanceof MGStorage && ((MGStorage) node.getValue()).isDefine()) {
            local.add(node.getValue().getName());
            return ap.var(node.getValue());
        } else if (node.getValue() != null && node.getValue() instanceof MGArray && ((MGArray) node.getValue()).getIndicesLen() > 0) {
            return accessArray((MGArray) getRootArg(node).getValue(), (MGArray) node.getValue());
        } else
            return ap.var(node.getValue());
    }

    @TruffleBoundary
    public AthenaPetObject visitAssignComplex(MGNodeAssignComplex node) {
        AthenaPetObject block = ap.block(2);
        block = ap.addChild(block, ap.statement(ap.assign(visitor(node.getReal().getLeft()), visitor(node.getReal().getRight())), s("complex_real")));
        block = ap.addChild(block, ap.statement(ap.assign(visitor(node.getImag().getLeft()), visitor(node.getImag().getRight())), s("complex_imag")));
        return block;
    }

    @TruffleBoundary
    private AthenaPetObject ifElseAssign(MGNode left, MGNodeIf ifElse) {
        AthenaPetObject cond = visitor(ifElse.getCond());
        AthenaPetObject lhs_cond = visitor(ifElse.getThen());
        AthenaPetObject rhs_cond = visitor(ifElse.getOrelse());
        AthenaPetObject lhs = visitor(left);
        AthenaPetObject rhs = ap.ternaryOp(cond, lhs_cond, rhs_cond);
        return ap.assign(lhs, rhs);
    }

    @TruffleBoundary
    private AthenaPetObject callAssign(MGNode left, MGNodeFunctionCall node) {
        HashMap<String, MGNode> params = new HashMap<>();
        for (int i = 0; i < node.getFunctionNode().getParameters().size(); i++) {
            MGStorage s = node.getFunctionNode().getParameters().get(i);
            params.put(s.getName(), node.getArgs().get(i));
        }
        if (!this.functionArgs.isEmpty()) {
            params.putAll(this.functionArgs.peek());
        }
        this.functionArgs.push(params);
        this.functionReturn.push(left);

        AthenaPetObject body = visitor(node.getFunctionNode().getBody().copy());

        this.functionArgs.pop();
        this.functionReturn.pop();
        return ap.statement(body, s(node.getFunctionNode().getFunctionID() + "_body"));
    }

    @TruffleBoundary
    public AthenaPetObject visitAssign(MGNodeAssign node) {
        MGNode lhs = node.getLeft();
        MGNode rhs = node.getRight();

        if (rhs instanceof MGNodeIf)
            return ifElseAssign(lhs, (MGNodeIf) rhs);

        if (rhs instanceof MGNodeFunctionCall)
            return callAssign(lhs, (MGNodeFunctionCall) rhs);

        AthenaPetObject prhs = visitor(rhs);
        AthenaPetObject plhs = visitor(lhs);
        return ap.statement(ap.assign(plhs, prhs), s(node));
    }

    @TruffleBoundary
    public AthenaPetObject visitUnaryOp(MGNodeUnaryOp node) {
        if (node.getType() == UnaryOpType.Not) {
            AthenaPetObject stmt = visitor(node.getChild());
            return ap.unaryOp(stmt, AthenaPetJNI.pet_op_lnot);
        }
        return visitor(node.getChild());
    }

    @TruffleBoundary
    public AthenaPetObject visitBinOp(MGNodeBinOp node) {
        AthenaPetObject lhs = visitor(node.getLeft());
        int op = binaryOpMap[node.getType().ordinal()];
        AthenaPetObject rhs = visitor(node.getRight());
        return ap.binaryOp(lhs, op, rhs);
    }

    @TruffleBoundary
    public AthenaPetObject visitBlock(MGNodeBlock node) {
        int size = node.getChildren().size();
        ArrayList<AthenaPetObject> stmts = new ArrayList<>();
        int i;
        for (i = 0; i < size; i++) {
            MGNode stmt = node.getChildren().get(i);
            if (stmt instanceof MGNodeJumpTo || stmt instanceof MGNodeJumpFrom)
                continue;
            AthenaPetObject s = ap.statement(visitor(stmt), s("block_" + i));
            for (AthenaPetObject o : this.functions) {
                stmts.add(o);
            }
            this.functions.clear();

            stmts.add(s);
        }

        AthenaPetObject b = ap.block(stmts.size());
        for (AthenaPetObject o : stmts) {
            b = ap.addChild(b, o);
        }

        return b;
    }

    @TruffleBoundary
    public AthenaPetObject visitBreak(MGNodeBreak node) {
        String dummy = DUMMY_VAR + postfix++;
        local.add(dummy);
        return ap.assign(ap.var(dummy, 0, 0), ap.literal(0));
    }

    @TruffleBoundary
    public AthenaPetObject visitBreakElse(MGNodeBreakElse node) {
        AthenaPetObject forBlock = ap.statement(visitor(node.getForBody()), s("forBreak"));
        AthenaPetObject elseBlock = visitor(node.getOrelse());

        AthenaPetObject block = ap.block(3);
        block = ap.addChild(block, forBlock);
        block = ap.addChild(block, elseBlock);
        return block;
    }

    @TruffleBoundary
    public AthenaPetObject visitBuiltinFunction(MGNodeBuiltinFunction node) {
        AthenaPetObject lhs_cond = visitor(node.getNodes().get(0));
        AthenaPetObject lhs = visitor(node.getNodes().get(0));
        AthenaPetObject rhs_cond = visitor(node.getNodes().get(1));
        AthenaPetObject rhs = visitor(node.getNodes().get(1));

        int op = node.getType() == BuiltinFunctionType.MIN ? AthenaPetJNI.pet_op_lt : AthenaPetJNI.pet_op_gt;
        AthenaPetObject cond = ap.binaryOp(lhs_cond, op, rhs_cond);
        return ap.ternaryOp(cond, lhs, rhs);
    }

    @TruffleBoundary
    public AthenaPetObject visitFor(MGNodeFor node) {
        LoopInfo info = node.getLoopInfo();
        String forTag = s(node);
        forTag = (forTag != null) ? forTag : "for_" + node.hashCode();
        loops.put(forTag, node);
        local.add(info.getInductionVariable().getName());

        AthenaPetObject iv = ap.var(info.getInductionVariable());
        AthenaPetObject lhs_cond = ap.var(info.getInductionVariable());
        AthenaPetObject rhs_cond = null;
        AthenaPetObject cond = null;
        AthenaPetObject inc = null;
        AthenaPetObject init = null;
        AthenaPetObject body = null;
        final long[] range = info.getRange();
        if (!info.isSimpleRange()) {
            rhs_cond = ap.binaryOp(visitor(info.getStopNode()), AthenaPetJNI.pet_op_add, ap.literal(minLoop));
            cond = ap.binaryOp(lhs_cond, AthenaPetJNI.pet_op_lt, rhs_cond);
            inc = (info.getStepNode() == null) ? ap.literal(range[2]) : visitor(info.getStepNode());
            init = (info.getStartNode() == null) ? ap.literal(range[0] + minLoop) : ap.binaryOp(visitor(info.getStartNode()), AthenaPetJNI.pet_op_add, ap.literal(minLoop));
        } else {
            rhs_cond = ap.literal(range[1] + minLoop);
            cond = ap.binaryOp(lhs_cond, AthenaPetJNI.pet_op_lt, rhs_cond);
            inc = ap.literal(range[2]);
            init = ap.literal(range[0] + minLoop);
        }

        AthenaPetObject preBody = null;
        if (info.getTargetVar() != null) {
            AthenaPetObject targetVar = ap.var(info.getTargetVar());
            AthenaPetObject targetrhs = accessArray((MGArray) info.getTargetVar().getValue());
            preBody = ap.statement(ap.assign(targetVar, targetrhs), s("preBody"));
        }

        ArrayList<AthenaPetObject> stmts = new ArrayList<>();
        if (preBody != null) {
            stmts.add(preBody);
            stmts.add(ap.statement(visitor(node.getForBody()), s("body")));
        } else {
            stmts.add(ap.statement(visitor(node.getForBody()), s("body")));
        }
        for (AthenaPetObject o : this.functions) {
            stmts.add(0, o);
        }
        this.functions.clear();

        body = ap.block(stmts.size());
        for (AthenaPetObject o : stmts) {
            body = ap.addChild(body, o);
        }
        return ap.forLoop(iv, init, cond, inc, body, forTag);

    }

    @TruffleBoundary
    public AthenaPetObject visitIf(MGNodeIf node) {
        AthenaPetObject cond = visitor(node.getCond());
        AthenaPetObject thenBody = ap.statement(visitor(node.getThen()), s("then"));
        AthenaPetObject elseBody = node.getOrelse() != null ? ap.statement(visitor(node.getOrelse()), s("else")) : null;
        return ap.ifElse(cond, thenBody, elseBody);
    }

    @TruffleBoundary
    public AthenaPetObject visitMathFunction(MGNodeMathFunction node) {
        AthenaPetObject dummyAccess = visitor(node.getNodes().get(0));
        for (int i = 1; i < node.getNodes().size(); i++) {
            dummyAccess = ap.binaryOp(dummyAccess, AthenaPetJNI.pet_op_add, visitor(node.getNodes().get(i)));
        }

        return dummyAccess;
    }

    public AthenaPetObject visitFunctionCall(MGNodeFunctionCall node) {
        HashMap<String, MGNode> params = new HashMap<>();
        for (int i = 0; i < node.getFunctionNode().getParameters().size(); i++) {
            MGStorage s = node.getFunctionNode().getParameters().get(i);
            params.put(s.getName(), node.getArgs().get(i));
        }
        if (!this.functionArgs.isEmpty()) {
            params.putAll(this.functionArgs.peek());
        }

        this.functionArgs.push(params);
        this.functionReturn.push(node.getFunctionNode().getReturnVar());

        AthenaPetObject body = visitor(node.getFunctionNode().getBody().copy());
        AthenaPetObject b;
        if (this.functions.size() > 0) {
            b = ap.block(this.functions.size() + 1);
            for (AthenaPetObject o : this.functions) {
                b = ap.addChild(b, o);
            }
            this.functions.clear();
            b = ap.addChild(b, ap.statement(body, s(node.getFunctionNode().getFunctionID() + "_body")));
        } else {
            b = ap.statement(body, s(node.getFunctionNode().getFunctionID() + "_body"));
        }
        this.functionArgs.pop();
        this.functionReturn.pop();
        if (!node.isReturnCall())
            return b;
        else
            this.functions.add(b);
        return visitor(node.getFunctionNode().getReturnVar());
    }

    public AthenaPetObject visitReturn(MGNodeReturn node) {
        if (this.functionReturn.peek() == null)
            return ap.literal(0);

        if (node.getRight() instanceof MGNodeIf)
            return ifElseAssign(this.functionReturn.peek(), (MGNodeIf) node.getRight());

        AthenaPetObject rhs = visitor(node.getRight());
        AthenaPetObject lhs = visitor(this.functionReturn.peek());
        return ap.assign(lhs, rhs);
    }

    public AthenaPetObject visitWhile(MGNodeWhile node) {
        AthenaPetObject cond = visitor(node.getCond());
        AthenaPetObject body = ap.statement(visitor(node.getBody()), s("while_body"));
        return ap.whileLoop(cond, body, s("while"));
    }

    public AthenaPetObject visitJumpFrom(MGNodeJumpFrom node) {
        String dummy = DUMMY_VAR + postfix++;
        local.add(dummy);
        return ap.assign(ap.var(dummy, 0, 0), ap.literal(0));
    }

    public AthenaPetObject visitJumpTo(MGNodeJumpTo node) {
        String dummy = DUMMY_VAR + postfix++;
        local.add(dummy);
        return ap.assign(ap.var(dummy, 0, 0), ap.literal(0));
    }

    public AthenaPetObject visitEmpty(MGNodeEmpty node) {
        return null;
    }

    public AthenaPetObject visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeLocalID(ParallelNodeLocalID node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeLocalSize(ParallelNodeLocalSize node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeGroupID(ParallelNodeGroupID node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeGroupSize(ParallelNodeGroupSize node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeGlobalID(ParallelNodeGlobalID node) throws CoverageException {
        return null;
    }

    public AthenaPetObject visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) throws CoverageException {
        return null;
    }

}
