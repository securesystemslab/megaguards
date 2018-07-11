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
package edu.uci.megaguards.unbox;

import edu.uci.megaguards.analysis.exception.TypeException;
import edu.uci.megaguards.object.ArrayInfo;
import edu.uci.megaguards.object.DataType;

public abstract class StaticUnboxer extends Unboxer {

    public StaticUnboxer(Object value, ArrayInfo info, DataType kind, int OriginHashCode, boolean ignore) {
        super(value, info, kind, OriginHashCode, ignore);
    }

    public abstract Object getFirstValue();

    public static class IntDimSize extends StaticUnboxer {

        private final int dim;

        public IntDimSize(ArrayInfo info, int dim) {
            super(info.getSize(dim), info, DataType.Int, info.hashCode(), false);
            this.dim = dim;
        }

        @Override
        public Object unbox() throws TypeException {
            return info.getSize(dim);
        }

        @Override
        public int getTypeSize() {
            return Integer.BYTES;
        }

        @Override
        public Object getFirstValue() {
            return unbox();
        }

    }

    public static class IntArray extends StaticUnboxer {

        public IntArray(Object value, ArrayInfo info, int OriginHashCode, boolean ignore) {
            super(value, info, DataType.IntArray, OriginHashCode, ignore);
        }

        @Override
        public Object unbox() throws TypeException {
            return value;
        }

        @Override
        public int getTypeSize() {
            return Integer.BYTES;
        }

        @Override
        public Object getFirstValue() {
            return ((int[]) value)[0];
        }

    }

    public static class LongArray extends StaticUnboxer {

        public LongArray(Object value, ArrayInfo info, int OriginHashCode, boolean ignore) {
            super(value, info, DataType.LongArray, OriginHashCode, ignore);
        }

        @Override
        public Object unbox() throws TypeException {
            return value;
        }

        @Override
        public int getTypeSize() {
            return Long.BYTES;
        }

        @Override
        public Object getFirstValue() {
            return ((long[]) value)[0];
        }

    }

    public static class DoubleArray extends StaticUnboxer {

        public DoubleArray(Object value, ArrayInfo info, int OriginHashCode, boolean ignore) {
            super(value, info, DataType.DoubleArray, OriginHashCode, ignore);
        }

        @Override
        public Object unbox() throws TypeException {
            return value;
        }

        @Override
        public int getTypeSize() {
            return Double.BYTES;
        }

        @Override
        public Object getFirstValue() {
            return ((double[]) value)[0];
        }

    }

    public static class BooleanArray extends StaticUnboxer {

        public BooleanArray(Object value, ArrayInfo info, int OriginHashCode, boolean ignore) {
            super(value, info, DataType.BoolArray, OriginHashCode, ignore);
        }

        @Override
        public Object unbox() throws TypeException {
            return value;
        }

        @Override
        public int getTypeSize() {
            return Integer.BYTES;
        }

        @Override
        public Object getFirstValue() {
            return ((boolean[]) value)[0];
        }

    }

}
