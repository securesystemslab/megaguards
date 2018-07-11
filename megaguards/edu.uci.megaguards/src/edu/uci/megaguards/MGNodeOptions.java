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

import java.util.HashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class MGNodeOptions {

    private static final HashMap<Integer, MGNodeOptions> nodeOptions = new HashMap<>();

    private final static String MGtag = "@mg:";

    public final static String OFF = MGtag + "off";
    public final static String DD_OFF = MGtag + "ddoff";
    public final static String DD_OFF_ALL = MGtag + "ddoff-all";
    public final static String REDUCE_ON = MGtag + "reduce-on";

    private boolean MGOff;
    private boolean ddOff;
    private boolean ddOffAll;
    private boolean reduceOn;

    public MGNodeOptions() {
        this.MGOff = false;
        this.ddOff = false;
        this.ddOffAll = false;
        this.reduceOn = false;
    }

    public void setDDOff(boolean ddOff) {
        this.ddOff = ddOff;
    }

    public void setDDOffAll(boolean ddOffAll) {
        this.ddOffAll = ddOffAll;
        this.ddOff = this.ddOff || ddOffAll;
    }

    public boolean isReduceOn() {
        return reduceOn;
    }

    public void setReduceOn(boolean reduceOn) {
        this.reduceOn = reduceOn;
    }

    public void setMGOff(boolean MGOff) {
        this.MGOff = MGOff;
    }

    public boolean isMGOff() {
        return MGOff;
    }

    public boolean isDDOff() {
        return ddOff;
    }

    public boolean isDDOffAll() {
        return ddOffAll;
    }

    public MGNodeOptions merge(MGNodeOptions opt) {
        if (opt != null) {
            this.MGOff = this.MGOff || opt.MGOff;
            this.ddOff = this.ddOff || opt.ddOff || opt.ddOffAll;
            this.ddOffAll = this.ddOffAll || opt.ddOffAll;
            this.reduceOn = this.reduceOn || opt.reduceOn;
        }
        return this;
    }

    @TruffleBoundary
    public static boolean hasOptions(int hashCode) {
        return nodeOptions.containsKey(hashCode);
    }

    @TruffleBoundary
    public static MGNodeOptions getOptions(int hashCode) {
        return nodeOptions.get(hashCode);
    }

    @TruffleBoundary
    public static void addOptions(int hashCode, MGNodeOptions options) {
        nodeOptions.put(hashCode, options);
    }

    @TruffleBoundary
    public static void removeOptions(int hashCode) {
        nodeOptions.remove(hashCode);
    }

    public static void processOptions(String s, int hashCode) {
        String[] options = s.toLowerCase().split(" ");

        boolean set = false;
        MGNodeOptions nodeOpts = new MGNodeOptions();

        for (String opt : options) {
            if (opt.startsWith(MGtag)) {
                if (opt.contentEquals(OFF)) {
                    nodeOpts.setMGOff(true);
                    set = true;
                } else if (opt.contentEquals(DD_OFF)) {
                    nodeOpts.setDDOff(true);
                    set = true;
                } else if (opt.contentEquals(DD_OFF_ALL)) {
                    nodeOpts.setDDOffAll(true);
                    set = true;
                } else if (opt.contentEquals(REDUCE_ON)) {
                    nodeOpts.setReduceOn(true);
                    set = true;
                }
            }
        }
        if (set)
            addOptions(hashCode, nodeOpts);
    }
}
