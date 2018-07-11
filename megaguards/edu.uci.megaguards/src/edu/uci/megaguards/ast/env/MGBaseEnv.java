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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.analysis.exception.TypeException;
import edu.uci.megaguards.analysis.parallel.ArrayAccesses;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeIf;
import edu.uci.megaguards.ast.node.MGNodeMathFunction;
import edu.uci.megaguards.ast.node.MGNodeMathFunction.MathFunctionType;
import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.object.ArrayInfo;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGBool;
import edu.uci.megaguards.object.MGBoolArray;
import edu.uci.megaguards.object.MGComplex;
import edu.uci.megaguards.object.MGDouble;
import edu.uci.megaguards.object.MGDoubleArray;
import edu.uci.megaguards.object.MGInt;
import edu.uci.megaguards.object.MGIntArray;
import edu.uci.megaguards.object.MGLong;
import edu.uci.megaguards.object.MGLongArray;
import edu.uci.megaguards.object.MGStorage;
import edu.uci.megaguards.unbox.Boxed;
import edu.uci.megaguards.unbox.StaticBoxed;
import edu.uci.megaguards.unbox.StaticUnboxer;
import edu.uci.megaguards.unbox.Unboxer;

public abstract class MGBaseEnv {

    public static final String DIMSIZE = "_$DS$";
    public static final String REPLACETAG = "$replace$";
    protected static int idPrefix = 0;

    protected static final HashMap<String, MathFunctionType> mathFun = populateMathFunctions();

    protected final HashSet<MathFunctionType> usedMathFunctions;

    // for data dependence
    protected final HashMap<String, Long> constantVars;

    protected final ArrayList<MGNode> arrayaccesses;
    protected final ArrayList<MGNode> overflowCheck;
    protected final HashMap<String, MGStorage> varTable;
    protected final HashMap<String, DataType> localVarTable;
    protected final HashMap<String, String> localVarReplacement;
    protected final ArrayList<HashMap<String, ArrayList<MGNode>>> defUseScop;
    protected final HashSet<Integer> skipScop;
    protected final ArrayList<LoopInfo> loopInfos;
    protected final ArrayAccesses arrayReadWrite;

    protected final Map<String, MGStorage> parameters;
    protected final Map<Integer, Unboxer> arrays;
    protected final String functionID;
    protected final String typeTag;

    // for map function
    protected int numLocalParams;

    protected MGBaseEnv(String functionName, String typeTag) {

        this.usedMathFunctions = new HashSet<>();
        this.constantVars = new HashMap<>();

        this.arrayReadWrite = new ArrayAccesses();
        this.arrayaccesses = new ArrayList<>();
        this.overflowCheck = new ArrayList<>();
        this.varTable = new HashMap<>();
        this.localVarTable = new HashMap<>();
        this.localVarReplacement = new HashMap<>();
        this.defUseScop = new ArrayList<>();
        this.skipScop = new HashSet<>();
        this.loopInfos = new ArrayList<>();

        this.parameters = new HashMap<>();
        this.arrays = new HashMap<>();
        this.numLocalParams = 0;
        this.functionID = functionName;
        this.typeTag = typeTag;
    }

    protected MGBaseEnv(MGGlobalEnv env, String tag) {

        this.usedMathFunctions = env.usedMathFunctions;
        this.constantVars = env.constantVars;

        this.arrayReadWrite = new ArrayAccesses();
        this.arrayaccesses = env.arrayaccesses;
        this.overflowCheck = env.overflowCheck;
        this.varTable = new HashMap<>();
        this.localVarTable = new HashMap<>();
        this.localVarReplacement = new HashMap<>();
        this.defUseScop = env.defUseScop;
        this.skipScop = env.skipScop;
        this.loopInfos = new ArrayList<>();

        this.parameters = new HashMap<>();
        this.arrays = new HashMap<>();
        this.numLocalParams = 0;
        this.functionID = env.functionID + tag;
        this.typeTag = env.typeTag;
    }

    @TruffleBoundary
    private static HashMap<String, MathFunctionType> populateMathFunctions() {
        HashMap<String, MathFunctionType> mathFunctions = new HashMap<>();
        for (int i = 0; i < MGNodeMathFunction.mathFuncStrings.length; i++)
            mathFunctions.put(MGNodeMathFunction.mathFuncStrings[i], MGNodeMathFunction.mathsOpKind[i]);
        return mathFunctions;
    }

    public HashMap<String, DataType> getLocalVarTable() {
        return localVarTable;
    }

    @TruffleBoundary
    public MathFunctionType getMathFunction(String math) {
        if (mathFun.containsKey(math))
            return mathFun.get(math);
        else
            return null;
    }

    public static void addMathFunctions(String funcName, MathFunctionType type) {
        mathFun.put(funcName, type);
    }

    public ArrayAccesses getArrayReadWrite() {
        return arrayReadWrite;
    }

    public HashSet<MathFunctionType> getUsedMathFunctions() {
        return usedMathFunctions;
    }

    public String getFunctionID() {
        return functionID;
    }

    @TruffleBoundary
    public static String createTempName() {
        idPrefix++;
        return "_temp$" + idPrefix;
    }

    @TruffleBoundary
    public Map<String, MGStorage> getParameters() {
        return parameters;
    }

    @TruffleBoundary
    public Unboxer getArrayUnboxer(int hashCode) {
        return arrays.get(hashCode);
    }

    @TruffleBoundary
    public String[] getOrderedParameters() {
        String[] orderedParameters = new String[parameters.size()];
        parameters.keySet().toArray(orderedParameters);
        Arrays.sort(orderedParameters);
        return orderedParameters;
    }

    public ArrayList<LoopInfo> getExistingLoopInfos() {
        return loopInfos;
    }

    @TruffleBoundary
    public HashMap<String, ArrayList<MGNode>> getDefUse() {
        assert defUseScop.size() == 1;
        return defUseScop.get(0);
    }

    @TruffleBoundary
    public void enterScop() {
        defUseScop.add(new HashMap<>());
    }

    // if/else
    @TruffleBoundary
    public void exitScop() {
        int size = defUseScop.size();
        assert size > 2;
        // then
        final HashMap<String, ArrayList<MGNode>> phi = new HashMap<>();
        for (Entry<String, ArrayList<MGNode>> var : defUseScop.get(size - 2).entrySet()) {
            if (defUseScop.get(size - 3).containsKey(var.getKey())) {
                defUseScop.get(size - 3).get(var.getKey()).addAll(var.getValue());
            } else {
                defUseScop.get(size - 3).put(var.getKey(), var.getValue());
            }
            if (defUseScop.get(size - 1).containsKey(var.getKey())) {
                ArrayList<MGNode> elseScop = defUseScop.get(size - 1).get(var.getKey());
                final int thenSize = var.getValue().size();
                final int elseSize = elseScop.size();
                phi.put(var.getKey(), new ArrayList<>());
                final MGNode defUse = new MGNodeIf(var.getValue().get(thenSize - 1), elseScop.get(elseSize - 1));
                phi.get(var.getKey()).add(defUse);
            }
        }

        // else
        for (Entry<String, ArrayList<MGNode>> var : defUseScop.get(size - 1).entrySet()) {
            if (defUseScop.get(size - 3).containsKey(var.getKey())) {
                defUseScop.get(size - 3).get(var.getKey()).addAll(var.getValue());
            } else {
                defUseScop.get(size - 3).put(var.getKey(), var.getValue());
            }
        }

        for (Entry<String, ArrayList<MGNode>> var : phi.entrySet()) {
            defUseScop.get(size - 3).get(var.getKey()).addAll(var.getValue());
        }

        defUseScop.remove(size - 1);
        defUseScop.remove(size - 2);
        skipScop.remove(size - 1);
        skipScop.remove(size - 2);
    }

    @TruffleBoundary
    public void addDefUse(String name, MGNode rhs) {
        if (!defUseScop.get(defUseScop.size() - 1).containsKey(name)) {
            defUseScop.get(defUseScop.size() - 1).put(name, new ArrayList<>());
        }
        defUseScop.get(defUseScop.size() - 1).get(name).add(rhs);
    }

    @TruffleBoundary
    public void addThenSkip() {
        skipScop.add(defUseScop.size() - 1);
    }

    @TruffleBoundary
    protected int defUseLookup(String name, boolean skip) {
        int size = 0;
        for (int i = 0; i < defUseScop.size(); i++) {
            if (skip) {
                if (skipScop.contains(i)) {
                    continue;
                }
            }
            if (defUseScop.get(i).containsKey(name)) {
                size += defUseScop.get(i).get(name).size();
            }
        }
        return size;
    }

    @TruffleBoundary
    public int getDefUseIndex(String name) {
        if (!defUseScop.get(defUseScop.size() - 1).containsKey(name)) {
            return defUseLookup(name, true) - 1;
        }
        return defUseLookup(name, false) - 1;
    }

    @TruffleBoundary
    public MGStorage getVar(String varname) {
        String name = addNameTag(varname);
        if (localVarReplacement.containsKey(name)) {
            name = localVarReplacement.get(name);
        }
        return varTable.get(name);
    }

    @TruffleBoundary
    public String createVarReplacementName(String name) {
        String replacement = name + REPLACETAG + ++idPrefix;
        localVarReplacement.put(name, replacement);
        return replacement;
    }

    public HashMap<String, Long> getConstantIntVars() {
        return constantVars;
    }

    @TruffleBoundary
    public void addArrayAccess(MGNode arrayAccess) {
        arrayaccesses.add(arrayAccess);
    }

    public ArrayList<MGNode> getArrayaccesses() {
        return arrayaccesses;
    }

    @TruffleBoundary
    public void addOFCheck(MGNode node) {
        overflowCheck.add(node);
    }

    public ArrayList<MGNode> getOverflowCheck() {
        return overflowCheck;
    }

    @TruffleBoundary
    public MGStorage registerVar(String name, MGStorage var) {
        varTable.put(name, var);
        return var;
    }

    @TruffleBoundary
    public void registerLocalVar(String name, DataType type) {
        localVarTable.put(name, type);
    }

    @TruffleBoundary
    public void registerParameter(String name, MGStorage var) {
        parameters.put(name, var);
    }

    @TruffleBoundary
    private void registerLocalVar(MGStorage var) {
        registerLocalVar(var.getName(), var.getDataType());
    }

    @TruffleBoundary
    public MGStorage registerVar(MGStorage var) {
        registerVar(var.getName(), var);
        registerLocalVar(var);
        return var;
    }

    public void registerParameter(MGStorage var) {
        registerParameter(var.getName(), var);
    }

    @TruffleBoundary
    private MGArray createArrayDimSizeVar(MGArray var) {
        if (var != null) {
            final ArrayInfo info = var.getArrayInfo();
            for (int i = 0; i < info.getDim(); i++) {
                final StaticUnboxer.IntDimSize dim = new StaticUnboxer.IntDimSize(info, i);
                final StaticBoxed box = new StaticBoxed(dim);
                final String dimSizeName = var.getName() + DIMSIZE + i;
                final MGInt variable = new MGInt(dimSizeName, box.getUnboxed(null), box);
                registerParameter(dimSizeName, variable);
                registerVar(dimSizeName, variable);
            }
        }
        return var;
    }

    protected MGArray createArray(String name, Boxed<?> valueNode) {
        final Unboxer boxed = valueNode.getUnboxer();

        if (boxed == null) {
            throw TypeException.INSTANCE.message("variable '" + name + "' data type is not supported!");
        }

        arrays.put(boxed.getValue().hashCode(), boxed);

        final MGArray var;
        switch (boxed.getKind()) {
            case IntArray:
                var = new MGIntArray(name, boxed, valueNode);
                break;
            case LongArray:
                var = new MGLongArray(name, boxed, valueNode);
                break;
            case DoubleArray:
                var = new MGDoubleArray(name, boxed, valueNode);
                break;
            case BoolArray:
                var = new MGBoolArray(name, boxed, valueNode);
                break;
            default:
                var = null;
        }
        return createArrayDimSizeVar(var);
    }

    protected MGArray createArray(String name, DataType type, ParallelWorkload storage) {
        MGArray var = null;
        switch (type) {
            case Int:
                var = new MGIntArray(name, storage);
                break;
            case Long:
                var = new MGLongArray(name, storage);
                break;
            case Double:
                var = new MGDoubleArray(name, storage);
                break;
            case Bool:
                var = new MGBoolArray(name, storage);
                break;
        }
        return var;
    }

    @TruffleBoundary
    public MGBool registerBoolVar(String n) {
        String name = n;
        if (name == null)
            name = createTempName();
        name = addNameTag(name);
        MGBool variable = new MGBool(name);
        registerLocalVar(name, DataType.Bool);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGInt registerIntArrayDimSize(String n, String dimSize) {
        String name = n;
        if (name == null)
            name = createTempName();
        name = addNameTag(name) + dimSize;
        MGInt variable = new MGInt(name);
        registerLocalVar(name, DataType.Int);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGInt registerIntVar(String n) {
        String name = n;
        if (name == null)
            name = createTempName();
        name = addNameTag(name);
        MGInt variable = new MGInt(name);
        registerLocalVar(name, DataType.Int);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGLong registerLongVar(String n) {
        String name = addNameTag(n);
        MGLong variable = new MGLong(name);
        registerLocalVar(name, DataType.Long);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGDouble registerDoubleVar(String n) {
        String name = addNameTag(n);
        getGlobalEnv().setRequireDouble(true);
        MGDouble variable = new MGDouble(name);
        registerLocalVar(name, DataType.Double);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage registerIntArrayVar(String n, MGArray variable) {
        String name = addNameTag(n);
        MGArray var = variable;
        if (!variable.isPropagated()) {
            // Must be a single dimensional array assignment
            int origDim = variable.getArrayInfo().getDim();
            int newDim = origDim - variable.getIndicesLen();
            // TODO: deal with flexible dimension sizes
            final Class<?> type;
            if (newDim == 1)
                type = int[].class;
            else if (newDim >= 2)
                type = int[][].class;
            else
                type = int.class;

            ArrayInfo info = new ArrayInfo(type, variable.getArrayInfo().getSize(origDim - 1));
            var = new MGIntArray(name, variable, info);
        }

        return registerVar(name, var);
    }

    @TruffleBoundary
    public MGStorage registerLongArrayVar(String n, MGArray variable) {
        String name = addNameTag(n);
        MGArray var = variable;
        if (!variable.isPropagated()) {
            // Must be a single dimensional array assignment
            int origDim = variable.getArrayInfo().getDim();
            int newDim = origDim - variable.getIndicesLen();
            // TODO: deal with flexible dimension sizes
            final Class<?> type;
            if (newDim == 1)
                type = long[].class;
            else if (newDim >= 2)
                type = long[][].class;
            else
                type = long.class;

            ArrayInfo info = new ArrayInfo(type, variable.getArrayInfo().getSize(origDim - 1));
            var = new MGLongArray(name, variable, info);
        }

        return registerVar(name, var);
    }

    @TruffleBoundary
    public MGStorage registerDoubleArrayVar(String n, MGArray variable) {
        String name = addNameTag(n);
        getGlobalEnv().setRequireDouble(true);
        MGArray var = variable;
        if (!variable.isPropagated()) {
            // Must be a single dimensional array assignment
            int origDim = variable.getArrayInfo().getDim();
            int newDim = origDim - variable.getIndicesLen();
            // TODO: deal with flexible dimension sizes
            final Class<?> type;
            if (newDim == 1)
                type = double[].class;
            else if (newDim >= 2)
                type = double[][].class;
            else
                type = double.class;

            ArrayInfo info = new ArrayInfo(type, variable.getArrayInfo().getSize(origDim - 1));
            var = new MGDoubleArray(name, variable, info);
        }

        return registerVar(name, var);
    }

    @TruffleBoundary
    public MGStorage registerComplexVar(String n) {
        String name = addNameTag(n);
        // Real
        getGlobalEnv().setRequireDouble(true);
        String nameReal = name + "_" + idPrefix + "_$real";

        MGDouble real = new MGDouble(nameReal);

        // Imaginary
        String nameImag = name + "_" + idPrefix + "_$imag";

        MGDouble imag = new MGDouble(nameImag);

        MGComplex variable = new MGComplex(name, real, imag);

        idPrefix++;
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public boolean reloadConstantLongValues() {
        for (Entry<String, Long> var : constantVars.entrySet()) {
            if (!varTable.containsKey(var.getKey()))
                return false;
            Object value = varTable.get(var.getKey()).getValue();
            if (value instanceof Integer)
                value = BigInteger.valueOf((int) value).longValue();

            constantVars.put(var.getKey(), (Long) value);
        }
        return true;
    }

    public int getNumLocalParams() {
        return numLocalParams;
    }

    @TruffleBoundary
    public MGStorage[] getStorageList() {
        final MGStorage[] list = new MGStorage[parameters.size() - numLocalParams];
        int i = 0;
        ArrayList<String> sort = new ArrayList<>(parameters.keySet());
        final String[] strparameters = new String[parameters.size()];
        final String[] sorted = sort.toArray(strparameters);
        Arrays.sort(sorted);
        for (String var : sorted) {
            final MGStorage s = parameters.get(var);
            if (s instanceof MGArray && ((MGArray) s).getValue() instanceof ParallelWorkload)
                continue;
            list[i++] = s;
        }
        return list;
    }

    @TruffleBoundary
    public void addLoopInfo(LoopInfo info) {
        loopInfos.add(info);
    }

    @TruffleBoundary
    public void clearValues() {
        for (MGStorage var : parameters.values()) {
            if (var instanceof MGArray && ((MGArray) var).getValue() instanceof ParallelWorkload)
                continue;
            var.getClearValue();
        }
    }

    public String addNameTag(String name) {
        return name;
    }

    @TruffleBoundary
    public void addUsedMathFunction(MathFunctionType name) {
        this.usedMathFunctions.add(name);
    }

    @TruffleBoundary
    public MGInt addIntParameter(String n, int value, Boxed<?> box) {
        String name = n;
        if (name == null)
            name = createTempName();

        name = addNameTag(name);
        if (varTable.containsKey(name))
            return (MGInt) varTable.get(name);

        constantVars.put(name, (long) value);

        final MGInt variable = new MGInt(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage addLongParameter(String n, long value, Boxed<?> box) {
        String name = addNameTag(n);
        if (varTable.containsKey(name))
            return varTable.get(name);

        constantVars.put(name, value);
        final MGLong variable = new MGLong(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage addDoubleParameter(String n, double value, Boxed<?> box) {
        String name = addNameTag(n);
        if (varTable.containsKey(name))
            return varTable.get(name);

        final MGDouble variable = new MGDouble(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage addBooleanParameter(String n, boolean value, Boxed<?> box) {
        String name = addNameTag(n);
        if (varTable.containsKey(name))
            return varTable.get(name);

        final MGBool variable = new MGBool(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage addComplexParameter(String n, double realv, Boxed<?> realBox, double imagv, Boxed<?> imagBox) {
        String name = addNameTag(n);
        if (varTable.containsKey(name))
            return varTable.get(name);

        // Real
        String nameReal = name + "_" + idPrefix + "_$real";
        final MGDouble real = new MGDouble(nameReal, realv, realBox);
        real.setDefine();
        registerParameter(real.getName(), real);

        // Imaginary
        String nameImag = name + "_" + idPrefix + "_$imag";
        final MGDouble imag = new MGDouble(nameImag, imagv, imagBox);
        imag.setDefine();
        registerParameter(imag.getName(), imag);

        final MGComplex variable = new MGComplex(name, real, imag);

        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGArray addArrayParameter(String n, Boxed<?> box) throws TypeException {
        String name = n;
        if (name == null)
            name = createTempName();

        name = addNameTag(name);

        if (varTable.containsKey(name))
            return (MGArray) varTable.get(name);

        if (box == null) {
            throw TypeException.INSTANCE.message("variable '" + n + "' data type is not supported!");
        }

        final MGArray variable = createArray(name, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGArray addInducedArrayParameter(String n, Boxed<?> box) throws TypeException {
        String name = n;
        if (name == null)
            name = createTempName();

        name = addNameTag(name);

        if (varTable.containsKey(name))
            return (MGArray) varTable.get(name);

        final MGArray variable = createArray(name, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGInt addInducedIntParameter(String n, int value, Boxed<?> box) {
        String name = n;
        if (name == null)
            name = createTempName();

        name = addNameTag(name);
        if (varTable.containsKey(name))
            return (MGInt) varTable.get(name);

        constantVars.put(name, (long) value);
        final MGInt variable = new MGInt(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage addInducedLongParameter(String n, long value, Boxed<?> box) {
        String name = addNameTag(n);
        if (varTable.containsKey(name))
            return varTable.get(name);

        constantVars.put(name, value);
        MGLong variable = null;

        variable = new MGLong(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGStorage addInducedDoubleParameter(String n, double value, Boxed<?> box) {
        String name = addNameTag(n);
        if (varTable.containsKey(name))
            return varTable.get(name);

        final MGDouble variable = new MGDouble(name, value, box);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    @TruffleBoundary
    public MGArray createAdjustableArray(String n, DataType type, ParallelWorkload storage, boolean isWriteOnly, boolean isLocal) throws TypeException {
        numLocalParams++;

        String name = n;
        if (name == null)
            name = createTempName();

        name = addNameTag(name);

        final MGArray variable = createArray(name, type, storage);
        variable.setReadWrite();
        variable.setWriteOnly(isWriteOnly).setLocal(isLocal).setNoBounds(true);

        registerParameter(variable.getName(), variable);
        registerVar(name, variable);
        return variable;
    }

    public abstract boolean isGlobalEnv();

    public abstract MGGlobalEnv getGlobalEnv();

}
