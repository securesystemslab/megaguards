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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

import edu.uci.megaguards.backend.truffle.exception.MGTBreakException;
import edu.uci.megaguards.object.DataType;

public abstract class MGTLoop extends MGTControl {

    public MGTLoop() {
        super(DataType.None);
    }

    @Child protected MGTNode<?> body;

    public static class For extends MGTLoop {
        @CompilationFinal @Child protected MGTOperand<Integer> inductionVar;
        @CompilationFinal @Child protected MGTNode<Integer> start;
        @CompilationFinal @Child protected MGTNode<Integer> step;
        @CompilationFinal @Child protected MGTNode<Integer> stop;

        public For(MGTOperand<Integer> inductionVar, MGTNode<Integer> start, MGTNode<Integer> stop, MGTNode<Integer> step, MGTNode<?> body) {
            this.inductionVar = inductionVar;
            this.start = start;
            this.step = step;
            this.stop = stop;
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int s = start.execute(frame);
            int t = step.execute(frame);
            int e = stop.execute(frame);
            for (; s < e; s += t) {
                inductionVar.executeWrite(frame, s);
                body.execute(frame);
            }

            return null;
        }

        public static final class ForBreak extends For {

            public ForBreak(MGTOperand<Integer> inductionVar, MGTNode<Integer> start, MGTNode<Integer> stop, MGTNode<Integer> step, MGTNode<?> body) {
                super(inductionVar, start, stop, step, body);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                int s = start.execute(frame);
                int t = step.execute(frame);
                int e = stop.execute(frame);
                try {
                    for (; s < e; s += t) {
                        inductionVar.executeWrite(frame, s);
                        body.execute(frame);
                    }
                } catch (MGTBreakException b) {
                    // pass
                }
                return null;
            }

        }

        public static final class ForBreakElse extends For {

            @CompilationFinal @Child private MGTNode<?> orelse;

            public ForBreakElse(MGTOperand<Integer> inductionVar, MGTNode<Integer> start, MGTNode<Integer> stop, MGTNode<Integer> step, MGTNode<?> body, MGTNode<?> orelse) {
                super(inductionVar, start, stop, step, body);
                this.orelse = orelse;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                int s = start.execute(frame);
                int t = step.execute(frame);
                int e = stop.execute(frame);
                try {
                    for (; s < e; s += t) {
                        inductionVar.executeWrite(frame, s);
                        body.execute(frame);
                    }
                    orelse.execute(frame);
                } catch (MGTBreakException b) {
                    // pass
                }

                return null;
            }

        }
    }

    public static final class WhileNode extends MGTLoop {
        @CompilationFinal @Child private MGTNode<Boolean> cond;

        public WhileNode(MGTNode<Boolean> cond, MGTNode<?> body) {
            this.cond = cond;
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame frame) {

            while (cond.execute(frame)) {
                body.execute(frame);
            }

            return null;
        }

    }
}
