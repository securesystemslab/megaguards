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

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.analysis.exception.CoverageException;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeBinOp;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction;
import edu.uci.megaguards.ast.node.MGNodeFunctionCall;
import edu.uci.megaguards.ast.node.MGNodeOperand;
import edu.uci.megaguards.ast.node.MGNodeReturn;
import edu.uci.megaguards.ast.node.MGNodeBinOp.BinOpType;
import edu.uci.megaguards.ast.node.MGNodeBuiltinFunction.BuiltinFunctionType;

public class ParallelFunctions {

    public ParallelFunctions() {
    }

    public static class Map {

        public static final String tag = ""; // $MAP$
        public static final String inductionVar = "i" + tag;
        public static final String iterableList = "list" + tag;
        public static final String resultList = "result" + tag;

    }

    public static class Reduce {

        public static final String tag = "$REDUCE$";
        public static final String globalID = "global_id" + tag;
        public static final String localID = "local_id" + tag;
        public static final String iterableList = "list" + tag;
        public static final String listSize = "list_size" + tag;
        public static final String tempResult = "temp_result" + tag;
        public static final String initializerValue = "initializer" + tag;
        public static final String initialFlag = "init_flag" + tag;
        public static final String result = "result" + tag;
        public static final String result2 = "result2" + tag;
        public static final String j = "j" + tag;
        public static final String offset = "offset" + tag;

        private static boolean isBothOperands(String name1, String name2, MGNode n1, MGNode n2) {
            if (n1 instanceof MGNodeOperand && n2 instanceof MGNodeOperand) {
                final MGNodeOperand op1 = (MGNodeOperand) n1;
                final MGNodeOperand op2 = (MGNodeOperand) n2;
                if (op1.getValue().getName().contentEquals(name1) && op2.getValue().getName().contentEquals(name2))
                    return true;

                if (op1.getValue().getName().contentEquals(name2) && op2.getValue().getName().contentEquals(name1))
                    return true;

            }

            return false;
        }

        public static void checkWhitelist(MGNodeFunctionCall call) throws CoverageException {
            final String name1 = call.getFunctionNode().getParameters().get(0).getName();
            final String name2 = call.getFunctionNode().getParameters().get(1).getName();
            final MGNode ret = call.getFunctionNode().getBody();
            if (ret instanceof MGNodeReturn) {
                final MGNode node = ((MGNodeReturn) ret).getRight();

                if (node instanceof MGNodeBinOp) {
                    if (((MGNodeBinOp) node).getType() == BinOpType.ADD ||
                                    (((MGNodeBinOp) node).getType() == BinOpType.MUL)) {
                        final MGNode n1 = ((MGNodeBinOp) node).getLeft();
                        final MGNode n2 = ((MGNodeBinOp) node).getRight();

                        if (isBothOperands(name1, name2, n1, n2)) {
                            return;
                        }
                    }
                }

                else if (node instanceof MGNodeBuiltinFunction) {
                    if ((((MGNodeBuiltinFunction) node).getType() == BuiltinFunctionType.MIN) ||
                                    ((MGNodeBuiltinFunction) node).getType() == BuiltinFunctionType.MAX) {
                        final MGNode n1 = ((MGNodeBuiltinFunction) node).getNodes().get(0);
                        final MGNode n2 = ((MGNodeBuiltinFunction) node).getNodes().get(1);

                        if (isBothOperands(name1, name2, n1, n2)) {
                            return;
                        }
                    }
                }
            }
            throw CoverageException.INSTANCE.message("Possible non-commutative reduction function. Please add \"\"\"" + MGNodeOptions.REDUCE_ON + "\"\"\" to bypass this check.");
        }

    }
}
