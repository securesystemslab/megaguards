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
package edu.uci.megaguards.backend.truffle.node;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.MGObjectTracker;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGStorage;

public abstract class MGTOperand<T> extends MGTNode<T> {

    protected final FrameSlot frameSlot;
    protected final boolean trackChanges;

    public MGTOperand(DataType t, FrameSlot frameSlot) {
        super(t);
        this.frameSlot = frameSlot;
        this.trackChanges = MGOptions.Backend.target != ExecutionMode.Truffle;
    }

    public abstract void executeWrite(VirtualFrame frame, T v);

    public abstract String getName();

    public static final class DimSize extends MGTOperand<Integer> {

        private final MGArray array;
        private final int dim;

        public DimSize(MGArray array, int dim) {
            super(DataType.Int, null);
            this.array = array;
            this.dim = dim;
        }

        @Override
        public void executeWrite(VirtualFrame frame, Integer v) {
            throw new IllegalStateException();
        }

        @Override
        public String getName() {
            return String.format("%s[%d]", array.getName(), array.getArrayInfo().getSize(dim));
        }

        @Override
        public Integer execute(VirtualFrame frame) {
            return array.getArrayInfo().getSize(dim);
        }
    }

    public static abstract class VarOperand<T> extends MGTOperand<T> {

        protected VarOperand(DataType t, FrameSlot frameSlot) {
            super(t, frameSlot);
        }

        @Override
        public String getName() {
            return (String) frameSlot.getIdentifier();
        }

        public static final class IntOperand extends VarOperand<Integer> {

            public IntOperand(FrameSlot frameSlot) {
                super(DataType.Int, frameSlot);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                try {
                    return frame.getInt(frameSlot);
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Integer v) {
                frame.setInt(frameSlot, v);
            }

        }

        public static final class LongOperand extends VarOperand<Long> {

            public LongOperand(FrameSlot frameSlot) {
                super(DataType.Long, frameSlot);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                try {
                    return frame.getLong(frameSlot);
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Long v) {
                frame.setLong(frameSlot, v);
            }

        }

        public static final class DoubleOperand extends VarOperand<Double> {

            public DoubleOperand(FrameSlot frameSlot) {
                super(DataType.Double, frameSlot);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                try {
                    return frame.getDouble(frameSlot);
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Double v) {
                frame.setDouble(frameSlot, v);
            }

        }

        public static final class BooleanOperand extends VarOperand<Boolean> {

            public BooleanOperand(FrameSlot frameSlot) {
                super(DataType.Bool, frameSlot);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                try {
                    return frame.getBoolean(frameSlot);
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Boolean v) {
                frame.setBoolean(frameSlot, v);
            }

        }

        public static final class ObjectOperand extends VarOperand<Object> {

            public ObjectOperand(FrameSlot frameSlot, DataType type) {
                super(type, frameSlot);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    return frame.getObject(frameSlot);
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Object v) {
                frame.setObject(frameSlot, v);
            }

        }

    }

    public static abstract class Var1DOperand<T> extends MGTOperand<T> {

        protected final MGObjectTracker changesTracker;
        protected int currentHashCode;
        protected int changedIndex;
        protected final MGArray object;
        @Child protected MGTNode<Integer> index1;

        protected Var1DOperand(DataType t, FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1) {
            super(t, frameSlot);
            this.index1 = index1;
            this.object = object;
            this.changesTracker = changesTracker;
            this.currentHashCode = -1;
            this.changedIndex = -1;
        }

        @Override
        public String getName() {
            return object.getName();
        }

        protected void updatedData(int hashCode) {
            if (currentHashCode != hashCode) {
                currentHashCode = hashCode;
                changedIndex = changesTracker.getIndex(hashCode);
            }
            changesTracker.updateDataForTruffle(changedIndex);
        }

        protected void notifyChanges() {
            changesTracker.setTruffleChanged(changedIndex);
        }

        public static final class Int1DOperand extends Var1DOperand<Integer> {

            public Int1DOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1) {
                super(DataType.Int, frameSlot, changesTracker, object, index1);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                final int idx = index1.execute(frame);
                try {
                    final int[] array = ((int[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }

            }

            @Override
            public void executeWrite(VirtualFrame frame, Integer v) {
                final int[] array;
                try {
                    array = ((int[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx = index1.execute(frame);
                array[idx] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

        public static final class Long1DOperand extends Var1DOperand<Long> {

            public Long1DOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1) {
                super(DataType.Long, frameSlot, changesTracker, object, index1);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                final int idx = index1.execute(frame);
                try {
                    final long[] array = ((long[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Long v) {
                final long[] array;
                try {
                    array = ((long[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx = index1.execute(frame);
                array[idx] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

        public static final class Double1DOperand extends Var1DOperand<Double> {

            public Double1DOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1) {
                super(DataType.Double, frameSlot, changesTracker, object, index1);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                final int idx = index1.execute(frame);
                try {
                    final double[] array = ((double[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Double v) {
                final double[] array;
                try {
                    array = ((double[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx = index1.execute(frame);
                array[idx] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

        public static final class Boolean1DOperand extends Var1DOperand<Boolean> {

            public Boolean1DOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1) {
                super(DataType.Bool, frameSlot, changesTracker, object, index1);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                final int idx = index1.execute(frame);
                try {
                    final boolean[] array = ((boolean[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Boolean v) {
                final boolean[] array;
                try {
                    array = ((boolean[]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx = index1.execute(frame);
                array[idx] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

    }

    public static abstract class VarNDOperand<T> extends Var1DOperand<T> {

        @Child protected MGTNode<Integer> index2;

        protected VarNDOperand(DataType t, FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1, MGTNode<Integer> index2) {
            super(t, frameSlot, changesTracker, object, index1);
            this.index2 = index2;
        }

        public static final class IntNDOperand extends VarNDOperand<Integer> {

            public IntNDOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1, MGTNode<Integer> index2) {
                super(DataType.Int, frameSlot, changesTracker, object, index1, index2);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                try {
                    final int[][] array = ((int[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx1][idx2];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Integer v) {
                final int[][] array;
                try {
                    array = ((int[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                array[idx1][idx2] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

        public static final class LongNDOperand extends VarNDOperand<Long> {

            public LongNDOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1, MGTNode<Integer> index2) {
                super(DataType.Long, frameSlot, changesTracker, object, index1, index2);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                try {
                    final long[][] array = ((long[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx1][idx2];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Long v) {
                final long[][] array;
                try {
                    array = ((long[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                array[idx1][idx2] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

        public static final class DoubleNDOperand extends VarNDOperand<Double> {

            public DoubleNDOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1, MGTNode<Integer> index2) {
                super(DataType.Double, frameSlot, changesTracker, object, index1, index2);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                try {
                    final double[][] array = ((double[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx1][idx2];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Double v) {
                final double[][] array;
                try {
                    array = ((double[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                array[idx1][idx2] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

        public static final class BooleanNDOperand extends VarNDOperand<Boolean> {

            public BooleanNDOperand(FrameSlot frameSlot, MGObjectTracker changesTracker, MGArray object, MGTNode<Integer> index1, MGTNode<Integer> index2) {
                super(DataType.Bool, frameSlot, changesTracker, object, index1, index2);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                try {
                    final boolean[][] array = ((boolean[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                    return array[idx1][idx2];
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void executeWrite(VirtualFrame frame, Boolean v) {
                final boolean[][] array;
                try {
                    array = ((boolean[][]) frame.getObject(frameSlot));
                    if (trackChanges) {
                        updatedData(array.hashCode());
                    }
                } catch (FrameSlotTypeException e) {
                    throw new IllegalStateException();
                }
                final int idx1 = index1.execute(frame);
                final int idx2 = index2.execute(frame);
                array[idx1][idx2] = v;
                if (trackChanges) {
                    notifyChanges();
                }
            }

        }

    }

    public static final class IfElseOperand<T> extends MGTOperand<T> {

        @CompilationFinal @Child private MGTNode<T> then;
        @CompilationFinal @Child private MGTNode<Boolean> cond;
        @CompilationFinal @Child private MGTNode<T> orelse;

        public IfElseOperand(MGTNode<T> t, MGTNode<Boolean> c, MGTNode<T> e, DataType ty) {
            super(ty, null);
            this.then = t;
            this.cond = c;
            this.orelse = e;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public T execute(VirtualFrame frame) {
            if (cond.execute(frame))
                return then.execute(frame);
            else
                return orelse.execute(frame);
        }

        @Override
        public void executeWrite(VirtualFrame frame, T v) {
            throw new IllegalStateException();
        }
    }

    public static final class ConstOperand<T> extends MGTOperand<T> {
        private final T value;

        public ConstOperand(T v, DataType t) {
            super(t, null);
            this.value = v;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public T execute(VirtualFrame frame) {
            return value;
        }

        @Override
        public void executeWrite(VirtualFrame frame, T v) {
            throw new IllegalStateException();
        }

    }

    public static final class ArgOperand<T> extends MGTOperand<T> {
        private final int idx;

        public ArgOperand(int index, DataType t) {
            super(t, null);
            this.idx = index;
        }

        @Override
        public String getName() {
            return null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T execute(VirtualFrame frame) {
            return (T) frame.getArguments()[idx];
        }

        @Override
        public void executeWrite(VirtualFrame frame, T v) {
            throw new IllegalStateException();
        }

    }

    public static final class MGStorageToValue extends MGTOperand<Object> {
        private final MGStorage value;

        public MGStorageToValue(MGStorage v) {
            super(v.getDataType(), null);
            this.value = v;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value.getValue();
        }

        @Override
        public void executeWrite(VirtualFrame frame, Object v) {
            throw new IllegalStateException();
        }

    }

    public static final class MGArrayToValue extends MGTOperand<Object> {
        private final MGArray value;

        public MGArrayToValue(MGArray v) {
            super(v.getDataType(), null);
            this.value = v;
        }

        @Override
        public String getName() {
            return value.getName();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return value.getBoxed().getValue();
        }

        @Override
        public void executeWrite(VirtualFrame frame, Object v) {
            throw new IllegalStateException();
        }

    }

    public static final class MGReduceResult extends MGTOperand<Object> {
        private final Object[] result;
        private final int index;
        private final String name;

        public MGReduceResult(Object[] r, int idx, String name, DataType t) {
            super(t, null);
            this.index = idx;
            this.result = r;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return result[index];
        }

        @Override
        public void executeWrite(VirtualFrame frame, Object v) {
            throw new IllegalStateException();
        }

    }

}
