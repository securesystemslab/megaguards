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
package edu.uci.megaguards.analysis.parallel.polyhedral;

import java.util.HashMap;

import org.bytedeco.javacpp.Pointer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.ast.env.MGBaseEnv;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeFor;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.object.MGArray;
import edu.uci.megaguards.object.MGLiteral;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;

public class AthenaPet extends AthenaPetJNI {

    private Pointer athenaPetScan;

    private int DEBUG;

    public enum AthenaPetObjType {
        pet_expr,
        pet_tree
    }

    public class AthenaPetObject {
        private Pointer p;
        private AthenaPetObjType type;

        public AthenaPetObject(Pointer p, AthenaPetObjType type) {
            this.p = p;
            this.type = type;
        }

        Pointer unbox() {
            return p;
        }

        public AthenaPetObjType getType() {
            return type;
        }
    }

    public AthenaPet() {
        super();
        this.DEBUG = MGOptions.Backend.AthenaPetJNIDebug;
        this.athenaPetScan = athena_create_athena_pet(DEBUG);
    }

    @TruffleBoundary
    public void addLocalVar(String name) {
        athena_add_local(athenaPetScan, name, "int");
    }

    @TruffleBoundary
    public AthenaPetObject var(String name, int dim, int size) {
        return new AthenaPetObject(athena_expr_id_int(athenaPetScan, name, dim, size), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject var(MGObject var) {
        if (var instanceof MGStorage) {
            int dim = var instanceof MGArray ? ((MGArray) var).getArrayInfo().getDim() : 0;
            String varname = var.getName();
            if (varname.contains(MGBaseEnv.REPLACETAG)) {
                varname = varname.substring(0, varname.indexOf(MGBaseEnv.REPLACETAG));
            }
            return new AthenaPetObject(athena_expr_id_int(athenaPetScan, varname, dim, 0), AthenaPetObjType.pet_expr);
        } else if (var instanceof MGLiteral) {
            int val = 0;
            if (var.getValue() instanceof Integer)
                val = (int) var.getValue();
            else if (var.getValue() instanceof Long)
                val = ((Long) var.getValue()).intValue();
            else if (var.getValue() instanceof Double)
                val = ((Double) var.getValue()).intValue();
            else if (var.getValue() instanceof Boolean)
                val = ((boolean) var.getValue()) ? 1 : 0;
            return literal(val);
        } else
            return literal(0);
    }

    @TruffleBoundary
    public AthenaPetObject literal(int val) {
        return new AthenaPetObject(athena_expr_int(athenaPetScan, val), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject literal(long val) {
        return new AthenaPetObject(athena_expr_int(athenaPetScan, ((Long) val).intValue()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject assign(AthenaPetObject lhs, AthenaPetObject rhs) {
        assert lhs.getType() == AthenaPetObjType.pet_expr;
        assert rhs.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_expr_assign(athenaPetScan, lhs.unbox(), rhs.unbox()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject constant(AthenaPetObject lhs, AthenaPetObject rhs) {
        assert lhs.getType() == AthenaPetObjType.pet_expr;
        assert rhs.getType() == AthenaPetObjType.pet_expr;
        AthenaPetObject expr = new AthenaPetObject(athena_expr_assign(athenaPetScan, lhs.unbox(), rhs.unbox()), AthenaPetObjType.pet_expr);
        return expr;// unaryOp(expr, pet_op_kill);
    }

    @TruffleBoundary
    public AthenaPetObject opAssign(AthenaPetObject lhs, int op, AthenaPetObject rhs) {
        assert lhs.getType() == AthenaPetObjType.pet_expr;
        assert rhs.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_expr_op_assign(athenaPetScan, lhs.unbox(), op, rhs.unbox()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject binaryOp(AthenaPetObject lhs, int op, AthenaPetObject rhs) {
        assert lhs.getType() == AthenaPetObjType.pet_expr;
        assert rhs.getType() == AthenaPetObjType.pet_expr;
        int typeSize = -32; // assume only integer types for simplicity
        return new AthenaPetObject(athena_expr_binary_op(athenaPetScan, lhs.unbox(), op, rhs.unbox(), typeSize), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject unaryOp(AthenaPetObject lhs, int op) {
        assert lhs.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_expr_unary_op(athenaPetScan, lhs.unbox(), op), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject increment() {
        return new AthenaPetObject(athena_extract_unary_increment(athenaPetScan), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject decrement() {
        return new AthenaPetObject(athena_extract_unary_decrement(athenaPetScan), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject binaryIncrement(AthenaPetObject iv, int op, AthenaPetObject expr) {
        assert iv.getType() == AthenaPetObjType.pet_expr;
        assert expr.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_extract_binary_increment(athenaPetScan, op, expr.unbox(), iv.unbox()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject compundIncrement(AthenaPetObject expr, int op) {
        assert expr.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_extract_compound_increment(athenaPetScan, op, expr.unbox()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject subscriptAccess(AthenaPetObject base, AthenaPetObject index) {
        assert base.getType() == AthenaPetObjType.pet_expr;
        assert index.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_expr_access_subscript(athenaPetScan, base.unbox(), index.unbox()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject ternaryOp(AthenaPetObject cond, AthenaPetObject lhs, AthenaPetObject rhs) {
        assert lhs.getType() == AthenaPetObjType.pet_expr;
        assert rhs.getType() == AthenaPetObjType.pet_expr;
        assert cond.getType() == AthenaPetObjType.pet_expr;
        return new AthenaPetObject(athena_expr_ternary(athenaPetScan, cond.unbox(), lhs.unbox(), rhs.unbox()), AthenaPetObjType.pet_expr);
    }

    @TruffleBoundary
    public AthenaPetObject forLoop(AthenaPetObject iv, AthenaPetObject init, AthenaPetObject cond, AthenaPetObject inc, AthenaPetObject body, String forTag) {
        assert iv.getType() == AthenaPetObjType.pet_expr;
        assert init.getType() == AthenaPetObjType.pet_expr;
        assert cond.getType() == AthenaPetObjType.pet_expr;
        assert inc.getType() == AthenaPetObjType.pet_expr;
        assert body.getType() == AthenaPetObjType.pet_tree;
        Pointer tree = athena_expr_for(athenaPetScan, 0, 0, iv.unbox(), init.unbox(), cond.unbox(), inc.unbox(), body.unbox());
        return new AthenaPetObject(athena_set_tree_label(athenaPetScan, tree, forTag), AthenaPetObjType.pet_tree);
    }

    @TruffleBoundary
    public AthenaPetObject whileLoop(AthenaPetObject pe_cond, AthenaPetObject body, String whileTag) {
        assert pe_cond.getType() == AthenaPetObjType.pet_expr;
        assert body.getType() == AthenaPetObjType.pet_tree;
        Pointer tree = athena_expr_while(athenaPetScan, pe_cond.unbox(), body.unbox());
        return new AthenaPetObject(athena_set_tree_label(athenaPetScan, tree, whileTag), AthenaPetObjType.pet_tree);
    }

    @TruffleBoundary
    public AthenaPetObject breakLoop() {
        return new AthenaPetObject(athena_expr_break(athenaPetScan), AthenaPetObjType.pet_tree);
    }

    @TruffleBoundary
    public AthenaPetObject ifElse(AthenaPetObject cond, AthenaPetObject then_body, AthenaPetObject else_body) {
        assert cond.getType() == AthenaPetObjType.pet_expr;
        assert then_body.getType() == AthenaPetObjType.pet_tree;
        if (else_body == null)
            return new AthenaPetObject(athena_expr_if(athenaPetScan, cond.unbox(), then_body.unbox()), AthenaPetObjType.pet_tree);
        else {
            assert else_body.getType() == AthenaPetObjType.pet_tree;
            return new AthenaPetObject(athena_expr_if_else(athenaPetScan, cond.unbox(), then_body.unbox(), else_body.unbox()), AthenaPetObjType.pet_tree);
        }
    }

    @TruffleBoundary
    public AthenaPetObject statement(AthenaPetObject expr, String statementTag) {
        if (statementTag == null)
            return expr;
        if (expr.getType() == AthenaPetObjType.pet_tree)
            return expr;
        Pointer tree = athena_expr_to_tree(athenaPetScan, expr.unbox());
        return new AthenaPetObject(athena_set_tree_label(athenaPetScan, tree, statementTag), AthenaPetObjType.pet_tree);
    }

    @TruffleBoundary
    public AthenaPetObject block(int nstmts) {
        return new AthenaPetObject(athena_new_block(athenaPetScan, 1, nstmts), AthenaPetObjType.pet_tree);
    }

    @TruffleBoundary
    public AthenaPetObject addChild(AthenaPetObject block, AthenaPetObject child) {
        assert block.getType() == AthenaPetObjType.pet_tree;
        assert child.getType() == AthenaPetObjType.pet_tree;
        return new AthenaPetObject(athena_add_child_to_tree_block(athenaPetScan, block.unbox(), child.unbox()), AthenaPetObjType.pet_tree);
    }

    @TruffleBoundary
    public void computeDependences(AthenaPetObject loop, HashMap<String, MGNode> loops, HashMap<String, String> flowDepStmt) {
        Pointer tree = athena_new_block(athenaPetScan, 0, 1);
        tree = athena_add_child_to_tree_block(athenaPetScan, tree, loop.unbox());
        Pointer scop = athena_tree_to_scop(athenaPetScan, tree);
        this.athenaPetScan = null;
        Pointer l = athena_extract_dependences(scop);
        StringVector list = athena_get_loop_list(l);
        StringVector si = athena_get_stmt_list_in(l);
        StringVector so = athena_get_stmt_list_out(l);
        long size = list.size();
        for (int i = 0; i < size; i++) {
            final String strl = list.get(i).toString();
            if (DEBUG > 0)
                MGLog.printlnTagged("Test drive: " + strl);
            MGNode f = loops.get(strl);
            if (f != null && f instanceof MGNodeFor) {
                ((MGNodeFor) f).setDependenceExists(false);
            }
            loops.remove(strl);
        }

        for (int i = 0; i < si.size(); i++) {
            final String in = si.get(i).toString();
            final String out = so.get(i).toString();
            if (DEBUG > 0)
                MGLog.printlnTagged("Test drive: " + in + "  ->  " + out);
            flowDepStmt.put(in, out);
        }

        athena_delete_results(l);
    }

    @TruffleBoundary
    public static boolean verify() {
        AthenaPet a = new AthenaPet();

        /*-
         * for (i = 0; i < 1024; i++){
         *      C[i - x] += 1; // stmt_1
         *      if( i < 100 )
         *          { C[i - x] += 300; }
         *      else
         *          { C[i - x] += 200; }
         *  }
         */
        // add local vars
        a.addLocalVar("i");

        // C[i - x] += 1;

        AthenaPetObject id_C = a.var("C", 1, 0); // C
        AthenaPetObject id_i = a.var("i", 0, 0); // i
        AthenaPetObject id_x = a.var("x", 0, 0); // x
        AthenaPetObject i_x = a.binaryOp(id_i, pet_op_sub, id_x); // i - x
        AthenaPetObject C_i_x = a.subscriptAccess(id_C, i_x); // C[i - x]
        AthenaPetObject val_123 = a.literal(123); // 123
        AthenaPetObject C_i_x_plus_equal_1 = a.opAssign(C_i_x, pet_op_add_assign, val_123);
        AthenaPetObject stmt_1 = a.statement(C_i_x_plus_equal_1, "stmt_1");

        // if( i < 100 )
        id_i = a.var("i", 0, 0); // i
        AthenaPetObject val_100_cond = a.literal(100); // 1024
        AthenaPetObject if_cond = a.binaryOp(id_i, pet_op_lt, val_100_cond); // i < 1024

        // { C[i - x] += athena_300; }
        id_C = a.var("C", 1, 0); // C
        id_i = a.var("i", 0, 0); // i
        id_x = a.var("x", 0, 0); // x
        i_x = a.binaryOp(id_i, pet_op_sub, id_x); // i - x
        C_i_x = a.subscriptAccess(id_C, i_x); // C[i - x]
        AthenaPetObject val_300 = a.literal(300); // 300
        C_i_x_plus_equal_1 = a.opAssign(C_i_x, pet_op_add_assign, val_300);
        AthenaPetObject stmt_2 = a.statement(C_i_x_plus_equal_1, "stmt_2");
        AthenaPetObject if_body = a.block(1); // nstmts = 1
        if_body = a.addChild(if_body, stmt_2);

        // else { C[i - x] += a.athena_200; }
        id_C = a.var("C", 1, 0); // C
        id_i = a.var("i", 0, 0); // i
        id_x = a.var("x", 0, 0); // x
        i_x = a.binaryOp(id_i, pet_op_sub, id_x); // i - x
        C_i_x = a.subscriptAccess(id_C, i_x); // C[i - x]
        AthenaPetObject val_200 = a.literal(200); // 300
        C_i_x_plus_equal_1 = a.opAssign(C_i_x, pet_op_add_assign, val_200);
        AthenaPetObject stmt_3 = a.statement(C_i_x_plus_equal_1, "stmt_3");
        AthenaPetObject if_else = a.block(1); // nstmts = 1
        if_else = a.addChild(if_else, stmt_3);

        AthenaPetObject if_stmt = a.ifElse(if_cond, if_body, if_else);

        AthenaPetObject for_body = a.block(2); // nstmts = 1
        for_body = a.addChild(for_body, stmt_1);
        for_body = a.addChild(for_body, if_stmt);

        // for (i = a.athena_0; i < 1024; i++)
        AthenaPetObject id_iv = a.var("i", 0, 0); // i
        AthenaPetObject val_init_iv = a.literal(0); // 1
        AthenaPetObject id_iv_cond = a.var("i", 0, 0); // i
        AthenaPetObject val_1024_cond = a.literal(1024); // 1024
        AthenaPetObject cond = a.binaryOp(id_iv_cond, pet_op_lt, val_1024_cond); // i < 1024
        AthenaPetObject inc = a.increment();

        // glue
        AthenaPetObject for_stmt = a.forLoop(id_iv, val_init_iv, cond, inc, for_body, "for_stmt");

        MGLog.printlnTagged("AthenaPet: create tree.. ok!");

        HashMap<String, MGNode> loopList = new HashMap<>();
        loopList.put("for_stmt", null);
        HashMap<String, String> flowDepStmt = new HashMap<>();

        a.computeDependences(for_stmt, loopList, flowDepStmt);
        if (!loopList.containsKey("for_stmt")) {
            MGLog.printlnTagged("AthenaPet: data dependence check.. ok!");
        } else {
            MGLog.printlnTagged("AthenaPet: data dependence check.. FAILED!");
            return false;
        }
        System.gc();

        return true;
    }

}
