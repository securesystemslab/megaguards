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
package edu.uci.megaguards;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import edu.uci.megaguards.ast.node.MGNodeMathFunction;
import edu.uci.megaguards.ast.node.MGNodeMathFunction.MathFunctionType;
import edu.uci.megaguards.backend.ExecutionMode;
import edu.uci.megaguards.backend.parallel.opencl.OpenCLMGR;
import edu.uci.megaguards.log.MGLog;
import edu.uci.megaguards.log.MGLogOption;

public class MGOptions {

    public static boolean MGOff = !getValue(MGLanguage.MegaGuards);
    public static int Debug = getValue(MGLanguage.DEBUG);
    public static final boolean testTrowable = false;
    public static boolean boundCheck = getValue(MGLanguage.BoundCheck); // true
    public static boolean junit = getValue(MGLanguage.JUnit);
    public static boolean logging = getValue(MGLanguage.Logging) != MGLanguage.EMPTY;
    public static boolean scanArrayMinMax = getValue(MGLanguage.ScanArrayMinMax);
    public static boolean ScanArrayUniqueness = getValue(MGLanguage.ScanArrayUniqueValues);
    public static final int FunctionLoopsLimit = getValue(MGLanguage.MaxNumberOfLoopsInFunction);
    public static boolean forceLongType = getValue(MGLanguage.ForceLong);
    public static final boolean ReuseStorage = getValue(MGLanguage.ReUseProcessedStorage);

    public static <T> T getValue(OptionKey<T> option) {
        final OptionValues ov = MGLanguage.INSTANCE.getOptionValues();
        return option.getValue(ov);
    }

    public static <T> void setValue(OptionKey<T> option, T value) {
        MGLanguage.INSTANCE.getOptionValues().set(option, value);
    }

    public static class Backend {

        protected static final String MG = "mg.";
        protected static final String BACKEND = MG + "backend.";
        public static final HashSet<MGNodeMathFunction.MathFunctionType> MathFunctionBlackList = populateMathFunctionBlackList();

        public static ExecutionMode target = ExecutionMode.NormalCPU;

        public static final boolean RecycleKernel = !Boolean.getBoolean(BACKEND + "RecycleKernel");

        public static int Debug = 0;

        public static boolean concurrentCompiler = !Boolean.getBoolean(BACKEND + "ConcurrentCompiler"); // true

        public static boolean AthenaPet = !Boolean.getBoolean(BACKEND + "AthenaPet"); // true

        public static int AthenaPetJNIDebug = 0;

        public static int AthenaPetDebug = 0;

        public static int BoundCheckDebug = 0;

        public static long offloadThreshold = 1000;

        public static int RetryLimit = 5;

        public static boolean cleanup = Boolean.getBoolean(BACKEND + "AthenaPet");

        public static double deviceMemoryPortion = 0.7;

        public static double localSizeRetry = 0.9;

        public static boolean clinfo = Boolean.getBoolean(BACKEND + "AthenaPet");

        public static boolean oclExceptions = !Boolean.getBoolean(BACKEND + "AthenaPet");

        public static boolean inlineCalls = Boolean.getBoolean(BACKEND + "AthenaPet");

        public static boolean disableDataManagementOptimization = Boolean.getBoolean(BACKEND + "disableKDM");

        public static int oclCPUNumCores = -1;

        public static boolean allowInAccurateMathFunctions = !Boolean.getBoolean(BACKEND + "RelaxedMathFunctions");

        public static int AutoDiff = 5; // %5

        public static int AutoTries = 1;

        public static int AutoMethod = 0; // min: 0, avg: 1

        public static boolean reductionOptimization = false;

        private static HashSet<MathFunctionType> populateMathFunctionBlackList() {
            HashSet<MathFunctionType> bl = new HashSet<>();
            // bl.add(MathFunctionType.cos);
            return bl;
        }

    }

    public static class Log {
        public static boolean CSV = false;
        public static boolean JSON = false;
        public static boolean NodeProfileJSON = false;
        public static boolean Exception = false;
        public static boolean ExceptionStack = false;
        public static boolean ExceptionGuestCode = false;
        public static boolean TraceUnboxing = false;
        public static boolean Summary = false;

        public static void updateLogOptions() {
            CSV = MGLogOption.isEnabled("CSV");
            JSON = MGLogOption.isEnabled("JSON");
            NodeProfileJSON = MGLogOption.isEnabled("NodeProfileJSON");
            Exception = MGLogOption.isEnabled("Exception");
            ExceptionStack = MGLogOption.isEnabled("ExceptionStack");
            ExceptionGuestCode = MGLogOption.isEnabled("ExceptionGuestCode");
            TraceUnboxing = MGLogOption.isEnabled("UnboxTime");
            Summary = MGLogOption.isEnabled("Summary");
        }
    }

    public static final boolean optionsLoaded = loadLogOptions();

    public static boolean loadLogOptions() {
        MGLogOption.addControlOption("CSV", "Output as CSV format", 'v');
        MGLogOption.addControlOption("NodeProfileJSON", "Output profile information as JSON format", 'p');
        MGLogOption.addControlOption("Exception", "Single line exception", 'e');
        MGLogOption.addControlOption("ExceptionStack", "Exception Stacktrace", 'k');
        MGLogOption.addControlOption("ExceptionGuestCode", "Exception of the related source code", 's');
        MGLogOption.addControlOption("JSON", "Output as JSON format", 'j');
        MGLogOption.addControlOption("Summary", "Summarize output before exit", 'r');

        MGLogOption.addOption("Filename", "%s", "filename", null, '0', true, false).setDefaultValue("NULL");
        MGLogOption.addOption("Line", null /*-"Line: %d"*/, "line", null, '0', false, false).setDefaultValue(-1);
        MGLogOption.addOption("Recycled", "Recycled: %s", "recycled", null, '0', false, false).setDefaultValue(true);
        MGLogOption.addOption("Executed", "Executed: %s", "executed", null, '0', false, false).setDefaultValue(true);
        MGLogOption.addOption("GuestCode", "Code:\n%s", null, "Related source code", 'q', false, false).setDefaultValue("NULL");
        MGLogOption.addOption("GeneratedCode", "Generated Code:\n%s", null, "Generated Code for the target device", 'c', false, false).setDefaultValue("NULL");
        MGLogOption.addOption("OffloadOffset", null /*-"Offload Offset: %d"*/, null, null, '0', false, false).setDefaultValue(-1);
        MGLogOption.addOption("FinalExecutionMode", null, "final_execution_device", null, '0', true, false).setDefaultValue("NULL");
        MGLogOption.addOption("ExecutionMode", "Execution Target: %s", "execution_device", "Used target device", 'x', true, false).setDefaultValue("NULL");
        MGLogOption.addOption("TotalTime", "Total Time: %d", "total_time", "Total Time", 't', true, false).setDefaultValue(0);
        MGLogOption.addOption("TranslationTime", "Translation Time: %d", "translation_time", "Translation Time", 'i', true, false).setDefaultValue(0);
        MGLogOption.addOption("ProfileTime", "Profile Time: %d ms", "profile_time", "Profiling time", 'f', true, false).setDefaultValue(0);
        MGLogOption.addOption("UnboxTime", "Unboxed in %d ms", "unbox_time", "Trace Unboxing", 'g', true, false).setDefaultValue(0);
        MGLogOption.addOption("DependenceTime", "Dependence Time: %d ms", "dependence_time", "Dependence Time", 'n', true, false).setDefaultValue(0);
        MGLogOption.addOption("DependenceCount", null /*-"Dependence Count: %d"*/, "dependence_count", null, '0', true, false).setDefaultValue(0);
        MGLogOption.addOption("BoundCheckTime", "Bound Check Time: %d ms", "bound_check_time", "Bound Check Time", 'b', true, false).setDefaultValue(0);
        MGLogOption.addOption("BoundCheckEnabled", null /*-"Bound Check Enabled: %s"*/, "bound_check_enabled", null, '0', true, false).setDefaultValue(MGOptions.boundCheck);
        MGLogOption.addOption("CompilationTime", "Compilation Time: %d ms", "compilation_time", "Compilation Time", 'm', true, false).setDefaultValue(0);
        MGLogOption.addOption("CodeGenerationTime", "Code Generation Time Time: %d ms", "code_generation_time", "Code Generation Time", 'o', true, false).setDefaultValue(0);
        MGLogOption.addOption("CoreExecutionTime", "Core Execution Time: %d ms", "core_execution_time", "Core Execution Time", 'u', true, false).setDefaultValue(0);
        MGLogOption.addOption("DataTransferTime", "Data Transfer Time: %d ms", "data_transfer_time", "Data Transfer Time", 'y', true, false).setDefaultValue(0);
        MGLogOption.addOption("TotalDataTransfer", "Total Data Transfer: %d byte", "total_data_transfer", "Total data transfer size", 'd', true, false).setDefaultValue(0);
        MGLogOption.addOption("TotalParallelLoops", "Total Parallel Loops: %d", "total_parallel_loops", "Total parallel loops counts", 'l', true, false).setDefaultValue(0);
        MGLogOption.addOption("TotalGeneratedKernels", null, "total_generated_kernels", null, '0', true, false).setDefaultValue(0);
        MGLogOption.addOption("TotalKernelExecutions", null, "total_kernels_executions", null, '0', true, false).setDefaultValue(0);
        MGLogOption.addOption("TotalTruffleExecutions", null, "total_Truffle_executions", null, '0', true, false).setDefaultValue(0);

        return true;
    }

    public static void shellMGOptions(PrintStream out) {
        out.println("--mg-target= option:");
        out.println("               GPU: OpenCL GPU (default)");
        out.println("               CPU: OpenCL CPU");

        out.println("--mg-target-no-bc:      Disable bound check");
        out.println("--mg-force:      Wait for all the compilation tasks");
        out.println("--mg-allow-int:  Allow creation of integer lists/arrays");

        out.println("--mg-log-file=<Logfile>:       Write logs to <Logfile>");
        out.println("--mg-log=:       print logs using option(s):");
        for (String o : MGLogOption.cmdOptions) {
            final MGLogOption opt = MGLogOption.logOptions.get(o);
            out.println(String.format("\t\t%s\t%s", opt.getCmdOption(), opt.getCmdDescription()));
        }

        out.println("--mg-threshold=<value>  Specify a threshold ( default:" + Backend.offloadThreshold + " )");

    }

    public static boolean setMGOption(String arg) {
        if (arg.startsWith("--mg-json-file=")) {
            String logfile = arg.replace("--mg-json-file=", "");
            MGLog.setJSONFile(logfile);
            return true;
        }

        if (arg.startsWith("--mg-log-file=")) {
            String logfile = arg.replace("-mg-log-file=", "");
            MGLog.setPrintOut(logfile);
            return true;
        }

        if (arg.startsWith("--mg-log=")) {
            String options = arg.replace("--mg-log=", "");
            logging = true;
            for (char option : options.toCharArray()) {
                MGLogOption.enableOption(option);
            }
            Log.updateLogOptions();
            return true;
        }

        if (arg.equals("--mg-force")) {
            Backend.concurrentCompiler = true;
            return true;
        }

        if (arg.equals("--mg-target-cleanup")) {
            Backend.cleanup = true;
            return true;
        }

        if (arg.equals("--mg-no-bc")) {
            MGOptions.boundCheck = false;
            return true;
        }

        if (arg.startsWith("--mg-target-threshold=")) {
            String option = arg.replace("--mg-target-threshold=", "");
            Backend.offloadThreshold = Long.valueOf(option);
            return true;
        }

        if (arg.startsWith("--mg-target-athenapetjni-debug=")) {
            String option = arg.replace("--mg-target-athenapetjni-debug=", "");
            Backend.AthenaPetJNIDebug = Integer.valueOf(option);
            return true;
        }

        if (arg.startsWith("--mg-debug=")) {
            String option = arg.replace("--mg-debug=", "");
            Debug = Integer.valueOf(option);
            Backend.Debug = Debug;
            return true;
        }

        if (arg.startsWith("--mg-target-athenapet-debug=")) {
            String option = arg.replace("--mg-target-athenapet-debug=", "");
            Backend.AthenaPetDebug = Integer.valueOf(option);
            return true;
        }

        if (arg.startsWith("--mg-target-cpu-cores=")) {
            String option = arg.replace("--mg-target-cpu-cores=", "");
            Backend.oclCPUNumCores = Integer.valueOf(option);
            return true;
        }

        if (arg.equals("--mg-target-no-dm-opt")) {
            Backend.disableDataManagementOptimization = true;
            return true;
        }

        if (arg.equals("--mg-target-no-athenapet")) {
            Backend.AthenaPet = false;
            return true;
        }

        if (arg.equals("--mg-target") || arg.startsWith("--mg-target=")) {
            Backend.target = ExecutionMode.Truffle;
            if (System.getProperty("os.name", "generic").contains("Linux")) {
                if (OpenCLMGR.MGR.getNumDevices() > 0) {
                    Backend.target = ExecutionMode.OpenCLAuto;
                    if (arg.contains("=")) {
                        String option = arg.replace("--mg-target=", "");
                        if (option.toLowerCase().equals("gpu")) {
                            Backend.target = ExecutionMode.OpenCLGPU;
                            OpenCLMGR.MGR.isValidGPUDevice();
                        } else if (option.toLowerCase().equals("cpu")) {
                            Backend.target = ExecutionMode.OpenCLCPU;
                            OpenCLMGR.MGR.isValidCPUDevice();
                        } else if (option.toLowerCase().equals("truffle"))
                            Backend.target = ExecutionMode.Truffle;
                    } else {
                        OpenCLMGR.MGR.isValidAutoDevice();
                    }
                }
                if (Debug > 0)
                    MGLog.printlnTagged("Selected: " + Backend.target);
                MGOff = false;
            }
            return true;
        }

        return false;
    }

    public static String[] processMGOptions(String[] args) {
        if (MGOptions.junit)
            return args;
        if (optionsLoaded) {
            ArrayList<String> rest = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                if (setMGOption(args[i]))
                    continue;
                rest.add(args[i]);
            }
            return rest.toArray(new String[rest.size()]);
        }
        return args;
    }
}
