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

public class MGNodeBlock extends MGNodeControl {

    private ArrayList<MGNode> children;

    public MGNodeBlock(ArrayList<MGNode> children) {
        super();
        this.children = children;
        if (children != null)
            if (this.children.size() > 0) {
                for (MGNode n : children) {
                    n.setParent(this);
                }
                this.alwaysReturn = checkAlwaysReturn(children.get(children.size() - 1));
                if (this.alwaysReturn) {
                    this.expectedType = children.get(children.size() - 1).expectedType;
                }
            }
    }

    public ArrayList<MGNode> getChildren() {
        return children;
    }

    @Override
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitBlock(this);
    }

    @Override
    public MGNode copy() {
        ArrayList<MGNode> _children = new ArrayList<>();
        for (MGNode c : children) {
            _children.add(c.copy());
        }
        return new MGNodeBlock(_children);
    }

    @Override
    public String toString() {
        String s = "";
        for (MGNode n : children)
            s += n.toString() + "\n";
        return s;
    }
}
