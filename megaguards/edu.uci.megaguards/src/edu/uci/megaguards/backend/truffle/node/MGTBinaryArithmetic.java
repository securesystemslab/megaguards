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
import com.oracle.truffle.api.frame.VirtualFrame;

import edu.uci.megaguards.backend.truffle.exception.MGTBoundException;
import edu.uci.megaguards.backend.truffle.exception.MGTOverflowException;
import edu.uci.megaguards.object.DataType;

public abstract class MGTBinaryArithmetic<T> extends MGTNode<T> {

    public MGTBinaryArithmetic(DataType t) {
        super(t);
    }

    protected void overflow() {
        throw MGTOverflowException.INSTANCE;
    }

    public static abstract class MGTBinaryArithmeticInt extends MGTBinaryArithmetic<Integer> {
        @CompilationFinal @Child protected MGTNode<Integer> left;
        @CompilationFinal @Child protected MGTNode<Integer> right;

        protected MGTBinaryArithmeticInt(MGTNode<Integer> l, MGTNode<Integer> r) {
            super(DataType.Int);
            this.left = l;
            this.right = r;
        }

        public static final class BoundCheck extends MGTBinaryArithmeticInt {

            public BoundCheck(MGTNode<Integer> index, MGTNode<Integer> size) {
                super(index, size);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int index = left.execute(frame);
                int size = right.execute(frame);
                if (index >= size || index < 0) {
                    throw MGTBoundException.INSTANCE;
                }
                return index;
            }

        }

        public static final class AddNode extends MGTBinaryArithmeticInt {

            public AddNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l + r;
            }

        }

        public static final class AddOFNode extends MGTBinaryArithmeticInt {

            public AddOFNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                try {
                    return Math.addExact(l, r);
                } catch (ArithmeticException e) {
                    overflow();
                    return null;
                }
            }

        }

        public static final class MulNode extends MGTBinaryArithmeticInt {

            public MulNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l * r;
            }

        }

        public static final class MulOFNode extends MGTBinaryArithmeticInt {

            public MulOFNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                try {
                    return Math.multiplyExact(l, r);
                } catch (ArithmeticException e) {
                    overflow();
                    return null;
                }
            }

        }

        public static final class SubNode extends MGTBinaryArithmeticInt {

            public SubNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l - r;
            }

        }

        public static final class SubOFNode extends MGTBinaryArithmeticInt {

            public SubOFNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                try {
                    return Math.subtractExact(l, r);
                } catch (ArithmeticException e) {
                    overflow();
                    return null;
                }
            }

        }

        public static final class DivNode extends MGTBinaryArithmeticInt {

            public DivNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l / r;
            }

        }

        public static final class PowNode extends MGTBinaryArithmeticInt {

            public PowNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return (int) Math.pow(l, r);
            }

        }

        public static final class ModNode extends MGTBinaryArithmeticInt {

            public ModNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                int v = l % r;
                return v < 0 ? ((r < 0) ? v - r : v + r) : v;
            }

        }

        public static final class LeftShiftNode extends MGTBinaryArithmeticInt {

            public LeftShiftNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l << r;
            }

        }

        public static final class RightShiftNode extends MGTBinaryArithmeticInt {

            public RightShiftNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l >> r;
            }

        }

        public static final class BitAndNode extends MGTBinaryArithmeticInt {

            public BitAndNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l & r;
            }

        }

        public static final class BitOrNode extends MGTBinaryArithmeticInt {

            public BitOrNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l | r;
            }

        }

        public static final class BitXorNode extends MGTBinaryArithmeticInt {

            public BitXorNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l ^ r;
            }

        }

    }

    public static abstract class MGTBinaryArithmeticLong extends MGTBinaryArithmetic<Long> {
        @CompilationFinal @Child protected MGTNode<Long> left;
        @CompilationFinal @Child protected MGTNode<Long> right;

        protected MGTBinaryArithmeticLong(MGTNode<Long> l, MGTNode<Long> r) {
            super(DataType.Long);
            this.left = l;
            this.right = r;
        }

        public static final class AddNode extends MGTBinaryArithmeticLong {

            public AddNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l + r;
            }

        }

        public static final class AddOFNode extends MGTBinaryArithmeticLong {

            public AddOFNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                try {
                    return Math.addExact(l, r);
                } catch (ArithmeticException e) {
                    overflow();
                    return null;
                }
            }

        }

        public static final class MulNode extends MGTBinaryArithmeticLong {

            public MulNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l * r;
            }

        }

        public static final class MulOFNode extends MGTBinaryArithmeticLong {

            public MulOFNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                try {
                    return Math.multiplyExact(l, r);
                } catch (ArithmeticException e) {
                    overflow();
                    return null;
                }
            }

        }

        public static final class SubNode extends MGTBinaryArithmeticLong {

            public SubNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l - r;
            }

        }

        public static final class SubOFNode extends MGTBinaryArithmeticLong {

            public SubOFNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                try {
                    return Math.subtractExact(l, r);
                } catch (ArithmeticException e) {
                    overflow();
                    return null;
                }
            }

        }

        public static final class DivNode extends MGTBinaryArithmeticLong {

            public DivNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l / r;
            }

        }

        public static final class PowNode extends MGTBinaryArithmeticLong {

            public PowNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return (long) Math.pow(l, r);
            }

        }

        public static final class ModNode extends MGTBinaryArithmeticLong {

            public ModNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                long v = l % r;
                return v < 0 ? ((r < 0) ? v - r : v + r) : v;
            }

        }

        public static final class LeftShiftNode extends MGTBinaryArithmeticLong {

            public LeftShiftNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l << r;
            }

        }

        public static final class RightShiftNode extends MGTBinaryArithmeticLong {

            public RightShiftNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l >> r;
            }

        }

        public static final class BitAndNode extends MGTBinaryArithmeticLong {

            public BitAndNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l & r;
            }

        }

        public static final class BitOrNode extends MGTBinaryArithmeticLong {

            public BitOrNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l | r;
            }

        }

        public static final class BitXorNode extends MGTBinaryArithmeticLong {

            public BitXorNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Long execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l ^ r;
            }

        }

    }

    public static abstract class MGTBinaryArithmeticDouble extends MGTBinaryArithmetic<Double> {
        @CompilationFinal @Child protected MGTNode<Double> left;
        @CompilationFinal @Child protected MGTNode<Double> right;

        protected MGTBinaryArithmeticDouble(MGTNode<Double> l, MGTNode<Double> r) {
            super(DataType.Double);
            this.left = l;
            this.right = r;
        }

        public static final class AddNode extends MGTBinaryArithmeticDouble {

            public AddNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l + r;
            }

        }

        public static final class MulNode extends MGTBinaryArithmeticDouble {

            public MulNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l * r;
            }

        }

        public static final class SubNode extends MGTBinaryArithmeticDouble {

            public SubNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l - r;
            }

        }

        public static final class DivNode extends MGTBinaryArithmeticDouble {

            public DivNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l / r;
            }

        }

        public static final class PowNode extends MGTBinaryArithmeticDouble {

            public PowNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return Math.pow(l, r);
            }

        }

        public static final class ModNode extends MGTBinaryArithmeticDouble {

            public ModNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Double execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                double v = l % r;
                return v < 0 ? ((r < 0) ? v - r : v + r) : v;
            }

        }

    }
}
