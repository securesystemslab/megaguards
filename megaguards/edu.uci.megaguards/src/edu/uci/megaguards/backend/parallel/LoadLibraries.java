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
package edu.uci.megaguards.backend.parallel;

import edu.uci.megaguards.MGEnvVars;
import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.parallel.polyhedral.AthenaPetJNI;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLMGR;
import edu.uci.megaguards.log.MGLog;

public class LoadLibraries {

    private static boolean loaded = false;

    public static void loadParams() {
        if (!loaded && !MGOptions.MGOff) {
            MGEnvVars.MGHome();
            switch (MGOptions.Backend.target) {
                case OpenCLAuto:
                case OpenCLCPU:
                case OpenCLGPU:
                case Truffle:
                    if (OpenCLMGR.MGR.getNumDevices() == 0) {
                        if (MGOptions.Backend.target != ExecutionMode.Truffle) {
                            MGLog.printlnErrTagged("MegaGuards was not able to find an OpenCL device. Fallback to Truffle mode");
                            MGOptions.Backend.target = ExecutionMode.Truffle;
                        }
                    }
                    break;
            }

            if (!MGEnvVars.loadNativeLib("libjniAthenaPetJNI", "ATHENAPET_LD_LIBRARY") && !AthenaPetJNI.buildAthenaPetLibrary()) {
                MGLog.printlnErrTagged("MegaGuards was not able to find or build AthenaPet library. Polyhedral check will be disabled");
                MGOptions.Backend.target = ExecutionMode.Truffle;
                MGOptions.Backend.AthenaPet = false;
            }

        }

    }

}
