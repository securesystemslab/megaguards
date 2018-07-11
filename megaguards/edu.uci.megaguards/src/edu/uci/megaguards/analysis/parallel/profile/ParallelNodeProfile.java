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
package edu.uci.megaguards.analysis.parallel.profile;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
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
import edu.uci.megaguards.ast.node.MGNodeWhile;
import edu.uci.megaguards.ast.node.MGVisitorIF;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;

public class ParallelNodeProfile implements MGVisitorIF<Object> {

    private final static ArrayList<ParallelNodeProfile> profiles = new ArrayList<>();

    private MGLog log;
    private MGNode rootForBody;

    private long multiplier;

    private long[] detailedNumBinOp;
    private long numBinOp;

    private long[] detailedNumMathFunc;
    private long numMathFunc;

    private long assignStatements;
    private long ifStatements;
    private long breakStatements;
    private long breakElseStatements;
    private long forStatements;
    private long numArrayAccesses;

    private long numBinOpPerIter;
    private long ifStatementsPerIter;
    private long forStatementsPerIter;
    private long numArrayAccessesPerIter;

    private FinalizedVariableValues finalizedValues;

    private ParallelNodeProfile(MGLog log) {
        detailedNumBinOp = new long[20];
        Arrays.fill(detailedNumBinOp, 0);
        numBinOp = 0;

        detailedNumMathFunc = new long[10];
        Arrays.fill(detailedNumBinOp, 0);
        numMathFunc = 0;

        assignStatements = 0;
        ifStatements = 0;
        breakStatements = 0;
        forStatements = 0;
        numArrayAccesses = 0;

        multiplier = 1;
        this.log = log;
    }

    public ParallelNodeProfile(MGLog log, ParallelNodeProfile traversed, long iterations) {
        this(log);
        assignStatements = traversed.assignStatements * iterations;
        ifStatements = traversed.ifStatements * iterations;
        breakStatements = traversed.breakStatements * iterations;
        forStatements = traversed.forStatements * iterations;
        numArrayAccesses = traversed.numArrayAccesses * iterations;

        numBinOpPerIter = numBinOp / iterations;
        ifStatementsPerIter = ifStatements / iterations;
        forStatementsPerIter = forStatements / iterations;
        numArrayAccessesPerIter = numArrayAccesses / iterations;
    }

    public ParallelNodeProfile(MGLog log, MGNode forBody, FinalizedVariableValues finalizedValues) {
        this(log);
        this.rootForBody = forBody;
        this.finalizedValues = finalizedValues;
    }

    public void profile() {
        visitor(rootForBody);
    }

    @TruffleBoundary
    private Object visitor(MGNode root) {
        try {
            return root.accept(this);
        } catch (Exception e) {
        }
        return null;
    }

    @TruffleBoundary
    private Object accessArray(MGArray arrayValue) {
        numArrayAccesses += multiplier;
        final MGNode[] indices = arrayValue.getIndices();
        final int indicesLen = arrayValue.getIndicesLen();
        for (int i = 0; i < indicesLen; i++) {
            visitor(indices[i]);
        }

        return null;
    }

    @SuppressWarnings("unused")
    private static int nestedIfElse(MGNodeIf ifElse) {
        int ifDivider = 1;
        if (ifElse.getOrelse() != null) {
            ifDivider += 1;
            final MGNode elseNode = ifElse.getOrelse();
            if (elseNode instanceof MGNodeIf) {
                ifDivider += nestedIfElse((MGNodeIf) elseNode);
            }
        }
        return ifDivider;
    }

    public Object visitOperandComplex(MGNodeOperandComplex node) {
        return null;
    }

    public Object visitAssignComplex(MGNodeAssignComplex node) {
        return null;
    }

    public Object visitOperand(MGNodeOperand node) {
        if (node.getValue() != null && node.getValue() instanceof MGArray && ((MGArray) node.getValue()).getIndicesLen() > 0)
            return accessArray((MGArray) node.getValue());
        return null;
    }

    public Object visitAssign(MGNodeAssign node) {
        assignStatements += multiplier;
        if (node.getExpectedType() == DataType.Complex)
            return null;
        visitor(node.getLeft());
        visitor(node.getRight());
        return null;
    }

    public Object visitUnaryOp(MGNodeUnaryOp node) {
        visitor(node.getChild());
        return null;
    }

    public Object visitBinOp(MGNodeBinOp node) {
        detailedNumBinOp[node.getType().ordinal()] += multiplier;
        numBinOp += multiplier;
        visitor(node.getLeft());
        visitor(node.getRight());
        return null;
    }

    public Object visitBlock(MGNodeBlock node) {
        for (final MGNode stmt : node.getChildren()) {
            visitor(stmt);
        }
        return null;
    }

    public Object visitBreak(MGNodeBreak node) {
        breakStatements += multiplier;
        return null;
    }

    public Object visitBreakElse(MGNodeBreakElse node) {
        ifStatements += multiplier;
        visitor(node.getForBody());
        visitor(node.getOrelse());
        return null;
    }

    public Object visitBuiltinFunction(MGNodeBuiltinFunction node) {
        if (node.getType() == BuiltinFunctionType.MIN || node.getType() == BuiltinFunctionType.MAX) {
            ifStatements += multiplier;
            visitor(node.getNodes().get(0));
            visitor(node.getNodes().get(1));
            return null;
        }
        return null;
    }

    public Object visitFor(MGNodeFor node) {
        long currentMultiplier = multiplier;
        forStatements += multiplier;
        LoopInfo info = node.getLoopInfo();
        finalizedValues.finalizeLoopInfo(info);
        final long iterations = info.getIterationCount();
        multiplier = (iterations > 0) ? iterations * multiplier : multiplier;
        visitor(node.getForBody());
        multiplier = currentMultiplier;
        return null;
    }

    public Object visitIf(MGNodeIf node) {
        ifStatements += multiplier;
        long currentMultiplier = multiplier;
        int ifDivider = 2;// nestedIfElse(ifElse);
        visitor(node.getCond());
        // TODO: process if %%
        multiplier /= ifDivider;
        // multiplier = currentMultiplier - (multiplier * (ifDivider - 1));
        visitor(node.getThen());

        if (node.getOrelse() != null) {
            final MGNode elseNode = node.getOrelse();
            visitor(elseNode);
        }
        multiplier = currentMultiplier;
        return null;
    }

    public Object visitMathFunction(MGNodeMathFunction node) {
        detailedNumMathFunc[node.getType().ordinal()] += multiplier;
        numMathFunc += multiplier;
        visitor(node.getNodes().get(0));
        for (int i = 1; i < node.getNodes().size(); i++) {
            visitor(node.getNodes().get(i));
        }
        return null;
    }

    public Object visitEmpty(MGNodeEmpty node) {
        return null;
    }

    public long[] getDetailedNumBinOp() {
        return detailedNumBinOp;
    }

    public long getNumBinOp() {
        return numBinOp;
    }

    public long[] getDetailedNumMathFunc() {
        return detailedNumMathFunc;
    }

    public long getNumMathFunc() {
        return numMathFunc;
    }

    public long getAssignStatements() {
        return assignStatements;
    }

    public long getIfStatements() {
        return ifStatements;
    }

    public long getBreakStatements() {
        return breakStatements;
    }

    public long getBreakElseStatements() {
        return breakElseStatements;
    }

    public long getForStatements() {
        return forStatements;
    }

    public long getNumArrayAccesses() {
        return numArrayAccesses;
    }

    public long getNumBinOpPerIter() {
        return numBinOpPerIter;
    }

    public long getIfStatementsPerIter() {
        return ifStatementsPerIter;
    }

    public long getForStatementsPerIter() {
        return forStatementsPerIter;
    }

    public long getNumArrayAccessesPerIter() {
        return numArrayAccessesPerIter;
    }

    @TruffleBoundary
    public void printNodeProfileJSON() {
        JSONObjectBuilder jsonOut = JSONHelper.object();
        ParallelNodeProfile summary = profilesSummary();

        jsonOut.add("filename", log.getOptionValueString("Filename"));

        jsonOut.add("binary_op_count", summary.getNumBinOp());
        jsonOut.add("math_func_count", summary.getNumMathFunc());
        jsonOut.add("assign_count", summary.getAssignStatements());
        jsonOut.add("break_count", summary.getBreakStatements());
        jsonOut.add("break_else_count", summary.getBreakElseStatements());
        jsonOut.add("for_count", summary.getForStatements());
        jsonOut.add("if_count", summary.getIfStatements());
        jsonOut.add("array_access_count", summary.getNumArrayAccesses());
        jsonOut.add("for_per_iter", summary.getForStatementsPerIter());
        jsonOut.add("if_per_iter", summary.getIfStatementsPerIter());
        jsonOut.add("binary_op_per_iter", summary.getNumBinOpPerIter());
        jsonOut.add("array_access_per_iter", summary.getNumArrayAccessesPerIter());
        String prettyJSON = jsonOut.toString();
        prettyJSON = prettyJSON.replace(", ", ",\n");
        prettyJSON = prettyJSON.replace("{", "{\n").replace("}", "\n}");
        prettyJSON = prettyJSON.replace("\n\"", "\n    \"");
        MGLog.getPrintJSON().println(prettyJSON);
    }

    @TruffleBoundary
    public static ParallelNodeProfile profilesSummary() {
        long numBinOp = 0;
        long numMathFunc = 0;
        long assignStatements = 0;
        long ifStatements = 0;
        long breakStatements = 0;
        long breakElseStatements = 0;
        long forStatements = 0;

        long numBinOpPerIter = 0;
        long ifStatementsPerIter = 0;
        long forStatementsPerIter = 0;
        long numArrayAccessesPerIter = 0;

        long numArrayAccesses = 0;
        for (ParallelNodeProfile p : profiles) {
            numBinOp += p.numBinOp;
            numMathFunc += p.numMathFunc;
            assignStatements += p.assignStatements;
            ifStatements += p.ifStatements;
            breakStatements += p.breakStatements;
            breakElseStatements += p.breakElseStatements;
            forStatements += p.forStatements;
            numArrayAccesses += p.numArrayAccesses;
            numBinOpPerIter += p.numBinOpPerIter;
            ifStatementsPerIter += p.ifStatementsPerIter;
            forStatementsPerIter += p.forStatementsPerIter;
            numArrayAccessesPerIter += p.numArrayAccessesPerIter;
        }
        ParallelNodeProfile summary = new ParallelNodeProfile(new MGLog(null));
        summary.numBinOp = numBinOp;
        summary.numMathFunc = numMathFunc;
        summary.assignStatements = assignStatements;
        summary.ifStatements = ifStatements;
        summary.breakStatements = breakStatements;
        summary.breakElseStatements = breakElseStatements;
        summary.forStatements = forStatements;
        summary.numArrayAccesses = numArrayAccesses;
        summary.numBinOpPerIter = numBinOpPerIter;
        summary.ifStatementsPerIter = ifStatementsPerIter;
        summary.forStatementsPerIter = forStatementsPerIter;
        summary.numArrayAccessesPerIter = numArrayAccessesPerIter;
        return summary;
    }

    public Object visitFunctionCall(MGNodeFunctionCall node) {
        return null;
    }

    public Object visitReturn(MGNodeReturn node) {
        return null;
    }

    public Object visitWhile(MGNodeWhile node) {
        return null;
    }

    public Object visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeLocalID(ParallelNodeLocalID node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeLocalSize(ParallelNodeLocalSize node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeGroupID(ParallelNodeGroupID node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeGroupSize(ParallelNodeGroupSize node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeGlobalID(ParallelNodeGlobalID node) throws CoverageException {
        return null;
    }

    public Object visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) throws CoverageException {
        return null;
    }

    public Object visitJumpFrom(MGNodeJumpFrom node) {
        // TODO Auto-generated method stub
        return null;
    }

    public Object visitJumpTo(MGNodeJumpTo node) {
        // TODO Auto-generated method stub
        return null;
    }

}
