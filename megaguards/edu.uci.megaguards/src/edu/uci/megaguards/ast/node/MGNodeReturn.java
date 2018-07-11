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

public class MGNodeReturn extends MGNodeControl {

    private MGNodeOperand left;
    private MGNode right;
    private MGNodeJumpTo jLabel;

    public MGNodeReturn(MGNodeJumpTo jLabel) {
        super();
        this.left = null;
        this.right = new MGNodeEmpty();
        this.jLabel = jLabel;
        this.expectedType = DataType.None;
        this.alwaysReturn = true;
    }

    public MGNodeReturn(MGNodeOperand left, MGNode right, MGNodeJumpTo jLabel) {
        this(jLabel);
        this.left = left;
        if (left != null)
            left.setParent(this);
        this.right = right;
        if (right != null)
            right.setParent(this);
        this.expectedType = right.getExpectedType();
        this.alwaysReturn = true;
    }

    public MGNode getRight() {
        return right;
    }

    public MGNodeOperand getLeft() {
        return left;
    }

    public MGNodeJumpTo getjLabel() {
        return jLabel;
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitReturn(this);
    }

    @Override
    public MGNode copy() {
        return new MGNodeReturn((MGNodeOperand) left.copy(), right.copy(), (MGNodeJumpTo) jLabel.copy());
    }

    @Override
    public String toString() {
        if (right != null)
            return "return " + right.toString() + "\n";
        else
            return "return;\n";

    }
}
