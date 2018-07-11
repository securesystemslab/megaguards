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
import com.oracle.truffle.api.nodes.ExplodeLoop;

import edu.uci.megaguards.backend.truffle.exception.MGTBreakException;
import edu.uci.megaguards.backend.truffle.exception.MGTContinueException;
import edu.uci.megaguards.backend.truffle.exception.MGTOverflowException;
import edu.uci.megaguards.backend.truffle.exception.MGTReturnException;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;

public abstract class MGTControl extends MGTNode<Object> {

    public MGTControl(DataType t) {
        super(t);
    }

    public static final class Block extends MGTControl {

        @Children protected final MGTNode<?>[] nodes;

        public Block(MGTNode<?>[] nodes) {
            super(DataType.None);
            this.nodes = nodes;
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            for (MGTNode<?> n : nodes) {
                n.execute(frame);
            }
            return null;
        }
    }

    public static final class Args extends MGTControl {

        @Children protected final MGTNode<?>[] nodes;

        public Args(MGTNode<?>[] nodes) {
            super(DataType.None);
            this.nodes = nodes;
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            frame.materialize();
            for (MGTNode<?> n : nodes) {
                n.execute(frame);
            }
            return null;
        }
    }

    public static final class Break extends MGTControl {

        public Break() {
            super(DataType.None);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw MGTBreakException.INSTANCE;
        }
    }

    public static final class Continue extends MGTControl {

        public Continue() {
            super(DataType.None);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw MGTContinueException.INSTANCE;
        }

    }

    public static class EmptyNode extends MGTControl {

        public EmptyNode() {
            super(DataType.None);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }
    }

    public static final class Assign<T> extends MGTControl {

        @Child protected MGTOperand<T> left;
        @Child protected MGTNode<T> right;

        public Assign(MGTOperand<T> l, MGTNode<T> r) {
            super(DataType.None);
            this.left = l;
            this.right = r;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            left.executeWrite(frame, right.execute(frame));
            return null;
        }
    }

    public static class If extends MGTControl {

        @Child protected MGTNode<Object> then;
        @Child protected MGTNode<Boolean> cond;

        public If(MGTNode<Object> t, MGTNode<Boolean> c) {
            super(DataType.None);
            this.then = t;
            this.cond = c;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (cond.execute(frame))
                then.execute(frame);
            return null;
        }
    }

    public static final class IfElse extends If {

        @Child private MGTNode<Object> orelse;

        public IfElse(MGTNode<Object> t, MGTNode<Boolean> c, MGTNode<Object> e) {
            super(t, c);
            this.orelse = e;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (cond.execute(frame))
                then.execute(frame);
            else
                orelse.execute(frame);
            return null;
        }
    }

    public static final class ReturnValue<T> extends MGTControl {

        @Child private MGTOperand<T> ret;
        @Child private MGTNode<T> value;

        public ReturnValue(MGTOperand<T> ret, MGTNode<T> v) {
            super(DataType.None);
            this.ret = ret;
            this.value = v;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ret.executeWrite(frame, value.execute(frame));
            throw MGTReturnException.INSTANCE;
        }

    }

    public static final class ReturnVoid extends MGTControl {

        public ReturnVoid() {
            super(DataType.None);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw MGTReturnException.INSTANCE;
        }

    }

    public static final class NotifyChanges extends MGTControl {

        private final MGArray[] readWrites;

        public NotifyChanges(MGArray[] readWrites) {
            super(DataType.None);
            this.readWrites = readWrites;
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            for (MGArray array : readWrites) {
                array.getBoxed().setChanged(true);
            }
            return null;
        }
    }

    public static final class MainBlock extends MGTControl {

        @Child private MGTControl paramBlock;
        @Child private MGTControl backupsBlock;
        @Child private MGTNode<?> body;
        @Child private MGTNode<?> getdata;
        @Child private MGTControl nc;
        @Child private MGTNode<?> rollbacks;

        public MainBlock(MGTControl paramBlock, MGTControl backupsBlock, MGTNode<?> body, MGTNode<?> getdata, MGTControl nc, MGTNode<?> rollbacks) {
            super(DataType.None);
            this.paramBlock = paramBlock;
            this.backupsBlock = backupsBlock;
            this.body = body;
            this.getdata = getdata;
            this.nc = nc;
            this.rollbacks = rollbacks;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                paramBlock.execute(frame);
                backupsBlock.execute(frame);
                Object value = null;
                try {
                    value = body.execute(frame);
                } catch (MGTBreakException b) {
                    // pass
                }
                getdata.execute(frame);
                nc.execute(frame);
                return value;
            } catch (MGTOverflowException of) {
                rollbacks.execute(frame);
                throw of;
            }
        }

    }

}
