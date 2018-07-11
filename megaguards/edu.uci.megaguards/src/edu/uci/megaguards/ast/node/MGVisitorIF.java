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

import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalBarrier;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalSize;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGroupID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGroupSize;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalBarrier;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalSize;

public interface MGVisitorIF<T> {

    public T visitOperandComplex(MGNodeOperandComplex node);

    public T visitOperand(MGNodeOperand node);

    public T visitAssignComplex(MGNodeAssignComplex node);

    public T visitAssign(MGNodeAssign node);

    public T visitUnaryOp(MGNodeUnaryOp node);

    public T visitBinOp(MGNodeBinOp node);

    public T visitBlock(MGNodeBlock node);

    public T visitBreak(MGNodeBreak node);

    public T visitJumpFrom(MGNodeJumpFrom node);

    public T visitJumpTo(MGNodeJumpTo node);

    public T visitBreakElse(MGNodeBreakElse node);

    public T visitBuiltinFunction(MGNodeBuiltinFunction node);

    public T visitFor(MGNodeFor node);

    public T visitWhile(MGNodeWhile node);

    public T visitIf(MGNodeIf node);

    public T visitMathFunction(MGNodeMathFunction node);

    public T visitEmpty(MGNodeEmpty node);

    public T visitFunctionCall(MGNodeFunctionCall node);

    public T visitReturn(MGNodeReturn node);

    // Special Nodes

    public T visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node);

    public T visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node);

    public T visitParallelNodeLocalID(ParallelNodeLocalID node);

    public T visitParallelNodeLocalSize(ParallelNodeLocalSize node);

    public T visitParallelNodeGroupID(ParallelNodeGroupID node);

    public T visitParallelNodeGroupSize(ParallelNodeGroupSize node);

    public T visitParallelNodeGlobalID(ParallelNodeGlobalID node);

    public T visitParallelNodeGlobalSize(ParallelNodeGlobalSize node);

}
