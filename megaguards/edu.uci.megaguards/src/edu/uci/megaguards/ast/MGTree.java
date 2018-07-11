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
package edu.uci.megaguards.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.analysis.exception.TypeException;
import edu.uci.megaguards.analysis.parallel.ParallelFunctions;
import edu.uci.megaguards.analysis.parallel.reduction.MapWorkload;
import edu.uci.megaguards.analysis.parallel.reduction.ReductionWorkload;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeAssign;
import edu.uci.megaguards.ast.node.MGNodeBinOp;
import edu.uci.megaguards.ast.node.MGNodeBinOp.BinOpType;
import edu.uci.megaguards.ast.node.MGNodeBlock;
import edu.uci.megaguards.ast.node.MGNodeFor;
import edu.uci.megaguards.ast.node.MGNodeFunctionCall;
import edu.uci.megaguards.ast.node.MGNodeIf;
import edu.uci.megaguards.ast.node.MGNodeJumpTo;
import edu.uci.megaguards.ast.node.MGNodeOperand;
import edu.uci.megaguards.ast.node.MGNodeReturn;
import edu.uci.megaguards.ast.node.MGNodeSpecial;
import edu.uci.megaguards.ast.node.MGNodeUnaryOp;
import edu.uci.megaguards.ast.node.MGNodeUnaryOp.UnaryOpType;
import edu.uci.megaguards.backend.MGRoot;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.backend.parallel.ParallelWorkload.LoadType;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGInt;
import edu.uci.megaguards.object.MGIntLiteral;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;
import edu.uci.megaguards.unbox.Boxed;
import edu.uci.megaguards.unbox.UnboxerUtil;

public abstract class MGTree<T extends Node, R> {

    protected VirtualFrame frame;
    protected HashMap<String, LoopInfo> currentLoopInfos;
    protected MGLog log;
    protected boolean storeSubscript;
    protected MGNodeJumpTo breakFlag;
    protected MGNodeJumpTo returnFlag;
    protected boolean sliceprocessing;
    protected Stack<T> forNodes;
    protected ArrayList<MGNodeReturn> returns;

    protected final Boxed<T> boxHelper;
    protected final UnboxerUtil<T> boxUtil;

    public MGTree(VirtualFrame frame, MGLog log, Boxed<T> boxHelper, UnboxerUtil<T> boxUtil) {
        this.breakFlag = null;
        this.returnFlag = null;
        this.frame = frame;
        this.sliceprocessing = false;
        this.returns = null;
        this.log = log;
        this.currentLoopInfos = new HashMap<>();
        this.forNodes = new Stack<>();
        this.returns = new ArrayList<>();
        this.boxHelper = boxHelper;
        this.boxUtil = boxUtil;
    }

    protected MGTree(Boxed<T> boxHelper, UnboxerUtil<T> boxUtil) {
        this(null, null, boxHelper, boxUtil);
    }

    public abstract boolean isTypeSupported(Object value);

    public abstract ArrayList<MGNode> specializeArguments(R function, Object[] arguments, int prefix);

    public abstract MGStorage specializeLocalVariable(T node, String varname, Object value, FrameSlotKind kind);

    public abstract MGStorage specializeParameter(T node, String varname, Object value);

    public abstract MGNodeFunctionCall createCall(ArrayList<MGNode> passedArgs, R function);

    public abstract MGTree<T, R> create(MGBaseEnv env, VirtualFrame f, MGLog l);

    @TruffleBoundary
    protected void setHashMaps(LoopInfo loopInfo) {
        this.currentLoopInfos = new HashMap<>();
        this.currentLoopInfos.put(loopInfo.getInductionVariable().getName(), loopInfo);
        this.forNodes = new Stack<>();
        getEnv().enterScop();
    }

    @TruffleBoundary
    public Stack<T> getInternalForNodes() {
        return forNodes;
    }

    protected static DataType getDominantType(DataType left, DataType right) {
        return MGObject.getDominantType(left, right);
    }

    protected static boolean isIntOrLong(MGNode node) {
        return node.getExpectedType() == DataType.Int || node.getExpectedType() == DataType.Long;
    }

    protected static MGNode castInt(MGNode node) {
        if (node.getExpectedType() != DataType.Int) {
            return new MGNodeUnaryOp(node, UnaryOpType.Cast, DataType.Int);
        }
        return node;
    }

    protected static MGNode castLong(MGNode node) {
        if (node.getExpectedType() != DataType.Long) {
            return new MGNodeUnaryOp(node, UnaryOpType.Cast, DataType.Long);
        }
        return node;
    }

    protected static boolean intOrLong(DataType left, DataType right) {
        return (left == DataType.Int || left == DataType.Long) && (right == DataType.Int || right == DataType.Long);
    }

    protected MGNode NotSupported(T node) throws MGException {
        throw CoverageException.INSTANCE.message("<" + node.getClass().getSimpleName() + ":NOT SUPPORTED>");
    }

    @TruffleBoundary
    protected MGStorage specializeParameter(String name, MGStorage basedOn) throws TypeException {
        switch (basedOn.getDataType()) {
            case IntArray:
                return getEnv().addInducedIntParameter(name, 0, boxUtil.BoxedInt(null));
            case LongArray:
                return getEnv().addInducedLongParameter(name, 0L, boxUtil.BoxedLong(null));
            case DoubleArray:
                return getEnv().addInducedDoubleParameter(name, 0.0, boxUtil.BoxedDouble(null));
        }

        throw TypeException.INSTANCE.message("Unsupported type " + basedOn.getDataType());
    }

    @TruffleBoundary
    protected static Object checkType(DataType type, Object o) throws TypeException {
        switch (type) {
            case Long:
                if (o instanceof Long)
                    return (long) o;
                if (o instanceof Integer) {
                    Integer v = (int) o;
                    return Long.valueOf(v.longValue());
                }
                break;
            case Double:
                if (o instanceof Double)
                    return (double) o;
                break;
            case Int:
                if (o instanceof Integer)
                    return (int) o;
                else if (o instanceof Long)
                    return ((Long) o).intValue();
                break;
        }
        throw TypeException.INSTANCE.message("Type mismatch " + o.getClass() + " with " + type);
    }

    @TruffleBoundary
    public MGNode buildCallTree(MGRoot<T, R> analysis, R function, Object[] arguments, int prefix) throws MGException {
        final ArrayList<MGNode> args = specializeArguments(function, arguments, prefix);
        final MGNodeFunctionCall call = createCall(args, function);

        analysis.setMGRootNode(call);
        getEnv().getGlobalEnv().mergeArgsDefUses();
        analysis.setCoreComputeNode(call);
        return analysis.getMGRootNode();
    }

    @TruffleBoundary
    public MGNode buildForLoopTree(MGRoot<T, R> analysis, T target, T body, long[] range, MGNodeOptions options) throws MGException {
        MGStorage inductionVariable = getInductionVariable(target);
        LoopInfo loopInfo = new LoopInfo(inductionVariable, range, options);
        setHashMaps(loopInfo);
        getEnv().getGlobalEnv().setGlobalLoopInfo(loopInfo, 0);
        getEnv().addLoopInfo(loopInfo);
        analysis.setMGRootNode(visit(body));
        getEnv().getGlobalEnv().mergeArgsDefUses();
        getEnv().getGlobalEnv().setOuterBreak(this.breakFlag != null);
        analysis.setCoreComputeNode(analysis.getMGRootNode());
        return analysis.getMGRootNode();
    }

    @TruffleBoundary
    public MapWorkload<T> buildMapTree(MGRoot<T, R> analysis, R function, long[] range, MGNodeOptions options, Object iterable, Object... otherIterable) throws MGException {
        MGStorage inductionVariable = getEnv().getGlobalEnv().setIteratorVar(ParallelFunctions.Map.inductionVar, 0).copy();
        LoopInfo loopInfo = new LoopInfo(inductionVariable, range, options);
        setHashMaps(loopInfo);
        getEnv().getGlobalEnv().setGlobalLoopInfo(loopInfo, 0);
        getEnv().addLoopInfo(loopInfo);
        Boxed<?> boxList = boxUtil.specialize1DArray(iterable);
        MGArray list = (MGArray) getEnv().addInducedArrayParameter(ParallelFunctions.Map.iterableList, boxList).copy();
        final int arrayLength = list.getArrayInfo().getSize(0);
        list.addIndex(new MGNodeOperand(inductionVariable));
        MGNodeOperand arg = new MGNodeOperand(list);
        getEnv().getArrayReadWrite().addArrayAccess(arg, true);
        getEnv().addArrayAccess(arg);
        ArrayList<MGNode> args = new ArrayList<>();
        args.add(arg);
        MGArray[] otherList = new MGArray[otherIterable.length];
        for (int i = 0; i < otherIterable.length; i++) {
            final Boxed<?> otherboxList = boxUtil.specialize1DArray(otherIterable[i]);
            otherList[i] = (MGArray) getEnv().addInducedArrayParameter(ParallelFunctions.Map.iterableList + i, otherboxList).copy();
            if (otherList[i].getArrayInfo().getSize(0) != arrayLength) {
                throw CoverageException.INSTANCE.message("Inconsistent arrays length");
                // TODO: add the same check to megaguard
            }
            otherList[i].addIndex(new MGNodeOperand(inductionVariable));
            final MGNodeOperand _arg = new MGNodeOperand(otherList[i]);
            getEnv().getArrayReadWrite().addArrayAccess(_arg, true);
            getEnv().addArrayAccess(_arg);
            args.add(_arg);
        }
        analysis.setCoreComputeNode(processUserFunction(function, args, ""));
        getEnv().getGlobalEnv().mergeArgsDefUses();
        final DataType type = analysis.getCoreComputeNode().getExpectedType();
        MapWorkload<T> resultLoad = new MapWorkload<>(LoadType.GlobalSize, new int[]{0}, type, boxUtil);
        MGArray r = getEnv().createAdjustableArray(ParallelFunctions.Map.resultList, type, resultLoad, true, false);
        r = (MGArray) r.copy();
        r.setReadWrite();
        r.addIndex(new MGNodeOperand(inductionVariable));
        MGNodeOperand left = new MGNodeOperand(r);
        getEnv().getArrayReadWrite().addArrayAccess(left, false);
        getEnv().addArrayAccess(left);
        analysis.setMGRootNode(new MGNodeAssign(left, analysis.getCoreComputeNode()));
        return resultLoad;
    }

    @TruffleBoundary
    public Object buildReduceTree(MGRoot<T, R> analysis, R function, Object iterable, Object initializer, boolean hasInitializer, long[] range, MGNodeOptions options) throws MGException {
        MGStorage globalIDVar = getEnv().getGlobalEnv().setIteratorVar(ParallelFunctions.Reduce.globalID, 0);
        LoopInfo loopInfo = new LoopInfo(globalIDVar.copy(), range, options);
        setHashMaps(loopInfo);
        getEnv().getGlobalEnv().setGlobalLoopInfo(loopInfo, 0);
        getEnv().addLoopInfo(loopInfo);
        int initFlagValue = hasInitializer ? 1 : 0;

        Boxed<?> boxList = boxUtil.specialize1DArray(iterable);
        MGArray listOrigin = getEnv().addInducedArrayParameter(ParallelFunctions.Reduce.iterableList, boxList);
        listOrigin.setNoBounds(true);
        listOrigin.setReadOnly();
        MGStorage listSize = getEnv().addInducedIntParameter(ParallelFunctions.Reduce.listSize, ((Long) range[1]).intValue(), boxUtil.BoxedInt(null));
        MGStorage initFlag = getEnv().addInducedIntParameter(ParallelFunctions.Reduce.initialFlag, initFlagValue, boxUtil.BoxedInt(null));
        MGStorage initializerValue = specializeParameter(ParallelFunctions.Reduce.initializerValue, listOrigin);
        if (initFlagValue == 1) {
            initializerValue.updateValue(checkType(initializerValue.getDataType(), initializer));
        }
        initializerValue = initializerValue.copy();
        // temp_result will be based on the function return type
        MGArray listT = (MGArray) listOrigin.copy();
        listT.addIndex(new MGNodeOperand(globalIDVar.copy()));
        MGNodeOperand argT = new MGNodeOperand(listT);
        getEnv().getArrayReadWrite().addArrayAccess(argT, true);
        getEnv().addArrayAccess(argT);
        ArrayList<MGNode> argsT = new ArrayList<>();
        argsT.add(argT);
        argsT.add(argT.copy());
        analysis.setCoreComputeNode(processUserFunction(function, argsT, ""));
        getEnv().getGlobalEnv().mergeArgsDefUses();
        final DataType type = analysis.getCoreComputeNode().getExpectedType();

        // add temp parameter
        ParallelWorkload tempResultLoad = new ReductionWorkload(LoadType.LocalSize, new int[]{0}, type);
        MGArray tr = getEnv().createAdjustableArray(ParallelFunctions.Reduce.tempResult, type, tempResultLoad, true, true);

        // result will be based on the function return type
        ParallelWorkload resultLoad = new ReductionWorkload(LoadType.GroupSize, new int[]{0}, type);
        MGArray r = getEnv().createAdjustableArray(ParallelFunctions.Reduce.result, type, resultLoad, true, false);

        // This is a helping data structure to help with swapping data after kernel executions.
        ParallelWorkload resultLoad2 = new ReductionWorkload(LoadType.GroupSize, new int[]{0}, type);
        getEnv().createAdjustableArray(ParallelFunctions.Reduce.result2, type, resultLoad2, true, false);

        /* ************************************* */

        ArrayList<MGNode> block = new ArrayList<>();
        MGNode localIDVar = new MGNodeOperand(new MGInt(ParallelFunctions.Reduce.localID));
        block.add(new MGNodeAssign(localIDVar, new MGNodeSpecial.ParallelNodeLocalID(0)));

        MGNode arg11 = new MGNodeOperand(initializerValue);
        MGArray list1 = (MGArray) listOrigin.copy();
        list1.addIndex(new MGNodeOperand(globalIDVar.copy()));
        MGNode arg12 = new MGNodeOperand(list1);
        MGNodeFunctionCall call1 = (MGNodeFunctionCall) analysis.getCoreComputeNode().copy();
        call1.getArgs().clear();
        call1.getArgs().add(arg11);
        call1.getArgs().add(arg12);
        MGArray tr1 = (MGArray) tr.copy();
        tr1.addIndex(localIDVar.copy());
        MGNode ifthen2 = new MGNodeAssign(new MGNodeOperand(tr1), call1);

        MGArray list2 = (MGArray) listOrigin.copy();
        list2.addIndex(new MGNodeOperand(globalIDVar.copy()));
        MGArray tr2 = (MGArray) tr.copy();
        tr2.addIndex(localIDVar.copy());
        MGNode ifelse2 = new MGNodeAssign(new MGNodeOperand(tr2), new MGNodeOperand(list2));

        MGNode ifcond2left = new MGNodeBinOp(new MGNodeOperand(initFlag.copy()), BinOpType.Equal, new MGNodeOperand(new MGIntLiteral(1)), DataType.Bool).setTrusted();
        MGNode ifcond2right = new MGNodeBinOp(new MGNodeOperand(globalIDVar.copy()), BinOpType.Equal, new MGNodeOperand(new MGIntLiteral(0)), DataType.Bool).setTrusted();
        MGNode ifcond2 = new MGNodeBinOp(ifcond2left, BinOpType.AND, ifcond2right, DataType.Bool).setTrusted();
        MGNode ifthen1 = new MGNodeIf(ifcond2, ifthen2, ifelse2);

        MGNode ifelse1 = new MGNodeReturn(null);
        MGNode ifcond1 = new MGNodeBinOp(new MGNodeOperand(globalIDVar.copy()), BinOpType.LessThan, new MGNodeOperand(listSize.copy()), DataType.Bool).setTrusted();
        block.add(new MGNodeIf(ifcond1, ifthen1, ifelse1));

        ArrayList<MGNode> forCond1 = new ArrayList<>();
        MGInt j = new MGInt(ParallelFunctions.Reduce.j);
        forCond1.add(new MGNodeBinOp(new MGNodeOperand(globalIDVar.copy()), BinOpType.ADD, new MGNodeSpecial.ParallelNodeGlobalSize(0), DataType.Int).setTrusted());
        forCond1.add(new MGNodeOperand(listSize.copy()));
        forCond1.add(new MGNodeSpecial.ParallelNodeGlobalSize(0));
        LoopInfo info1 = new LoopInfo(j.copy(), forCond1, options);

        MGArray tr3 = (MGArray) tr.copy();
        tr3.addIndex(localIDVar.copy());
        MGNode arg21 = new MGNodeOperand(tr3);
        MGArray list3 = (MGArray) listOrigin.copy();
        list3.addIndex(new MGNodeOperand(j.copy()));
        MGNode arg22 = new MGNodeOperand(list3);
        MGNodeFunctionCall call2 = (MGNodeFunctionCall) analysis.getCoreComputeNode().copy();
        call2.getArgs().clear();
        call2.getArgs().add(arg21);
        call2.getArgs().add(arg22);
        MGArray tr4 = (MGArray) tr.copy();
        tr4.addIndex(localIDVar.copy());
        MGNode forBody1 = new MGNodeAssign(new MGNodeOperand(tr4), call2);
        block.add(new MGNodeFor(forBody1, info1, null));

        block.add(new MGNodeSpecial.ParallelNodeLocalBarrier());

        ArrayList<MGNode> forCond2 = new ArrayList<>();
        MGStorage offset = new MGInt(ParallelFunctions.Reduce.offset);
        forCond2.add(new MGNodeOperand(new MGIntLiteral(1)));
        forCond2.add(new MGNodeSpecial.ParallelNodeLocalSize(0));
        forCond2.add(new MGNodeOperand(new MGIntLiteral(1)));
        LoopInfo info2 = new LoopInfo(offset.copy(), forCond2, options);
        info2.setStepOp(MGNodeBinOp.BinOpType.LeftShift);

        MGArray tr5 = (MGArray) tr.copy();
        tr5.addIndex(localIDVar.copy());
        MGNode arg31 = new MGNodeOperand(tr5);
        MGArray tr6 = (MGArray) tr.copy();
        tr6.addIndex(new MGNodeBinOp(localIDVar.copy(), BinOpType.ADD, new MGNodeOperand(offset.copy()), DataType.Int).setTrusted());
        MGNode arg32 = new MGNodeOperand(tr6);
        MGNodeFunctionCall call3 = (MGNodeFunctionCall) analysis.getCoreComputeNode().copy();
        call3.getArgs().clear();
        call3.getArgs().add(arg31);
        call3.getArgs().add(arg32);
        MGArray tr7 = (MGArray) tr.copy();
        tr7.addIndex(localIDVar.copy());
        MGNode ifthen3 = new MGNodeAssign(new MGNodeOperand(tr7), call3);
        MGNode maskLeft = new MGNodeBinOp(new MGNodeOperand(offset.copy()), BinOpType.LeftShift, new MGNodeOperand(new MGIntLiteral(1)), DataType.Int).setTrusted();
        MGNode mask = new MGNodeBinOp(maskLeft, BinOpType.SUB, new MGNodeOperand(new MGIntLiteral(1)), DataType.Int).setTrusted();
        MGNode ifcond3left = new MGNodeBinOp(localIDVar.copy(), BinOpType.BitAND, mask, DataType.Int).setTrusted();
        MGNode ifcond3 = new MGNodeBinOp(ifcond3left, BinOpType.Equal, new MGNodeOperand(new MGIntLiteral(0)), DataType.Bool).setTrusted();

        ArrayList<MGNode> forBodyStmts = new ArrayList<>();
        forBodyStmts.add(new MGNodeIf(ifcond3, ifthen3, null));
        forBodyStmts.add(new MGNodeSpecial.ParallelNodeLocalBarrier());
        MGNode forBody2 = new MGNodeBlock(forBodyStmts);

        block.add(new MGNodeFor(forBody2, info2, null));

        MGNode ifcond4 = new MGNodeBinOp(localIDVar.copy(), BinOpType.Equal, new MGNodeOperand(new MGIntLiteral(0)), DataType.Bool).setTrusted();

        r = (MGArray) r.copy();
        r.setReadWrite();
        r.addIndex(new MGNodeSpecial.ParallelNodeGroupID(0));
        MGNode left = new MGNodeOperand(r);
        MGArray tr8 = (MGArray) tr.copy();
        tr8.addIndex(new MGNodeOperand(new MGIntLiteral(0)));

        MGNode ifthen4 = new MGNodeAssign(left, new MGNodeOperand(tr8));

        block.add(new MGNodeIf(ifcond4, ifthen4, null));

        analysis.setMGRootNode(new MGNodeBlock(block));

        return resultLoad;
    }

    protected abstract MGBaseEnv getEnv();

    protected abstract MGStorage getInductionVariable(T target) throws TypeException;

    protected abstract MGNode processUserFunction(R function, ArrayList<MGNode> args, String functionTag) throws MGException;

    protected abstract MGNode visit(T node) throws MGException;

}
