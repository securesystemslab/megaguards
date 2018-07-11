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

public class MGNodeFunctionCall extends MGNode {

    private final MGNodeUserFunction functionNode;
    private final ArrayList<MGNode> args;
    private boolean inline;

    public MGNodeFunctionCall(MGNodeUserFunction functionNode, ArrayList<MGNode> args) {
        this.expectedType = functionNode.getExpectedType();
        this.args = args;
        if (args != null)
            for (MGNode n : args)
                n.setParent(this);
        this.functionNode = functionNode;
        this.inline = false;
        functionNode.getCalls().add(this);
    }

    public MGNodeUserFunction getFunctionNode() {
        return functionNode;
    }

    public ArrayList<MGNode> getArgs() {
        return args;
    }

    public boolean isInline() {
        return inline;
    }

    public void setInline(boolean l) {
        this.inline = l;
    }

    private boolean recursiveIsReturnCall(MGNode rparent) {
        if (rparent instanceof MGNodeAssign)
            return true;

        if (rparent instanceof MGNodeReturn)
            return true;

        if (rparent instanceof MGNodeBinOp)
            return true;

        if (rparent instanceof MGNodeUnaryOp)
            return true;

        if (rparent instanceof MGNodeFunctionCall)
            return true;

        if (rparent instanceof MGNodeMathFunction)
            return true;

        if (rparent instanceof MGNodeIf)
            recursiveIsReturnCall(rparent.getParent());

        return false;
    }

    public boolean isReturnCall() {
        if (expectedType == DataType.None)
            return false;

        return recursiveIsReturnCall(this.parent);
    }

    @Override
    @TruffleBoundary
    public <R> R accept(MGVisitorIF<R> visitor) {
        return visitor.visitFunctionCall(this);
    }

    @Override
    public MGNode copy() {
        ArrayList<MGNode> _args = new ArrayList<>();
        for (MGNode n : args)
            _args.add(n.copy());
        return new MGNodeFunctionCall((MGNodeUserFunction) functionNode.copy(), _args);
    }

    @Override
    public String toString() {
        if (args == null)
            return String.format("%s()\n", functionNode.getFunctionName());
        else {
            String s = "";
            for (MGNode n : args)
                s += n.toString() + ", ";
            return String.format("%s(%s)\n", functionNode.getFunctionName(), s);
        }

    }

}
