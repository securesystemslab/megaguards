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

import java.util.HashMap;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.analysis.exception.TypeException;
import edu.uci.megaguards.object.ArrayInfo;
import edu.uci.megaguards.object.DataType;

public abstract class Unboxer {

    protected static final HashMap<Integer, Object> UNBOXED = new HashMap<>();

    protected static final HashSet<Integer> changedlist = new HashSet<>();

    protected Object value;
    protected final ArrayInfo info;
    protected final DataType kind;
    protected int OriginHashCode;
    protected boolean changed;

    public Unboxer(Object value, ArrayInfo info, int OriginHashCode, DataType kind) {
        this(value, info, kind, OriginHashCode, false);
    }

    public Unboxer(Object value, ArrayInfo info, DataType kind, int OriginHashCode, boolean ignore) {
        this.value = value;
        this.info = info;
        this.kind = kind;
        this.changed = false;
        this.OriginHashCode = OriginHashCode;
        if (!ignore)
            Boxed.addBoxed(this);
    }

    @TruffleBoundary
    public static void setChanged(int hashCode, boolean changed) {
        if (changed) {
            changedlist.add(hashCode);
        }
    }

    public abstract Object unbox() throws TypeException;

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public ArrayInfo getInfo() {
        return info;
    }

    public DataType getKind() {
        return kind;
    }

    public void setOriginHashCode(int hashCode) {
        OriginHashCode = hashCode;
    }

    public abstract int getTypeSize();

    public boolean isChanged() {
        return changedlist.remove(OriginHashCode);
    }

    public void setChanged(boolean changed) {
        setChanged(OriginHashCode, changed);
    }

}
