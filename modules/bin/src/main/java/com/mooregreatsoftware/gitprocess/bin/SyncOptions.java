/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mooregreatsoftware.gitprocess.bin;

import com.mooregreatsoftware.gitprocess.config.GeneralConfig;
import javaslang.control.Either;
import javaslang.control.Option;
import joptsimple.OptionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.PrintStream;

import static java.util.Arrays.asList;
import static javaslang.control.Either.right;

/**
 * CLI options for {@link SyncRunner}
 */
public class SyncOptions extends Options {
    private static final Logger LOG = LoggerFactory.getLogger(SyncOptions.class);

    @Nonnull
    private final GeneralConfig generalConfig;


    @SuppressWarnings("unused")
    protected SyncOptions(@Nonnull PrintStream printStream, @Nonnull GeneralConfig generalConfig) {
        super(printStream);
        this.generalConfig = generalConfig;
    }


    /**
     * Try to create an instance of {@link SyncOptions} but return an error message to print if not successful.
     *
     * @param args          the command line arguments
     * @param printStream   where to send logging output
     * @param generalConfig git configuration to use
     * @return Left(message to print before exiting) or Right(the options)
     */
    public static Either<String, SyncOptions> create(@Nonnull String[] args, @Nonnull PrintStream printStream, @Nonnull GeneralConfig generalConfig) {
        final SyncOptions syncOptions = new SyncOptions(printStream, generalConfig);
        final Option<String> msgOption = syncOptions.parse(args);
        return msgOption.
            <Either<String, SyncOptions>>map(Either::left).
            getOrElse(right(syncOptions));
    }


    /**
     * Try to create an instance of {@link SyncOptions} but return an error message to print if not successful.
     *
     * @param args          the command line arguments
     * @param generalConfig git configuration to use
     * @return Left(message to print before exiting) or Right(the options)
     */
    public static Either<String, SyncOptions> create(@Nonnull String[] args, @Nonnull GeneralConfig generalConfig) {
        return create(args, System.out, generalConfig);
    }


    @Nonnull
    public String description() {
        return "Syncs local changes with the server.";
    }


    @Nonnull
    public String usageInfo() {
        return "git sync [OPTIONS]";
    }


    /**
     * Override this to customize the OptionParser
     */
    @Nonnull
    protected OptionParser createOptionParser() {
        final OptionParser optionParser = super.createOptionParser();
        optionParser.accepts("local", "Don't fetch or push with remote");
        optionParser.acceptsAll(asList("r", "rebase"), "Rebase instead of merge against the integration branch (default: true)");
        optionParser.accepts("merge", "Merge instead of rebase against the integration branch");

        return optionParser;
    }


    @Override
    public boolean showHelp() {
        if (helpOptionValue()) return true;

        if (nonOptionArgs().size() > 1) {
            LOG.warn("Can have at most one branch name");
            return true;
        }

        if (mergeOptionValue() && rebaseOptionValue()) {
            LOG.warn("--rebase and --merge are mutually exclusive");
            return true;
        }

        return false;
    }


    public boolean localOnly() {
        return booleanValue("local");
    }


    public boolean rebase() {
        return (rebaseOptionValue() || generalConfig.defaultRebaseSync()) && !booleanValue("merge");
    }


    protected boolean rebaseOptionValue() {
        return rebaseOptionValue("rebase");
    }


    protected boolean rebaseOptionValue(String rebase) {
        return booleanValue(rebase);
    }


    public boolean merge() {
        return mergeOptionValue() || !(rebaseOptionValue() || generalConfig.defaultRebaseSync());
    }


    protected boolean mergeOptionValue() {
        return booleanValue("merge");
    }

}
