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

import edu.uci.megaguards.object.DataType;

public abstract class MGNodeSpecial extends MGNode {

    public MGNodeSpecial() {
        this.expectedType = DataType.None;
    }

    public static class ParallelNodeLocalBarrier extends MGNodeSpecial {

        @Override
        public MGNode copy() {
            return new ParallelNodeLocalBarrier();
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeLocalBarrier(this);
        }
    }

    public static class ParallelNodeGlobalBarrier extends MGNodeSpecial {

        @Override
        public MGNode copy() {
            return new ParallelNodeLocalBarrier();
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeGlobalBarrier(this);
        }
    }

    public static class ParallelNodeLocalID extends MGNodeSpecial {
        private final int dim;

        public ParallelNodeLocalID(int dim) {
            this.dim = dim;
            this.expectedType = DataType.Int;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public MGNode copy() {
            return new ParallelNodeLocalID(dim);
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeLocalID(this);
        }
    }

    public static class ParallelNodeLocalSize extends MGNodeSpecial {
        private final int dim;

        public ParallelNodeLocalSize(int dim) {
            this.dim = dim;
            this.expectedType = DataType.Int;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public MGNode copy() {
            return new ParallelNodeLocalSize(dim);
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeLocalSize(this);
        }
    }

    public static class ParallelNodeGroupID extends MGNodeSpecial {
        private final int dim;

        public ParallelNodeGroupID(int dim) {
            this.dim = dim;
            this.expectedType = DataType.Int;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public MGNode copy() {
            return new ParallelNodeGroupID(dim);
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeGroupID(this);
        }
    }

    public static class ParallelNodeGroupSize extends MGNodeSpecial {
        private final int dim;

        public ParallelNodeGroupSize(int dim) {
            this.dim = dim;
            this.expectedType = DataType.Int;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public MGNode copy() {
            return new ParallelNodeGroupSize(dim);
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeGroupSize(this);
        }
    }

    public static class ParallelNodeGlobalID extends MGNodeSpecial {
        private final int dim;

        public ParallelNodeGlobalID(int dim) {
            this.dim = dim;
            this.expectedType = DataType.Int;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public MGNode copy() {
            return new ParallelNodeGlobalID(dim);
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeGlobalID(this);
        }
    }

    public static class ParallelNodeGlobalSize extends MGNodeSpecial {
        private final int dim;

        public ParallelNodeGlobalSize(int dim) {
            this.dim = dim;
            this.expectedType = DataType.Int;
        }

        public int getDim() {
            return dim;
        }

        @Override
        public MGNode copy() {
            return new ParallelNodeGlobalSize(dim);
        }

        @Override
        public <R> R accept(MGVisitorIF<R> visitor) {
            return visitor.visitParallelNodeGlobalSize(this);
        }
    }

}
