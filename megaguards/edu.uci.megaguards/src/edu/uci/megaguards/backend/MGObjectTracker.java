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
package edu.uci.megaguards.backend;

import java.util.Arrays;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import edu.uci.megaguards.analysis.parallel.exception.CompilationException;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLData;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLDevice;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLMGR;

public class MGObjectTracker {

    private final HashMap<Integer, Integer> HashtoIndex;
    private final int[] IndexToHash;
    private final boolean[] RestoreTruffleChanges;
    private final boolean[] TruffleChanged;
    private final boolean[] OpenCLChanged;
    private int currentIndex;

    private OpenCLDevice device;

    @TruffleBoundary
    public MGObjectTracker(int sizeOfChangeList) {
        this.TruffleChanged = new boolean[sizeOfChangeList];
        this.RestoreTruffleChanges = new boolean[sizeOfChangeList];
        this.OpenCLChanged = new boolean[sizeOfChangeList];
        this.IndexToHash = new int[sizeOfChangeList];
        this.HashtoIndex = new HashMap<>(sizeOfChangeList);
        this.currentIndex = 0;
        this.device = OpenCLMGR.MGR.getDevice();
        Arrays.fill(TruffleChanged, false);
        Arrays.fill(RestoreTruffleChanges, false);
        Arrays.fill(OpenCLChanged, false);
        Arrays.fill(IndexToHash, -1);
    }

    @TruffleBoundary
    public int getIndex(int hashCode) {
        if (!HashtoIndex.containsKey(hashCode)) {
            HashtoIndex.put(hashCode, currentIndex);
            IndexToHash[currentIndex] = hashCode;
            currentIndex++;
            return currentIndex - 1;
        }

        return HashtoIndex.get(hashCode);
    }

    public void getAllOpenCLData() {
        if (this.device != null) {
            for (int index = 0; index < currentIndex; index++) {
                if (OpenCLChanged[index]) {
                    getDataFromOpenCL(index);
                    OpenCLChanged[index] = false;
                }
            }
        }
    }

    public void setDevice(OpenCLDevice device) {
        if (this.device != null && this.device != device) {
            getAllOpenCLData();
        }
        this.device = device;
    }

    @TruffleBoundary
    private void getDataFromOpenCL(int index) {
        final int hashCode = IndexToHash[index];
        final OpenCLData d = OpenCLData.getData(hashCode);
        assert device != null;
        if (d.getOnDeviceData(device).isLoaded()) {
            boolean success = d.getOnDeviceData(device).get();
            if (!success) {
                throw CompilationException.INSTANCE.message(String.format("ERROR while getting the data from '%s'", device.getDeviceName()));
            }
        }
    }

    public boolean updateDataForTruffle(int index) {
        if (OpenCLChanged[index]) {
            getDataFromOpenCL(index);
            OpenCLChanged[index] = false;
            return true;
        }
        return false;
    }

    public void setTruffleChanged(int index) {
        TruffleChanged[index] = true;
        RestoreTruffleChanges[index] = true;
    }

    public boolean shouldTruffleRestore(int index) {
        if (RestoreTruffleChanges[index]) {
            RestoreTruffleChanges[index] = false;
            return true;
        }
        return false;
    }

    public boolean updateDataForOpenCL(int index) {
        if (TruffleChanged[index]) {
            TruffleChanged[index] = false;
            return true;
        }
        return false;
    }

    public void setOpenCLChanged(int index) {
        OpenCLChanged[index] = true;
    }

    @TruffleBoundary
    public void reset() {
        currentIndex = 0;
        HashtoIndex.clear();
        Arrays.fill(TruffleChanged, false);
        Arrays.fill(RestoreTruffleChanges, false);
        Arrays.fill(OpenCLChanged, false);
        Arrays.fill(IndexToHash, -1);
    }
}
