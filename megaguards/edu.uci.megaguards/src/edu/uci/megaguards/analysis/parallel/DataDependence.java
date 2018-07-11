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
package edu.uci.megaguards.analysis.parallel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.analysis.parallel.ArrayAccesses.ArrayAccess;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.env.MGPrivateEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeFor;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.unbox.Unboxer;

public abstract class DataDependence {

    public static final int Initial = 0;
    public static final int Independent = 1;
    public static final int EqualDependent = 2;
    public static final int TrueDependent = 3;
    public static final int NotSupported = 4;

    protected LoopInfo loopInfo;
    protected MGNode forBody;
    protected HashMap<String, Long> constants;
    protected HashMap<Integer, String> arraysRefs;
    protected HashMap<String, Boolean> processedArrays;

    protected String ddreason;

    // Added for AthenaPetTest
    protected HashMap<String, MGNode> loops;
    protected HashMap<String, MGNode> taggedStmts;

    protected FinalizedVariableValues finalizedValues;

    protected DataDependence(MGNode forBody, LoopInfo info, MGGlobalEnv env, FinalizedVariableValues finalizedValues) {
        this.forBody = forBody;
        this.loopInfo = info;
        this.constants = new HashMap<>(env.getConstantIntVars());
        for (MGPrivateEnv p : env.getPrivateEnvironments().values()) {
            this.constants.putAll(p.getConstantIntVars());
        }
        this.arraysRefs = new HashMap<>();
        this.processedArrays = new HashMap<>();
        this.loops = new HashMap<>();
        this.taggedStmts = new HashMap<>();
        this.ddreason = "";
        this.finalizedValues = finalizedValues;
    }

    @TruffleBoundary
    protected static String s(String s) {
        return s + "_" + System.nanoTime();
    }

    protected static String s(SourceSection section) {
        if (section != null) {
            return "_L<" + section.getStartLine() + ">_" + System.nanoTime();
        }
        return "_" + System.nanoTime();
    }

    @TruffleBoundary
    protected String s(MGNode node) {
        SourceSection s = node.getSource();
        if (s == null)
            return null;
        String tag = null;
        if (node instanceof MGNodeFor)
            tag = "for";
        else
            tag = "S";
        tag += s(node.getSource());
        taggedStmts.put(tag, node);
        return tag;
    }

    @TruffleBoundary
    public boolean checkArrayReference(MGArray array) {
        String varname = array.getName();
        if (processedArrays.containsKey(varname))
            return true;
        else
            processedArrays.put(varname, true);

        switch (array.getDataType()) {
            case BoolArray:
                if (array.getDim() == 1) {
                    boolean[] values = (boolean[]) array.getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                } else {
                    boolean[][] values = (boolean[][]) ((Unboxer) array.getValue()).getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                    else
                        arraysRefs.put(values.hashCode(), varname);
                    for (boolean[] v : values) {
                        if (arraysRefs.containsKey(v.hashCode()))
                            return false;
                        else
                            arraysRefs.put(v.hashCode(), varname);
                    }
                }
                break;

            case IntArray:
                if (array.getDim() == 1) {
                    int[] values = (int[]) array.getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                } else {
                    int[][] values = (int[][]) ((Unboxer) array.getValue()).getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                    else
                        arraysRefs.put(values.hashCode(), varname);
                    for (int[] v : values) {
                        if (arraysRefs.containsKey(v.hashCode()))
                            return false;
                        else
                            arraysRefs.put(v.hashCode(), varname);
                    }
                }
                break;

            case LongArray:
                if (array.getDim() == 1) {
                    long[] values = (long[]) array.getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                } else {
                    long[][] values = (long[][]) ((Unboxer) array.getValue()).getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                    else
                        arraysRefs.put(values.hashCode(), varname);
                    for (long[] v : values) {
                        if (arraysRefs.containsKey(v.hashCode()))
                            return false;
                        else
                            arraysRefs.put(v.hashCode(), varname);
                    }
                }
                break;

            case DoubleArray:
                if (array.getDim() == 1) {
                    double[] values = (double[]) array.getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                } else {
                    double[][] values = (double[][]) ((Unboxer) array.getValue()).getValue();
                    if (arraysRefs.containsKey(values.hashCode()))
                        return false;
                    else
                        arraysRefs.put(values.hashCode(), varname);
                    for (double[] v : values) {
                        if (arraysRefs.containsKey(v.hashCode()))
                            return false;
                        else
                            arraysRefs.put(v.hashCode(), varname);
                    }
                }
                break;
        }
        return true;
    }

    @TruffleBoundary
    private MGObject verifyArrayReferences(MGBaseEnv env) {
        for (Entry<String, ArrayAccess> var : env.getArrayReadWrite().getReadWrites().entrySet()) {
            ArrayList<MGArray> reads = env.getArrayReadWrite().getReads(var.getKey());
            ArrayList<MGArray> writes = env.getArrayReadWrite().getWrites(var.getKey());
            if (reads.size() > 0 && writes.size() > 0) {
                for (MGArray write : writes) {
                    for (MGArray read : reads) {
                        if (!checkArrayReference(write))
                            return write;
                        if (!checkArrayReference(read))
                            return read;
                    }
                }
            }
        }

        return null;

    }

    @TruffleBoundary
    public boolean testArrayReferences(MGBaseEnv env) {
        MGObject r = verifyArrayReferences(env);
        boolean test = r == null;
        if (!test)
            this.ddreason = "Data dependence failed for variable '" + r.getName() + "' due to reference collision";

        return test;
    }

    public abstract boolean testDependence(String name, SourceSection source) throws MGException;

    public abstract boolean testDependence(MGNode node) throws MGException;

    public String getReason() {
        return this.ddreason;
    }

    @TruffleBoundary
    public boolean isIndependent(MGNode fornode) {
        if (MGOptions.Backend.AthenaPet)
            return !loops.containsKey(s(fornode));
        return false;
    }

}
