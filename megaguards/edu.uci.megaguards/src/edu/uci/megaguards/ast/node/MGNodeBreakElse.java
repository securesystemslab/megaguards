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

public class MGNodeBreakElse extends MGNodeControl {

    private MGNodeFor forBody;
    private MGNode orelse;
    private MGNodeJumpTo jLabel;

    public MGNodeBreakElse(MGNodeFor forBody, MGNode orelse, MGNodeJumpTo jLabel) {
        super();
        this.forBody = (MGNodeFor) forBody.setParent(this);
        this.orelse = orelse.setParent(this);
        this.jLabel = jLabel;
    }

    public MGNodeFor getForBody() {
        return forBody;
    }

    public MGNode getOrelse() {
        return orelse;
    }

    public MGNodeJumpTo getjLabel() {
        return jLabel;
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitBreakElse(this);
    }

    @Override
    public MGNode copy() {
        return new MGNodeBreakElse((MGNodeFor) forBody.copy(), orelse.copy(), (MGNodeJumpTo) jLabel.copy());
    }

    @Override
    public String toString() {
        return String.format("%s\nelse {\n%s}\n\n", forBody.toString(), orelse.toString());

    }

}
