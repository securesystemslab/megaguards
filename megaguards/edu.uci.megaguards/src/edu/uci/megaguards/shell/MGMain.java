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
package edu.uci.megaguards.shell;

import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.analysis.parallel.polyhedral.AthenaPetJNI;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.parallel.LoadLibraries;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLDevice;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLMGR;

public class MGMain {

    private static final String SEPARATOR = new String(new char[40]).replace("\0", "-");

    private static void enableMG() {
        MGOptions.MGOff = false;
        MGOptions.Backend.target = ExecutionMode.OpenCLAuto;
        LoadLibraries.loadParams();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            MGOptions.shellMGOptions(System.out);
            return;
        }

        if (args[0].contentEquals("clinfo")) {
            enableMG();
            System.out.println("Total number of OpenCL devices: " + OpenCLMGR.MGR.getNumDevices());
            System.out.println(SEPARATOR);
            System.out.println("Number of OpenCL CPU devices: " + OpenCLMGR.CPUs.size());
            System.out.println(SEPARATOR);
            for (OpenCLDevice d : OpenCLMGR.CPUs) {
                System.out.println(d.getSummary());
                System.out.println(SEPARATOR);
            }
            System.out.println("Number of OpenCL GPU devices: " + OpenCLMGR.GPUs.size());
            System.out.println(SEPARATOR);
            for (OpenCLDevice d : OpenCLMGR.GPUs) {
                System.out.println(d.getSummary());
                System.out.println(SEPARATOR);
            }
        }
        if (args[0].contentEquals("clinfo-json")) {
            enableMG();
            JSONObjectBuilder jsonOut = JSONHelper.object();
            jsonOut.add("Total number of OpenCL devices", OpenCLMGR.MGR.getNumDevices());
            jsonOut.add("Number of OpenCL CPU devices", OpenCLMGR.CPUs.size());
            jsonOut.add("Number of OpenCL GPU devices", OpenCLMGR.GPUs.size());
            JSONObjectBuilder jsonCPUs = JSONHelper.object();
            for (OpenCLDevice d : OpenCLMGR.CPUs) {
                final JSONObjectBuilder jsonCPU = JSONHelper.object();
                final String dname = d.descriptionJSON(jsonCPU);
                jsonCPUs.add(dname, jsonCPU);
            }
            jsonOut.add("CPUs", jsonCPUs);
            JSONObjectBuilder jsonGPUs = JSONHelper.object();
            for (OpenCLDevice d : OpenCLMGR.GPUs) {
                final JSONObjectBuilder jsonGPU = JSONHelper.object();
                final String dname = d.descriptionJSON(jsonGPU);
                jsonGPUs.add(dname, jsonGPU);
            }
            jsonOut.add("GPUs", jsonGPUs);

            String prettyJSON = jsonOut.toString();
            prettyJSON = prettyJSON.replace(", ", ",\n");
            prettyJSON = prettyJSON.replace("{", "{\n").replace("}", "\n}");
            prettyJSON = prettyJSON.replace("\n\"", "\n    \"");
            System.out.println(prettyJSON);

        }

        if (args[0].contentEquals("polyhedral-test")) {
            enableMG();
            if (AthenaPetJNI.verifyJNI()) {
                System.out.println("Polyhedral test is operational");
            } else {
                System.out.println("Polyhedral test FAILED!");
            }
        }
    }

}
