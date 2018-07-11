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

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;

public abstract class Boxed<T extends Node> extends Node {

    @Child protected T valueNode;
    protected Object recentValue;
    protected final DataType type;

    protected static final List<Unboxer> unProcessed = new ArrayList<>();

    @TruffleBoundary
    public static Unboxer addBoxed(Unboxer boxed) {
        unProcessed.add(boxed);
        return boxed;
    }

    @TruffleBoundary
    public static void UnboxAll(MGLog log) throws MGException {
        long dataSize = 0;
        for (Unboxer boxed : unProcessed) {
            dataSize += boxed.getInfo().sizeEstimate(boxed.getTypeSize());
            boxed.setValue(boxed.unbox());
        }
        log.setOptionValue("TotalDataTransfer", log.getOptionValueLong("TotalDataTransfer") + dataSize);
        unProcessed.clear();
    }

    @SuppressWarnings("unchecked")
    public Boxed(T valueNode, DataType type) {
        if (valueNode != null) {
            T v = (T) valueNode.deepCopy();
            this.valueNode = v;
            adoptChildren();
        } else {
            this.valueNode = null;
        }
        this.recentValue = null;
        this.type = type;
    }

    public T getValueNode() {
        return valueNode;
    }

    public Object getRecentValue() {
        return recentValue;
    }

    public void setRecentValue(Object recentValue) {
        this.recentValue = recentValue;
    }

    public DataType getType() {
        return type;
    }

    public Unboxer getUnboxer() {
        throw new IllegalStateException("This is intended for Boxed that require Unboxer. e.g Array, List..");
    }

    public abstract Object getUnboxed(VirtualFrame frame, Object v);

    public Object getUnboxed(VirtualFrame frame) {
        return getUnboxed(frame, null);
    }

}
