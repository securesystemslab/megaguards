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

public class MGNodeFor extends MGNodeControl {

    private MGNode forBody;
    private LoopInfo loopInfo;

    private boolean[] dependenceExists;

    private MGNodeJumpTo jLabel;

    private MGNodeFor(MGNode forBody, LoopInfo info, MGNodeJumpTo jLabel, boolean[] dep) {
        super();
        this.forBody = forBody.setParent(this);
        this.loopInfo = info;
        this.alwaysReturn = checkAlwaysReturn(forBody);
        this.dependenceExists = dep;
        this.jLabel = jLabel;
    }

    public MGNodeFor(MGNode forBody, LoopInfo info, MGNodeJumpTo jLabel) {
        this(forBody, info, jLabel, new boolean[]{true});
    }

    public LoopInfo getLoopInfo() {
        return loopInfo;
    }

    public MGNode getForBody() {
        return forBody;
    }

    public boolean isDependenceExists() {
        return dependenceExists[0];
    }

    public void setDependenceExists(boolean dependenceExists) {
        this.dependenceExists[0] = dependenceExists;
    }

    public boolean hasBreak() {
        return jLabel != null;
    }

    public MGNodeJumpTo getjLabel() {
        return jLabel;
    }

    public void setHasBreak(MGNodeJumpTo jLabel) {
        this.jLabel = jLabel;
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitFor(this);
    }

    @Override
    public MGNode copy() {
        return new MGNodeFor(forBody.copy(), loopInfo, jLabel != null ? (MGNodeJumpTo) jLabel.copy() : null, dependenceExists);
    }

    @Override
    public String toString() {
        return String.format("for (%s ..){\n%s}\n", loopInfo.getInductionVariable().toString(), forBody.toString());

    }

}
