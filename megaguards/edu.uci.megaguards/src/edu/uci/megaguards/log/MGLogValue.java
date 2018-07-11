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

import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

public abstract class MGLogValue<T> {

    protected MGLogOption option;

    protected MGLogValue(MGLogOption option) {
        this.option = option;
    }

    public abstract void setValue(T v);

    public abstract T getValue();

    public abstract String getRuntimeMsg();

    public abstract String getCSVFormat();

    public abstract void addJSONField(JSONObjectBuilder json);

    public abstract MGLogValue<?> copy();

    public static class OptionLong extends MGLogValue<Long> {

        private long value;

        public OptionLong(MGLogOption o, long v) {
            super(o);
            this.value = v;
        }

        @Override
        public void setValue(Long v) {
            this.value = v;
        }

        @Override
        public Long getValue() {
            return value;
        }

        @Override
        public String getRuntimeMsg() {
            return String.format(option.getRuntimeMsg(), this.value);
        }

        @Override
        public void addJSONField(JSONObjectBuilder json) {
            json.add(option.getJsonName(), this.value);
        }

        @Override
        public String getCSVFormat() {
            return String.format("%d, ", value);
        }

        @Override
        public MGLogValue<?> copy() {
            return new OptionLong(option, value);
        }
    }

    public static class OptionBoolean extends MGLogValue<Boolean> {

        private boolean value;

        public OptionBoolean(MGLogOption o, boolean v) {
            super(o);
            this.value = v;
        }

        @Override
        public void setValue(Boolean v) {
            this.value = v;
        }

        @Override
        public Boolean getValue() {
            return value;
        }

        @Override
        public String getRuntimeMsg() {
            return String.format(option.getRuntimeMsg(), this.value ? "true" : "false");
        }

        @Override
        public void addJSONField(JSONObjectBuilder json) {
            json.add(option.getJsonName(), this.value ? "true" : "false");
        }

        @Override
        public String getCSVFormat() {
            return String.format("%s, ", value ? "true" : "false");
        }

        @Override
        public MGLogValue<?> copy() {
            return new OptionBoolean(option, value);
        }
    }

    public static class OptionString extends MGLogValue<String> {

        private String value;

        public OptionString(MGLogOption o, String v) {
            super(o);
            this.value = v;
        }

        @Override
        public void setValue(String v) {
            this.value = v;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String getRuntimeMsg() {
            return String.format(option.getRuntimeMsg(), this.value);
        }

        @Override
        public void addJSONField(JSONObjectBuilder json) {
            json.add(option.getJsonName(), this.value);
        }

        @Override
        public String getCSVFormat() {
            return String.format("%s, ", value);
        }

        @Override
        public MGLogValue<?> copy() {
            return new OptionString(option, value);
        }
    }

}
