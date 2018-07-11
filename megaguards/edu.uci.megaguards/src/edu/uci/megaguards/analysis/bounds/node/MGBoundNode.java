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
package edu.uci.megaguards.analysis.bounds.node;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.object.DataType;

public abstract class MGBoundNode {

    protected boolean requireBoundCheck;
    protected final DataType type;

    protected MGBoundNode(DataType type) {
        this.type = type;
        this.requireBoundCheck = true;
    }

    public boolean isRequireBoundCheck() {
        return requireBoundCheck;
    }

    public MGBoundNode setRequireBoundCheck(boolean requireBoundCheck) {
        this.requireBoundCheck = requireBoundCheck;
        return this;
    }

    public DataType getType() {
        return type;
    }

    @TruffleBoundary
    public <R> R accept(@SuppressWarnings("unused") MGBoundVisitorIF<R> visitor) throws Exception {
        throw new RuntimeException("Unexpected MGNode: " + this);
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    public void traverse(MGBoundVisitorIF<?> visitor) throws Exception {
        throw new RuntimeException("No viable traversal node: " + this);
    }

}
