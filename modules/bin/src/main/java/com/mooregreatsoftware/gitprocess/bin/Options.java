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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.EncoderBase;
import javaslang.control.Try;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

import static com.mooregreatsoftware.gitprocess.bin.ReadVersionFromClasspath.version;
import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;
import static java.util.Arrays.asList;
import static java.util.Optional.empty;

/**
 * The options given for running the process commands.
 */
public abstract class Options {
    @MonotonicNonNull
    protected OptionSet optionSet;

    private final PrintStream printStream;


    protected Options() {
        this(System.out);
    }


    protected Options(PrintStream printStream) {
        this.printStream = printStream;
    }


    /**
     * Parse the command line options and handle the "standard" set before returning the result.
     *
     * @return the message to show before exiting; null if everything went well
     * @see #createOptionParser()
     * @see #handleStandardOptions(OptionParser)
     */
    @SuppressWarnings("RedundantTypeArguments")
    protected @Nullable String parse(String[] args) {
        return Try.<@Nullable String>of(() -> parseAndHandleStandardOptions(args)).
            recoverWith(e -> Try.success(e.getMessage())).
            get();
    }


    /**
     * Parse the command line options and handle the "standard" set before returning the result.
     *
     * @return the message to show before exiting; null if everything went well
     * @throws OptionException if problems are detected while parsing
     * @see #createOptionParser()
     * @see #handleStandardOptions(OptionParser)
     */
    private @Nullable String parseAndHandleStandardOptions(String[] args) throws OptionException {
        final OptionParser parser = createOptionParser();

        OptionSet optionSet = parser.parse(args);
        this.optionSet(optionSet);

        return handleStandardOptions(parser);
    }


    /**
     * Handle the "standard" options: info, quiet, verbose, help, version
     *
     * @return the message to show before exiting; null if everything went well
     */
    protected @Nullable String handleStandardOptions(OptionParser parser) {
        if (showHelp()) {
            return createHelp(parser);
        }
        else if (showVersion()) {
            final String version = version();
            return "version: " + (version != null ? version : "unknown");
        }
        else {
            return setupLogging();
        }
    }


    private String createHelp(OptionParser parser) {
        final StringWriter writer = new StringWriter();
        writer.write("USAGE: ");
        writer.write(usageInfo());
        writer.write(System.lineSeparator());
        writer.write(System.lineSeparator());
        writer.write(description());
        writer.write(System.lineSeparator());
        writer.write(System.lineSeparator());
        Try.run(() -> parser.printHelpOn(writer));
        writer.write(System.lineSeparator());
        writer.write("version: ");
        final String version = version();
        writer.write(version != null ? version : "unknown");
        writer.write(System.lineSeparator());
        return writer.toString();
    }


    /**
     * A short description for the command.
     */
    public abstract String description();


    /**
     * The syntax for running the command. (e.g., "git new-fb [OPTIONS] 'branch_name'")
     */
    public abstract String usageInfo();


    /**
     * Set up the logging system to show logs at the right level and at the right detail.
     *
     * @return the message to show before exiting; null if everything went well
     */
    protected @Nullable String setupLogging() {
        if (useVerboseLogging()) {
            if (useQuietLogging()) {
                return "--verbose and --quiet are mutually exclusive\n";
            }
            if (infoOptionValue()) {
                return "--info and --verbose are mutually exclusive\n";
            }
        }
        if (useQuietLogging()) {
            if (infoOptionValue()) {
                return "--quiet and --info are mutually exclusive\n";
            }
        }

        // guard since this is a static singleton, it can get "confused" if changes a lot from tests
        if (System.getProperty("gitprocess.logging.testing", "false").equals("false")) {
            final LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
            final Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
            final ConsoleAppender<ILoggingEvent> consoleAppender = (ConsoleAppender<ILoggingEvent>)rootLogger.getAppender("console");
            consoleAppender.setContext(context);
            consoleAppender.setOutputStream(printStream);
            final EncoderBase<ILoggingEvent> layoutEncoder;

            final PatternLayout patternLayout = new PatternLayout();
            patternLayout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            patternLayout.setContext(context);
            patternLayout.start();

            if (useInfoLogging()) {
                rootLogger.setLevel(Level.INFO);
                layoutEncoder = new CustomLoggingEncoder(patternLayout);
                layoutEncoder.setContext(context);
            }
            else if (useQuietLogging()) {
                rootLogger.setLevel(Level.WARN);
                layoutEncoder = new CustomLoggingEncoder(patternLayout);
                layoutEncoder.setContext(context);
            }
            else {
                rootLogger.setLevel(Level.DEBUG);
                layoutEncoder = new PatternLayoutEncoder();
                layoutEncoder.setContext(context);
                ((PatternLayoutEncoder)layoutEncoder).setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            }

            consoleAppender.setEncoder(layoutEncoder);
            v(() -> layoutEncoder.init(consoleAppender.getOutputStream()));

            layoutEncoder.stop();
            layoutEncoder.start();

            // StatusPrinter.print(context);
        }
        return null;
    }


    /**
     * Override this to customize the OptionParser
     */
    protected OptionParser createOptionParser() {
        return defaultOptionParser();
    }


    /**
     * Creates a standard option parser that handles: info, quiet, verbose, version, help
     */
    protected static OptionParser defaultOptionParser() {
        final OptionParser optionParser = new OptionParser();
        optionParser.acceptsAll(asList("i", "info"), "moderate output (default: true)");
        optionParser.acceptsAll(asList("q", "quiet"), "only show errors");
        optionParser.acceptsAll(asList("v", "verbose"), "show \"everything\"");
        optionParser.accepts("version", "show the version");
        optionParser.acceptsAll(asList("h", "?", "help"), "show help").forHelp();

        return optionParser;
    }


    @SuppressWarnings({"PointlessBooleanExpression", "SimplifiableConditionalExpression", "SimplifiableIfStatement"})
    public boolean booleanValue(String optionName) {
        if (optionSet == null) throw new IllegalStateException("parse(String[] args) was not called");
        final boolean has = optionSet.has(optionName);
        if (has == false) return false;
        return optionSet.hasArgument(optionName) ?
            Boolean.getBoolean(optionSet.valueOf(optionName).toString()) :
            true;
    }


    @SuppressWarnings({"PointlessBooleanExpression", "unused"})
    public Optional<String> stringValue(String optionName) {
        if (optionSet == null) throw new IllegalStateException("parse(String[] args) was not called");
        final boolean has = optionSet.has(optionName);
        if (has == false) return empty();
        return optionSet.hasArgument(optionName) ?
            Optional.of(optionSet.valueOf(optionName).toString()) :
            Optional.<@NonNull String>empty();
    }


    @SuppressWarnings("unchecked")
    public List<String> nonOptionArgs() {
        if (optionSet == null) throw new IllegalStateException("parse(String[] args) was not called");
        return (List<String>)optionSet.nonOptionArguments();
    }


    @EnsuresNonNull("this.optionSet")
    public void optionSet(OptionSet optionSet) {
        this.optionSet = optionSet;
    }


    /**
     * Should the help text be shown?
     * <p>
     * In addition to checking {@link #helpOptionValue()}, this should check that the correct number of arguments have
     * been set, etc.
     *
     * @see #helpOptionValue()
     */
    public abstract boolean showHelp();


    /**
     * The value of the --help option
     */
    public boolean helpOptionValue() {
        return booleanValue("help");
    }


    public boolean showVersion() {
        return booleanValue("version");
    }


    public boolean useInfoLogging() {
        return infoOptionValue() || !(useVerboseLogging() || useQuietLogging());
    }


    public boolean infoOptionValue() {
        return booleanValue("info");
    }


    public boolean useVerboseLogging() {
        return booleanValue("verbose");
    }


    public boolean useQuietLogging() {
        return booleanValue("quiet");
    }

}
