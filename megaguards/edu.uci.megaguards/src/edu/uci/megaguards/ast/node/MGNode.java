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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.object.DataType;

public abstract class MGNode {

    protected DataType expectedType;
    protected SourceSection source;
    protected MGNode parent;

    protected MGNode() {
        this.parent = null;
    }

    public DataType getExpectedType() {
        return expectedType;
    }

    public SourceSection getSource() {
        return source;
    }

    public MGNode getParent() {
        return parent;
    }

    public MGNode setParent(MGNode parent) {
        this.parent = parent;
        return this;
    }

    public MGNode setSource(SourceSection source) {
        this.source = source;
        return this;
    }

    public MGBoundNode getBound() {
        return null;
    }

    public void setBound(@SuppressWarnings("unused") MGBoundNode bound) {
    }

    public abstract MGNode copy();

    @TruffleBoundary
    public abstract <R> R accept(MGVisitorIF<R> visitor);

}
