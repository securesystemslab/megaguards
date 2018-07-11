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

import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.unbox.Boxed;
import edu.uci.megaguards.unbox.Unboxer;

public abstract class MGArray extends MGStorage {

    protected final ArrayInfo info;
    protected boolean readOnly;
    protected boolean writeOnly;
    protected boolean local;
    protected MGBoundNode[] bounds;
    protected boolean propagated;

    // dimensions
    protected MGNode[] indices;
    protected int indicesLen;

    protected boolean noBounds;

    protected Unboxer boxed;

    protected MGArray(String name, Unboxer boxed, Boxed<?> valueNode) {
        super(name, boxed, valueNode);
        this.info = boxed.getInfo();
        this.array = true;
        this.readOnly = true;
        this.writeOnly = false;
        this.local = false;
        this.bounds = null;
        this.propagated = false;
        this.noBounds = false;
        this.boxed = boxed;
    }

    protected MGArray(String name, ParallelWorkload value) {
        super(name, value, null);
        this.info = null;
        this.array = true;
        this.readOnly = true;
        this.writeOnly = false;
        this.local = false;
        this.bounds = null;
        this.propagated = false;
        this.noBounds = false;
        this.boxed = null;
    }

    protected MGArray(String name, MGArray valueRef) {
        super(name, valueRef, null);
        this.info = valueRef.info;
        this.array = true;
        this.readOnly = true;
        this.writeOnly = false;
        this.local = false;
        this.bounds = null;
        this.propagated = false;
        this.noBounds = false;
        this.boxed = valueRef.boxed;
    }

    protected MGArray(String name, MGArray valueRef, ArrayInfo info) {
        super(name, valueRef, null);
        this.info = (info == null) ? valueRef.info : info;
        this.array = true;
        this.readOnly = true;
        this.writeOnly = false;
        this.local = false;
        this.bounds = null;
        this.propagated = false;
        this.noBounds = false;
        this.boxed = valueRef.boxed;
    }

    protected MGArray(MGArray o) {
        super(o);
        this.array = o.array;
        this.readOnly = o.readOnly;
        this.writeOnly = o.writeOnly;
        this.local = o.local;
        this.bounds = o.bounds;
        this.propagated = o.propagated;
        this.indices = new MGNode[10];
        this.indicesLen = o.indicesLen;
        this.info = o.info;
        this.noBounds = o.noBounds;
        this.boxed = o.boxed;
    }

    public static boolean isArray(DataType type) {
        return type == DataType.IntArray ||
                        type == DataType.LongArray ||
                        type == DataType.DoubleArray ||
                        type == DataType.BoolArray;
    }

    @Override
    public final boolean isPropagated() {
        return propagated;
    }

    @Override
    public final int getDim() {
        return (info != null) ? info.getDim() - indicesLen : 0;
    }

    public final MGNode[] getIndices() {
        return this.indices;
    }

    public final int getIndicesLen() {
        return indicesLen;
    }

    public final MGBoundNode[] getBounds() {
        return bounds;
    }

    public final void setBounds(MGBoundNode[] bounds) {
        this.bounds = bounds;
    }

    public final boolean isNoBounds() {
        return noBounds;
    }

    public final void setNoBounds(boolean noBounds) {
        this.noBounds = noBounds;
    }

    @Override
    public final boolean isReadOnly() {
        return ((MGArray) this.getOrigin()).readOnly;
    }

    public final boolean isWriteOnly() {
        return ((MGArray) this.getOrigin()).writeOnly;
    }

    public final MGArray setWriteOnly(boolean writeOnly) {
        ((MGArray) this.getOrigin()).writeOnly = writeOnly;
        return this;
    }

    public final boolean isLocal() {
        return local;
    }

    public final MGArray setLocal(boolean local) {
        this.local = local;
        return this;
    }

    @Override
    public final void setReadWrite() {
        ((MGArray) this.getOrigin()).readOnly = false;
    }

    public final MGArray setReadOnly() {
        ((MGArray) this.getOrigin()).readOnly = true;
        return this;
    }

    public final ArrayInfo getArrayInfo() {
        if (info == null) {
            if (value instanceof ParallelWorkload) {
                if (((ParallelWorkload) value).getBoxed() != null)
                    return ((ParallelWorkload) value).getBoxed().getInfo();
                else
                    return ParallelWorkload.DEFAULT;
            }
        }
        return info;
    }

    public final MGArray addIndex(MGNode index) {
        this.indices[indicesLen++] = index;
        updateType();
        return this;
    }

    public final void addPerminantIndex(String varname, MGNode index) {
        this.indices[indicesLen++] = index;
        updateType();
        if (getDim() > 1) {
            this.propagated = true;
        } else {
            this.name = varname;
            this.propagated = false;
        }

    }

    @Override
    public final void updateValue(Object newVal) {
        this.value = boxed = (Unboxer) newVal;
    }

    public final Unboxer getBoxed() {
        return boxed;
    }

    protected abstract void updateType();

}
