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
package edu.uci.megaguards.analysis.parallel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.ast.node.MGNodeOperand;
import edu.uci.megaguards.object.MGArray;

public class ArrayAccesses {

    public class ArrayAccess {
        HashSet<MGArray> reads;
        HashSet<MGArray> writes;

        public ArrayAccess(MGArray array, boolean isRead) {
            this.reads = new HashSet<>();
            this.writes = new HashSet<>();
            if (isRead)
                reads.add(array);
            else
                writes.add(array);
        }
    }

    private HashMap<String, ArrayAccess> readWrites;

    public ArrayAccesses() {
        this.readWrites = new HashMap<>();
    }

    @TruffleBoundary
    public HashMap<String, ArrayAccess> getReadWrites() {
        return readWrites;
    }

    private void mergeExists(String var, String otherVar, ArrayAccesses other) {
        final ArrayAccess access = this.readWrites.get(var);
        final ArrayAccess otherAccess = other.readWrites.get(otherVar);
        access.reads.addAll(otherAccess.reads);
        access.writes.addAll(otherAccess.writes);
    }

    public void mergeReadWrite(ArrayAccesses other) {
        for (String var : other.readWrites.keySet()) {
            if (!this.readWrites.containsKey(var)) {
                this.readWrites.put(var, other.readWrites.get(var));
                if (var.contains("$")) {
                    final String v = var.substring(0, var.indexOf('$'));
                    if (this.readWrites.containsKey(v)) {
                        mergeExists(v, var, other);
                    }
                }
            } else {
                mergeExists(var, var, other);
            }
        }
    }

    @TruffleBoundary
    public void setReadWrites(HashMap<String, ArrayAccess> readWrites) {
        this.readWrites = readWrites;
    }

    @TruffleBoundary
    public ArrayList<MGArray> getReads(String name) {
        ArrayList<MGArray> reads = new ArrayList<>();

        for (MGArray var : this.readWrites.get(name).reads)
            reads.add(var);

        return reads;
    }

    public boolean isWrite(MGArray array) {
        final String name = array.getName();
        if (this.readWrites.containsKey(name) && this.readWrites.get(name).writes.size() > 0)
            return true;
        return false;
    }

    @TruffleBoundary
    public ArrayList<MGArray> getWrites(String name) {
        ArrayList<MGArray> writes = new ArrayList<>();

        for (MGArray var : this.readWrites.get(name).writes)
            writes.add(var);

        return writes;
    }

    @TruffleBoundary
    public void addArrayAccess(MGNodeOperand arrayaccess, boolean isRead) {
        MGArray array = (MGArray) arrayaccess.getValue();
        addArrayAccess(array, isRead);
    }

    @TruffleBoundary
    public void addArrayAccess(MGArray array, boolean isRead) {
        String name = array.getName();
        if (readWrites.containsKey(name)) {
            if (isRead && !readWrites.get(name).reads.contains(array))
                readWrites.get(name).reads.add(array);
            else if (!isRead && !readWrites.get(name).writes.contains(array))
                readWrites.get(name).writes.add(array);
        } else {
            readWrites.put(name, new ArrayAccess(array, isRead));
        }
    }

}
