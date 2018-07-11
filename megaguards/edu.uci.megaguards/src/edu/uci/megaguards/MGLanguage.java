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

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

@TruffleLanguage.Registration(id = "MG", name = "MegaGuards", version = "0.1", mimeType = MGLanguage.MIME_TYPE)
@ProvidedTags({StandardTags.CallTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public class MGLanguage extends TruffleLanguage<MGContext> {

    public static final String MIME_TYPE = "application/x-megaguards";

    public final static String EMPTY = "EMPTY";
    @Option(help = "Enable MegaGuards", deprecated = false, category = OptionCategory.USER) //
    public static final OptionKey<Boolean> MegaGuards = new OptionKey<>(false);

    @Option(help = "Debug level", deprecated = false, category = OptionCategory.DEBUG) //
    public static final OptionKey<Integer> DEBUG = new OptionKey<>(0);

    @Option(help = "Enforce bounds check", deprecated = false, category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> BoundCheck = new OptionKey<>(true);

    @Option(help = "Options for JUnit", deprecated = false, category = OptionCategory.DEBUG) //
    public static final OptionKey<Boolean> JUnit = new OptionKey<>(false);

    @Option(help = "Logging", deprecated = false, category = OptionCategory.DEBUG) //
    public static final OptionKey<String> Logging = new OptionKey<>(EMPTY);

    @Option(help = "Scan arrays for Min and Max values", deprecated = false, category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> ScanArrayMinMax = new OptionKey<>(true);

    /*- FIXME: too slow */
    @Option(help = "Scan arrays for unique values", deprecated = false, category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> ScanArrayUniqueValues = new OptionKey<>(false);

    @Option(help = "Number of allowed loops inside a nested function", deprecated = false, category = OptionCategory.EXPERT) //
    public static final OptionKey<Integer> MaxNumberOfLoopsInFunction = new OptionKey<>(5);

    @Option(help = "Force use of long storage instead of integer values", deprecated = false, category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> ForceLong = new OptionKey<>(false);

    @Option(help = "Reuse unboxed arrays from previous execution", deprecated = false, category = OptionCategory.EXPERT) //
    public static final OptionKey<Boolean> ReUseProcessedStorage = new OptionKey<>(true);

    public static MGLanguage INSTANCE = create();

    private MGContext context;

    public MGLanguage() {
        INSTANCE = this;
        this.context = null;
    }

    @SuppressWarnings("deprecation")
    private static MGLanguage create() {
        com.oracle.truffle.api.vm.PolyglotEngine engine = com.oracle.truffle.api.vm.PolyglotEngine.newBuilder().build();
        try {
            engine.eval(Source.newBuilder("").name("").mimeType(MGLanguage.MIME_TYPE).build());
        } catch (RuntimeException e) {
        }

        return INSTANCE;
    }

    @Override
    protected MGContext createContext(Env env) {
        this.context = new MGContext(env);
        return context;
    }

    public OptionValues getOptionValues() {
        return context.getEnv().getOptions();
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

}
