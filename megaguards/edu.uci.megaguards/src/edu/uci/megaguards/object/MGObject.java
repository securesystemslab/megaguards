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
package edu.uci.megaguards.object;

public abstract class MGObject {

    protected String name;
    protected Object value;

    protected DataType dataType;
    protected DataType dataTypeHelper; // for Complex numbers

    protected MGObject(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    protected MGObject(MGObject o) {
        this.name = o.name;
        this.value = o.value;
        this.dataType = DataType.values()[o.dataType.ordinal()];
        this.dataTypeHelper = DataType.values()[o.dataType.ordinal()];
    }

    public final String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public void getClearValue() {
        value = null;
    }

    public final DataType getDataType() {
        return dataType;
    }

    public static DataType getDominantType(DataType left, DataType right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        if (left == right)
            return left;
        if (left == DataType.Complex || right == DataType.Complex)
            return DataType.Complex;
        if (left == DataType.DoubleArray || right == DataType.DoubleArray)
            return DataType.DoubleArray;
        if (left == DataType.LongArray || right == DataType.LongArray)
            return DataType.LongArray;
        if (left == DataType.IntArray || right == DataType.IntArray)
            return DataType.IntArray;
        if (left == DataType.Double || right == DataType.Double)
            return DataType.Double;
        if (left == DataType.Long || right == DataType.Long)
            return DataType.Long;
        return DataType.Int;
    }

}
