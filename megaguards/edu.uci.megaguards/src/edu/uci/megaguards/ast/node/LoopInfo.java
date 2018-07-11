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

import edu.uci.megaguards.MGNodeOptions;
import edu.uci.megaguards.analysis.bounds.node.MGBoundNodeRange;
import edu.uci.megaguards.ast.node.MGNodeBinOp.BinOpType;
import edu.uci.megaguards.object.MGObject;
import edu.uci.megaguards.object.MGStorage;

public class LoopInfo {

    private MGStorage targetVar;

    private MGStorage inductionVariable;

    private boolean simpleRange;
    // in cases with simple range
    private long[] range;

    // in cases with variables range
    private MGNode startNode;
    private MGNode stopNode;
    private MGNode stepNode;
    private MGNodeBinOp.BinOpType stopOp;
    private MGNodeBinOp.BinOpType stepOp;

    // in cases with simple range
    private long[] runtimeRange;

    private MGBoundNodeRange bounds;
    private long lowerBound;
    private long upperBound;

    private final MGNodeOptions options;

    private boolean reductionOpt;

    public LoopInfo(MGStorage inductionVariable, long[] range, MGNodeOptions options) {
        this.inductionVariable = inductionVariable;
        this.range = range;
        this.runtimeRange = range;
        this.startNode = null;
        this.stopNode = null;
        this.stepNode = null;

        this.targetVar = null;
        this.bounds = null;
        this.lowerBound = Integer.MIN_VALUE;
        this.upperBound = Integer.MAX_VALUE;

        this.simpleRange = true;
        this.options = options;
        this.stopOp = BinOpType.LessThan;
        this.stepOp = BinOpType.ADD;
        this.reductionOpt = false;
    }

    public LoopInfo(MGStorage inductionVariable, ArrayList<MGNode> range, MGNodeOptions options) {
        this(inductionVariable, new long[]{0, 0, 1}, options);
        if (range.size() < 3) {
            this.startNode = this.stopNode = this.stepNode = null;
        } else {
            this.startNode = range.get(0);
            this.stopNode = range.get(1);
            this.stepNode = range.get(2);
        }
        this.runtimeRange = null;
        this.simpleRange = false;
        this.reductionOpt = false;
    }

    public LoopInfo(MGStorage inductionVariable, MGNode stop, MGStorage targetVar, MGNodeOptions options) {
        this(inductionVariable, new long[]{0, 0, 1}, options);
        this.targetVar = targetVar;
        this.stopNode = stop;
        this.runtimeRange = null;
        this.simpleRange = false;
        this.reductionOpt = false;
    }

    public MGStorage getInductionVariable() {
        return inductionVariable;
    }

    public MGNode getStartNode() {
        return startNode;
    }

    public MGNode getStopNode() {
        return stopNode;
    }

    public MGNode getStepNode() {
        return stepNode;
    }

    public MGNodeBinOp.BinOpType getStepOp() {
        return stepOp;
    }

    public void setStepOp(MGNodeBinOp.BinOpType stepOp) {
        this.stepOp = stepOp;
    }

    public MGNodeBinOp.BinOpType getStopOp() {
        return stopOp;
    }

    public void setStopOp(MGNodeBinOp.BinOpType stopOp) {
        this.stopOp = stopOp;
    }

    public MGObject getTargetVar() {
        return targetVar;
    }

    public MGNodeOptions getOptions() {
        return options;
    }

    public MGBoundNodeRange getBounds() {
        return bounds;
    }

    public void setBounds(MGBoundNodeRange bounds) {
        this.bounds = bounds;
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public void setLowerBound(long lowerBound) {
        this.lowerBound = lowerBound;
    }

    public long getUpperBound() {
        return upperBound;
    }

    public void setUpperBound(long upperBound) {
        this.upperBound = upperBound;
    }

    public boolean isSimpleRange() {
        return this.simpleRange;
    }

    public long[] getRange() {
        if (!simpleRange && runtimeRange != null)
            return runtimeRange;
        return range;
    }

    public void setRange(long[] range) {
        this.range = range;
    }

    public long[] getRuntimeRange() {
        return runtimeRange;
    }

    public void setRuntimeRange(long[] runtimeRange) {
        this.runtimeRange = runtimeRange;
    }

    public boolean isReductionOpt() {
        return reductionOpt;
    }

    public void setReductionOpt() {
        this.reductionOpt = true;
    }

    public long getIterationCount() {
        final long[] r = getRange();
        return (r[1] - r[0]) / r[2];
    }
}
