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
package edu.uci.megaguards.analysis.parallel.reduction;

import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.object.ArrayInfo;
import edu.uci.megaguards.object.DataType;
import edu.uci.megaguards.unbox.StaticUnboxer;

public class ReductionWorkload extends ParallelWorkload {

    public ReductionWorkload(LoadType type, int[] dim, DataType dataType) {
        super(type, dim, dataType);
    }

    @Override
    public boolean ensureCapacity(long size) {
        if (type == LoadType.LocalSize)
            return false;

        if (this.info == null || info.getSize(0) != size) {
            final int len = (int) size;
            switch (dataType) {
                case Int:
                    final int[] i = new int[len];
                    this.info = new ArrayInfo(int[].class, len);
                    this.boxed = new StaticUnboxer.IntArray(i, info, i.hashCode(), true);
                    return true;
                case Long:
                    final long[] l = new long[len];
                    this.info = new ArrayInfo(long[].class, len);
                    this.boxed = new StaticUnboxer.LongArray(l, info, l.hashCode(), true);
                    return true;
                case Double:
                    final double[] d = new double[len];
                    this.info = new ArrayInfo(double[].class, len);
                    this.boxed = new StaticUnboxer.DoubleArray(d, info, d.hashCode(), true);
                    return true;
                case Bool:
                    final boolean[] b = new boolean[len];
                    this.info = new ArrayInfo(boolean[].class, len);
                    this.boxed = new StaticUnboxer.BooleanArray(b, info, b.hashCode(), true);
                    return true;
            }
        }

        // TODO: Might need to reset the stored values.
        return false;
    }

}
