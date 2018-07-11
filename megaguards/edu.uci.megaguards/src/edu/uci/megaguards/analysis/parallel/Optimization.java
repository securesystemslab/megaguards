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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.analysis.bounds.FinalizedVariableValues;
import edu.uci.megaguards.analysis.exception.MGException;
import edu.uci.megaguards.analysis.parallel.polyhedral.AthenaPetTest;
import edu.uci.megaguards.ast.env.MGGlobalEnv;
import edu.uci.megaguards.ast.node.LoopInfo;
import edu.uci.megaguards.ast.node.MGNode;
import edu.uci.megaguards.ast.node.MGNodeFor;

public class Optimization {

    private MGNode root;
    private MGGlobalEnv env;
    private MGNodeOptions options;

    public Optimization(MGNode root, MGGlobalEnv env, MGNodeOptions options) {
        this.root = root;
        this.env = env;
        this.options = options;
    }

    @TruffleBoundary
    public MGNode optimizeLoop(FinalizedVariableValues finalizedValues) {
        MGNode optimized = root;
        MGNodeFor forNode_level1 = root instanceof MGNodeFor ? (MGNodeFor) root : null;

        if (forNode_level1 != null) {
            LoopInfo loopInfo1 = forNode_level1.getLoopInfo();
            if (loopInfo1.getTargetVar() != null)
                return optimized;

            if (forNode_level1.hasBreak())
                return optimized;

            if (finalizedValues.finalizeLoopInfo(loopInfo1) == null)
                return optimized;

            boolean dd = checkDataDependence(forNode_level1, finalizedValues);
            if (!dd)
                return optimized;

            // Now we should inline forNode body
            // replace root nodes with this forNode body
            optimized = forNode_level1.getForBody();

            env.increaseLevel();
            env.setIteratorVar(loopInfo1.getInductionVariable(), 1);
            env.setGlobalLoopInfo(loopInfo1, 1);
            // add to codeWriter
            // it has been inline.. This is the 2nd inner loop
            MGNodeFor forNode_level2 = optimized instanceof MGNodeFor ? (MGNodeFor) optimized : null;
            if (forNode_level2 != null) {
                LoopInfo loopInfo2 = forNode_level2.getLoopInfo();
                if (loopInfo2.getTargetVar() != null)
                    return optimized;

                if (forNode_level2.hasBreak())
                    return optimized;

                if (finalizedValues.finalizeLoopInfo(loopInfo2) == null)
                    return optimized;

                dd = checkDataDependence(forNode_level2, finalizedValues);
                if (!dd)
                    return optimized;

                // Now we should inline forNode body
                // replace root nodes with this forNode body
                optimized = forNode_level2.getForBody();

                env.increaseLevel();
                env.setIteratorVar(loopInfo2.getInductionVariable(), 2);
                env.setGlobalLoopInfo(loopInfo2, 2);

            }
        }

        return optimized;
    }

    @TruffleBoundary
    private boolean checkDataDependence(MGNodeFor forNode, FinalizedVariableValues finalizedValues) {
        boolean dd = true;
        if (this.options != null && this.options.isDDOffAll())
            return dd;

        this.options = forNode.getLoopInfo().getOptions();
        if (!(this.options != null && this.options.isDDOff())) {
            return !forNode.isDependenceExists();
        } else {
            final AthenaPetTest checkDDep = new AthenaPetTest(forNode.getForBody(), forNode.getLoopInfo(),
                            env, finalizedValues);
            try {
                dd = checkDDep.testDependence(forNode);
                dd = (dd) ? checkDDep.testArrayReferences(env) : dd;
            } catch (MGException e) {
                dd = false;
            }

        }
        return dd;
    }

}
