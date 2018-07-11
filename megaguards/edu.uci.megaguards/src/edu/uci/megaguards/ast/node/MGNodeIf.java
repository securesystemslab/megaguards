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

import edu.uci.megaguards.object.DataType;

public class MGNodeIf extends MGNodeControl {

    private final MGNode cond;
    private final MGNode then;
    private final MGNode orelse;

    public MGNodeIf(MGNode cond, MGNode then, MGNode orelse) {
        super();
        this.cond = cond.setParent(this);
        this.then = then.setParent(this);
        this.orelse = orelse;
        if (this.orelse != null)
            this.orelse.setParent(this);
        this.expectedType = then.expectedType;
        this.alwaysReturn = checkAlwaysReturn(then) && checkAlwaysReturn(orelse);

    }

    public MGNodeIf(MGNode then, MGNode orelse) {
        super();
        this.cond = null;
        this.then = then.setParent(this);
        this.orelse = orelse;
        if (this.orelse != null)
            this.orelse.setParent(this);
        this.expectedType = DataType.None;
    }

    public MGNode getCond() {
        return cond;
    }

    public MGNode getThen() {
        return then;
    }

    public MGNode getOrelse() {
        return orelse;
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitIf(this);
    }

    @Override
    public MGNode copy() {
        final MGNode t = then.copy();
        final MGNode e = (orelse != null) ? orelse.copy() : null;
        if (cond != null) {
            return new MGNodeIf(cond.copy(), t, e);
        } else {
            return new MGNodeIf(t, e);
        }
    }

    @Override
    public String toString() {
        if (orelse == null)
            return String.format("if (%s){\n%s}\n", cond.toString(), then.toString());
        else
            return String.format("if (%s){\n%s}\nelse {\n%s}\n", cond.toString(), then.toString(), orelse.toString());

    }

}
