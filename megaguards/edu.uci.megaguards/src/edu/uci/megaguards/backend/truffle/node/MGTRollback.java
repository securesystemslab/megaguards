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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.MGObjectTracker;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLData;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLDevice;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;

public abstract class MGTRollback extends MGTControl {

    @Child protected MGTOperand<Object> src;
    @Child protected MGTOperand<Object> dest;

    public MGTRollback(MGTOperand<Object> src, MGTOperand<Object> dest, DataType t) {
        super(t);
        this.src = src;
        this.dest = dest;
    }

    protected static void copyArray(Object src, Object dest, int length) {
        System.arraycopy(src, 0, dest, 0, length);
    }

    public abstract static class Backup extends MGTRollback {

        public Backup(MGTOperand<Object> src, MGTOperand<Object> dest, DataType t) {
            super(src, dest, t);
        }

        public static final class OpenCLBackup extends Backup {

            private final OpenCLDevice device;
            private final MGArray array;

            public OpenCLBackup(OpenCLDevice device, MGArray array) {
                super(null, null, array.getDataType());
                this.device = device;
                this.array = array;
            }

            @TruffleBoundary
            private void processOpenCL() {
                OpenCLData d = OpenCLData.initData(device, array);
                Object value = array.getBoxed().getValue();
                d.getOnDeviceData(device).put(value, MGOptions.Backend.disableDataManagementOptimization);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                processOpenCL();
                return null;
            }

        }

        public static final class Array1DInt extends Backup {

            public Array1DInt(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.IntArray);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final int[] array = (int[]) src.execute(frame);
                final int[] backup = new int[array.length];
                copyArray(array, backup, array.length);
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class ArrayNDInt extends Backup {

            public ArrayNDInt(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.IntArray);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final int[][] array = (int[][]) src.execute(frame);
                final int[][] backup = new int[array.length][];
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    backup[i] = new int[l];
                    copyArray(array[i], backup[i], l);
                }
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class Array1DLong extends Backup {

            public Array1DLong(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.LongArray);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final long[] array = (long[]) src.execute(frame);
                final long[] backup = new long[array.length];
                copyArray(array, backup, array.length);
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class ArrayNDLong extends Backup {

            public ArrayNDLong(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.LongArray);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final long[][] array = (long[][]) src.execute(frame);
                final long[][] backup = new long[array.length][];
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    backup[i] = new long[l];
                    copyArray(array[i], backup[i], l);
                }
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class Array1DDouble extends Backup {

            public Array1DDouble(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.DoubleArray);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final double[] array = (double[]) src.execute(frame);
                final double[] backup = new double[array.length];
                copyArray(array, backup, array.length);
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class ArrayNDDouble extends Backup {

            public ArrayNDDouble(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.DoubleArray);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final double[][] array = (double[][]) src.execute(frame);
                final double[][] backup = new double[array.length][];
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    backup[i] = new double[l];
                    copyArray(array[i], backup[i], l);
                }
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class Array1DBoolean extends Backup {

            public Array1DBoolean(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.BoolArray);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final boolean[] array = (boolean[]) src.execute(frame);
                final boolean[] backup = new boolean[array.length];
                copyArray(array, backup, array.length);
                dest.executeWrite(frame, backup);
                return null;
            }
        }

        public static final class ArrayNDBoolean extends Backup {

            public ArrayNDBoolean(MGTOperand<Object> src, MGTOperand<Object> dest) {
                super(src, dest, DataType.BoolArray);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final boolean[][] array = (boolean[][]) src.execute(frame);
                final boolean[][] backup = new boolean[array.length][];
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    backup[i] = new boolean[l];
                    copyArray(array[i], backup[i], l);
                }
                dest.executeWrite(frame, backup);
                return null;
            }
        }

    }

    public abstract static class Restore extends MGTRollback {

        protected final MGObjectTracker changesTracker;
        protected final boolean trackChanges;

        public Restore(MGTOperand<Object> src, MGTOperand<Object> dest, DataType t, MGObjectTracker changesTracker) {
            super(src, dest, t);
            this.changesTracker = changesTracker;
            this.trackChanges = MGOptions.Backend.target != ExecutionMode.Truffle;
        }

        public static final class OpenCLRestore extends Restore {

            private final OpenCLDevice device;
            private final MGArray array;

            public OpenCLRestore(OpenCLDevice device, MGArray array, MGObjectTracker changesTracker) {
                super(null, null, array.getDataType(), changesTracker);
                this.device = device;
                this.array = array;
            }

            @TruffleBoundary
            private void processOpenCL() {
                OpenCLData d = OpenCLData.getData(array);
                d.getOnDeviceData(device).get();
            }

            @Override
            public Object execute(VirtualFrame frame) {
                processOpenCL();
                return null;
            }

        }

        public static final class Array1DInt extends Restore {

            public Array1DInt(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.IntArray, changesTracker);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final int[] backup = (int[]) src.execute(frame);
                final int[] array = (int[]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                copyArray(backup, array, array.length);
                return null;
            }
        }

        public static final class ArrayNDInt extends Restore {

            public ArrayNDInt(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.IntArray, changesTracker);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final int[][] backup = (int[][]) src.execute(frame);
                final int[][] array = (int[][]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    copyArray(backup[i], array[i], l);
                }
                return null;
            }
        }

        public static final class Array1DLong extends Restore {

            public Array1DLong(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.LongArray, changesTracker);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final long[] backup = (long[]) src.execute(frame);
                final long[] array = (long[]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                copyArray(backup, array, array.length);
                return null;
            }
        }

        public static final class ArrayNDLong extends Restore {

            public ArrayNDLong(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.LongArray, changesTracker);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final long[][] backup = (long[][]) src.execute(frame);
                final long[][] array = (long[][]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    copyArray(backup[i], array[i], l);
                }
                return null;
            }
        }

        public static final class Array1DDouble extends Restore {

            public Array1DDouble(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.DoubleArray, changesTracker);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final double[] backup = (double[]) src.execute(frame);
                final double[] array = (double[]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                copyArray(backup, array, array.length);
                return null;
            }
        }

        public static final class ArrayNDDouble extends Restore {

            public ArrayNDDouble(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.DoubleArray, changesTracker);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final double[][] backup = (double[][]) src.execute(frame);
                final double[][] array = (double[][]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    copyArray(backup[i], array[i], l);
                }
                return null;
            }
        }

        public static final class Array1DBoolean extends Restore {

            public Array1DBoolean(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.BoolArray, changesTracker);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                final boolean[] backup = (boolean[]) src.execute(frame);
                final boolean[] array = (boolean[]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                copyArray(backup, array, array.length);
                return null;
            }
        }

        public static final class ArrayNDBoolean extends Restore {

            public ArrayNDBoolean(MGTOperand<Object> src, MGTOperand<Object> dest, MGObjectTracker changesTracker) {
                super(src, dest, DataType.BoolArray, changesTracker);
            }

            @ExplodeLoop
            @Override
            public Object execute(VirtualFrame frame) {
                final boolean[][] backup = (boolean[][]) src.execute(frame);
                final boolean[][] array = (boolean[][]) dest.execute(frame);
                final int index = changesTracker.getIndex(array.hashCode());
                if (trackChanges && !changesTracker.shouldTruffleRestore(index))
                    return null;
                for (int i = 0; i < array.length; i++) {
                    final int l = array[i].length;
                    copyArray(backup[i], array[i], l);
                }
                return null;
            }
        }

    }

}
