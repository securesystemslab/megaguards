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
package edu.uci.megaguards.backend.parallel.opencl;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNode;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeAssign;
import edu.uci.megaguards.ast.node.MGNodeAssignComplex;
import edu.uci.megaguards.ast.node.MGNodeBinOp;
import edu.uci.megaguards.ast.node.MGNodeBinOp.BinOpType;
import edu.uci.megaguards.ast.node.MGNodeBlock;
import edu.uci.megaguards.ast.node.MGNodeBreak;
import edu.uci.megaguards.ast.node.MGNodeBreakElse;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction.BuiltinFunctionType;
import edu.uci.megaguards.ast.node.MGNodeEmpty;
import edu.uci.megaguards.ast.node.MGNodeFor;
import edu.uci.megaguards.ast.node.MGNodeFunctionCall;
import edu.uci.megaguards.ast.node.MGNodeIf;
import edu.uci.megaguards.ast.node.MGNodeJumpFrom;
import edu.uci.megaguards.ast.node.MGNodeJumpTo;
import edu.uci.megaguards.ast.node.MGNodeMathFunction;
import edu.uci.megaguards.ast.node.MGNodeMathFunction.MathFunctionType;
import edu.uci.megaguards.ast.node.MGNodeOperand;
import edu.uci.megaguards.ast.node.MGNodeOperandComplex;
import edu.uci.megaguards.ast.node.MGNodeReturn;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalBarrier;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGlobalSize;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGroupID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeGroupSize;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalBarrier;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalID;
import edu.uci.megaguards.ast.node.MGNodeSpecial.ParallelNodeLocalSize;
import edu.uci.megaguards.ast.node.MGNodeUnaryOp;
import edu.uci.megaguards.ast.node.MGNodeUnaryOp.UnaryOpType;
import edu.uci.megaguards.ast.node.MGNodeUserFunction;
import edu.uci.megaguards.ast.node.MGNodeWhile;
import edu.uci.megaguards.ast.node.MGVisitorIF;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGStorage;

public class OpenCLTranslator implements MGVisitorIF<String> {

    protected static int indent = 0;

    public final static String __local = "__local";

    public final static String __global = "__global";

    public final static String __constant = "__constant";

    public final static String __private = "__private";

    protected final static String OFFSET = "_offset$";

    protected final static String STEP = "_step$";

    // Array Bound Check
    public static final String BOUNDFLAG = "_boundFlag$";

    protected final static String boundCheckMethod = "_boundCheck$";

    // Arithmetic Overflow Check
    public static final String OVERFLOWFLAG = "_overflowFlag$";

    public static final String RETURN_ID = "_return$val";

    protected final static String license = "/*\n" + //
                    " * Copyright (c) 2018, Regents of the University of California\n" + //
                    " * All rights reserved.\n" + //
                    " *\n" + //
                    " * Redistribution and use in source and binary forms, with or without\n" + //
                    " * modification, are permitted provided that the following conditions are met:\n" + //
                    " *\n" + //
                    " * 1. Redistributions of source code must retain the above copyright notice, this\n" + //
                    " *    list of conditions and the following disclaimer.\n" + //
                    " * 2. Redistributions in binary form must reproduce the above copyright notice,\n" + //
                    " *    this list of conditions and the following disclaimer in the documentation\n" + //
                    " *    and/or other materials provided with the distribution." + //
                    " *\n" + //
                    " * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND\n" + //
                    " * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED\n" + //
                    " * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE\n" + //
                    " * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR\n" + //
                    " * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES\n" + //
                    " * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;\n" + //
                    " * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND\n" + //
                    " * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n" + //
                    " * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS\n" + //
                    " * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.\n" + //
                    " */\n";

    protected boolean runtimeBoundCheck;

    public boolean runtimeOFCheckAddInt;
    public final static String exactAddInt = "exactAdd$Int";

    public boolean runtimeOFCheckAddLong;
    public final static String exactAddLong = "exactAdd$Long";

    public boolean runtimeOFCheckMulInt;
    public final static String exactMulInt = "exactMul$Int";

    public boolean runtimeOFCheckMulLong;
    public final static String exactMulLong = "exactMul$Long";

    public boolean runtimeOFCheckSubInt;
    public final static String exactSubInt = "exactSub$Int";

    public boolean runtimeOFCheckSubLong;
    public final static String exactSubLong = "exactSub$Long";

    public boolean ModInt;
    public final static String ModIntStr = "mod$Int";

    public boolean ModLong;
    public final static String ModLongStr = "mod$Long";

    protected final MGGlobalEnv env;

    protected int levels;
    protected MGStorage[] iterVar;
    protected ArrayList<MGNodeUserFunction> localFunctions;
    protected String kernelFile;
    protected String kernelName;
    protected Map<String, MGStorage> parameters;
    protected String[] orderedParameters;

    protected ArrayList<String> methods;
    protected boolean requireDouble;

    private final MGLog log;
    private final HashSet<String> definedVars;
    private final HashSet<String> variablesDefinitions;

    public OpenCLTranslator(MGGlobalEnv env, //
                    Map<String, MGStorage> parameters, //
                    String[] orderedParameters, //
                    ArrayList<MGNodeUserFunction> localFunctions, //
                    String kernelName, String kernelFile, //
                    MGLog log) {
        this.env = env;
        this.parameters = parameters;
        this.orderedParameters = orderedParameters;
        this.kernelName = kernelName;
        this.kernelFile = kernelFile;
        this.localFunctions = localFunctions;
        levels = env.getIterationLevels();
        iterVar = env.getIteratorVar();

        requireDouble = env.isRequireDouble();
        runtimeBoundCheck = env.isRuntimeBoundCheck();

        methods = new ArrayList<>();
        this.log = log;
        this.definedVars = new HashSet<>();
        this.variablesDefinitions = new HashSet<>();

        runtimeBoundCheck = false;
        runtimeOFCheckAddInt = false;
        runtimeOFCheckAddLong = false;
        runtimeOFCheckMulInt = false;
        runtimeOFCheckMulLong = false;
        runtimeOFCheckSubInt = false;
        runtimeOFCheckSubLong = false;

        ModInt = false;
        ModLong = false;

    }

    @TruffleBoundary
    public void clearDefinitions() {
        definedVars.clear();
    }

    public static void in() {
        indent++;
    }

    public static void out() {
        indent--;
    }

    public static String newLine() {
        String s = "\n";
        for (int i = 0; i < indent; i++) {
            s += "   ";
        }
        return s;
    }

    @TruffleBoundary
    public String translateFunctionBody(MGNode body) {
        variablesDefinitions.clear();
        clearDefinitions();
        in();
        String opencl = visitor(body);
        String definitions = "";
        for (String s : variablesDefinitions)
            definitions += newLine() + s;
        out();
        variablesDefinitions.clear();
        return definitions + opencl;
    }

    public static String DataTypeOpenCL(DataType type) {
        String s = "";
        switch (type) {
            case Bool:
                s = "bool";
                break;
            case BoolArray:
                s = "bool *";
                break;
            case Double:
                s = "double";
                break;
            case DoubleArray:
                s = "double *";
                break;
            case Int:
                s = "int";
                break;
            case IntArray:
                s = "int *";
                break;
            case Long:
                s = "long";
                break;
            case LongArray:
                s = "long *";
                break;
            case None:
                s = "void";
                break;
        }
        return s;
    }

    private static String arrayOpenCL(MGStorage storage) {
        String s = "";
        switch (storage.getDataType()) {
            case BoolArray:
            case DoubleArray:
            case IntArray:
            case LongArray:
                if (((MGArray) storage).isLocal())
                    s += __local + " ";
                else
                    s += __global + " ";
                break;
        }
        return s;
    }

    @TruffleBoundary
    private static String toKernelArg(MGStorage variable) {
        String argName = variable.getName();
        String dataType = DataTypeOpenCL(variable.getDataType());
        String s = "";

        if (variable instanceof MGArray) {
            if (((MGArray) variable).isLocal()) {
                s = __local;
            } else {
                s = __global;
            }
            s += " " + dataType + argName;
        } else {
            s = dataType + " " + argName;
        }
        return s + ", ";
    }

    @TruffleBoundary
    protected String generateSrc(MGNode kernelBody) {
        indent = 0;
        String src = "";
        // if (this.requireDouble)
        src += writePragma("cl_khr_fp64", true);
        // TODO: Not all platforms support it.
        // src += writePragma("cl_nv_pragma_unroll", true);

        String body = translateFunctionBody(kernelBody);

        String args = "";
        String functionsSrc = "";
        String functionsDefs = "";
        for (MGNodeUserFunction f : localFunctions) {
            ArrayList<MGStorage> params = new ArrayList<>(f.getParameters());
            for (String p : f.getPrivateEnv().getOrderedParameters()) {
                params.add(f.getPrivateEnv().getParameters().get(p));
            }
            String s = newLine() + ""; // TODO: test inline
            s += DataTypeOpenCL(f.getExpectedType()) + " ";
            s += f.getFunctionName() + "(";
            for (int i = 0; i < params.size(); i++) {
                s += arrayOpenCL(params.get(i));
                s += DataTypeOpenCL(params.get(i).getDataType()) + " ";
                s += params.get(i).getName() + ", ";
            }
            s += __global + " long *" + BOUNDFLAG + ", ";
            s += __global + " long *" + OVERFLOWFLAG;
            s += ")";
            functionsDefs += newLine() + s + ";";
            s += " {";
            in();
            s += translateFunctionBody(f.getBody());
            out();
            s += newLine() + "}";

            functionsSrc += newLine() + "// derived from " + f.getFunctionID();
            functionsSrc += newLine() + s;
        }

        if (ModInt) {
            methods.add(modMethod(ModIntStr, "int"));
        }

        if (ModLong) {
            methods.add(modMethod(ModLongStr, "long"));
        }

        if (runtimeBoundCheck) {
            methods.add(openclBoundCheckMethod());
        }

        if (runtimeOFCheckAddInt) {
            methods.add(extactAddIntMethod());
        }

        if (runtimeOFCheckAddLong) {
            methods.add(extactAddLongMethod());
        }

        if (runtimeOFCheckMulInt) {
            methods.add(extactMulIntMethod());
        }

        if (runtimeOFCheckMulLong) {
            methods.add(extactMulLongMethod());
        }

        if (runtimeOFCheckSubInt) {
            methods.add(extactSubIntMethod());
        }

        if (runtimeOFCheckSubLong) {
            methods.add(extactSubLongMethod());
        }

        for (String method : methods)
            src += newLine() + method;

        src += newLine() + functionsDefs;
        src += newLine() + functionsSrc;

        for (int i = 0; i < orderedParameters.length; i++) {
            Object var = orderedParameters[i];
            args += toKernelArg(parameters.get(var));
        }

        for (int i = 0; i < levels; i++) {
            args += "const int " + OFFSET + i + ", " + "const int " + STEP + i + ", ";
        }

        args += __global + " long *" + BOUNDFLAG + ", ";
        args += __global + " long *" + OVERFLOWFLAG;

        src += newLine() + "__kernel void " + kernelName + "(" + args + ") {" + //
                        globalIDs() + //
                        body + //
                        newLine() + "}";

        return src;
    }

    @TruffleBoundary
    private String globalID(int dim) {
        return newLine() + "int " + iterVar[dim].getName() + " = ( get_global_id(" + dim + ") * " + STEP + dim + ") + " + OFFSET + dim + ";";
    }

    @TruffleBoundary
    private String globalIDs() {
        String globalIds = "";
        globalIds += globalID(0);
        if (levels > 1)
            globalIds += globalID(1);

        if (levels > 2)
            globalIds += globalID(2);

        return globalIds;
    }

    public String visitor(MGNode root) {
        try {
            return root.accept(this);
        } catch (Exception e) {
            log.printException(e);
        }
        assert false : "There is something uncovered!";
        return null;
    }

    public void addVarDefinition(String s) {
        variablesDefinitions.add(s);
    }

    @TruffleBoundary
    private String defineVariable(MGStorage storage) {
        final String name = storage.getName();
        if (!definedVars.contains(name)) {
            String definition = DataTypeOpenCL(storage.getDataType()) + " " + name;
            switch (storage.getDataType()) {
                case Bool:
                    definition += " = " + "false;";
                    break;
                case Int:
                case Long:
                    definition += " = " + "0;";
                    break;
                case Double:
                    definition += " = " + "0.0;";
                    break;
                default:
                    definition += ";";
            }
            addVarDefinition(definition);
            definedVars.add(name);
        }
        return storage.getName();
    }

    private boolean isDefined(MGStorage storage) {
        final String name = storage.getName();
        return definedVars.contains(name);
    }

    public String visitOperandComplex(MGNodeOperandComplex node) throws CoverageException {
        return "";
    }

    public String visitOperand(MGNodeOperand node) throws CoverageException {
        if (node.getValue() instanceof MGStorage && ((MGStorage) node.getValue()).isDefine() && !isDefined((MGStorage) node.getValue()))
            return newLine() + defineVariable(((MGStorage) node.getValue()));
        else if (node.getValue() != null && node.getValue() instanceof MGArray && ((MGArray) node.getValue()).getIndicesLen() > 0)
            return openclAccessArray((MGArray) node.getValue());
        return node.getValue().getName();
    }

    // Not supported yet
    @TruffleBoundary
    private String ifElseComplexAssignOpenCL(MGNodeAssignComplex node) {
        String sT = newLine();
        String sR = newLine();
        String sI = newLine();
        String realRight = ifElseAssignOpenCL((MGNodeIf) node.getReal().getRight());
        sI += visitor(node.getImag().getLeft()) + " = " + ifElseAssignOpenCL((MGNodeIf) node.getImag().getRight()) + ";";
        if (!((MGStorage) ((MGNodeOperand) node.getReal().getLeft()).getValue()).isDefine() && !isDefined((MGStorage) ((MGNodeOperand) node.getReal().getLeft()).getValue())) {
            String tempReal = MGBaseEnv.createTempName();
            sT += "double " + tempReal + " = " + realRight + ";";
            realRight = tempReal;
        }
        sR += visitor(node.getReal().getLeft()) + " = " + realRight + ";";
        return sT + sI + sR;
    }

    public String visitAssignComplex(MGNodeAssignComplex node) throws CoverageException {
        if (node.getReal().getRight() instanceof MGNodeIf)
            return ifElseComplexAssignOpenCL(node);
        String s = "";
        String realRight = visitor(node.getReal().getRight());
        if (((MGStorage) ((MGNodeOperand) node.getReal().getLeft()).getValue()).isDefine() && !isDefined((MGStorage) ((MGNodeOperand) node.getReal().getLeft()).getValue())) {
            s += newLine() + visitor(node.getImag().getLeft()) + " = " + visitor(node.getImag().getRight()) + ";";
        } else {
            String tempReal = MGBaseEnv.createTempName();
            s += newLine() + "double " + tempReal + " = " + realRight + ";";
            s += newLine() + visitor(node.getImag().getLeft()) + " = " + visitor(node.getImag().getRight()) + ";";
            realRight = tempReal;
        }
        s += newLine() + visitor(node.getReal().getLeft()) + " = " + realRight + ";";
        return s;
    }

    @TruffleBoundary
    protected String ifElseAssignOpenCL(MGNodeIf ifElse) {
        String s = "";
        s += "(" + visitor(ifElse.getCond()) + ")? ";
        s += visitor(ifElse.getThen());
        s += " : ";
        s += visitor(ifElse.getOrelse());
        s += ";";
        return s;
    }

    public String visitAssign(MGNodeAssign node) throws CoverageException {
        String s = newLine() + visitor(node.getLeft()) + " = ";
        if (node.getRight() instanceof MGNodeIf)
            return s + ifElseAssignOpenCL((MGNodeIf) node.getRight());

        return s + visitor(node.getRight()) + ";";
    }

    public static String castString(DataType type) {
        String castType = "";
        switch (type) {
            case Long:
                castType = "(long)";
                break;
            case Double:
                castType = "(double)";
                break;
            case Int:
                castType = "(int)";
                break;
        }
        return castType;
    }

    public String visitUnaryOp(MGNodeUnaryOp node) throws CoverageException {
        if (node.getType() == UnaryOpType.Not)
            return "!(" + visitor(node.getChild()) + ")";

        if (node.getType() == UnaryOpType.Cast) {
            return "(" + castString(node.getExpectedType()) + "(" + visitor(node.getChild()) + ")" + ")";
        }

        return null;
    }

    public final static String[] binOpStr = {
                    " + ", " - ", " * ", " / ", //
                    ", ", " % ", " | ", " & ", //
                    " << ", " >> ", " & ", " | ", //
                    " ^ ", " == ", " != ", " < ", //
                    " <= ", " > ", " >= "};

    private static boolean overflowable(MGNodeBinOp node) {
        return (node.getExpectedType() == DataType.Int //
                        || node.getExpectedType() == DataType.Long) //
                        && (node.getType() == BinOpType.ADD //
                                        || node.getType() == BinOpType.MUL //
                                        || node.getType() == BinOpType.SUB) //
                        && node.overflowCheck(); //
    }

    private String overflowCheck(MGNodeBinOp node) {
        String s = "";
        switch (node.getType()) {
            case ADD:
                if (node.getExpectedType() == DataType.Int) {
                    runtimeOFCheckAddInt = true;
                    s += exactAddInt;
                } else {
                    runtimeOFCheckAddLong = true;
                    s += exactAddLong;
                }
                break;
            case MUL:
                if (node.getExpectedType() == DataType.Int) {
                    runtimeOFCheckMulInt = true;
                    s += exactMulInt;
                } else {
                    runtimeOFCheckMulLong = true;
                    s += exactMulLong;
                }
                break;
            case SUB:
                if (node.getExpectedType() == DataType.Int) {
                    runtimeOFCheckSubInt = true;
                    s += exactSubInt;
                } else {
                    runtimeOFCheckSubLong = true;
                    s += exactSubLong;
                }
                break;
        }
        s += "(" + OVERFLOWFLAG + ", ";
        return s;
    }

    public String visitBinOp(MGNodeBinOp node) throws CoverageException {
        String cast = "";
        String op = binOpStr[node.getType().ordinal()];
        String s = castString(node.getExpectedType());
        if (node.getType() == BinOpType.POW) {
            cast = String.format("(%s)", "double");
            s += "pow(";
        } else if (node.getType() == BinOpType.MOD) {
            if (node.getLeft().getExpectedType() == DataType.Double || node.getRight().getExpectedType() == DataType.Double) {
                s += "fmod";
            } else if (node.getLeft().getExpectedType() == DataType.Long || node.getRight().getExpectedType() == DataType.Long) {
                s += ModLongStr;
                ModLong = true;
            } else {
                s += ModIntStr;
                ModInt = true;
            }
            s += "(";
            op = ", ";
        } else if (overflowable(node)) {
            s += overflowCheck(node);
            op = ", ";
        } else {
            s += "(";
        }
        s += cast;
        s += visitor(node.getLeft());
        s += op;
        s += cast;
        s += visitor(node.getRight());
        return s + ")";
    }

    public String visitBlock(MGNodeBlock node) throws CoverageException {
        String s = "";
        int size = node.getChildren().size();
        for (int i = 0; i < size; i++) {
            MGNode stmt = node.getChildren().get(i);
            s += visitor(stmt);
        }

        return s;
    }

    public String visitBreak(MGNodeBreak node) throws CoverageException {
        return "goto " + node.getjLabel().getLabel() + ";";
    }

    public String visitBreakElse(MGNodeBreakElse node) throws CoverageException {
        String s = "";
        s += visitor(node.getForBody());
        s += visitor(node.getOrelse());
        s += newLine() + visitor(node.getjLabel());
        return s;
    }

    public String visitBuiltinFunction(MGNodeBuiltinFunction node) throws CoverageException {
        if (node.getType() == BuiltinFunctionType.MIN || node.getType() == BuiltinFunctionType.MAX) {
            String op = " > ";
            if (node.getType() == BuiltinFunctionType.MIN)
                op = " < ";
            String arg1 = "( " + visitor(node.getNodes().get(0)) + " )";
            String arg2 = "( " + visitor(node.getNodes().get(1)) + " )";
            return "((" + arg1 + op + arg2 + ")? (" + arg1 + "):(" + arg2 + "))";
        }

        return "";
    }

    public String visitFor(MGNodeFor node) throws CoverageException {
        LoopInfo info = node.getLoopInfo();
        final long[] range = info.getRange();
        String start = Long.toString(range[0]);
        String stop = Long.toString(range[1]);
        String step = Long.toString(range[2]);

        if (!info.isSimpleRange()) {
            start = (info.getStartNode() == null) ? start : visitor(info.getStartNode());
            stop = visitor(info.getStopNode());
            step = (info.getStepNode() == null) ? step : visitor(info.getStepNode());
        }

        String s = "";

        if (!(info.getStartNode() instanceof MGNodeEmpty))
            s += newLine() + defineVariable((info.getInductionVariable())) + " = " + start + ";";
        s += newLine() + "for(;";
        s += info.getInductionVariable().getName() + binOpStr[info.getStopOp().ordinal()] + "( " + stop + " )";
        s += ";";
        s += info.getInductionVariable().getName() + " " + binOpStr[info.getStepOp().ordinal()].replaceAll(" ", "") + "=" + step;
        s += ")";
        s += newLine() + "{";
        in();
        if (info.getTargetVar() != null) {
            s += newLine() + defineVariable(((MGStorage) info.getTargetVar()));
            s += " = ";
            s += openclAccessArray((MGArray) info.getTargetVar().getValue());
            s += ";";
        }
        s += visitor(node.getForBody());
        out();
        s += newLine() + "}";
        if (!(node.getParent() instanceof MGNodeBreakElse) && node.hasBreak())
            s += newLine() + visitor(node.getjLabel());

        return s;
    }

    public String visitWhile(MGNodeWhile node) throws CoverageException {
        String s = "";
        s += newLine() + "while(";
        s += visitor(node.getCond());
        s += ")";
        s += newLine() + "{";
        in();
        s += visitor(node.getBody());
        out();
        s += newLine() + "}";
        if (node.hasBreak())
            s += newLine() + visitor(node.getjLabel());
        return s;
    }

    public String visitIf(MGNodeIf node) throws CoverageException {
        String s = "";
        String scond = visitor(node.getCond());
        if (scond.startsWith("(") && scond.endsWith("")) {
            scond = scond.substring(1, scond.length() - 1);
        }
        s += newLine() + "if(" + scond + ") {";
        in();
        s += visitor(node.getThen());
        out();
        s += newLine() + "}";
        if (node.getOrelse() != null) {
            s += newLine() + "else {";
            in();
            s += visitor(node.getOrelse());
            out();
            s += newLine() + "}";
        }
        return s;
    }

    public String visitMathFunction(MGNodeMathFunction node) throws CoverageException {
        String s = MGNodeMathFunction.mathFuncStrings[node.getType().ordinal()];
        if (node.getType() == MathFunctionType.abs && node.getExpectedType() == DataType.Double)
            s = "fabs";

        s += "(" + visitor(node.getNodes().get(0));
        for (int i = 1; i < node.getNodes().size(); i++) {
            s += ", " + visitor(node.getNodes().get(i));
        }
        s += ")";

        return s;
    }

    public String visitEmpty(MGNodeEmpty node) throws CoverageException {
        return "";
    }

    @SuppressWarnings("static-method")
    private String inlineOpenCL(@SuppressWarnings("unused") MGNodeFunctionCall node) {
        String s = "";
        // s += visitor(functionNode);
        // TODO: implement inlining.
        return s;
    }

    public String visitFunctionCall(MGNodeFunctionCall node) throws CoverageException {
        if (node.isInline()) {
            return inlineOpenCL(node);
        }
        boolean isReturn = node.isReturnCall();
        String s = (isReturn) ? "" : newLine();
        s = node.getFunctionNode().getFunctionName() + "(";
        ArrayList<MGNode> params = new ArrayList<>(node.getArgs());
        for (String p : node.getFunctionNode().getPrivateEnv().getOrderedParameters()) {
            params.add(new MGNodeOperand(node.getFunctionNode().getPrivateEnv().getParameters().get(p)));
        }
        for (int i = 0; i < params.size(); i++) {
            s += visitor(params.get(i));
            s += ", ";
        }
        s += BOUNDFLAG + ", ";
        s += OVERFLOWFLAG;
        s += ")";
        s += (isReturn) ? "" : ";";
        return s;
    }

    public String visitReturn(MGNodeReturn node) throws CoverageException {
        String s = newLine();
        /*- TODO: (inline)
         * if (MGOptions.Parallel.inlineCalls && node.getLeft() != null) {
         *      s += visitor(node.getLeft());
         *      s += "goto " + jLable;
         *      } */
        s += "return ";
        if (node.getRight() instanceof MGNodeIf)
            return s + ifElseAssignOpenCL((MGNodeIf) node.getRight());

        return s + visitor(node.getRight()) + ";";
    }

    public String visitJumpFrom(MGNodeJumpFrom node) {
        return newLine() + "goto " + node.getjLabel().getLabel() + ";";
    }

    public String visitJumpTo(MGNodeJumpTo node) {
        return node.getLabel() + ":" + newLine() + "; // resume here after jump" + newLine();
    }

    public String visitParallelNodeLocalBarrier(ParallelNodeLocalBarrier node) throws CoverageException {
        return newLine() + "barrier(CLK_LOCAL_MEM_FENCE);";
    }

    public String visitParallelNodeGlobalBarrier(ParallelNodeGlobalBarrier node) throws CoverageException {
        return newLine() + "barrier(CLK_GLOBAL_MEM_FENCE);";
    }

    public String visitParallelNodeLocalID(ParallelNodeLocalID node) throws CoverageException {
        return String.format("get_local_id(%d)", node.getDim());
    }

    public String visitParallelNodeLocalSize(ParallelNodeLocalSize node) throws CoverageException {
        return String.format("get_local_size(%d)", node.getDim());
    }

    public String visitParallelNodeGroupID(ParallelNodeGroupID node) throws CoverageException {
        return String.format("get_group_id(%d)", node.getDim());
    }

    public String visitParallelNodeGroupSize(ParallelNodeGroupSize node) throws CoverageException {
        return String.format("get_num_groups(%d)", node.getDim());
    }

    public String visitParallelNodeGlobalID(ParallelNodeGlobalID node) throws CoverageException {
        return String.format("get_global_id(%d)", node.getDim());
    }

    public String visitParallelNodeGlobalSize(ParallelNodeGlobalSize node) throws CoverageException {
        return String.format("get_global_size(%d)", node.getDim());
    }

    private String openclBoundCheck(MGArray arrayValue, int i) {
        final MGNode index = arrayValue.getIndices()[i];
        final String s = visitor(index);
        if (arrayValue.isNoBounds())
            return s;
        final MGBoundNode bound = arrayValue.getBounds()[i];
        String ds = arrayValue.getName() + MGBaseEnv.DIMSIZE + i;
        if (bound != null && bound.isRequireBoundCheck()) {
            runtimeBoundCheck = true;
            if (MGOptions.Backend.BoundCheckDebug > 0) {
                MGLog.printlnErrTagged(" Runtime Bound Check set '" + arrayValue.getName() + "[ " + s + " ]' dimension: " + i);
            }
            return String.format("%s(%s, %s, %s) ", boundCheckMethod, BOUNDFLAG, s, ds);
        }
        return s;
    }

    public String openclAccessArray(MGArray arrayValue) {
        int dims = arrayValue.getArrayInfo().getDim();
        String s = arrayValue.getName();
        String ds = arrayValue.getName() + MGBaseEnv.DIMSIZE;
        if (dims > 1) {
            String[] dimSizes = new String[dims];
            dimSizes[dims - 1] = ds + (dims - 1);
            for (int i = dims - 2; i >= 0; i--)
                dimSizes[i] = "(" + (ds + i) + " * " + dimSizes[i + 1] + ")";

            if (dims == arrayValue.getIndicesLen()) {
                // Returning single value
                s += "[";
                for (int i = 0; i < dims - 1; i++)
                    s += openclBoundCheck(arrayValue, i) + " * " + dimSizes[i + 1] + " + ";

                s += openclBoundCheck(arrayValue, dims - 1) + "]";
            } else {
                // Returning single dimension.
                s += " + ";
                for (int i = 0; i < dims - 2; i++)
                    s += openclBoundCheck(arrayValue, i) + " * " + dimSizes[i + 1] + " + ";

                s += openclBoundCheck(arrayValue, dims - 1);
            }
        } else {
            s += "[" + openclBoundCheck(arrayValue, 0) + "]";
        }
        return s;
    }

    public static String openclBoundCheckMethod() {
        String s = "";
        s += newLine() + "int " + boundCheckMethod + "(__global long *" + BOUNDFLAG + ", int index, int size) {";
        in();
        s += newLine() + "if(index >= size || index < 0) {";
        in();
        s += newLine() + BOUNDFLAG + "[0] = 1;";
        s += newLine() + "return 0;";
        out();
        s += newLine() + "}";
        s += newLine() + "return index;";
        out();
        s += newLine() + "}";

        return s;
    }

    @TruffleBoundary
    public String writePragma(String pragma, boolean enable) {
        String s = "#pragma OPENCL EXTENSION " + pragma + " : " + (enable ? "en" : "dis") + "able";
        s += newLine();
        return s;
    }

    public static String extactAddIntMethod() {
        String s = "";
        s += newLine() + "int " + exactAddInt + "(__global long *" + OVERFLOWFLAG + ", int x, int y) {";
        in();
        s += newLine() + "int r = add_sat(x, y);";
        s += newLine() + "if (r == INT_MAX || r == INT_MIN) {";
        in();
        s += newLine() + OVERFLOWFLAG + "[0] = 1;";
        out();
        s += newLine() + "}";
        s += newLine() + "return r;";
        out();
        s += newLine() + "}";

        return s;
    }

    public static String extactMulIntMethod() {
        String s = "";
        s += newLine() + "int " + exactMulInt + "(__global long *" + OVERFLOWFLAG + ", int x, int y) {";
        in();
        s += newLine() + "int r = mad_sat(x, y, 0);";
        s += newLine() + "if (r == INT_MAX || r == INT_MIN) {";
        in();
        s += newLine() + OVERFLOWFLAG + "[0] = 1;";
        out();
        s += newLine() + "}";
        s += newLine() + "return r;";
        out();
        s += newLine() + "}";

        return s;
    }

    public static String extactSubIntMethod() {
        String s = "";
        s += newLine() + "int " + exactSubInt + "(__global long *" + OVERFLOWFLAG + ", int x, int y) {";
        in();
        s += newLine() + "int r = sub_sat(x, y);";
        s += newLine() + "if (r == INT_MAX || r == INT_MIN) {";
        in();
        s += newLine() + OVERFLOWFLAG + "[0] = 1;";
        out();
        s += newLine() + "}";
        s += newLine() + "return r;";
        out();
        s += newLine() + "}";

        return s;
    }

    public static String extactAddLongMethod() {
        String s = "";
        s += newLine() + "long " + exactAddLong + "(__global long *" + OVERFLOWFLAG + ", long x, long y) {";
        in();
        s += newLine() + "long r = add_sat(x, y);";
        s += newLine() + "if (r == LONG_MAX || r == LONG_MIN) {";
        in();
        s += newLine() + OVERFLOWFLAG + "[0] = 1;";
        out();
        s += newLine() + "}";
        s += newLine() + "return r;";
        out();
        s += newLine() + "}";

        return s;
    }

    public static String extactMulLongMethod() {
        String s = "";
        s += newLine() + "long " + exactMulLong + "(__global long *" + OVERFLOWFLAG + ", long x, long y) {";
        in();
        s += newLine() + "long r = (long)((double) x * (double) y);";
        s += newLine() + "if (r == LONG_MAX || r == LONG_MIN) {";
        in();
        s += newLine() + OVERFLOWFLAG + "[0] = 1;";
        out();
        s += newLine() + "}";
        s += newLine() + "return r;";
        out();
        s += newLine() + "}";

        return s;
    }

    public static String extactSubLongMethod() {
        String s = "";
        s += newLine() + "long " + exactSubLong + "(__global long *" + OVERFLOWFLAG + ", long x, long y) {";
        in();
        s += newLine() + "long r = sub_sat(x, y);";
        s += newLine() + "if (r == LONG_MAX || r == LONG_MIN) {";
        in();
        s += newLine() + OVERFLOWFLAG + "[0] = 1;";
        out();
        s += newLine() + "}";
        s += newLine() + "return r;";
        out();
        s += newLine() + "}";

        return s;
    }

    public static String modMethod(String funcName, String type) {
        String s = "";
        s += newLine() + type + " " + funcName + "(" + type + " l, " + type + " r) {";
        in();
        s += newLine() + type + " v = l % r;";
        s += newLine() + "return v < 0 ? ((r < 0) ? v - r : v + r) : v;";
        out();
        s += newLine() + "}";

        return s;
    }

    protected void writer(String src) throws IllegalArgumentException, SecurityException, IOException {
        FileWriter aWriter = new FileWriter(kernelFile, false);
        aWriter.write(license);
        aWriter.write(src);
        aWriter.flush();
        aWriter.close();
    }

}
