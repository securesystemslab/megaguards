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

public abstract class MGTUnaryOp<T> extends MGTNode<T> {

    public MGTUnaryOp(DataType t) {
        super(t);
    }

    public static final class Not extends MGTUnaryOp<Boolean> {

        @CompilationFinal @Child private MGTNode<Boolean> value;

        public Not(MGTNode<Boolean> v) {
            super(DataType.Bool);
            this.value = v;
        }

        @Override
        public Boolean execute(VirtualFrame frame) {
            return !value.execute(frame);
        }
    }

    public static final class CastIntToLong extends MGTUnaryOp<Long> {

        @CompilationFinal @Child private MGTNode<Integer> value;

        public CastIntToLong(MGTNode<Integer> v) {
            super(DataType.Long);
            this.value = v;
        }

        @Override
        public Long execute(VirtualFrame frame) {
            return value.execute(frame).longValue();
        }
    }

    public static final class CastIntToDouble extends MGTUnaryOp<Double> {

        @CompilationFinal @Child private MGTNode<Integer> value;

        public CastIntToDouble(MGTNode<Integer> v) {
            super(DataType.Double);
            this.value = v;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return value.execute(frame).doubleValue();
        }
    }

    public static final class CastLongToInt extends MGTUnaryOp<Integer> {

        @CompilationFinal @Child private MGTNode<Long> value;

        public CastLongToInt(MGTNode<Long> v) {
            super(DataType.Int);
            this.value = v;
        }

        @Override
        public Integer execute(VirtualFrame frame) {
            return value.execute(frame).intValue();
        }
    }

    public static final class CastLongToDouble extends MGTUnaryOp<Double> {

        @CompilationFinal @Child private MGTNode<Long> value;

        public CastLongToDouble(MGTNode<Long> v) {
            super(DataType.Double);
            this.value = v;
        }

        @Override
        public Double execute(VirtualFrame frame) {
            return value.execute(frame).doubleValue();
        }
    }

    public static final class CastDoubleToLong extends MGTUnaryOp<Long> {

        @CompilationFinal @Child private MGTNode<Double> value;

        public CastDoubleToLong(MGTNode<Double> v) {
            super(DataType.Long);
            this.value = v;
        }

        @Override
        public Long execute(VirtualFrame frame) {
            return value.execute(frame).longValue();
        }
    }

    public static final class CastDoubleToInt extends MGTUnaryOp<Integer> {

        @CompilationFinal @Child private MGTNode<Double> value;

        public CastDoubleToInt(MGTNode<Double> v) {
            super(DataType.Int);
            this.value = v;
        }

        @Override
        public Integer execute(VirtualFrame frame) {
            return value.execute(frame).intValue();
        }
    }

}
