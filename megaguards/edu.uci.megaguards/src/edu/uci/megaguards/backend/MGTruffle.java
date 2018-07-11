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
package edu.uci.megaguards.backend;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.MGNodeUserFunction;
import edu.uci.megaguards.backend.truffle.TruffleTranslator;
import edu.uci.megaguards.backend.truffle.exception.MGTReturnException;
import edu.uci.megaguards.backend.truffle.node.MGTNode;
import edu.uci.megaguards.backend.truffle.node.MGTOperand;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;

public class MGTruffle extends MGInvoke {

    @Child protected MGTNode<?> body;

    public MGTruffle(MGGlobalEnv env) {
        super(env);
    }

    public MGTruffle(MGBaseEnv env, FrameDescriptor frameDescriptor) {
        super(env, frameDescriptor);
    }

    public static MGTruffle createLoop(MGGlobalEnv env, MGLog log) {
        TruffleTranslator translator = new TruffleTranslator(log);
        return translator.translateToLoop(env);
    }

    /*-
    public static MGTruffle createCall(MGGlobalEnv env, MGLog log) {
        TruffleTranslator translator = new TruffleTranslator(log);
        return translator.translateToCall(env);
    }
    */
    public void setBody(MGTNode<?> body) {
        this.body = body;
        adoptChildren();
    }

    public DataType getType() {
        return DataType.None;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return body.execute(frame);
    }

    @Override
    public MGInvoke invalidate(MGLog log) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return createLoop((MGGlobalEnv) env, log);
    }

    public static class FunctionRoot extends MGTruffle {

        protected final MGNodeUserFunction function;
        protected final String functionName;
        @Child protected MGTOperand<?> returnVal;

        public FunctionRoot(MGBaseEnv env, MGNodeUserFunction function, MGTOperand<?> r, FrameDescriptor frameDescriptor) {
            super(env, frameDescriptor);
            this.function = function;
            this.functionName = function.getFunctionName();
            this.returnVal = r;
        }

        public MGTOperand<?> getReturnVal() {
            return returnVal;
        }

        public MGNodeUserFunction getFunction() {
            return function;
        }

        @Override
        public DataType getType() {
            return function.getExpectedType();
        }

        public String getFunctionName() {
            return functionName;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return body.execute(frame);
            } catch (MGTReturnException e) {
                return returnVal.execute(frame);
            }
        }

        public DirectCallNode getDirectCallNode() {
            return Truffle.getRuntime().createDirectCallNode(callTarget);
        }

    }

    public static class FunctionRootVoid extends FunctionRoot {

        public FunctionRootVoid(MGBaseEnv env, MGNodeUserFunction function, FrameDescriptor frameDescriptor) {
            super(env, function, null, frameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return body.execute(frame);
            } catch (MGTReturnException e) {
                // pass
            }
            return null;
        }

    }

}
