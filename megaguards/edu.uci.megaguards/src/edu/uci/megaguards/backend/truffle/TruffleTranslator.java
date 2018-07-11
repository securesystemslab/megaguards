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
package edu.uci.megaguards.backend.truffle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.analysis.parallel.exception.DataDependenceException;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGEnvASTCheck;
import edu.uci.megaguards.ast.env.MGEnvBuilder;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.env.MGPrivateEnv;
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
import edu.uci.megaguards.ast.node.MGNodeUserFunction;
import edu.uci.megaguards.ast.node.MGNodeWhile;
import edu.uci.megaguards.ast.node.MGVisitorIF;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.MGObjectTracker;
import edu.uci.megaguards.backend.MGParallel;
import edu.uci.megaguards.backend.MGTruffle;
import edu.uci.megaguards.backend.MGTruffle.FunctionRoot;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLAutoDevice;
import edu.uci.megaguards.backend.truffle.node.MGTBinaryArithmetic;
import edu.uci.megaguards.backend.truffle.node.MGTBinaryBoolean;
import edu.uci.megaguards.backend.truffle.node.MGTCall;
import edu.uci.megaguards.backend.truffle.node.MGTCall.MGTCallVoid;
import edu.uci.megaguards.backend.truffle.node.MGTControl;
import edu.uci.megaguards.backend.truffle.node.MGTLoop;
import edu.uci.megaguards.backend.truffle.node.MGTMathFunction;
import edu.uci.megaguards.backend.truffle.node.MGTNode;
import edu.uci.megaguards.backend.truffle.node.MGTOperand;
import edu.uci.megaguards.backend.truffle.node.MGTParallel;
import edu.uci.megaguards.backend.truffle.node.MGTRollback;
import edu.uci.megaguards.backend.truffle.node.MGTUnaryOp;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGLiteral;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;

public class TruffleTranslator implements MGVisitorIF<MGTNode<?>> {

    private final MGLog log;

    private HashMap<MGNodeUserFunction, MGTruffle.FunctionRoot> functions;

    private static final String RETURN = "<ret_val>";
    private static final String BACKUP = "<backup>";

    private MGTOperand<?> returnFrame;
    private FrameDescriptor frameDescriptor;
    private HashSet<String> defined;
    private MGBaseEnv currentEnv;
    private HashMap<MGArray, MGTNode<?>> restoresList;
    private HashMap<MGArray, MGTNode<?>> backupsList;
    private final HashSet<MGArray> rwSet;

    private final HashSet<MGArray> offloadedData;
    private final HashSet<MGArray> truffleArrayUse;

    private MGObjectTracker changesTracker;

    @TruffleBoundary
    public TruffleTranslator(MGLog log) {
        this.log = log;
        functions = new HashMap<>();
        this.defined = new HashSet<>();
        this.rwSet = new HashSet<>();
        this.offloadedData = new HashSet<>();
        this.truffleArrayUse = new HashSet<>();
        this.restoresList = null;
        this.backupsList = null;
    }

    @TruffleBoundary
    private void processUserFunctions(MGGlobalEnv env) {
        /**
         * First we register each function to avoid recursive calls loop
         */
        for (Entry<String, MGPrivateEnv> entry : env.getPrivateEnvironments().entrySet()) {
            MGPrivateEnv e = entry.getValue();
            MGTruffle.FunctionRoot f;
            FrameDescriptor fd = new FrameDescriptor();
            if (e.getFunction().getExpectedType() == DataType.None) {
                f = new MGTruffle.FunctionRootVoid(e, e.getFunction(), fd);
            } else {
                frameDescriptor = fd;
                MGTOperand<?> r = localVarOperand(RETURN, e.getFunction().getExpectedType());
                f = new FunctionRoot(e, e.getFunction(), r, fd);
            }
            functions.put(e.getFunction(), f);
        }

        /**
         * Translate each function
         */
        for (Entry<MGNodeUserFunction, FunctionRoot> e : functions.entrySet()) {
            currentEnv = e.getKey().getPrivateEnv();
            frameDescriptor = e.getValue().getFrameDescriptor();
            returnFrame = e.getValue().getReturnVal();
            MGTNode<?> body = visitor(e.getKey().getBody());
            final MGTNode<?>[] params = new MGTNode<?>[e.getKey().getParameters().size() + currentEnv.getParameters().size() - currentEnv.getNumLocalParams()];

            int i = 0;
            for (; i < e.getKey().getParameters().size(); i++) {
                MGStorage var = e.getKey().getParameters().get(i);
                if (var instanceof MGArray && ((MGArray) var).isLocal())
                    continue;
                params[i] = argsVarOperand(var, i);
            }

            for (String p : currentEnv.getOrderedParameters()) {
                MGStorage s = currentEnv.getParameters().get(p);
                params[i] = argsGlobalVarOperand(s);
                i++;
            }

            MGTControl.Args paramBlock = new MGTControl.Args(params);
            MGTControl.Block block = new MGTControl.Block(new MGTNode<?>[]{paramBlock, body});

            e.getValue().setBody(block);
            defined.clear();
            returnFrame = null;
        }
    }

    @TruffleBoundary
    public MGTruffle translateToCall(MGGlobalEnv env) {
        return translate(env, false);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    private MGTLoop.For processGlobalTruffleLoop(MGGlobalEnv env) {
        final MGTNode<?> body = visitor(env.getMGRootNode());
        final MGTOperand<Integer> inductionVar = (MGTOperand<Integer>) localVarOperand(env.getIteratorVar()[0].getName(), DataType.Int);
        final MGTNode<Integer> start = new MGTOperand.ArgOperand<>(0, DataType.Int);
        final MGTNode<Integer> stop = new MGTOperand.ArgOperand<>(1, DataType.Int);
        final MGTNode<Integer> step = new MGTOperand.ArgOperand<>(2, DataType.Int);
        final MGTLoop.For fornode = new MGTLoop.For(inductionVar, start, stop, step, body);
        return fornode;
    }

    @TruffleBoundary
    private MGTruffle translate(MGGlobalEnv env, boolean isLoop) {
        final boolean TruffleMode = MGOptions.Backend.target == ExecutionMode.Truffle;
        changesTracker = new MGObjectTracker(env.sizeOfChangeList());
        processUserFunctions(env);
        currentEnv = env;
        frameDescriptor = new FrameDescriptor();
        final MGTruffle root = new MGTruffle(env, frameDescriptor);
        restoresList = new HashMap<>();
        backupsList = new HashMap<>();
        final MGTNode<?>[] params = new MGTNode<?>[currentEnv.getParameters().size() - currentEnv.getNumLocalParams()];
        int i = 0;
        for (String p : currentEnv.getOrderedParameters()) {
            MGStorage s = currentEnv.getParameters().get(p).getOrigin();
            if (s instanceof MGArray && ((MGArray) s).isLocal())
                continue;
            params[i] = argsGlobalVarOperand(s);
            i++;
        }
        final MGTControl paramBlock = i > 0 ? new MGTControl.Args(params) : new MGTControl.EmptyNode();
        final HashMap<MGArray, MGTNode<?>> restoresListTemp = restoresList;
        final HashMap<MGArray, MGTNode<?>> backupsListTemp = backupsList;
        restoresList = null;
        backupsList = null;
        OpenCLAutoDevice.deviceLocked = true;

        final MGTNode<?> rootNode;
        if (isLoop) {
            rootNode = processGlobalTruffleLoop(env);
        } else {
            rootNode = visitor(env.getMGRootNode());
        }
        // clean unnecessary backups and restores
        final ArrayList<MGArray> rwList = new ArrayList<>();
        final ArrayList<MGTNode<?>> backupsL = new ArrayList<>();
        final ArrayList<MGTNode<?>> restoresL = new ArrayList<>();
        for (MGArray s : truffleArrayUse) {
            if (backupsListTemp.containsKey(s)) {
                backupsL.add(backupsListTemp.get(s));
                restoresL.add(restoresListTemp.get(s));
            }

            if (rwSet.contains(s)) {
                rwList.add(s);
            }
        }
        final MGTNode<?>[] restores = new MGTNode<?>[backupsL.size()];
        final MGTNode<?>[] backups = new MGTNode<?>[restoresL.size()];
        for (int j = 0; j < restoresL.size(); j++) {
            restores[j] = restoresL.get(j);
            backups[j] = backupsL.get(j);
        }
        final MGTControl backupsBlock = backupsL.size() == 0 ? new MGTControl.EmptyNode() : new MGTControl.Block(backups);
        final MGTControl restoreBlock = restoresL.size() == 0 ? new MGTControl.EmptyNode() : new MGTControl.Block(restores);
        restoresListTemp.clear();
        backupsListTemp.clear();
        final MGTControl nc;
        if (rwSet.size() > 0) {
            MGArray[] readWrites = new MGArray[rwList.size()];
            for (int j = 0; j < rwList.size(); j++) {
                readWrites[j] = rwList.get(j);
            }
            nc = new MGTControl.NotifyChanges(readWrites);
        } else {
            nc = new MGTControl.EmptyNode();
        }

        final MGTNode<?> getdata;
        if (offloadedData.size() != 0) {
            final HashSet<MGArray> onDeviceOnlyData = new HashSet<>(offloadedData.size());
            for (MGArray a : offloadedData) {
                if (!truffleArrayUse.contains(a)) {
                    onDeviceOnlyData.add(a);
                }
            }

            getdata = TruffleMode ? new MGTControl.EmptyNode() : new MGTParallel.OpenCLGetData(changesTracker);
            offloadedData.clear();
        } else {
            getdata = new MGTControl.EmptyNode();
        }

        root.setBody(new MGTControl.MainBlock(paramBlock, backupsBlock, rootNode, getdata, nc, restoreBlock));
        return root;
    }

    @TruffleBoundary
    public MGTruffle translateToLoop(MGGlobalEnv env) {
        return translate(env, true);
    }

    private MGTNode<?> visitor(MGNode root) {
        try {
            return root.accept(this);
        } catch (Exception e) {
            e.printStackTrace();
            log.printException(e);
        }
        assert false : "There is something uncovered!";
        return NotSupported();
    }

    @SuppressWarnings("unchecked")
    private static MGTNode<Integer> ensureInt(MGTNode<?> node) {
        if (node.getType() == DataType.Int) {
            return (MGTNode<Integer>) node;
        } else {
            switch (node.getType()) {
                case Long:
                    return new MGTUnaryOp.CastLongToInt((MGTNode<Long>) node);
                case Double:
                    return new MGTUnaryOp.CastDoubleToInt((MGTNode<Double>) node);
            }
        }
        return (MGTNode<Integer>) NotSupported();
    }

    @SuppressWarnings("unchecked")
    private static MGTNode<Long> ensureLong(MGTNode<?> node) {
        if (node.getType() == DataType.Long) {
            return (MGTNode<Long>) node;
        } else {
            switch (node.getType()) {
                case Double:
                    return new MGTUnaryOp.CastDoubleToLong((MGTNode<Double>) node);
                case Int:
                    return new MGTUnaryOp.CastIntToLong((MGTNode<Integer>) node);
            }
        }
        return (MGTNode<Long>) NotSupported();
    }

    @SuppressWarnings("unchecked")
    private static MGTNode<Double> ensureDouble(MGTNode<?> node) {
        if (node.getType() == DataType.Double) {
            return (MGTNode<Double>) node;
        } else {
            switch (node.getType()) {
                case Long:
                    return new MGTUnaryOp.CastLongToDouble((MGTNode<Long>) node);
                case Int:
                    return new MGTUnaryOp.CastIntToDouble((MGTNode<Integer>) node);
            }
        }
        return (MGTNode<Double>) NotSupported();
    }

    @SuppressWarnings("unchecked")
    private MGTNode<Integer> boundCheck(MGArray arrayValue, int i, MGTNode<Integer> idx) {
        if (arrayValue.isNoBounds())
            return idx;
        final String name = arrayValue.getName();
        final MGBoundNode bound = arrayValue.getBounds()[i];
        final MGTNode<Integer> dimSize = (MGTNode<Integer>) localVarOperand(name + MGBaseEnv.DIMSIZE + i, DataType.Int);
        if (bound != null && bound.isRequireBoundCheck()) {
            if (MGOptions.Backend.BoundCheckDebug > 0) {
                MGLog.printlnErrTagged(" Runtime Bound Check set '" + arrayValue.getName() + "[ " + idx + " ]' dimension: " + i);
            }
            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.BoundCheck(idx, dimSize);
        }

        return idx;
    }

    // process first index of ND arrays
    @SuppressWarnings("unchecked")
    private MGTNode<Integer> processNDAccess(MGArray array) {
        int dims = array.getArrayInfo().getDim();
        final String name = array.getName();
        final MGTNode<?>[] dimSizes = new MGTNode<?>[dims];
        dimSizes[0] = new MGTOperand.ConstOperand<>(1, DataType.Int); // we don't need these values
        dimSizes[dims - 1] = new MGTOperand.ConstOperand<>(1, DataType.Int); /*- we don't need these values */
        for (int i = dims - 2; i >= 0; i--) {
            final MGTNode<Integer> left = (MGTNode<Integer>) localVarOperand(name + MGBaseEnv.DIMSIZE + i, DataType.Int);
            final MGTNode<Integer> right = (MGTNode<Integer>) dimSizes[i + 1].deepCopy();
            dimSizes[i] = new MGTBinaryArithmetic.MGTBinaryArithmeticInt.MulNode(left, right);
        }

        MGTNode<Integer> sum = null;
        for (int i = 0; i < dims - 2; i++) {
            MGTNode<Integer> idx = ensureInt(visitor(array.getIndices()[i]));
            idx = boundCheck(array, i, idx);
            final MGTNode<Integer> size = (MGTNode<Integer>) dimSizes[i + 1].deepCopy();
            idx = new MGTBinaryArithmetic.MGTBinaryArithmeticInt.MulNode(idx, size);
            if (sum == null) {
                sum = idx;
            } else {
                sum = new MGTBinaryArithmetic.MGTBinaryArithmeticInt.AddNode(idx, sum);
            }
        }

        MGTNode<Integer> i = ensureInt(visitor(array.getIndices()[dims - 2]));

        if (sum != null) {
            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.AddNode(sum, i);
        }

        return i;
    }

    private MGTOperand<?> localVarOperand(String name, DataType type) {
        FrameSlot slot = frameDescriptor.findFrameSlot(name);
        switch (type) {
            case Double:
                slot = slot != null ? slot : frameDescriptor.addFrameSlot(name, FrameSlotKind.Double);
                return new MGTOperand.VarOperand.DoubleOperand(slot);
            case Long:
                slot = slot != null ? slot : frameDescriptor.addFrameSlot(name, FrameSlotKind.Long);
                return new MGTOperand.VarOperand.LongOperand(slot);
            case Int:
                slot = slot != null ? slot : frameDescriptor.addFrameSlot(name, FrameSlotKind.Int);
                return new MGTOperand.VarOperand.IntOperand(slot);
            case Bool:
                slot = slot != null ? slot : frameDescriptor.addFrameSlot(name, FrameSlotKind.Boolean);
                return new MGTOperand.VarOperand.BooleanOperand(slot);
            default:
                slot = slot != null ? slot : frameDescriptor.addFrameSlot(name, FrameSlotKind.Object);
                return new MGTOperand.VarOperand.ObjectOperand(slot, type);

        }
    }

    @SuppressWarnings("unchecked")
    private MGTNode<?> argsGlobalVarOperand(MGStorage s) {
        String name = s.getName();
        DataType type = s.getDataType();
        MGTOperand<?> val = null;
        if (s instanceof MGArray) {
            MGArray array = (MGArray) s;
            val = new MGTOperand.MGArrayToValue(array);
            MGTOperand<Object> argO = (MGTOperand<Object>) localVarOperand(name, type);
            MGTNode<?> assign = new MGTControl.Assign<>(argO, (MGTNode<Object>) val);
            if (restoresList != null) {
                if (!array.isReadOnly() && !array.isLocal()) {
                    rwSet.add(array);
                    MGTRollback.Backup backup = null;
                    MGTRollback.Restore restore = null;
                    MGTOperand<Object> argOBackup = (MGTOperand<Object>) localVarOperand(name + BACKUP, type);
                    if (array.getDim() > 1) {
                        switch (type) {
                            case IntArray:
                                backup = new MGTRollback.Backup.ArrayNDInt((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.ArrayNDInt(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                            case LongArray:
                                backup = new MGTRollback.Backup.ArrayNDLong((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.ArrayNDLong(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                            case DoubleArray:
                                backup = new MGTRollback.Backup.ArrayNDDouble((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.ArrayNDDouble(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                            case BoolArray:
                                backup = new MGTRollback.Backup.ArrayNDBoolean((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.ArrayNDBoolean(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                        }
                    } else {
                        switch (type) {
                            case IntArray:
                                backup = new MGTRollback.Backup.Array1DInt((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.Array1DInt(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                            case LongArray:
                                backup = new MGTRollback.Backup.Array1DLong((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.Array1DLong(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                            case DoubleArray:
                                backup = new MGTRollback.Backup.Array1DDouble((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.Array1DDouble(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                            case BoolArray:
                                backup = new MGTRollback.Backup.Array1DBoolean((MGTOperand<Object>) argO.copy(), argOBackup);
                                restore = new MGTRollback.Restore.Array1DBoolean(argOBackup, (MGTOperand<Object>) argO.copy(), changesTracker);
                                break;
                        }
                    }

                    restoresList.put(array, restore);
                    backupsList.put(array, backup);
                }
            }
            return assign;
        } else {
            val = new MGTOperand.MGStorageToValue(s);
            switch (type) {
                case Double:
                    MGTOperand<Double> argD = (MGTOperand<Double>) localVarOperand(name, type);
                    MGTNode<Double> valD = ensureDouble(val);
                    return new MGTControl.Assign<>(argD, valD);
                case Long:
                    MGTOperand<Long> argL = (MGTOperand<Long>) localVarOperand(name, type);
                    MGTNode<Long> valL = ensureLong(val);
                    return new MGTControl.Assign<>(argL, valL);
                case Int:
                    MGTOperand<Integer> argI = (MGTOperand<Integer>) localVarOperand(name, type);
                    MGTNode<Integer> valI = ensureInt(val);
                    return new MGTControl.Assign<>(argI, valI);
                case Bool:
                    MGTOperand<Boolean> argB = (MGTOperand<Boolean>) localVarOperand(name, type);
                    MGTNode<Boolean> valB = (MGTNode<Boolean>) val;
                    return new MGTControl.Assign<>(argB, valB);
                default:
                    MGTOperand<Object> argO = (MGTOperand<Object>) localVarOperand(name, type);
                    MGTNode<Object> valO = (MGTNode<Object>) val;
                    return new MGTControl.Assign<>(argO, valO);
            }
        }

    }

    @SuppressWarnings("unchecked")
    private MGTNode<?> argsVarOperand(MGStorage var, int idx) {
        String name = var.getName();
        DataType type = var.getDataType();
        switch (type) {
            case Double:
                MGTOperand<Double> argD = (MGTOperand<Double>) localVarOperand(name, type);
                MGTNode<Double> valD = ensureDouble(new MGTOperand.ArgOperand<>(idx, type));
                return new MGTControl.Assign<>(argD, valD);
            case Long:
                MGTOperand<Long> argL = (MGTOperand<Long>) localVarOperand(name, type);
                MGTNode<Long> valL = ensureLong(new MGTOperand.ArgOperand<>(idx, type));
                return new MGTControl.Assign<>(argL, valL);
            case Int:
                MGTOperand<Integer> argI = (MGTOperand<Integer>) localVarOperand(name, type);
                MGTNode<Integer> valI = ensureInt(new MGTOperand.ArgOperand<>(idx, type));
                return new MGTControl.Assign<>(argI, valI);
            case Bool:
                MGTOperand<Boolean> argB = (MGTOperand<Boolean>) localVarOperand(name, type);
                MGTNode<Boolean> valB = new MGTOperand.ArgOperand<>(idx, type);
                return new MGTControl.Assign<>(argB, valB);
            default:
                MGTOperand<Object> argO = (MGTOperand<Object>) localVarOperand(name, type);
                MGTNode<Object> valO = new MGTOperand.ArgOperand<>(idx, type);
                return new MGTControl.Assign<>(argO, valO);

        }
    }

    private MGTOperand<?> processOperand(MGObject o) {
        if (o instanceof MGArray) {
            final String name = o.getName();
            FrameSlot slot = frameDescriptor.findOrAddFrameSlot(name);
            MGArray array = (MGArray) o;

            if (!array.getOrigin().isReadOnly() && currentEnv.getParameters().containsKey(name)) {
                truffleArrayUse.add((MGArray) array.getOrigin());
            }

            if (((MGArray) o).getIndicesLen() == 0)
                return new MGTOperand.VarOperand.ObjectOperand(slot, o.getDataType());

            MGTNode<Integer> idx1, idx2;
            int dims = array.getArrayInfo().getDim();
            idx1 = ensureInt(visitor(array.getIndices()[dims - 1]));
            idx1 = boundCheck(array, dims - 1, idx1);
            switch (o.getDataType()) {
                case Double:
                    if (dims == 1) {
                        return new MGTOperand.Var1DOperand.Double1DOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx1);
                    } else {
                        idx2 = processNDAccess(array);
                        return new MGTOperand.VarNDOperand.DoubleNDOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx2, idx1);
                    }
                case Long:
                    if (dims == 1) {
                        return new MGTOperand.Var1DOperand.Long1DOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx1);
                    } else {
                        idx2 = processNDAccess(array);
                        return new MGTOperand.VarNDOperand.LongNDOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx2, idx1);
                    }
                case Int:
                    if (dims == 1) {
                        return new MGTOperand.Var1DOperand.Int1DOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx1);
                    } else {
                        idx2 = processNDAccess(array);
                        return new MGTOperand.VarNDOperand.IntNDOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx2, idx1);
                    }
                case Bool:
                    if (dims == 1) {
                        return new MGTOperand.Var1DOperand.Boolean1DOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx1);
                    } else {
                        idx2 = processNDAccess(array);
                        return new MGTOperand.VarNDOperand.BooleanNDOperand(slot, changesTracker, (MGArray) array.getOrigin(), idx2, idx1);
                    }
            }

        }

        if (o instanceof MGStorage) {
            return localVarOperand(o.getName(), o.getDataType());
        }

        if (o instanceof MGLiteral) {
            switch (o.getDataType()) {
                case Double:
                    return new MGTOperand.ConstOperand<>((Double) o.getValue(), o.getDataType());
                case Long:
                    return new MGTOperand.ConstOperand<>((Long) o.getValue(), o.getDataType());
                case Int:
                    return new MGTOperand.ConstOperand<>((Integer) o.getValue(), o.getDataType());
                case Bool:
                    return new MGTOperand.ConstOperand<>((Boolean) o.getValue(), o.getDataType());
            }
        }
        return (MGTOperand<?>) NotSupported();
    }

    public MGTNode<?> visitOperand(MGNodeOperand node) {
        return processOperand(node.getValue());
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public MGTNode<?> visitAssignComplex(MGNodeAssignComplex node) {
        MGTNode<?>[] block;
        MGTNode<?> realAssign, imagAssign, tempAssign;
        MGNodeAssign real = node.getReal();
        MGNodeAssign imag = node.getImag();

        MGTOperand<Double> realLeft = (MGTOperand<Double>) visitor(real.getLeft());
        MGTNode<Double> realRight = (MGTNode<Double>) visitor(real.getRight());
        MGTOperand<Double> imagLeft = (MGTOperand<Double>) visitor(imag.getLeft());
        MGTNode<Double> imagRight = (MGTNode<Double>) visitor(imag.getRight());

        if (defined.contains(realLeft.getName())) {
            MGTOperand<Double> tempReal = (MGTOperand<Double>) localVarOperand(MGBaseEnv.createTempName(), DataType.Double);
            tempAssign = new MGTControl.Assign<>(tempReal, realRight);
            imagAssign = new MGTControl.Assign<>(imagLeft, imagRight);
            realAssign = new MGTControl.Assign<>(realLeft, tempReal);
            block = new MGTNode<?>[]{tempAssign, imagAssign, realAssign};
        } else {
            defined.add(realLeft.getName());
            defined.add(imagLeft.getName());
            imagAssign = new MGTControl.Assign<>(imagLeft, imagRight);
            realAssign = new MGTControl.Assign<>(realLeft, realRight);
            block = new MGTNode<?>[]{imagAssign, realAssign};
        }

        return new MGTControl.Block(block);
    }

    @SuppressWarnings("unchecked")
    private MGTNode<?> ifElseAssign(MGNodeIf node) {
        MGTNode<?> then = visitor(node.getThen());
        MGTNode<Boolean> cond = (MGTNode<Boolean>) visitor(node.getCond());
        MGTNode<?> orelse = visitor(node.getOrelse());
        switch (then.getType()) {
            case Double:
                return new MGTOperand.IfElseOperand<>((MGTNode<Double>) then, cond, ensureDouble(orelse), DataType.Double);
            case Long:
                return new MGTOperand.IfElseOperand<>((MGTNode<Long>) then, cond, ensureLong(orelse), DataType.Long);
            case Int:
                return new MGTOperand.IfElseOperand<>((MGTNode<Integer>) then, cond, ensureInt(orelse), DataType.Int);
        }
        return NotSupported();
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    public MGTNode<?> createAssign(MGTOperand<?> left, MGTNode<?> right) {
        switch (left.getType()) {
            case Double:
                return new MGTControl.Assign<>((MGTOperand<Double>) left, ensureDouble(right));
            case Long:
                return new MGTControl.Assign<>((MGTOperand<Long>) left, ensureLong(right));
            case Int:
                return new MGTControl.Assign<>((MGTOperand<Integer>) left, ensureInt(right));
            case Bool:
                return new MGTControl.Assign<>((MGTOperand<Boolean>) left, (MGTNode<Boolean>) right);
            case DoubleArray:
            case LongArray:
            case IntArray:
            case BoolArray:
                return new MGTControl.Assign<>((MGTOperand<Object>) left, (MGTNode<Object>) right);

        }
        return NotSupported();
    }

    @TruffleBoundary
    public MGTNode<?> visitAssign(MGNodeAssign node) {
        MGTOperand<?> left = (MGTOperand<?>) visitor(node.getLeft());
        defined.add(left.getName());
        MGTNode<?> right;
        if (node.getRight() instanceof MGNodeIf)
            right = ifElseAssign((MGNodeIf) node.getRight());
        else
            right = visitor(node.getRight());

        return createAssign(left, right);
    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitUnaryOp(MGNodeUnaryOp node) {
        MGTNode<?> n = visitor(node.getChild());
        switch (node.getType()) {
            case Cast:
                switch (node.getExpectedType()) {
                    case Double:
                        return ensureDouble(n);
                    case Long:
                        return ensureLong(n);
                    case Int:
                        return ensureInt(n);
                }
                break;
            case Not:
                return new MGTUnaryOp.Not((MGTNode<Boolean>) n);

        }
        return NotSupported();
    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitBinOp(MGNodeBinOp node) {
        DataType ltype = node.getLeft().getExpectedType();
        DataType rtype = node.getRight().getExpectedType();

        MGTNode<Double> leftD, rightD;
        MGTNode<Long> leftL, rightL;
        MGTNode<Integer> leftI, rightI;
        MGTNode<Boolean> leftB, rightB;
        switch (node.getExpectedType()) {
            case Double:
                leftD = ensureDouble(visitor(node.getLeft()));
                rightD = ensureDouble(visitor(node.getRight()));
                switch (node.getType()) {
                    case ADD:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticDouble.AddNode(leftD, rightD);
                    case MUL:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticDouble.MulNode(leftD, rightD);
                    case SUB:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticDouble.SubNode(leftD, rightD);
                    case DIV:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticDouble.DivNode(leftD, rightD);
                    case MOD:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticDouble.ModNode(leftD, rightD);
                    case POW:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticDouble.PowNode(leftD, rightD);
                }
                break;
            case Long:
                leftL = ensureLong(visitor(node.getLeft()));
                rightL = ensureLong(visitor(node.getRight()));
                switch (node.getType()) {
                    case ADD:
                        if (node.overflowCheck())
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.AddOFNode(leftL, rightL);
                        else
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.AddNode(leftL, rightL);
                    case MUL:
                        if (node.overflowCheck())
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.MulOFNode(leftL, rightL);
                        else
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.MulNode(leftL, rightL);
                    case SUB:
                        if (node.overflowCheck())
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.SubOFNode(leftL, rightL);
                        else
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.SubNode(leftL, rightL);
                    case DIV:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.DivNode(leftL, rightL);
                    case MOD:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.ModNode(leftL, rightL);
                    case POW:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.PowNode(leftL, rightL);
                    case LeftShift:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.LeftShiftNode(leftL, rightL);
                    case RightShift:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.RightShiftNode(leftL, rightL);
                    case BitAND:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.BitAndNode(leftL, rightL);
                    case BitOR:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.BitOrNode(leftL, rightL);
                    case BitXOR:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticLong.BitXorNode(leftL, rightL);
                }

                break;
            case Int:
                leftI = ensureInt(visitor(node.getLeft()));
                rightI = ensureInt(visitor(node.getRight()));
                switch (node.getType()) {
                    case ADD:
                        if (node.overflowCheck())
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.AddOFNode(leftI, rightI);
                        else
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.AddNode(leftI, rightI);
                    case MUL:
                        if (node.overflowCheck())
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.MulOFNode(leftI, rightI);
                        else
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.MulNode(leftI, rightI);
                    case SUB:
                        if (node.overflowCheck())
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.SubOFNode(leftI, rightI);
                        else
                            return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.SubNode(leftI, rightI);
                    case DIV:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.DivNode(leftI, rightI);
                    case MOD:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.ModNode(leftI, rightI);
                    case POW:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.PowNode(leftI, rightI);
                    case LeftShift:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.LeftShiftNode(leftI, rightI);
                    case RightShift:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.RightShiftNode(leftI, rightI);
                    case BitAND:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.BitAndNode(leftI, rightI);
                    case BitOR:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.BitOrNode(leftI, rightI);
                    case BitXOR:
                        return new MGTBinaryArithmetic.MGTBinaryArithmeticInt.BitXorNode(leftI, rightI);
                }

                break;
            case Bool:
                if (ltype == DataType.Double || rtype == DataType.Double) {
                    leftD = ensureDouble(visitor(node.getLeft()));
                    rightD = ensureDouble(visitor(node.getRight()));
                    switch (node.getType()) {
                        case Equal:
                            return new MGTBinaryBoolean.MGTBinaryBooleanDouble.EqualNode(leftD, rightD);
                        case NotEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanDouble.NotEqualNode(leftD, rightD);
                        case LessThan:
                            return new MGTBinaryBoolean.MGTBinaryBooleanDouble.LessThanNode(leftD, rightD);
                        case LessEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanDouble.LessEqualNode(leftD, rightD);
                        case GreaterThan:
                            return new MGTBinaryBoolean.MGTBinaryBooleanDouble.GreaterThanNode(leftD, rightD);
                        case GreaterEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanDouble.GreaterEqualNode(leftD, rightD);
                    }
                }

                if (ltype == DataType.Long || rtype == DataType.Long) {
                    leftL = ensureLong(visitor(node.getLeft()));
                    rightL = ensureLong(visitor(node.getRight()));
                    switch (node.getType()) {
                        case Equal:
                            return new MGTBinaryBoolean.MGTBinaryBooleanLong.EqualNode(leftL, rightL);
                        case NotEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanLong.NotEqualNode(leftL, rightL);
                        case LessThan:
                            return new MGTBinaryBoolean.MGTBinaryBooleanLong.LessThanNode(leftL, rightL);
                        case LessEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanLong.LessEqualNode(leftL, rightL);
                        case GreaterThan:
                            return new MGTBinaryBoolean.MGTBinaryBooleanLong.GreaterThanNode(leftL, rightL);
                        case GreaterEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanLong.GreaterEqualNode(leftL, rightL);
                    }
                }

                if (ltype == DataType.Int || rtype == DataType.Int) {
                    leftI = ensureInt(visitor(node.getLeft()));
                    rightI = ensureInt(visitor(node.getRight()));
                    switch (node.getType()) {
                        case Equal:
                            return new MGTBinaryBoolean.MGTBinaryBooleanInt.EqualNode(leftI, rightI);
                        case NotEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanInt.NotEqualNode(leftI, rightI);
                        case LessThan:
                            return new MGTBinaryBoolean.MGTBinaryBooleanInt.LessThanNode(leftI, rightI);
                        case LessEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanInt.LessEqualNode(leftI, rightI);
                        case GreaterThan:
                            return new MGTBinaryBoolean.MGTBinaryBooleanInt.GreaterThanNode(leftI, rightI);
                        case GreaterEqual:
                            return new MGTBinaryBoolean.MGTBinaryBooleanInt.GreaterEqualNode(leftI, rightI);
                    }
                }

                if (ltype == DataType.Bool || rtype == DataType.Bool) {

                    leftB = (MGTNode<Boolean>) visitor(node.getLeft());
                    rightB = (MGTNode<Boolean>) visitor(node.getRight());
                    switch (node.getType()) {
                        case AND:
                            return new MGTBinaryBoolean.MGTBinaryBooleanBoolean.AndNode(leftB, rightB);
                        case OR:
                            return new MGTBinaryBoolean.MGTBinaryBooleanBoolean.OrNode(leftB, rightB);
                    }
                }
                break;
        }
        return NotSupported();
    }

    public MGTNode<?> visitBlock(MGNodeBlock node) {
        MGTNode<?>[] block = new MGTNode<?>[node.getChildren().size()];
        for (int i = 0; i < node.getChildren().size(); i++)
            block[i] = visitor(node.getChildren().get(i));

        return new MGTControl.Block(block);
    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitBreakElse(MGNodeBreakElse node) {
        LoopInfo info = node.getForBody().getLoopInfo();
        MGTOperand<Integer> i = (MGTOperand<Integer>) processOperand(info.getInductionVariable());

        MGTNode<Integer> start, step, stop;
        if (info.getStartNode() != null)
            start = ensureInt(visitor(info.getStartNode()));
        else
            start = new MGTOperand.ConstOperand<>(((Long) info.getRange()[0]).intValue(), DataType.Int);

        if (info.getStopNode() != null)
            stop = ensureInt(visitor(info.getStopNode()));
        else
            stop = new MGTOperand.ConstOperand<>(((Long) info.getRange()[1]).intValue(), DataType.Int);

        if (info.getStepNode() != null)
            step = ensureInt(visitor(info.getStepNode()));
        else
            step = new MGTOperand.ConstOperand<>(((Long) info.getRange()[2]).intValue(), DataType.Int);

        MGTNode<?> body = visitor(node.getForBody().getForBody());
        MGTNode<?> orelse = visitor(node.getOrelse());

        return new MGTLoop.For.ForBreakElse(i, start, stop, step, body, orelse);
    }

    public MGTNode<?> visitBuiltinFunction(MGNodeBuiltinFunction node) {
        MGTNode<Double> leftD, rightD;
        MGTNode<Long> leftL, rightL;
        MGTNode<Integer> leftI, rightI;

        switch (node.getExpectedType()) {
            case Double:
                leftD = ensureDouble(visitor(node.getNodes().get(0)));
                rightD = ensureDouble(visitor(node.getNodes().get(1)));
                switch (node.getType()) {
                    case MIN:
                        return new MGTMathFunction.TwoArgsMathFunction.MinDouble(leftD, rightD);
                    case MAX:
                        return new MGTMathFunction.TwoArgsMathFunction.MaxDouble(leftD, rightD);
                }
                break;
            case Long:
                leftL = ensureLong(visitor(node.getNodes().get(0)));
                rightL = ensureLong(visitor(node.getNodes().get(1)));
                switch (node.getType()) {
                    case MIN:
                        return new MGTMathFunction.TwoArgsMathFunction.MinLong(leftL, rightL);
                    case MAX:
                        return new MGTMathFunction.TwoArgsMathFunction.MaxLong(leftL, rightL);
                }

                break;
            case Int:
                leftI = ensureInt(visitor(node.getNodes().get(0)));
                rightI = ensureInt(visitor(node.getNodes().get(1)));
                switch (node.getType()) {
                    case MIN:
                        return new MGTMathFunction.TwoArgsMathFunction.MinInt(leftI, rightI);
                    case MAX:
                        return new MGTMathFunction.TwoArgsMathFunction.MaxInt(leftI, rightI);
                }

                break;
        }
        return NotSupported();
    }

    @TruffleBoundary
    private MGTNode<?> processParallel(MGNodeFor node, MGTNode<Integer> start, MGTNode<Integer> step, MGTNode<Integer> stop) {
        MGGlobalEnv loopEnv = MGEnvBuilder.createInternalEnv(currentEnv, currentEnv.getGlobalEnv(), node);
        MGEnvASTCheck.checkAST(loopEnv, node);
        final MGNodeOptions options = node.getLoopInfo().getOptions();
        final FinalizedVariableValues finalizedValues = new FinalizedVariableValues(loopEnv);
        MGParallel parallelCall = null;
        if (node.hasBreak())
            return null;

        boolean disprovedDependences = !node.isDependenceExists();
        if (!disprovedDependences) {
            try {
                finalizedValues.reloadGlobalLoopInfos();
                MGParallel.dataDependenceAnalysis(options, loopEnv, node.getForBody(), loopEnv.getFinalizedValues(), log);
                disprovedDependences = true;
            } catch (DataDependenceException d) {
                // pass
            }
        }
        if (disprovedDependences) {
            try {
                parallelCall = MGParallel.createInternalLoop(options, log.getSourceSection(), node.getForBody(), loopEnv, log);
            } catch (Exception e) {
                return null;
                // pass
            }
        }
        if (parallelCall == null) {
            return null;
        }
        parallelCall.getExecuter().setLockDevice();
        parallelCall.getExecuter().setChangesTracker(changesTracker);

        for (MGStorage s : loopEnv.getParameters().values()) {
            if (s instanceof MGArray && !s.isReadOnly() && !((MGArray) s).isLocal()) {
                offloadedData.add((MGArray) s);
            }
        }
        ArrayList<MGStorage> localList = new ArrayList<>();
        ArrayList<MGTNode<?>> valueList = new ArrayList<>();
        for (Entry<String, MGStorage> e : loopEnv.getParameters().entrySet()) {
            final MGStorage o = e.getValue();

            if (!(currentEnv.getParameters().containsKey(e.getKey()) || (o instanceof MGArray && ((MGArray) o).getValue() instanceof ParallelWorkload))) {
                localList.add(o);
                valueList.add(processOperand(o));
            }
        }
        MGStorage[] local = new MGStorage[localList.size()];
        MGTNode<?>[] values = new MGTNode<?>[localList.size()];
        for (int i = 0; i < localList.size(); i++) {
            local[i] = localList.get(i);
            values[i] = valueList.get(i);
        }

        return new MGTParallel(parallelCall, loopEnv, start, stop, step, local, values, log);
    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitFor(MGNodeFor node) {
        final LoopInfo info = node.getLoopInfo();
        MGTOperand<Integer> i = (MGTOperand<Integer>) processOperand(info.getInductionVariable());
        MGTNode<Integer> start, step, stop;
        if (info.getStartNode() != null)
            start = ensureInt(visitor(info.getStartNode()));
        else
            start = new MGTOperand.ConstOperand<>(((Long) info.getRange()[0]).intValue(), DataType.Int);

        if (info.getStopNode() != null)
            stop = ensureInt(visitor(info.getStopNode()));
        else
            stop = new MGTOperand.ConstOperand<>(((Long) info.getRange()[1]).intValue(), DataType.Int);

        if (info.getStepNode() != null)
            step = ensureInt(visitor(info.getStepNode()));
        else
            step = new MGTOperand.ConstOperand<>(((Long) info.getRange()[2]).intValue(), DataType.Int);

        if (MGOptions.Backend.target != ExecutionMode.Truffle) {
            try {
                final MGTNode<?> parallel = processParallel(node, start, step, stop);
                if (parallel != null)
                    return parallel;
            } catch (Exception e) {
                if (MGOptions.Debug > 0) {
                    e.printStackTrace();
                }
            }
        }

        final MGTNode<?> body = visitor(node.getForBody());
        if (node.hasBreak())
            return new MGTLoop.For.ForBreak(i, start, stop, step, body);
        else
            return new MGTLoop.For(i, start, stop, step, body);

    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitWhile(MGNodeWhile node) {
        return new MGTLoop.WhileNode((MGTNode<Boolean>) visitor(node.getCond()), visitor(node.getBody()));
    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitIf(MGNodeIf node) {
        MGTNode<Object> then = (MGTNode<Object>) visitor(node.getThen());
        MGTNode<Boolean> cond = (MGTNode<Boolean>) visitor(node.getCond());
        if (node.getOrelse() == null)
            return new MGTControl.If(then, cond);

        MGTNode<Object> orelse = (MGTNode<Object>) visitor(node.getOrelse());
        return new MGTControl.IfElse(then, cond, orelse);
    }

    public MGTNode<?> visitMathFunction(MGNodeMathFunction node) {
        MGTNode<Double> argD, arg2D;
        MGTNode<Long> argL;
        MGTNode<Integer> argI;

        switch (node.getExpectedType()) {
            case Double:
                argD = ensureDouble(visitor(node.getNodes().get(0)));
                switch (node.getType()) {
                    case abs:
                        return new MGTMathFunction.AbsDouble(argD);
                    case acos:
                        return new MGTMathFunction.AcosDouble(argD);
                    case ceil:
                        return new MGTMathFunction.CeilDouble(argD);
                    case cos:
                        return new MGTMathFunction.CosDouble(argD);
                    case exp:
                        return new MGTMathFunction.ExpDouble(argD);
                    case hypot:
                        arg2D = ensureDouble(visitor(node.getNodes().get(1)));
                        return new MGTMathFunction.TwoArgsMathFunction.HypotDouble(argD, arg2D);
                    case log:
                        return new MGTMathFunction.LogDouble(argD);
                    case round:
                        return new MGTMathFunction.RoundDouble(argD);
                    case sin:
                        return new MGTMathFunction.SinDouble(argD);
                    case sqrt:
                        return new MGTMathFunction.SqrtDouble(argD);
                }
                break;
            case Long:
                argL = ensureLong(visitor(node.getNodes().get(0)));
                switch (node.getType()) {
                    case abs:
                        return new MGTMathFunction.AbsLong(argL);
                }

                break;
            case Int:
                argI = ensureInt(visitor(node.getNodes().get(0)));
                switch (node.getType()) {
                    case abs:
                        return new MGTMathFunction.AbsInt(argI);
                }

                break;
        }
        return NotSupported();
    }

    private static MGTNode<?> ensureType(MGTNode<?> node, DataType t) {
        switch (t) {
            case Double:
                return ensureDouble(node);
            case Long:
                return ensureLong(node);
            case Int:
                return ensureInt(node);
        }
        return node;
    }

    @TruffleBoundary
    public MGTNode<?> visitFunctionCall(MGNodeFunctionCall node) {
        FunctionRoot f = functions.get(node.getFunctionNode());
        final MGTNode<?>[] args = new MGTNode<?>[node.getArgs().size()];
        for (int i = 0; i < node.getArgs().size(); i++) {
            MGNode a = node.getArgs().get(i);
            args[i] = ensureType(visitor(a), node.getFunctionNode().getParameters().get(i).getDataType());
        }
        MGTCall<?> call = null;
        if (node.getExpectedType() == DataType.None) {
            call = new MGTCallVoid(f, args);
        } else {
            switch (node.getExpectedType()) {
                case Double:
                    call = new MGTCall<Double>(f, args, DataType.Double);
                    break;
                case Long:
                    call = new MGTCall<Long>(f, args, DataType.Long);
                    break;
                case Int:
                    call = new MGTCall<Integer>(f, args, DataType.Int);
                    break;
                case Bool:
                    call = new MGTCall<Boolean>(f, args, DataType.Bool);
                    break;
            }
        }
        if (call == null)
            return NotSupported();
        return call;
    }

    @SuppressWarnings("unchecked")
    public MGTNode<?> visitReturn(MGNodeReturn node) {
        MGTNode<?> val;
        if (node.getRight() instanceof MGNodeIf)
            val = ifElseAssign((MGNodeIf) node.getRight());
        else
            val = visitor(node.getRight());
        if (val.getType() == DataType.None)
            return new MGTControl.ReturnVoid();
        else {
            switch (returnFrame.getType()) {
                case Double:
                    return new MGTControl.ReturnValue<>((MGTOperand<Double>) returnFrame.copy(), ensureDouble(val));
                case Long:
                    return new MGTControl.ReturnValue<>((MGTOperand<Long>) returnFrame.copy(), ensureLong(val));
                case Int:
                    return new MGTControl.ReturnValue<>((MGTOperand<Integer>) returnFrame.copy(), ensureInt(val));
            }
        }
        return NotSupported();
    }

    public MGTNode<?> visitJumpFrom(MGNodeJumpFrom node) {
        return new MGTControl.Break();
    }

    public MGTNode<?> visitJumpTo(MGNodeJumpTo node) {
        return new MGTControl.EmptyNode();
    }

    private static MGTNode<?> NotSupported() {
        throw CoverageException.INSTANCE.message("Fatal error while translating to Truffle AST");
    }

    public MGTNode<?> visitBreak(MGNodeBreak node) {
        return new MGTControl.Break();
    }

    public MGTNode<?> visitEmpty(MGNodeEmpty node) {
        return new MGTControl.EmptyNode();
    }

    public MGTNode<?> visitOperandComplex(MGNodeOperandComplex node) {
        return new MGTControl.EmptyNode();
    }

    public MGTNode<?> visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeLocalID(ParallelNodeLocalID node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeLocalSize(ParallelNodeLocalSize node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeGroupID(ParallelNodeGroupID node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeGroupSize(ParallelNodeGroupSize node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeGlobalID(ParallelNodeGlobalID node) {
        return NotSupported();
    }

    public MGTNode<?> visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) {
        return NotSupported();
    }

}
