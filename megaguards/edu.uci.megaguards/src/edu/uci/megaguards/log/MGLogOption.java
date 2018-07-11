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

import java.util.ArrayList;
import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class MGLogOption {

    public static final HashMap<String, MGLogOption> logOptions = new HashMap<>();
    private static final HashMap<Character, MGLogOption> logOptionsChar = new HashMap<>();
    public static final ArrayList<String> summaryFields = new ArrayList<>();
    public static final ArrayList<String> nonsummaryFields = new ArrayList<>();
    public static final ArrayList<String> jsonFields = new ArrayList<>();
    public static final ArrayList<String> jsonSummaryFields = new ArrayList<>();
    public static final ArrayList<String> cmdOptions = new ArrayList<>();

    private final String name;

    private final boolean summary;
    private final boolean control;

    private final String runtimeMsg;
    private final String jsonName;

    private final String cmdDescription;
    private final char cmdOption;
    private boolean enabled;

    private MGLogValue<?> defaultValue;

    public MGLogOption(String name, String runtimeMsg, String jsonName, String cmdDescription, char cmdOption, boolean summary, boolean control) {
        this.name = name;
        this.runtimeMsg = runtimeMsg;
        this.jsonName = jsonName;
        this.summary = summary;
        this.cmdOption = cmdOption;
        this.cmdDescription = cmdDescription;
        this.control = control;
        this.defaultValue = null;
        this.enabled = false;
    }

    public String getName() {
        return name;
    }

    public boolean isSummary() {
        return summary;
    }

    public String getRuntimeMsg() {
        return runtimeMsg;
    }

    public String getJsonName() {
        return jsonName;
    }

    public String getCmdDescription() {
        return cmdDescription;
    }

    public char getCmdOption() {
        return cmdOption;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isControl() {
        return control;
    }

    public MGLogValue<?> getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String v) {
        this.defaultValue = new MGLogValue.OptionString(this, v);
    }

    public void setDefaultValue(long v) {
        this.defaultValue = new MGLogValue.OptionLong(this, v);
    }

    public void setDefaultValue(boolean v) {
        this.defaultValue = new MGLogValue.OptionBoolean(this, v);
    }

    @TruffleBoundary
    public static MGLogOption addOption(String name, String runtimeMsg, String jsonName, String cmdDescription, char cmdOption, boolean summary, boolean control) {
        MGLogOption option = new MGLogOption(name, runtimeMsg, jsonName, cmdDescription, cmdOption, summary, control);
        logOptions.put(name, option);
        logOptionsChar.put(cmdOption, option);
        if (jsonName != null) {
            jsonFields.add(name);
        }

        if (jsonName != null && summary) {
            jsonSummaryFields.add(name);
        }

        if (cmdDescription != null) {
            cmdOptions.add(name);
        } else {
            option.enabled = true;
        }

        if (summary)
            summaryFields.add(name);
        else
            nonsummaryFields.add(name);

        return option;
    }

    @TruffleBoundary
    public static void addControlOption(String name, String cmdDescription, char cmdOption) {
        addOption(name, null, null, cmdDescription, cmdOption, false, true);
    }

    @TruffleBoundary
    public static void enableOption(char cmdOption) {
        logOptionsChar.get(cmdOption).enabled = true;
    }

    @TruffleBoundary
    public static void enableOption(String name) {
        logOptions.get(name).enabled = true;
    }

    @TruffleBoundary
    public static boolean isEnabled(String name) {
        return logOptions.get(name).enabled;
    }
}
