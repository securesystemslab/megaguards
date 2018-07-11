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
package edu.uci.megaguards.log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import edu.uci.megaguards.MGOptions;
import edu.uci.megaguards.backend.truffle.node.MGTParallel;

public class MGLog {

    private static final ArrayList<MGLog> logs = new ArrayList<>();
    private static PrintStream out = System.out;
    private static PrintStream err = System.err;
    private static PrintStream json = System.out;

    public static final String tag = "[MegaGuards]";

    private static final int MSG_LINE_LENGTH = 120 - (tag.length() + 1);

    private static long ldload = 0;

    public final HashMap<String, MGLogValue<?>> values;

    private final SourceSection sourceSection;

    @TruffleBoundary
    public MGLog(SourceSection source) {
        this.sourceSection = source;
        this.values = new HashMap<>();
        if (source != null) {
            setOptionValue("Filename", source.getSource().getName() + ":" + source.getStartLine());
            setOptionValue("Line", source.getStartLine());
            setOptionValue("GuestCode", source.getCharacters().toString());
        }
    }

    public static long getLdload() {
        return ldload;
    }

    public static void setLdload(long ld) {
        ldload = ld;
    }

    public static PrintStream getPrintOut() {
        return out;
    }

    public static PrintStream getPrintErr() {
        return err;
    }

    public static PrintStream getPrintJSON() {
        return json;
    }

    @TruffleBoundary
    public static void setPrintOut(String logfile) {
        try {
            PrintStream logout = new PrintStream(new FileOutputStream(logfile, true));
            MGLog.out = logout;
        } catch (FileNotFoundException e) {
            printlnErrTagged("Log file '" + logfile + "' is not writable (" + e.getMessage() + ")");
        }
    }

    @TruffleBoundary
    public static void setJSONFile(String jsonfile) {
        String jsonfilename = jsonfile;
        if (jsonfilename == null || jsonfilename.contentEquals("")) {
            jsonfilename = "profile.json";
        }
        try {
            MGLog.json = new PrintStream(new FileOutputStream(jsonfilename, true));
        } catch (FileNotFoundException e) {
            printlnErrTagged("Profile file '" + jsonfilename + "' is not writable (" + e.getMessage() + ")");
        }
    }

    @TruffleBoundary
    public static MGLogOption getOption(String option) {
        if (MGLogOption.logOptions.containsKey(option)) {
            return MGLogOption.logOptions.get(option);
        } else
            getPrintErr().println(String.format("Option '%s' not found!", option));
        return null;
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @TruffleBoundary
    public void setOptionValue(String option, long v) {
        if (values.containsKey(option)) {
            ((MGLogValue.OptionLong) this.values.get(option)).setValue(v);
        } else {
            this.values.put(option, new MGLogValue.OptionLong(getOption(option), v));
        }
    }

    @TruffleBoundary
    public void setOptionValue(String option, boolean v) {
        if (values.containsKey(option)) {
            ((MGLogValue.OptionBoolean) this.values.get(option)).setValue(v);
        } else {
            this.values.put(option, new MGLogValue.OptionBoolean(getOption(option), v));
        }
    }

    @TruffleBoundary
    public void setOptionValue(String option, String v) {
        if (values.containsKey(option)) {
            ((MGLogValue.OptionString) this.values.get(option)).setValue(v);
        } else {
            this.values.put(option, new MGLogValue.OptionString(getOption(option), v));
        }
    }

    @TruffleBoundary
    public MGLogValue<?> getOptionValue(String option) {
        if (this.values.containsKey(option)) {
            return this.values.get(option);
        } else {
            return getOption(option).getDefaultValue();
        }
    }

    @TruffleBoundary
    public long getOptionValueLong(String option) {
        if (this.values.containsKey(option)) {
            return ((MGLogValue.OptionLong) this.values.get(option)).getValue();
        } else {
            return ((MGLogValue.OptionLong) getOption(option).getDefaultValue()).getValue();
        }
    }

    @TruffleBoundary
    public boolean getOptionValueBoolean(String option) {
        if (this.values.containsKey(option)) {
            return ((MGLogValue.OptionBoolean) this.values.get(option)).getValue();
        } else {
            return ((MGLogValue.OptionBoolean) getOption(option).getDefaultValue()).getValue();
        }
    }

    @TruffleBoundary
    public String getOptionValueString(String option) {
        if (this.values.containsKey(option)) {
            return ((MGLogValue.OptionString) this.values.get(option)).getValue();
        } else {
            return ((MGLogValue.OptionString) getOption(option).getDefaultValue()).getValue();
        }
    }

    @TruffleBoundary
    public static ArrayList<MGLog> getLogs() {
        return logs;
    }

    @TruffleBoundary
    public static void addLog(MGLog log) {
        MGLog.logs.add(log);
    }

    @TruffleBoundary
    public void printLog() {
        if (!(MGOptions.Log.Summary)) // v
        {
            if ((MGOptions.Log.CSV)) // v
                printLogCSV();
            else if ((MGOptions.Log.JSON)) // v
                printLogJSON();
            else
                printLogTagged();
        }
    }

    public void printGeneratedCode(String code) {
        if (MGOptions.Debug > 0 && MGOptions.logging && !(MGOptions.Log.Summary)) {
            println(code);
        }
    }

    @TruffleBoundary
    private void printLogTagged() {

        String msg = "";
        if (!MGOptions.Log.Summary) {
            for (String option : MGLogOption.nonsummaryFields) {
                if (!this.values.containsKey(option))
                    continue;
                MGLogOption o = getOption(option);
                if (o.isEnabled() && o.getRuntimeMsg() != null) {
                    msg += getOptionValue(option).getRuntimeMsg() + " | ";
                }

                if (msg.length() > MSG_LINE_LENGTH) {
                    printlnTagged(msg);
                    msg = "";
                }
            }
        }

        for (String option : MGLogOption.summaryFields) {
            MGLogOption o = getOption(option);
            if (o.isEnabled() && o.getRuntimeMsg() != null) {
                msg += getOptionValue(option).getRuntimeMsg() + " | ";
            }

            if (msg.length() > MSG_LINE_LENGTH) {
                printlnTagged(msg);
                msg = "";
            }
        }
        if (msg.length() > 0) {
            printlnTagged(msg);
        }
    }

    @TruffleBoundary
    public void printLogJSON() {

        JSONObjectBuilder jsonOut = JSONHelper.object();

        ArrayList<String> fields = null;
        if ((MGOptions.Log.Summary)) {
            fields = MGLogOption.jsonSummaryFields;
        } else {
            fields = MGLogOption.jsonFields;
        }

        for (String option : fields) {
            getOptionValue(option).addJSONField(jsonOut);
        }

        String prettyJSON = jsonOut.toString();
        prettyJSON = prettyJSON.replace(", ", ",\n");
        prettyJSON = prettyJSON.replace("{", "{\n").replace("}", "\n}");
        prettyJSON = prettyJSON.replace("\n\"", "\n    \"");
        getPrintJSON().println(prettyJSON);
    }

    @TruffleBoundary
    public void printLogCSV() {
        ArrayList<String> fields = null;
        if ((MGOptions.Log.Summary)) {
            fields = MGLogOption.jsonSummaryFields;
        } else {
            fields = MGLogOption.jsonFields;
        }

        for (String option : fields) {
            getPrintOut().print(getOption(option).getJsonName() + ", ");
        }
        getPrintOut().println();

        for (String option : fields) {
            getPrintOut().print(getOptionValue(option).getCSVFormat());
        }
        getPrintOut().println();

    }

    @TruffleBoundary
    public static void printSummary() {
        MGLog summary = new MGLog(null);
        ArrayList<String> fields = MGLogOption.jsonSummaryFields;
        MGLogValue<?> values[] = new MGLogValue<?>[fields.size()];
        final HashMap<String, Integer> executionMode = new HashMap<>();
        final HashMap<String, HashSet<Long>> finalExecutionMode = new HashMap<>();
        if (logs.size() > 0) {
            values[fields.indexOf("Filename")] = logs.get(0).getOptionValue("Filename").copy();
            values[fields.indexOf("BoundCheckEnabled")] = logs.get(0).getOptionValue("BoundCheckEnabled").copy();
            HashSet<String> avoidFields = new HashSet<>();
            avoidFields.add("Filename");
            avoidFields.add("BoundCheckEnabled");
            avoidFields.add("FinalExecutionMode");
            avoidFields.add("ExecutionMode");
            for (MGLog l : logs) {
                for (String f : fields) {
                    if (!(getOption(f).getDefaultValue() instanceof MGLogValue.OptionLong) || avoidFields.contains(f))
                        continue;

                    if (l.values.containsKey(f)) {
                        final int idx = fields.indexOf(f);
                        if (values[idx] == null) {
                            values[idx] = l.getOptionValue(f).copy();
                        } else {
                            final long acc = ((MGLogValue.OptionLong) values[idx]).getValue() + ((MGLogValue.OptionLong) l.getOptionValue(f)).getValue();
                            ((MGLogValue.OptionLong) values[idx]).setValue(acc);
                        }
                    }
                }

                if (l.values.containsKey("FinalExecutionMode")) {
                    final String fm = (String) l.values.get("FinalExecutionMode").getValue();
                    if (!finalExecutionMode.containsKey(fm))
                        finalExecutionMode.put(fm, new HashSet<>());
                    finalExecutionMode.get(fm).add(l.getOptionValueLong("Line"));
                }

                if (l.values.containsKey("ExecutionMode") && !l.values.containsKey("Executed")) {
                    final String em = (String) l.values.get("ExecutionMode").getValue();
                    if (!executionMode.containsKey(em)) {
                        executionMode.put(em, 1);
                    } else {
                        executionMode.put(em, executionMode.get(em) + 1);
                    }
                }

            }

            int idx = fields.indexOf("FinalExecutionMode");
            values[idx] = new MGLogValue.OptionString(getOption("FinalExecutionMode"), "");
            for (Entry<String, HashSet<Long>> e : finalExecutionMode.entrySet()) {
                final String acc = ((MGLogValue.OptionString) values[idx]).getValue() + String.format("%s:%d;;", e.getKey(), e.getValue().size());
                ((MGLogValue.OptionString) values[idx]).setValue(acc);
            }

            idx = fields.indexOf("ExecutionMode");
            values[idx] = new MGLogValue.OptionString(getOption("ExecutionMode"), "");
            for (Entry<String, Integer> e : executionMode.entrySet()) {
                final String acc = ((MGLogValue.OptionString) values[idx]).getValue() + String.format("%s:%d;;", e.getKey(), e.getValue());
                ((MGLogValue.OptionString) values[idx]).setValue(acc);
            }

            for (int i = 0; i < fields.size(); i++) {
                final String f = fields.get(i);
                if (values[i] == null) {
                    summary.values.put(f, getOption(f).getDefaultValue());
                } else {
                    summary.values.put(f, values[i]);
                }
            }

            summary.setOptionValue("DataTransferTime", summary.getOptionValueLong("DataTransferTime") + MGTParallel.OpenCLGetData.sharedDTLog.getOptionValueLong("DataTransferTime"));
        }

        if ((MGOptions.Log.JSON)) {
            summary.printLogJSON();
        } else if ((MGOptions.Log.CSV)) {
            summary.printLogCSV();
        } else {
            printlnTagged("Total number of executions: " + logs.size() + " times");
            summary.printLogTagged();
        }
    }

    @TruffleBoundary
    public void printException(Exception e) {
        if (MGOptions.Log.Exception)
            printException(e, getOptionValueString("Filename"), getOptionValueLong("Line"), getOptionValueString("GuestCode"));
    }

    @TruffleBoundary
    public static void printException(Exception e, SourceSection source) {
        if (MGOptions.Log.Exception) {
            if (source != null)
                printException(e, source.getSource().getName(), source.getStartLine(), source.getCharacters().toString());
            else if ((MGOptions.Log.ExceptionStack))
                e.printStackTrace(getPrintErr());
            else
                printlnErrTagged("Failed!: " + e.getMessage());
        }

    }

    @TruffleBoundary
    public static void printException(Exception e, String filename, long line, String code) {
        if (MGOptions.Log.Exception) {
            printlnErrTagged(String.format("%s:%d\tFailed!: %s", filename, line, e.getMessage()));

            if ((MGOptions.Log.ExceptionStack))
                e.printStackTrace(getPrintErr());

            if ((MGOptions.Log.ExceptionGuestCode))
                printlnErrTagged(code);
        }
    }

    @TruffleBoundary
    public void println(String msg) {
        println(getOptionValueString("Filename"), msg);
    }

    @TruffleBoundary
    public void print(String msg) {
        print(getOptionValueString("Filename"), msg);
    }

    @TruffleBoundary
    public static void print(String filename, String msg) {
        printlnTagged(String.format("%s\t%s", filename, msg));
    }

    @TruffleBoundary
    public static void println(String filename, String msg) {
        printlnTagged(String.format("%s\t%s", filename, msg));
    }

    @TruffleBoundary
    public static void printlnTagged(String msg) {
        getPrintOut().println(String.format("%s %s", tag, msg));
    }

    @TruffleBoundary
    public static void printlnErrTagged(String msg) {
        getPrintErr().println(String.format("%s %s", tag, msg));
    }

}
