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

import com.oracle.truffle.api.nodes.Node;

import edu.uci.megaguards.object.DataType;

public abstract class UnboxerUtil<T extends Node> {

    public abstract Object createList(DataType type, Object array);

    public abstract Boxed<?> BoxedInt(T node);

    public abstract Boxed<?> BoxedLong(T node);

    public abstract Boxed<?> BoxedDouble(T node);

    public abstract Boxed<?> specialize1DArray(T node, Object val);

    public abstract Boxed<?> specializeArray(T node, Object val);

    public Boxed<?> specializePrimitive(T node, Object value) {
        Boxed<?> specialized = null;

        if (value instanceof Integer) {
            specialized = BoxedInt(node);
        } else if (value instanceof Long) {
            specialized = BoxedLong(node);
        } else if (value instanceof Double) {
            specialized = BoxedDouble(node);
        }

        return specialized;
    }

    public Boxed<?> specializePrimitive(Object value) {
        return specializePrimitive(null, value);
    }

    public Boxed<?> specialize1DArray(Object val) {
        return specialize1DArray(null, val);
    }

    public Boxed<?> specializeArray(Object val) {
        return specializeArray(null, val);
    }

}
