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

import java.nio.file.Paths;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.annotation.ByRef;
import org.bytedeco.javacpp.annotation.ByVal;
import org.bytedeco.javacpp.annotation.Cast;
import org.bytedeco.javacpp.annotation.Index;
import org.bytedeco.javacpp.annotation.Name;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.StdString;
import org.bytedeco.javacpp.tools.Builder;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGEnvVars;
import edu.uci.megaguards.log.MGLog;

@Platform(include = {"AthenaPetJNI.h"})
public class AthenaPetJNI {

    public static final int pet_op_add_assign = 0;
    public static final int pet_op_sub_assign = 1;
    public static final int pet_op_mul_assign = 2;
    public static final int pet_op_div_assign = 3;
    public static final int pet_op_assign = 4;
    public static final int pet_op_add = 5;
    public static final int pet_op_sub = 6;
    public static final int pet_op_mul = 7;
    public static final int pet_op_div = 8;
    public static final int pet_op_mod = 9;
    public static final int pet_op_shl = 10;
    public static final int pet_op_shr = 11;
    public static final int pet_op_eq = 12;
    public static final int pet_op_ne = 13;
    public static final int pet_op_le = 14;
    public static final int pet_op_ge = 15;
    public static final int pet_op_lt = 16;
    public static final int pet_op_gt = 17;
    public static final int pet_op_minus = 18;
    public static final int pet_op_post_inc = 19;
    public static final int pet_op_post_dec = 20;
    public static final int pet_op_pre_inc = 21;
    public static final int pet_op_pre_dec = 22;
    public static final int pet_op_address_of = 23;
    public static final int pet_op_assume = 24;
    public static final int pet_op_kill = 25;
    public static final int pet_op_and = 26;
    public static final int pet_op_xor = 27;
    public static final int pet_op_or = 28;
    public static final int pet_op_not = 29;
    public static final int pet_op_land = 30;
    public static final int pet_op_lor = 31;
    public static final int pet_op_lnot = 32;
    public static final int pet_op_cond = 33;
    public static final int pet_op_last = 34;

    public static boolean buildAthenaPetLibrary() {
        MGLog.printlnTagged("Builder");
        Class<?> c = AthenaPetJNI.class;
        Builder builder;
        try {
            builder = new Builder().classesOrPackages(c.getName());
            String libPath = Paths.get(MGEnvVars.MGHome(), "lib").toString();
            String libPet = Paths.get(libPath, "AthenaPet").toString();
            String libInclude = Paths.get(libPet, "include").toString();
            String libpeta = Paths.get(libPet, "lib", "libpet.a").toString();
            String libisla = Paths.get(libPet, "lib", "libisl.a").toString();
            String[] options = {"-I" + libInclude, libpeta, libisla, "-lgmp"};
            builder.compilerOptions(options);
            builder.outputDirectory(libPath);
            builder.build();
        } catch (Exception e) {
            return false;
        }

        if (!MGEnvVars.loadNativeLib("libjniAthenaPet", "ATHENAPET_LD_LIBRARY"))
            return false;

        long timeload = System.currentTimeMillis();
        if (AthenaPet.verify())
            MGLog.printlnTagged("AthenaPet verify().. success! Time: " + (System.currentTimeMillis() - timeload));
        else
            return false;
        return true;
    }

    @Name("std::vector<std::string>")
    protected static class StringVector extends Pointer {

        public StringVector(Pointer p) {
            super(p);
        }

        public StringVector(String... array) {
            this(array.length);
            put(array);
        }

        public StringVector() {
            allocate();
        }

        public StringVector(long n) {
            allocate(n);
        }

        private native void allocate();

        private native void allocate(@Cast("size_t") long n);

        public native @Name("operator=") @ByRef StringVector put(@ByRef StringVector x);

        public native long size();

        public native void resize(@Cast("size_t") long n);

        @Index
        public native @StdString String get(@Cast("size_t") long i);

        public native StringVector put(@Cast("size_t") long i, String value);

        public StringVector put(String... array) {
            if (size() != array.length) {
                resize(array.length);
            }
            for (int i = 0; i < array.length; i++) {
                put(i, array[i]);
            }
            return this;
        }
    }

    protected native Pointer athena_create_athena_pet(int DEBUG);

    protected native void athena_add_local(Pointer aps, String name, String type);

    protected native Pointer athena_expr_id_int(Pointer aps, String name, int dim, int array_size);

    protected native Pointer athena_expr_id_float(Pointer aps, String name, int dim, int array_size);

    protected native Pointer athena_expr_int(Pointer aps, int value);

    protected native Pointer athena_expr_float_literal(Pointer aps, double d, String s);

    protected native Pointer athena_expr_assign(Pointer aps, Pointer lhs, Pointer rhs);

    protected native Pointer athena_expr_op_assign(Pointer aps, Pointer lhs, int op, Pointer rhs);

    protected native Pointer athena_expr_binary_op(Pointer aps, Pointer lhs, int op, Pointer rhs, int type_size);

    protected native Pointer athena_expr_unary_op(Pointer aps, Pointer arg, int op);

    protected native Pointer athena_extract_unary_increment(Pointer aps);

    protected native Pointer athena_extract_unary_decrement(Pointer aps);

    protected native Pointer athena_extract_binary_increment(Pointer aps, int op, Pointer expr, Pointer expr_iv);

    protected native Pointer athena_extract_compound_increment(Pointer aps, int op, Pointer expr);

    protected native Pointer athena_expr_access_subscript(Pointer aps, Pointer base_expr, Pointer index);

    protected native Pointer athena_expr_ternary(Pointer aps, Pointer cond, Pointer lhs, Pointer rhs);

    protected native Pointer athena_expr_for(Pointer aps, int independent, int declared, Pointer iv, Pointer init,
                    Pointer cond, Pointer inc, Pointer body);

    protected native Pointer athena_expr_while(Pointer aps, Pointer pe_cond, Pointer tree);

    protected native Pointer athena_expr_break(Pointer aps);

    protected native Pointer athena_expr_if(Pointer aps, Pointer cond, Pointer then_body);

    protected native Pointer athena_expr_if_else(Pointer aps, Pointer cond, Pointer then_body, Pointer else_body);

    protected native Pointer athena_expr_to_tree(Pointer aps, Pointer expr);

    protected native Pointer athena_new_block(Pointer aps, int isblock, int nstmts);

    protected native Pointer athena_add_child_to_tree_block(Pointer aps, Pointer block, Pointer child);

    protected native Pointer athena_set_tree_label(Pointer aps, Pointer tree, String name);

    protected native Pointer athena_tree_to_scop(Pointer aps, Pointer tree);

    protected native Pointer athena_extract_dependences(Pointer scop);

    protected native @ByVal StringVector athena_get_loop_list(Pointer l);

    protected native @ByVal StringVector athena_get_stmt_list_in(Pointer l);

    protected native @ByVal StringVector athena_get_stmt_list_out(Pointer l);

    protected native void athena_delete_results(Pointer l);

    protected static native void verifyC();

    protected AthenaPetJNI() {
    }

    @TruffleBoundary
    public static boolean verifyJNI() {
        AthenaPetJNI a = new AthenaPetJNI();
        Pointer aps = a.athena_create_athena_pet(0);

        /*
         * for (i = 0; i < 1024; i++){ C[i - x] += 1; // stmt_1 if( i < 100 ) { C[i - x] += 300; }
         * else { C[i - x] += 200; } }
         */
        // add local vars
        a.athena_add_local(aps, "i", "int");

        // C[i - x] += 1;

        Pointer id_C = a.athena_expr_id_int(aps, "C", 1, 0); // C
        Pointer id_i = a.athena_expr_id_int(aps, "i", 0, 0); // i
        Pointer id_x = a.athena_expr_id_int(aps, "x", 0, 0); // x
        Pointer i_x = a.athena_expr_binary_op(aps, id_i, pet_op_sub, id_x, -32); // i
                                                                                 // -
                                                                                 // x
        Pointer C_i_x = a.athena_expr_access_subscript(aps, id_C, i_x); // C[i -
                                                                        // x]
        Pointer val_123 = a.athena_expr_int(aps, 123); // 123
        Pointer C_i_x_plus_equal_1 = a.athena_expr_op_assign(aps, C_i_x, pet_op_add_assign, val_123);
        Pointer stmt_1 = a.athena_expr_to_tree(aps, C_i_x_plus_equal_1);
        stmt_1 = a.athena_set_tree_label(aps, stmt_1, "stmt_1");

        // if( i < 100 )
        id_i = a.athena_expr_id_int(aps, "i", 0, 0); // i
        Pointer val_100_cond = a.athena_expr_int(aps, 100); // 1024
        Pointer if_cond = a.athena_expr_binary_op(aps, id_i, pet_op_lt, val_100_cond, -32); // i
                                                                                            // <
                                                                                            // 1024

        // { C[i - x] += athena_300; }

        id_C = a.athena_expr_id_int(aps, "C", 1, 0); // C
        id_i = a.athena_expr_id_int(aps, "i", 0, 0); // i
        id_x = a.athena_expr_id_int(aps, "x", 0, 0); // x
        i_x = a.athena_expr_binary_op(aps, id_i, pet_op_sub, id_x, -32); // i -
                                                                         // x
        C_i_x = a.athena_expr_access_subscript(aps, id_C, i_x); // C[i - x]
        Pointer val_300 = a.athena_expr_int(aps, 300); // 300
        C_i_x_plus_equal_1 = a.athena_expr_op_assign(aps, C_i_x, pet_op_add_assign, val_300);
        Pointer stmt_2 = a.athena_expr_to_tree(aps, C_i_x_plus_equal_1);
        stmt_2 = a.athena_set_tree_label(aps, stmt_2, "stmt_2");
        Pointer if_body = a.athena_new_block(aps, 1, 1); // isblock =
                                                         // a.athena_1,
                                                         // nstmts =
                                                         // a.athena_1
        if_body = a.athena_add_child_to_tree_block(aps, if_body, stmt_2);

        // else { C[i - x] += a.athena_200; }
        id_C = a.athena_expr_id_int(aps, "C", 1, 0); // C
        id_i = a.athena_expr_id_int(aps, "i", 0, 0); // i
        id_x = a.athena_expr_id_int(aps, "x", 0, 0); // x
        i_x = a.athena_expr_binary_op(aps, id_i, pet_op_sub, id_x, -32); // i -
                                                                         // x
        C_i_x = a.athena_expr_access_subscript(aps, id_C, i_x); // C[i - x]
        Pointer val_200 = a.athena_expr_int(aps, 200); // 300
        C_i_x_plus_equal_1 = a.athena_expr_op_assign(aps, C_i_x, pet_op_add_assign, val_200);
        Pointer stmt_3 = a.athena_expr_to_tree(aps, C_i_x_plus_equal_1);
        stmt_3 = a.athena_set_tree_label(aps, stmt_3, "stmt_3");
        Pointer if_else = a.athena_new_block(aps, 1, 1); // isblock =
                                                         // a.athena_1,
                                                         // nstmts =
                                                         // a.athena_1
        if_else = a.athena_add_child_to_tree_block(aps, if_else, stmt_3);

        Pointer if_stmt = a.athena_expr_if_else(aps, if_cond, if_body, if_else);
        if_stmt = a.athena_set_tree_label(aps, if_stmt, "if_stmt");

        Pointer for_body = a.athena_new_block(aps, 1, 2); // isblock =
                                                          // a.athena_1,
                                                          // nstmts =
                                                          // a.athena_2
        for_body = a.athena_add_child_to_tree_block(aps, for_body, stmt_1);
        for_body = a.athena_add_child_to_tree_block(aps, for_body, if_stmt);

        // for (i = a.athena_0; i < 1024; i++)
        Pointer id_iv = a.athena_expr_id_int(aps, "i", 0, 0); // i
        Pointer val_init_iv = a.athena_expr_int(aps, 0); // 1
        Pointer id_iv_cond = a.athena_expr_id_int(aps, "i", 0, 0); // i
        Pointer val_1024_cond = a.athena_expr_int(aps, 1024); // 1024
        Pointer cond = a.athena_expr_binary_op(aps, id_iv_cond, pet_op_lt, val_1024_cond, -32); // i
                                                                                                // <
                                                                                                // 1024
        Pointer inc = a.athena_extract_unary_increment(aps);

        // glue
        Pointer for_stmt = a.athena_expr_for(aps, 0, 0, id_iv, val_init_iv, cond, inc, for_body);
        for_stmt = a.athena_set_tree_label(aps, for_stmt, "for_stmt");

        Pointer tree = a.athena_new_block(aps, 0, 1);
        tree = a.athena_add_child_to_tree_block(aps, tree, for_stmt);
        MGLog.printlnTagged("AthenaPet: create tree.. ok!");
        Pointer scop = a.athena_tree_to_scop(aps, tree);
        MGLog.printlnTagged("AthenaPet: create scop from tree.. ok!");
        Pointer l = a.athena_extract_dependences(scop);
        StringVector list = a.athena_get_loop_list(l);
        a.athena_delete_results(l);
        if (list.get(0).contentEquals("for_stmt")) {
            MGLog.printlnTagged("AthenaPet: data dependence check.. ok!");
        } else {
            MGLog.printlnTagged("AthenaPet: data dependence check.. FAILED!");
            return false;
        }
        System.gc();
        return true;

    }

}
