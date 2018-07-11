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
package edu.uci.megaguards.ast.env;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.exception.LoopException;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNodeUserFunction;
import edu.uci.megaguards.ast.node.MGNodeMathFunction.MathFunctionType;

public class MGPrivateEnv extends MGBaseEnv {

    protected MGNodeUserFunction function;

    protected MGGlobalEnv globalEnv;

    public MGPrivateEnv(String functionID, String typeTag, MGGlobalEnv globalEnv) {
        super(functionID, typeTag);
        this.globalEnv = globalEnv;
    }

    @Override
    public boolean isGlobalEnv() {
        return false;
    }

    @Override
    public MGGlobalEnv getGlobalEnv() {
        return globalEnv;
    }

    public void setFunction(MGNodeUserFunction function) {
        this.function = function;
    }

    public MGNodeUserFunction getFunction() {
        return function;
    }

    @Override
    @TruffleBoundary
    public void addLoopInfo(LoopInfo info) throws LoopException {
        super.addLoopInfo(info);
        if (loopInfos.size() > MGOptions.FunctionLoopsLimit)
            throw LoopException.INSTANCE.message(String.format("Reached the maximum threshold limit (%d) in function '%s'", MGOptions.FunctionLoopsLimit, functionID));
    }

    @Override
    public String addNameTag(String name) {
        String tag = "$" + this.functionID;
        if (name.contains(tag))
            return name;
        return name + tag;
    }

    @Override
    public void addUsedMathFunction(MathFunctionType name) {
        this.globalEnv.getUsedMathFunctions().add(name);
    }

}
