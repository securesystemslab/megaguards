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
package edu.uci.megaguards.analysis.precheck;

import java.util.HashSet;
import java.util.Stack;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.ast.node.MGNode;

public abstract class MGPreCheck<T> {

    protected final HashSet<String> varTable;
    protected Stack<T> forNodes;
    protected boolean processInductionVariable;
    protected boolean allowReturn;

    public MGPreCheck(boolean allowReturn) {
        this.varTable = new HashSet<>();
        this.forNodes = new Stack<>();
        this.processInductionVariable = false;
        this.allowReturn = allowReturn;
    }

    @TruffleBoundary
    public HashSet<String> getVarTable() {
        return varTable;
    }

    protected MGNode NotSupported(T node) {
        throw CoverageException.INSTANCE.message("<" + node.getClass().getSimpleName() + ":NOT SUPPORTED>");
    }

    public abstract MGPreCheck<T> create(boolean allowBodyReturn);

    public abstract void check(T node);
}
