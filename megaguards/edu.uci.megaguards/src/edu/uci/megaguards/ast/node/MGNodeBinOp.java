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

import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.object.DataType;

public class MGNodeBinOp extends MGNode {

    public enum BinOpType {
        ADD,
        SUB,
        MUL,
        DIV,

        POW,
        MOD,

        OR,
        AND,

        LeftShift,
        RightShift,
        BitAND,
        BitOR,
        BitXOR,

        Equal,
        NotEqual,
        LessThan,
        LessEqual,
        GreaterThan,
        GreaterEqual,
    }

    private MGNode left;
    private MGNode right;
    private BinOpType type;
    private MGBoundNode bound;
    private boolean trusted;

    public MGNodeBinOp(MGNode left, BinOpType op, MGNode right, DataType kind) {
        super();
        this.left = left.setParent(this);
        this.right = right.setParent(this);
        this.type = op;
        this.expectedType = kind;
        this.trusted = false;
    }

    private MGNodeBinOp(MGNode left, BinOpType op, MGNode right, DataType kind, MGBoundNode bound, boolean isTrusted) {
        this(left, op, right, kind);
        this.bound = bound;
        this.trusted = isTrusted;
    }

    public MGNode getLeft() {
        return left;
    }

    public MGNode getRight() {
        return right;
    }

    @Override
    public MGBoundNode getBound() {
        return bound;
    }

    @Override
    public void setBound(MGBoundNode bound) {
        this.bound = bound;
    }

    public boolean overflowCheck() {
        return !this.trusted && (this.bound == null || this.bound.isRequireBoundCheck());
    }

    public BinOpType getType() {
        return type;
    }

    public MGNodeBinOp setTrusted() {
        this.trusted = true;
        return this;
    }

    @Override
    public MGNode copy() {
        return new MGNodeBinOp(left.copy(), type, right.copy(), expectedType, bound, trusted);
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitBinOp(this);
    }

    @Override
    public String toString() {
        return String.format("(%s %s %s):%s", left.toString(), type, right.toString(), expectedType);

    }

}
