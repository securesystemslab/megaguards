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
package edu.uci.megaguards.object;

import edu.uci.megaguards.backend.parallel.ParallelWorkload;
import edu.uci.megaguards.unbox.Boxed;
import edu.uci.megaguards.unbox.Unboxer;

public final class MGIntArray extends MGArray {

    public MGIntArray(String name, Unboxer boxed, Boxed<?> valueNode) {
        super(name, boxed, valueNode);
        this.dataType = this.dataTypeHelper = DataType.IntArray;
    }

    public MGIntArray(String name, ParallelWorkload value) {
        super(name, value);
        this.dataType = this.dataTypeHelper = DataType.IntArray;
    }

    public MGIntArray(String name, MGArray valueRef) {
        super(name, valueRef);
        this.dataType = this.dataTypeHelper = DataType.IntArray;
    }

    public MGIntArray(String name, MGArray valueRef, ArrayInfo info) {
        super(name, valueRef, info);
        this.dataType = this.dataTypeHelper = DataType.IntArray;
    }

    private MGIntArray(MGIntArray o) {
        super(o);
        updateType();
    }

    @Override
    public final void updateType() {
        if (getDim() == 0) {
            this.dataType = DataType.Int;
        }
    }

    @Override
    public final MGStorage copy() {
        return new MGIntArray(this);
    }

}
