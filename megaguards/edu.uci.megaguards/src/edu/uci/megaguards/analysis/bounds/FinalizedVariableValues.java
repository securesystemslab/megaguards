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

import java.util.HashMap;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeArray;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeBinOp;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeBuiltinFunction;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeEither;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeFail;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeFunctionCall;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeLimit;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeLiteral;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeMathFunction;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeRange;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeVariable;
import edu.uci.megaguards.analysis.bounds.node.MGBoundVisitorIF;
import edu.uci.megaguards.analysis.exception.BoundInvalidateException;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeBinOp.BinOpType;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction.BuiltinFunctionType;
import edu.uci.megaguards.ast.node.MGNodeMathFunction.MathFunctionType;
import edu.uci.megaguards.ast.node.MGNodeOperand;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.unbox.Unboxer;

public class FinalizedVariableValues implements MGBoundVisitorIF<MGBoundNode> {

    private final MGGlobalEnv env;
    private boolean violated;
    private final HashMap<Integer, long[]> arrayValuesBound;
    private long minimumValue;

    private HashMap<String, LoopInfo> currentLoopInfo;
    private boolean isSafe;

    public FinalizedVariableValues(MGGlobalEnv env) {
        this.env = env;
        env.setFinalizedValues(this);
        this.violated = false;
        this.currentLoopInfo = null;
        this.isSafe = true;
        this.arrayValuesBound = new HashMap<>();
        this.minimumValue = 0;
    }

    public boolean isViolated() {
        return violated;
    }

    public long getMinValue() {
        return minimumValue;
    }

    @TruffleBoundary
    public HashMap<Integer, long[]> getArrayValuesBound() {
        return arrayValuesBound;
    }

    @TruffleBoundary
    public long[] finalizeLoopInfo(LoopInfo info) {
        if (info.isSimpleRange())
            return info.getRange();

        info.setRuntimeRange(null);
        final long[] runtimeRange = new long[]{0, 0, 1};

        final MGBoundNode stopBound;
        final MGBoundNode startBound;
        final MGBoundNode stepBound;

        if (info.getStartNode() == null) {
            runtimeRange[0] = info.getRange()[0];
        } else {
            isSafe = true;
            startBound = visitor(info.getBounds().getStart());
            if (isSafe && !(startBound instanceof MGBoundNodeLiteral))
                return null;
            runtimeRange[0] = ((MGBoundNodeLiteral) startBound).getLiteral();
        }
        if (info.getStopNode() == null) {
            runtimeRange[1] = info.getRange()[1];
        } else {
            isSafe = true;
            stopBound = visitor(info.getBounds().getStop());
            if (isSafe && !(stopBound instanceof MGBoundNodeLiteral))
                return null;
            runtimeRange[1] = ((MGBoundNodeLiteral) stopBound).getLiteral();
        }
        if (info.getStepNode() == null) {
            runtimeRange[2] = info.getRange()[2];
        } else {
            isSafe = true;
            stepBound = visitor(info.getBounds().getStep());
            if (isSafe && !(stepBound instanceof MGBoundNodeLiteral))
                return null;
            runtimeRange[2] = ((MGBoundNodeLiteral) stepBound).getLiteral();
        }

        info.setRuntimeRange(runtimeRange);
        return runtimeRange;
    }

    @TruffleBoundary
    public void reloadGlobalLoopInfos() {
        for (int i = 0; i < env.getIterationLevels(); i++) {
            final LoopInfo info = env.getGlobalLoopInfos()[i];
            finalizeLoopInfo(info);
        }

    }

    private void verifySingleBound(MGBoundNode bound, int dimSize) {
        if (bound instanceof MGBoundNodeLiteral) {
            final long literal = ((MGBoundNodeLiteral) bound).getLiteral();
            if (literal < 0) {
                violated = true;
                isSafe = false;
                minimumValue = minimumValue > literal ? literal : minimumValue;

            } else if (((MGBoundNodeLiteral) bound).getLiteral() >= dimSize) {
                violated = true;
                isSafe = false;
            }
        } else if (bound instanceof MGBoundNodeLimit) {
            final MGBoundNode min = ((MGBoundNodeLimit) bound).getMin();
            final MGBoundNode max = ((MGBoundNodeLimit) bound).getMax();
            if (min instanceof MGBoundNodeLiteral && max instanceof MGBoundNodeLiteral) {
                if (((MGBoundNodeLiteral) min).getLiteral() < 0 || ((MGBoundNodeLiteral) max).getLiteral() >= dimSize) {
                    isSafe = false;
                }
            } else {
                if (!(min instanceof MGBoundNodeLiteral)) {
                    verifySingleBound(min, dimSize);
                } else if (((MGBoundNodeLiteral) min).getLiteral() < 0) {
                    isSafe = false;
                }
                if (!(max instanceof MGBoundNodeLiteral)) {
                    verifySingleBound(max, dimSize);
                } else if (((MGBoundNodeLiteral) max).getLiteral() >= dimSize) {
                    isSafe = false;
                }
            }
        } else if (bound instanceof MGBoundNodeEither) {
            final MGBoundNode lhs = ((MGBoundNodeEither) bound).getThen();
            final MGBoundNode rhs = ((MGBoundNodeEither) bound).getOrelse();
            if (lhs instanceof MGBoundNodeLiteral) {
                if (((MGBoundNodeLiteral) lhs).getLiteral() < 0 || ((MGBoundNodeLiteral) lhs).getLiteral() >= dimSize) {
                    isSafe = false;
                }
            } else {
                verifySingleBound(lhs, dimSize);
            }
            if (rhs instanceof MGBoundNodeLiteral) {
                if (((MGBoundNodeLiteral) rhs).getLiteral() < 0 || ((MGBoundNodeLiteral) rhs).getLiteral() >= dimSize) {
                    isSafe = false;
                }
            } else {
                verifySingleBound(rhs, dimSize);
            }

        } else if (bound instanceof MGBoundNodeFail) {
            isSafe = false;
        } else {
            assert false : "There is something wrong";
        }
    }

    private void verifyOFSingleBound(MGBoundNode bound, long lower, long upper) {
        if (bound instanceof MGBoundNodeLiteral) {
            if (((MGBoundNodeLiteral) bound).getLiteral() == lower) {
                isSafe = false;
            } else if (((MGBoundNodeLiteral) bound).getLiteral() == upper) {
                isSafe = false;
            }
        } else if (bound instanceof MGBoundNodeLimit) {
            final MGBoundNode min = ((MGBoundNodeLimit) bound).getMin();
            final MGBoundNode max = ((MGBoundNodeLimit) bound).getMax();
            if (min instanceof MGBoundNodeLiteral && max instanceof MGBoundNodeLiteral) {
                if (((MGBoundNodeLiteral) min).getLiteral() == lower || ((MGBoundNodeLiteral) max).getLiteral() == upper) {
                    isSafe = false;
                }
            } else {
                if (!(min instanceof MGBoundNodeLiteral)) {
                    verifyOFSingleBound(min, lower, upper);
                } else if (((MGBoundNodeLiteral) min).getLiteral() == lower) {
                    isSafe = false;
                }
                if (!(max instanceof MGBoundNodeLiteral)) {
                    verifyOFSingleBound(max, lower, upper);
                } else if (((MGBoundNodeLiteral) max).getLiteral() == upper) {
                    isSafe = false;
                }
            }
        } else if (bound instanceof MGBoundNodeEither) {
            final MGBoundNode lhs = ((MGBoundNodeEither) bound).getThen();
            final MGBoundNode rhs = ((MGBoundNodeEither) bound).getOrelse();
            if (lhs instanceof MGBoundNodeLiteral) {
                if (((MGBoundNodeLiteral) lhs).getLiteral() == lower || ((MGBoundNodeLiteral) lhs).getLiteral() == upper) {
                    isSafe = false;
                }
            } else {
                verifyOFSingleBound(lhs, lower, upper);
            }
            if (rhs instanceof MGBoundNodeLiteral) {
                if (((MGBoundNodeLiteral) rhs).getLiteral() == lower || ((MGBoundNodeLiteral) rhs).getLiteral() == upper) {
                    isSafe = false;
                }
            } else {
                verifyOFSingleBound(rhs, lower, upper);
            }

        } else if (bound instanceof MGBoundNodeFail) {
            isSafe = false;
        } else {
            assert false : "There is something wrong";
        }
    }

    @TruffleBoundary
    public void boundCheck(boolean justVerify) {
        for (MGNode aa : env.getArrayaccesses()) {
            final MGArray arrayValue = (MGArray) ((MGNodeOperand) aa).getValue();
            if (arrayValue.getValue() instanceof ParallelWorkload) {
                for (MGBoundNode i : arrayValue.getBounds()) {
                    i.setRequireBoundCheck(false);
                }
                continue;
            }
            currentLoopInfo = arrayValue.getRelatedLoopInfos();
            final int dims = arrayValue.getArrayInfo().getDim();
            final MGBoundNode[] indices = arrayValue.getBounds();
            for (int i = 0; i < dims; i++) {
                isSafe = true;
                final int dimSize = arrayValue.getArrayInfo().getSize(i);
                MGBoundNode idx = indices[i];
                verifySingleBound(visitor(idx), dimSize);
                if (!justVerify) {
                    indices[i].setRequireBoundCheck(!isSafe);
                } else {
                    final boolean current = indices[i].isRequireBoundCheck();
                    if (!current && !isSafe) {
                        throw BoundInvalidateException.INSTANCE.message("Bound check requires re-computation");
                    }
                }
            }
        }

        for (MGNode of : env.getOverflowCheck()) {
            isSafe = true;
            if (of.getExpectedType() == DataType.Int) {
                verifyOFSingleBound(visitor(of.getBound()), Integer.MIN_VALUE, Integer.MAX_VALUE);
            } else {
                verifyOFSingleBound(visitor(of.getBound()), Long.MIN_VALUE, Long.MAX_VALUE);
            }
            if (!justVerify) {
                of.getBound().setRequireBoundCheck(!isSafe);
            } else {
                final boolean current = of.getBound().isRequireBoundCheck();
                if (!current && !isSafe) {
                    throw BoundInvalidateException.INSTANCE.message("Bound check requires re-computation");
                }
            }
        }
        currentLoopInfo = null;
    }

    private LoopInfo backwordLookup(String name) {
        for (int i = env.getExistingLoopInfos().size() - 1; i >= 0; i--) {
            if (env.getExistingLoopInfos().get(i).getInductionVariable().getName().contentEquals(name))
                return env.getExistingLoopInfos().get(i);
        }

        return null;
    }

    @TruffleBoundary
    private MGBoundNode visitor(MGBoundNode root) {
        try {
            return root.accept(this);
        } catch (Exception e) {
            if (MGOptions.Debug > 0)
                e.printStackTrace();
        }
        assert false : "There is something uncovered!";
        return null;

    }

    @TruffleBoundary
    private long[] scanArrayValues(long[] array, int size) {
        if (arrayValuesBound.containsKey(array.hashCode()))
            return arrayValuesBound.get(array.hashCode());

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        final long isUnique;
        if (MGOptions.ScanArrayUniqueness) {
            // too slow.. think about GPU sorting.
            HashSet<Long> values = new HashSet<>();
            for (int i = 0; i < size; i++) {
                final long v = array[i];
                min = min < v ? min : v;
                max = max > v ? max : v;
                values.add(v);
            }
            isUnique = values.size() == size ? 1 : 0;
            values.clear();
        } else {
            for (int i = 0; i < size; i++) {
                final long v = array[i];
                min = min < v ? min : v;
                max = max > v ? max : v;
            }
            isUnique = -1;
        }
        final long[] bounds = new long[]{min, max, isUnique};
        arrayValuesBound.put(array.hashCode(), bounds);
        return bounds;
    }

    @TruffleBoundary
    private long[] scanArrayValues(int[] array, int size) {
        if (arrayValuesBound.containsKey(array.hashCode()))
            return arrayValuesBound.get(array.hashCode());

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        final long isUnique;
        if (MGOptions.ScanArrayUniqueness) {
            // too slow.. think about GPU sorting.
            HashSet<Integer> values = new HashSet<>();
            for (int i = 0; i < size; i++) {
                final int v = array[i];
                min = min < v ? min : v;
                max = max > v ? max : v;
                values.add(v);
            }
            isUnique = values.size() == size ? 1 : 0;
            values.clear();
        } else {
            for (int i = 0; i < size; i++) {
                final int v = array[i];
                min = min < v ? min : v;
                max = max > v ? max : v;
            }
            isUnique = -1;
        }
        final long[] bounds = new long[]{min, max, isUnique};
        arrayValuesBound.put(array.hashCode(), bounds);
        return bounds;
    }

    @TruffleBoundary
    private long[] processArrayValuesBound(MGArray arrayValue) {
        boolean shouldScan = MGOptions.scanArrayMinMax;
        shouldScan = shouldScan && ((MGArray) arrayValue.getOrigin()).getArrayInfo().getDim() == 1;
        shouldScan = shouldScan && arrayValue.getOrigin().getValue() instanceof Unboxer;
        shouldScan = shouldScan && arrayValue.getOrigin().isReadOnly();
        shouldScan = shouldScan && (arrayValue.getOrigin().getDataType() == DataType.LongArray || arrayValue.getOrigin().getDataType() == DataType.IntArray);

        if (shouldScan) {
            if (arrayValue.getOrigin().getDataType() == DataType.LongArray) {
                return scanArrayValues((long[]) ((Unboxer) arrayValue.getOrigin().getValue()).getValue(), arrayValue.getArrayInfo().getSize(0));
            } else if (arrayValue.getOrigin().getDataType() == DataType.IntArray) {
                return scanArrayValues((int[]) ((Unboxer) arrayValue.getOrigin().getValue()).getValue(), arrayValue.getArrayInfo().getSize(0));

            }
        }
        isSafe = true;
        return null;
    }

    @TruffleBoundary
    private MGBoundNode accessArray(MGArray arrayValue) {
        final long[] bound = processArrayValuesBound(arrayValue);
        DataType t = arrayValue.getDataType() == DataType.IntArray ? DataType.Int : DataType.Long;
        int dims = arrayValue.getArrayInfo().getDim();
        final MGBoundNode[] bounds = new MGBoundNode[dims];
        MGBoundNode[] indices = arrayValue.getBounds();
        boolean previousReqBC = isSafe;
        for (int i = 0; i < dims; i++) {
            isSafe = true;
            bounds[i] = visitor(indices[i]);
            final int dimSize = arrayValue.getArrayInfo().getSize(i);
            verifySingleBound(bounds[i], dimSize);
        }
        isSafe = previousReqBC;
        final MGBoundNode array;
        if (bound != null) {
            array = new MGBoundNodeLimit(new MGBoundNodeLiteral(bound[0], t), new MGBoundNodeLiteral(bound[1], t), t);
        } else {
            array = new MGBoundNodeFail();
        }
        return array;
    }

    private static long getMin(MGBoundNode min) {
        if (min instanceof MGBoundNodeLiteral)
            return ((MGBoundNodeLiteral) min).getLiteral();

        if (min instanceof MGBoundNodeLimit)
            return getMin(((MGBoundNodeLimit) min).getMin());

        if (min instanceof MGBoundNodeEither) {
            long then = getMin(((MGBoundNodeEither) min).getThen());
            long orelse = getMin(((MGBoundNodeEither) min).getOrelse());
            return (then < orelse) ? then : orelse;
        }

        assert false : "Unknown bound!";
        return -1;
    }

    private static long getMax(MGBoundNode max) {
        if (max instanceof MGBoundNodeLiteral)
            return ((MGBoundNodeLiteral) max).getLiteral();

        if (max instanceof MGBoundNodeLimit)
            return getMax(((MGBoundNodeLimit) max).getMax());

        if (max instanceof MGBoundNodeEither) {
            long then = getMax(((MGBoundNodeEither) max).getThen());
            long orelse = getMax(((MGBoundNodeEither) max).getOrelse());
            return (then > orelse) ? then : orelse;
        }

        assert false : "Unknown bound!";
        return -1;
    }

    private static boolean bothLiteral(MGBoundNode left, MGBoundNode right) {
        return left instanceof MGBoundNodeLiteral && right instanceof MGBoundNodeLiteral;
    }

    private static boolean EitherFail(MGBoundNode left, MGBoundNode right) {
        return left instanceof MGBoundNodeFail || right instanceof MGBoundNodeFail;
    }

    private static boolean EitherLiteral(MGBoundNode left, MGBoundNode right) {
        final boolean fail = EitherFail(left, right);
        return !fail && (left instanceof MGBoundNodeLiteral || right instanceof MGBoundNodeLiteral);
    }

    public MGBoundNode visitArray(MGBoundNodeArray node) {
        MGArray var = node.getArray();

        if (var != null && var.getIndicesLen() > 0) {
            return accessArray(var);
        }
        return node;
    }

    @TruffleBoundary
    private long calculateBinOp(long v1, BinOpType op, long v2) {
        switch (op) {
            case ADD:
                return v1 + v2;
            case SUB:
                return v1 - v2;
            case MUL:
                return v1 * v2;
            case DIV:
                if (v2 == 0) {
                    isSafe = true;
                    return v1;
                }
                return v1 / v2;
            case MOD:
                if (v2 == 0) {
                    isSafe = true;
                    return v1;
                }
                return v1 % v2;
            case POW:
                return (long) Math.pow(v1, v2);
        }
        return 0;
    }

    public MGBoundNode visitBinOp(MGBoundNodeBinOp node) {
        BinOpType op = node.getBinOpType();
        final MGBoundNode left = visitor(node.getLeft());
        final MGBoundNode right = visitor(node.getRight());
        if (bothLiteral(left, right)) {
            return new MGBoundNodeLiteral(calculateBinOp(((MGBoundNodeLiteral) left).getLiteral(), op, ((MGBoundNodeLiteral) right).getLiteral()), node.getType());
        } else {
            final MGBoundNode b1;
            final MGBoundNode b2;
            if (EitherLiteral(left, right)) {
                if (left instanceof MGBoundNodeLiteral) {
                    final long v = ((MGBoundNodeLiteral) left).getLiteral();
                    final MGBoundNodeLimit argLimit = (MGBoundNodeLimit) right;
                    b1 = new MGBoundNodeLiteral(calculateBinOp(v, op, getMin(visitor((argLimit).getMin()))), node.getType());
                    b2 = new MGBoundNodeLiteral(calculateBinOp(v, op, getMax(visitor((argLimit).getMax()))), node.getType());
                } else {
                    final long v = ((MGBoundNodeLiteral) right).getLiteral();
                    final MGBoundNodeLimit argLimit = (MGBoundNodeLimit) left;
                    b1 = new MGBoundNodeLiteral(calculateBinOp(getMin(visitor((argLimit).getMin())), op, v), node.getType());
                    b2 = new MGBoundNodeLiteral(calculateBinOp(getMax(visitor((argLimit).getMax())), op, v), node.getType());
                }
                return new MGBoundNodeLimit(b1, b2, node.getType());
            } else if ((left instanceof MGBoundNodeLimit) && (right instanceof MGBoundNodeLimit)) {
                final MGBoundNodeLimit arg1Limit = (MGBoundNodeLimit) left;
                final MGBoundNodeLimit arg2Limit = (MGBoundNodeLimit) right;
                final long arg1min = getMin(visitor((arg1Limit).getMin()));
                final long arg1max = getMax(visitor((arg1Limit).getMax()));
                final long arg2min = getMin(visitor((arg2Limit).getMin()));
                final long arg2max = getMax(visitor((arg2Limit).getMax()));
                b1 = new MGBoundNodeLiteral(calculateBinOp(arg1min, op, arg2min), node.getType());
                b2 = new MGBoundNodeLiteral(calculateBinOp(arg1max, op, arg2max), node.getType());
                return new MGBoundNodeLimit(b1, b2, node.getType());
            }
        }

        return new MGBoundNodeFail();

    }

    @TruffleBoundary
    private static long calculateBuiltin(BuiltinFunctionType op, long v1, long v2) {
        switch (op) {
            case MIN:
                return Math.min(v1, v2);
            case MAX:
                return Math.max(v1, v2);
        }
        return 0;
    }

    public MGBoundNode visitBuiltinFunction(MGBoundNodeBuiltinFunction node) {
        BuiltinFunctionType op = node.getBuiltinType();
        if (op == BuiltinFunctionType.MAX || op == BuiltinFunctionType.MIN) {
            final MGBoundNode arg1 = visitor(node.getArg1());
            final MGBoundNode arg2 = visitor(node.getArg2());
            if (bothLiteral(arg1, arg2)) {
                return new MGBoundNodeLiteral(calculateBuiltin(op, ((MGBoundNodeLiteral) arg1).getLiteral(), ((MGBoundNodeLiteral) arg2).getLiteral()), node.getType());
            } else {
                final MGBoundNode b1;
                final MGBoundNode b2;
                if (EitherLiteral(arg1, arg2)) {
                    final long v = (arg1 instanceof MGBoundNodeLiteral) ? ((MGBoundNodeLiteral) arg1).getLiteral() : ((MGBoundNodeLiteral) arg2).getLiteral();
                    final MGBoundNode arg = (arg1 instanceof MGBoundNodeLiteral) ? arg2 : arg1;

                    final MGBoundNodeLimit argLimit = (MGBoundNodeLimit) arg;
                    b1 = new MGBoundNodeLiteral(calculateBuiltin(op, v, getMin(visitor((argLimit).getMin()))), node.getType());
                    b2 = new MGBoundNodeLiteral(calculateBuiltin(op, v, getMax(visitor((argLimit).getMax()))), node.getType());
                    return new MGBoundNodeLimit(b1, b2, node.getType());
                } else if ((arg1 instanceof MGBoundNodeLimit) && (arg2 instanceof MGBoundNodeLimit)) {
                    final MGBoundNodeLimit arg1Limit = (MGBoundNodeLimit) arg1;
                    final MGBoundNodeLimit arg2Limit = (MGBoundNodeLimit) arg2;
                    final long arg1min = getMin(visitor((arg1Limit).getMin()));
                    final long arg1max = getMax(visitor((arg1Limit).getMax()));
                    final long arg2min = getMin(visitor((arg2Limit).getMin()));
                    final long arg2max = getMax(visitor((arg2Limit).getMax()));
                    b1 = new MGBoundNodeLiteral(calculateBuiltin(op, arg1min, arg2min), node.getType());
                    b2 = new MGBoundNodeLiteral(calculateBuiltin(op, arg1max, arg2max), node.getType());
                    return new MGBoundNodeLimit(b1, b2, node.getType());
                }
            }

            return new MGBoundNodeFail();
        }
        return node;
    }

    public MGBoundNode visitEither(MGBoundNodeEither arg) {
        final MGBoundNode then = visitor(arg.getThen());
        final MGBoundNode orelse = visitor(arg.getOrelse());
        final MGBoundNode min;
        final MGBoundNode max;
        if (EitherFail(then, orelse))
            return new MGBoundNodeFail();
        if (bothLiteral(then, orelse)) {
            if (((MGBoundNodeLiteral) then).getLiteral() > ((MGBoundNodeLiteral) orelse).getLiteral()) {
                min = orelse;
                max = then;
            } else {
                max = orelse;
                min = then;
            }
        } else if (EitherLiteral(then, orelse)) {
            final MGBoundNodeLimit limit;
            final MGBoundNodeLiteral literal;

            if (then instanceof MGBoundNodeLiteral) {
                literal = (MGBoundNodeLiteral) then;
                limit = (MGBoundNodeLimit) orelse;
            } else {
                limit = (MGBoundNodeLimit) then;
                literal = (MGBoundNodeLiteral) orelse;
            }
            if (literal.getLiteral() < ((MGBoundNodeLiteral) limit.getMin()).getLiteral()) {
                min = literal;
            } else {
                min = limit.getMin();
            }

            if (literal.getLiteral() > ((MGBoundNodeLiteral) limit.getMax()).getLiteral()) {
                max = literal;
            } else {
                max = limit.getMax();
            }

        } else {
            if (((MGBoundNodeLiteral) ((MGBoundNodeLimit) then).getMin()).getLiteral() < ((MGBoundNodeLiteral) ((MGBoundNodeLimit) orelse).getMin()).getLiteral()) {
                min = ((MGBoundNodeLimit) then).getMin();
            } else {
                min = ((MGBoundNodeLimit) orelse).getMin();
            }

            if (((MGBoundNodeLiteral) ((MGBoundNodeLimit) then).getMax()).getLiteral() > ((MGBoundNodeLiteral) ((MGBoundNodeLimit) orelse).getMax()).getLiteral()) {
                max = ((MGBoundNodeLimit) then).getMax();
            } else {
                max = ((MGBoundNodeLimit) orelse).getMax();
            }

        }
        return new MGBoundNodeLimit(min, max, arg.getType());
    }

    public MGBoundNode visitLimit(MGBoundNodeLimit arg) {
        final MGBoundNode minLimit = visitor(arg.getMin());
        final MGBoundNode maxLimit = visitor(arg.getMax());
        final MGBoundNode min;
        final MGBoundNode max;
        if (bothLiteral(minLimit, maxLimit)) {
            return arg;
        } else if (EitherLiteral(minLimit, maxLimit)) {
            final MGBoundNodeLimit limit;
            final MGBoundNodeLiteral literal;

            if (minLimit instanceof MGBoundNodeLiteral) {
                literal = (MGBoundNodeLiteral) minLimit;
                limit = (MGBoundNodeLimit) maxLimit;
            } else {
                limit = (MGBoundNodeLimit) minLimit;
                literal = (MGBoundNodeLiteral) maxLimit;
            }
            if (literal.getLiteral() < ((MGBoundNodeLiteral) limit.getMin()).getLiteral()) {
                min = literal;
            } else {
                min = limit.getMin();
            }

            if (literal.getLiteral() > ((MGBoundNodeLiteral) limit.getMax()).getLiteral()) {
                max = literal;
            } else {
                max = limit.getMax();
            }

        } else {
            if (((MGBoundNodeLiteral) ((MGBoundNodeLimit) minLimit).getMin()).getLiteral() < ((MGBoundNodeLiteral) ((MGBoundNodeLimit) maxLimit).getMin()).getLiteral()) {
                min = ((MGBoundNodeLimit) minLimit).getMin();
            } else {
                min = ((MGBoundNodeLimit) maxLimit).getMin();
            }

            if (((MGBoundNodeLiteral) ((MGBoundNodeLimit) min).getMax()).getLiteral() > ((MGBoundNodeLiteral) ((MGBoundNodeLimit) maxLimit).getMax()).getLiteral()) {
                max = ((MGBoundNodeLimit) min).getMax();
            } else {
                max = ((MGBoundNodeLimit) maxLimit).getMax();
            }

        }
        return new MGBoundNodeLimit(min, max, arg.getType());
    }

    public MGBoundNode visitLiteral(MGBoundNodeLiteral node) {
        return node;
    }

    @TruffleBoundary
    private static long calculateMathFunction(MathFunctionType op, long v1) {
        switch (op) {
            case abs:
            case hypot:
                return Math.abs(v1);
            case sqrt:
                return (long) Math.sqrt(v1);
            case exp:
                return (long) Math.exp(v1);
            case ceil:
                return (long) Math.ceil(v1);
            case acos:
                return (long) Math.acos(v1);
            case cos:
                return (long) Math.cos(v1);
            case sin:
                return (long) Math.sin(v1);
            case log:
                return (long) Math.log(v1);
            case round:
                return Math.round(v1);
        }
        return 0;
    }

    public MGBoundNode visitMathFunction(MGBoundNodeMathFunction node) {
        MathFunctionType op = node.getMathType();
        final MGBoundNode arg = visitor(node.getArg());
        if (arg instanceof MGBoundNodeLiteral) {
            return new MGBoundNodeLiteral(calculateMathFunction(op, ((MGBoundNodeLiteral) arg).getLiteral()), node.getType());
        } else {
            final MGBoundNode b1;
            final MGBoundNode b2;
            if (arg instanceof MGBoundNodeLimit) {
                final MGBoundNodeLimit argLimit = (MGBoundNodeLimit) arg;
                b1 = new MGBoundNodeLiteral(calculateMathFunction(op, getMin(visitor((argLimit).getMin()))), node.getType());
                b2 = new MGBoundNodeLiteral(calculateMathFunction(op, getMax(visitor((argLimit).getMax()))), node.getType());
                return new MGBoundNodeLimit(b1, b2, node.getType());
            } else if (arg instanceof MGBoundNodeEither) {
                final MGBoundNodeEither argEither = (MGBoundNodeEither) arg;
                b1 = new MGBoundNodeLiteral(calculateMathFunction(op, ((MGBoundNodeLiteral) visitor(argEither.getThen())).getLiteral()), node.getType());
                b2 = new MGBoundNodeLiteral(calculateMathFunction(op, ((MGBoundNodeLiteral) visitor(argEither.getOrelse())).getLiteral()), node.getType());
                return new MGBoundNodeEither(b1, b2, node.getType());
            }
        }

        return new MGBoundNodeFail();
    }

    public MGBoundNode visitRange(MGBoundNodeRange node) {
        return new MGBoundNodeRange(visitor(node.getStart()), visitor(node.getStop()), visitor(node.getStep()));
    }

    private Long getConstantVal(String name) {
        if (env.getConstantIntVars().containsKey(name)) {
            return env.getConstantIntVars().get(name);
        }
        if (!env.isGlobalEnv() && env.getGlobalEnv().getConstantIntVars().containsKey(name)) {
            return env.getGlobalEnv().getConstantIntVars().get(name);
        }
        return null;
    }

    public MGBoundNode visitVariable(MGBoundNodeVariable node) {
        String varname = node.getVariable();

        // Global Variable
        final Long val = getConstantVal(varname);
        if (val != null) {
            return new MGBoundNodeLiteral(val, node.getType());
        }

        // Induction Variable
        final LoopInfo info = currentLoopInfo != null && currentLoopInfo.containsKey(varname) ? currentLoopInfo.get(varname) : backwordLookup(varname);
        if (info != null) {
            final MGBoundNode start = visitor(info.getBounds().getStart());
            MGBoundNode stop = visitor(info.getBounds().getStop());
            if (!(start instanceof MGBoundNodeFail || stop instanceof MGBoundNodeFail)) {
                if (stop instanceof MGBoundNodeLiteral) {
                    stop = new MGBoundNodeLiteral(((MGBoundNodeLiteral) stop).getLiteral(), node.getType()).decrementLiteral();
                } else if (stop instanceof MGBoundNodeLimit) {
                    MGBoundNodeLimit stopLimit = (MGBoundNodeLimit) stop;
                    final MGBoundNode max = stopLimit.getMax() instanceof MGBoundNodeLiteral
                                    ? new MGBoundNodeLiteral(((MGBoundNodeLiteral) stopLimit.getMax()).getLiteral(), node.getType()).decrementLiteral() : stopLimit.getMax();
                    stop = new MGBoundNodeLimit(stopLimit.getMin(), max, node.getType());
                }
                return new MGBoundNodeLimit(start, stop, node.getType());
            }
        }

        return new MGBoundNodeFail();
    }

    public MGBoundNode visitFunctionCall(MGBoundNodeFunctionCall node) {
        return new MGBoundNodeLiteral(0, node.getType());
    }

    public MGBoundNode visitFail(MGBoundNodeFail node) {
        isSafe = false;
        return node;
    }

    @TruffleBoundary
    public void reset() {
        this.violated = false;
        this.currentLoopInfo = null;
        this.isSafe = true;
        this.arrayValuesBound.clear();
    }

}
