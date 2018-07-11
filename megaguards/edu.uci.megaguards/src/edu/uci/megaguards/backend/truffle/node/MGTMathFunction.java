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

import edu.uci.megaguards.object.DataType;

public abstract class MGTMathFunction<T> extends MGTNode<T> {

    public MGTMathFunction(DataType t) {
        super(t);
    }

    @CompilationFinal @Child protected MGTNode<T> arg1;

    public static final class AbsInt extends MGTMathFunction<Integer> {

        public AbsInt(MGTNode<Integer> arg) {
            super(DataType.Int);
            this.arg1 = arg;
        }

        @Override
        public Integer execute(VirtualFrame frame) {
            return Math.abs(arg1.execute(frame));
        }

    }

    public static final class AbsLong extends MGTMathFunction<Long> {

        public AbsLong(MGTNode<Long> arg) {
            super(DataType.Long);
            this.arg1 = arg;
        }

        @Override
        public Long execute(VirtualFrame frame) {
            return Math.abs(arg1.execute(frame));
        }

    }

    public static final class AbsDouble extends MGTMathFunction<Double> {

        public AbsDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.abs(arg1.execute(frame));
        }

    }

    public static final class SqrtDouble extends MGTMathFunction<Double> {

        public SqrtDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.sqrt(arg1.execute(frame));
        }

    }

    public static final class ExpDouble extends MGTMathFunction<Double> {

        public ExpDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.exp(arg1.execute(frame));
        }

    }

    public static final class CeilDouble extends MGTMathFunction<Double> {

        public CeilDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.ceil(arg1.execute(frame));
        }

    }

    public static final class AcosDouble extends MGTMathFunction<Double> {

        public AcosDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.acos(arg1.execute(frame));
        }

    }

    public static final class CosDouble extends MGTMathFunction<Double> {

        public CosDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.cos(arg1.execute(frame));
        }

    }

    public static final class SinDouble extends MGTMathFunction<Double> {

        public SinDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.sin(arg1.execute(frame));
        }

    }

    public static final class LogDouble extends MGTMathFunction<Double> {

        public LogDouble(MGTNode<Double> arg) {
            super(DataType.Double);
            this.arg1 = arg;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return Math.log(arg1.execute(frame));
        }

    }

    public static final class RoundDouble extends MGTMathFunction<Long> {

        @Child protected MGTNode<Double> arg;

        public RoundDouble(MGTNode<Double> arg) {
            super(DataType.Long);
            this.arg = arg;
        }

        @Override
        public Long execute(VirtualFrame frame) {
            return Math.round(arg.execute(frame));
        }

    }

    public static abstract class TwoArgsMathFunction<T> extends MGTMathFunction<T> {

        public TwoArgsMathFunction(DataType t) {
            super(t);
        }

        @CompilationFinal @Child protected MGTNode<T> arg2;

        public static final class MinInt extends TwoArgsMathFunction<Integer> {
            public MinInt(MGTNode<Integer> arg1, MGTNode<Integer> arg2) {
                super(DataType.Int);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                return Math.min(arg1.execute(frame), arg2.execute(frame));
            }

        }

        public static final class MinLong extends TwoArgsMathFunction<Long> {
            public MinLong(MGTNode<Long> arg1, MGTNode<Long> arg2) {
                super(DataType.Long);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            @Override
            public Long execute(VirtualFrame frame) {
                return Math.min(arg1.execute(frame), arg2.execute(frame));
            }

        }

        public static final class MinDouble extends TwoArgsMathFunction<Double> {
            public MinDouble(MGTNode<Double> arg1, MGTNode<Double> arg2) {
                super(DataType.Double);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            @Override
            public Double execute(VirtualFrame frame) {
                return Math.min(arg1.execute(frame), arg2.execute(frame));
            }

        }

        public static final class MaxInt extends TwoArgsMathFunction<Integer> {
            public MaxInt(MGTNode<Integer> arg1, MGTNode<Integer> arg2) {
                super(DataType.Int);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            @Override
            public Integer execute(VirtualFrame frame) {
                return Math.max(arg1.execute(frame), arg2.execute(frame));
            }

        }

        public static final class MaxLong extends TwoArgsMathFunction<Long> {
            public MaxLong(MGTNode<Long> arg1, MGTNode<Long> arg2) {
                super(DataType.Long);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            @Override
            public Long execute(VirtualFrame frame) {
                return Math.max(arg1.execute(frame), arg2.execute(frame));
            }

        }

        public static final class MaxDouble extends TwoArgsMathFunction<Double> {
            public MaxDouble(MGTNode<Double> arg1, MGTNode<Double> arg2) {
                super(DataType.Double);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            @Override
            public Double execute(VirtualFrame frame) {
                return Math.max(arg1.execute(frame), arg2.execute(frame));
            }

        }

        public static final class HypotDouble extends TwoArgsMathFunction<Double> {
            public HypotDouble(MGTNode<Double> arg1, MGTNode<Double> arg2) {
                super(DataType.Double);
                this.arg1 = arg1;
                this.arg2 = arg2;
            }

            public static double hypot(final double x, final double y) {
                if (Double.isInfinite(x) || Double.isInfinite(y)) {
                    return Double.POSITIVE_INFINITY;
                } else if (Double.isNaN(x) || Double.isNaN(y)) {
                    return Double.NaN;
                } else {

                    final int expX = getExponent(x);
                    final int expY = getExponent(y);
                    if (expX > expY + 27) {
                        return abs(x);
                    } else if (expY > expX + 27) {
                        return abs(y);
                    } else {
                        final int middleExp = (expX + expY) / 2;
                        final double scaledX = scalb(x, -middleExp);
                        final double scaledY = scalb(y, -middleExp);
                        final double scaledH = Math.sqrt(scaledX * scaledX + scaledY * scaledY);
                        return scalb(scaledH, middleExp);
                    }
                }
            }

            public static double scalb(final double d, final int n) {

                if ((n > -1023) && (n < 1024)) {
                    return d * Double.longBitsToDouble(((long) (n + 1023)) << 52);
                }

                if (Double.isNaN(d) || Double.isInfinite(d) || (d == 0)) {
                    return d;
                }
                if (n < -2098) {
                    return (d > 0) ? 0.0 : -0.0;
                }
                if (n > 2097) {
                    return (d > 0) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }

                final long bits = Double.doubleToRawLongBits(d);
                final long sign = bits & 0x8000000000000000L;
                int exponent = ((int) (bits >>> 52)) & 0x7ff;
                long mantissa = bits & 0x000fffffffffffffL;

                int scaledExponent = exponent + n;

                if (n < 0) {
                    if (scaledExponent > 0) {
                        return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                    } else if (scaledExponent > -53) {
                        mantissa = mantissa | (1L << 52);
                        final long mostSignificantLostBit = mantissa & (1L << (-scaledExponent));
                        mantissa = mantissa >>> (1 - scaledExponent);
                        if (mostSignificantLostBit != 0) {
                            mantissa++;
                        }
                        return Double.longBitsToDouble(sign | mantissa);

                    } else {
                        return (sign == 0L) ? 0.0 : -0.0;
                    }
                } else {
                    if (exponent == 0) {
                        while ((mantissa >>> 52) != 1) {
                            mantissa = mantissa << 1;
                            --scaledExponent;
                        }
                        ++scaledExponent;
                        mantissa = mantissa & 0x000fffffffffffffL;

                        if (scaledExponent < 2047) {
                            return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                        } else {
                            return (sign == 0L) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                        }

                    } else if (scaledExponent < 2047) {
                        return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                    } else {
                        return (sign == 0L) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                    }
                }
            }

            public static int getExponent(final double d) {
                return (int) ((Double.doubleToRawLongBits(d) >>> 52) & 0x7ff) - 1023;
            }

            public static double abs(double x) {
                return Double.longBitsToDouble(0x7fffffffffffffffL & Double.doubleToRawLongBits(x));
            }

            @Override
            public Double execute(VirtualFrame frame) {
                return hypot(arg1.execute(frame), arg2.execute(frame));
            }

        }

    }
}
