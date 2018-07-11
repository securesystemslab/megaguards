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

public abstract class MGTBinaryBoolean<T> extends MGTNode<Boolean> {

    @CompilationFinal @Child protected MGTNode<T> left;
    @CompilationFinal @Child protected MGTNode<T> right;

    public MGTBinaryBoolean(MGTNode<T> l, MGTNode<T> r) {
        super(DataType.Bool);
        this.left = l;
        this.right = r;
    }

    public static abstract class MGTBinaryBooleanInt extends MGTBinaryBoolean<Integer> {

        public MGTBinaryBooleanInt(MGTNode<Integer> l, MGTNode<Integer> r) {
            super(l, r);
        }

        public static final class EqualNode extends MGTBinaryBooleanInt {

            public EqualNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l == r;
            }

        }

        public static final class NotEqualNode extends MGTBinaryBooleanInt {

            public NotEqualNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l != r;
            }

        }

        public static final class LessThanNode extends MGTBinaryBooleanInt {

            public LessThanNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l < r;
            }

        }

        public static final class LessEqualNode extends MGTBinaryBooleanInt {

            public LessEqualNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l <= r;
            }

        }

        public static final class GreaterThanNode extends MGTBinaryBooleanInt {

            public GreaterThanNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l > r;
            }

        }

        public static final class GreaterEqualNode extends MGTBinaryBooleanInt {

            public GreaterEqualNode(MGTNode<Integer> l, MGTNode<Integer> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                int l = left.execute(frame);
                int r = right.execute(frame);
                return l >= r;
            }

        }

    }

    public static abstract class MGTBinaryBooleanLong extends MGTBinaryBoolean<Long> {

        public MGTBinaryBooleanLong(MGTNode<Long> l, MGTNode<Long> r) {
            super(l, r);
        }

        public static final class EqualNode extends MGTBinaryBooleanLong {

            public EqualNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l == r;
            }

        }

        public static final class NotEqualNode extends MGTBinaryBooleanLong {

            public NotEqualNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l != r;
            }

        }

        public static final class LessThanNode extends MGTBinaryBooleanLong {

            public LessThanNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l < r;
            }

        }

        public static final class LessEqualNode extends MGTBinaryBooleanLong {

            public LessEqualNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l <= r;
            }

        }

        public static final class GreaterThanNode extends MGTBinaryBooleanLong {

            public GreaterThanNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l > r;
            }

        }

        public static final class GreaterEqualNode extends MGTBinaryBooleanLong {

            public GreaterEqualNode(MGTNode<Long> l, MGTNode<Long> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                long l = left.execute(frame);
                long r = right.execute(frame);
                return l >= r;
            }

        }

    }

    public static abstract class MGTBinaryBooleanDouble extends MGTBinaryBoolean<Double> {

        public MGTBinaryBooleanDouble(MGTNode<Double> l, MGTNode<Double> r) {
            super(l, r);
        }

        public static final class EqualNode extends MGTBinaryBooleanDouble {

            public EqualNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l == r;
            }

        }

        public static final class NotEqualNode extends MGTBinaryBooleanDouble {

            public NotEqualNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l != r;
            }

        }

        public static final class LessThanNode extends MGTBinaryBooleanDouble {

            public LessThanNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l < r;
            }

        }

        public static final class LessEqualNode extends MGTBinaryBooleanDouble {

            public LessEqualNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l <= r;
            }

        }

        public static final class GreaterThanNode extends MGTBinaryBooleanDouble {

            public GreaterThanNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l > r;
            }

        }

        public static final class GreaterEqualNode extends MGTBinaryBooleanDouble {

            public GreaterEqualNode(MGTNode<Double> l, MGTNode<Double> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                double l = left.execute(frame);
                double r = right.execute(frame);
                return l >= r;
            }

        }

    }

    public static abstract class MGTBinaryBooleanBoolean extends MGTBinaryBoolean<Boolean> {

        public MGTBinaryBooleanBoolean(MGTNode<Boolean> l, MGTNode<Boolean> r) {
            super(l, r);
        }

        public static final class AndNode extends MGTBinaryBooleanBoolean {

            public AndNode(MGTNode<Boolean> l, MGTNode<Boolean> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                return left.execute(frame) && right.execute(frame);
            }

        }

        public static final class OrNode extends MGTBinaryBooleanBoolean {

            public OrNode(MGTNode<Boolean> l, MGTNode<Boolean> r) {
                super(l, r);
            }

            @Override
            public Boolean execute(VirtualFrame frame) {
                return left.execute(frame) || right.execute(frame);
            }

        }

    }

}
