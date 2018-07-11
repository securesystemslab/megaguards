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

import com.oracle.truffle.api.nodes.ExplodeLoop;

public class ArrayInfo {
    private Class<?> type;
    private final int[] dims;
    private int len;

    public ArrayInfo(Class<?> kind, int size) {
        this(kind);
        addSize(size);
    }

    public ArrayInfo(Class<?> kind) {
        type = kind;
        dims = new int[10];
        len = 0;
    }

    public void setKind(Class<?> kind) {
        type = kind;
    }

    public Class<?> getType() {
        return type;
    }

    public void resetDim() {
        len = 0;
    }

    public int getDim() {
        return len;
    }

    public void addSize(int size) {
        dims[len++] = size;
    }

    public int getSize(int dim) {
        return dims[dim];
    }

    public void setSize(int dim, int length) {
        dims[dim] = length;
    }

    @ExplodeLoop
    public int getSize(int from, int to) {
        int size = 1;
        for (int i = from; i < to; i++)
            size *= getSize(i);
        return size;
    }

    public int[] getDimSizes() {
        int[] ret = new int[len];
        for (int i = 0; i < len; i++) {
            ret[i] = dims[i];
        }
        return ret;
    }

    @ExplodeLoop
    public long sizeEstimate(long dataTypeSize) {
        if (len == 0)
            return 0;
        long size = 0;

        size = dims[0];
        for (int i = 1; i < len; i++)
            size *= dims[i];

        return size * dataTypeSize + ((len > 1) ? (size / dims[0]) * 20 : 0) + 16;
    }

    @ExplodeLoop
    public boolean equals(ArrayInfo info) {
        boolean match = true;
        match = match && this.type == info.type;
        match = match && this.len == info.len;
        for (int i = 0; i < len && match; i++) {
            match = match && dims[i] == info.dims[i];
        }
        return match;
    }

}
