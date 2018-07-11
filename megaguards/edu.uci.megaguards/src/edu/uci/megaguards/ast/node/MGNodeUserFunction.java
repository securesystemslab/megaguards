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
import java.util.HashMap;

import edu.uci.megaguards.ast.env.MGPrivateEnv;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGStorage;

public class MGNodeUserFunction extends MGNode {

    private final String functionID;
    private final String functionName;
    private final ArrayList<MGStorage> parameters;
    private final HashMap<String, MGStorage> hashParameters;
    private final MGNode body;
    private final MGPrivateEnv privateEnv;
    private final ArrayList<MGNodeFunctionCall> calls;
    private final MGNodeOperand returnVar;

    public MGNodeUserFunction(String functionID, String functionName, ArrayList<MGStorage> parameters, MGNode body, MGNodeOperand returnVar, DataType type,
                    MGPrivateEnv privateEnv) {
        this.functionID = functionID;
        this.functionName = functionName;
        this.parameters = parameters;
        this.hashParameters = new HashMap<>();
        for (MGStorage s : parameters) {
            hashParameters.put(s.getName(), s);
        }
        this.body = body.setParent(this);
        this.returnVar = returnVar;
        this.expectedType = type;
        this.privateEnv = privateEnv;
        this.calls = new ArrayList<>();
    }

    public String getFunctionID() {
        return functionID;
    }

    public String getFunctionName() {
        return functionName;
    }

    public ArrayList<MGStorage> getParameters() {
        return parameters;
    }

    public HashMap<String, MGStorage> getHashParameters() {
        return hashParameters;
    }

    public MGNode getBody() {
        return body;
    }

    public MGNodeOperand getReturnVar() {
        return returnVar;
    }

    public MGPrivateEnv getPrivateEnv() {
        return privateEnv;
    }

    public ArrayList<MGNodeFunctionCall> getCalls() {
        return calls;
    }

    @Override
    public MGNode copy() {
        return new MGNodeUserFunction(functionID, functionName, parameters, body.copy(), getReturnVar(), expectedType, getPrivateEnv());
    }

    @Override
    public <R> R accept(MGVisitorIF<R> visitor) {
        return null;
    }

}
