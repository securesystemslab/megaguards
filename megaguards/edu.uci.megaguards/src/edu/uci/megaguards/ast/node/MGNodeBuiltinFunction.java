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
package edu.uci.megaguards.ast.node;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.object.DataType;

public class MGNodeBuiltinFunction extends MGNode {

    public enum BuiltinFunctionType {
        MIN,
        MAX,

        RANGE, // should be handled by #MGForNode

    }

    ArrayList<MGNode> nodes;
    private BuiltinFunctionType type;

    public MGNodeBuiltinFunction(ArrayList<MGNode> nodes, DataType kind, BuiltinFunctionType op) {
        this.nodes = nodes;
        if (nodes != null)
            for (MGNode n : nodes)
                if (n != null)
                    n.setParent(this);
        this.expectedType = kind;
        this.type = op;
    }

    @TruffleBoundary
    public ArrayList<MGNode> getNodes() {
        return nodes;
    }

    public BuiltinFunctionType getType() {
        return type;
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitBuiltinFunction(this);
    }

    @Override
    public MGNode copy() {
        ArrayList<MGNode> _nodes = new ArrayList<>();
        for (MGNode f : nodes)
            _nodes.add(f.copy());
        return new MGNodeBuiltinFunction(_nodes, expectedType, type);
    }

    @Override
    public String toString() {
        String s = "";
        for (MGNode n : nodes)
            s += n.toString() + ", ";
        return String.format("%s(%s):%s\n", type, s, expectedType);

    }

}
