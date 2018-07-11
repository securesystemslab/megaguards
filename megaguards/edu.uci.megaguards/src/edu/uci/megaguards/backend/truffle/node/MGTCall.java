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
package edu.uci.megaguards.backend.truffle.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import edu.uci.megaguards.backend.MGTruffle;
import edu.uci.megaguards.object.DataType;

public class MGTCall<T> extends MGTNode<T> {

    final protected MGTruffle.FunctionRoot funcRoot;
    @Child protected DirectCallNode callNode;
    @Children protected final MGTNode<?>[] args;
    protected final Object[] arguments;

    public MGTCall(MGTruffle.FunctionRoot funcRoot, MGTNode<?>[] args, DataType t) {
        super(t);
        this.funcRoot = funcRoot;
        this.args = args;
        this.arguments = new Object[args.length];
        this.callNode = funcRoot.getDirectCallNode();
    }

    @SuppressWarnings("unchecked")
    @ExplodeLoop
    @Override
    public T execute(VirtualFrame frame) {
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = args[i].execute(frame);
        }

        return (T) callNode.call(arguments);
    }

    public static final class MGTCallVoid extends MGTCall<Object> {

        public MGTCallVoid(MGTruffle.FunctionRoot funcRoot, MGTNode<?>[] args) {
            super(funcRoot, args, DataType.None);
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = args[i].execute(frame);
            }
            callNode.call(arguments);
            return null;
        }

    }
}
