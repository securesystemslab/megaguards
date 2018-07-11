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
package edu.uci.megaguards.backend.parallel;

import edu.uci.megaguards.object.ArrayInfo;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.unbox.Unboxer;

public abstract class ParallelWorkload {

    public static final ArrayInfo DEFAULT = new ArrayInfo(null, 0);

    protected final LoadType type;
    protected final DataType dataType;
    protected final int[] dim;
    protected Unboxer boxed;
    protected ArrayInfo info;

    public enum LoadType {
        GlobalSize,
        GroupSize,
        LocalSize,
    }

    protected ParallelWorkload() {
        this.dim = null;
        this.type = null;
        this.dataType = null;
        this.boxed = null;
        this.info = null;
    }

    public ParallelWorkload(LoadType type, int[] dim, DataType dataType) {
        this.dim = dim;
        this.type = type;
        this.dataType = dataType;
        this.boxed = null;
        this.info = null;
    }

    public LoadType getType() {
        return type;
    }

    public int[] getDim() {
        return dim;
    }

    public Unboxer getBoxed() {
        return boxed;
    }

    public abstract boolean ensureCapacity(long size);

}
