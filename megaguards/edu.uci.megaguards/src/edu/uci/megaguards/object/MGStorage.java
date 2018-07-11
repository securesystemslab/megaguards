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

import java.util.HashMap;

import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.unbox.Boxed;

public abstract class MGStorage extends MGObject {

    protected boolean array;
    protected boolean define;
    protected MGStorage origin;
    protected int defUseIndex;

    protected Boxed<?> valueNode;

    protected HashMap<String, LoopInfo> relatedLoopInfos;

    protected MGStorage(String name, Object value, Boxed<?> valueNode) {
        super(name, value);
        this.origin = this;
        this.valueNode = valueNode;
        this.array = false;
        this.defUseIndex = -1;
        this.relatedLoopInfos = null;
    }

    protected MGStorage(String name) {
        super(name, null);
        this.origin = this;
        this.valueNode = null;
        this.array = false;
        this.defUseIndex = -1;
        this.relatedLoopInfos = null;
    }

    protected MGStorage(MGStorage o) {
        super(o);
        this.origin = o.origin;
        this.define = false;
        this.valueNode = o.valueNode;
        this.defUseIndex = o.defUseIndex;
        this.relatedLoopInfos = null;
        if (o.relatedLoopInfos != null) {
            this.relatedLoopInfos = new HashMap<>();
            this.relatedLoopInfos.putAll(o.relatedLoopInfos);
        }
    }

    @Override
    public final Object getValue() {
        return origin.value;
    }

    @Override
    public final void getClearValue() {
        origin.value = null;
    }

    public final Boxed<?> getValueNode() {
        return valueNode;
    }

    public final MGStorage getOrigin() {
        return origin;
    }

    public final void setValueNode(Boxed<?> valueNode) {
        this.valueNode = valueNode;
    }

    public final DataType getDataKindHelper() {
        return dataTypeHelper;
    }

    public final void setDataKindHelper(DataType dataKindHelper) {
        this.dataTypeHelper = dataKindHelper;
    }

    public final boolean isDefine() {
        return define;
    }

    public final MGStorage setDefine() {
        this.define = true;
        return this;
    }

    public final MGStorage setDefined() {
        this.define = false;
        return this;
    }

    public final HashMap<String, LoopInfo> getRelatedLoopInfos() {
        return relatedLoopInfos;
    }

    public final void setRelatedLoopInfos(HashMap<String, LoopInfo> currentLoopInfos) {
        this.relatedLoopInfos = new HashMap<>();
        this.relatedLoopInfos.putAll(currentLoopInfos);
    }

    public final int getDefUseIndex() {
        return defUseIndex;
    }

    public final MGStorage setDefUseIndex(int defUseIndex) {
        this.defUseIndex = defUseIndex;
        return this;
    }

    public int getDim() {
        return 0;
    }

    public boolean isPropagated() {
        return false;
    }

    public boolean isReadOnly() {
        return true;
    }

    public void setReadWrite() {
    }

    public abstract MGStorage copy();

    public final void setValue(Object newVal) {
        this.origin.value = newVal;
    }

    public void updateValue(Object newVal) {
        this.value = newVal;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", name, dataType);
    }
}
