package com.mooregreatsoftware.gitprocess.bin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.EncoderBase;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;

import static com.mooregreatsoftware.gitprocess.bin.ReadVersionFromClasspath.version;
import static com.mooregreatsoftware.gitprocess.lib.ExecUtils.v;
import static java.util.Arrays.asList;

/**
 * The base class for the CLI commands to extend from. Provides handling of standard functionality.
 */
public abstract class AbstractRunner {

    /**
     * Parse the command line options and handle the "standard" set before returning the result.
     *
     * @see #createOptionParser()
     * @see #handleStandardOptions(OptionParser, OptionSet)
     */
    @Nonnull
    protected OptionSet parse(@Nonnull String[] args) {
        final OptionParser parser = createOptionParser();

        final OptionSet optionSet = parser.parse(args);

        handleStandardOptions(parser, optionSet);

        return optionSet;
    }


    /**
     * Handle the "standard" options: info, quiet, verbose, help, version
     */
    protected void handleStandardOptions(@Nonnull OptionParser parser, @Nonnull OptionSet optionSet) {
        if (optionSet.has("help") || shouldShowHelp(optionSet)) {
            printHelp(parser);
            System.exit(1);
        }

        if (optionSet.has("version")) {
            System.out.println("version: " + version().orElse("unknown"));
            System.exit(1);
        }

        setupLogging(optionSet);
    }


    private void printHelp(@Nonnull OptionParser parser) {
        System.out.println("USAGE: " + usageInfo());
        System.out.println("\n" + description() + "\n");
        v(() -> parser.printHelpOn(System.out));
        System.out.println("\nversion: " + version().orElse("unknown"));
    }


    /**
     * A short description for the command.
     */
    @Nonnull
    public abstract String description();


    /**
     * The syntax for running the command. (e.g., "git new-fb [OPTIONS] 'branch_name'")
     */
    @Nonnull
    public abstract String usageInfo();


    /**
     * Is the set of options not right for the command?
     */
    protected abstract boolean shouldShowHelp(@Nonnull OptionSet optionSet);


    /**
     * Override this to customize the OptionParser
     */
    @Nonnull
    protected OptionParser createOptionParser() {
        return defaultOptionParser();
    }


    /**
     * Creates a standard option parser that handles: info, quiet, verbose, version, help
     */
    @Nonnull
    protected static OptionParser defaultOptionParser() {
        return new OptionParser() {
            {
                final OptionSpecBuilder infoSpec = acceptsAll(asList("i", "info"), "moderate output (default: true)");
                final OptionSpecBuilder quietSpec = acceptsAll(asList("q", "quiet"), "only show errors");
                final OptionSpecBuilder verboseSpec = acceptsAll(asList("v", "verbose"), "show \"everything\"").availableUnless(infoSpec, quietSpec);
                infoSpec.availableUnless(quietSpec, verboseSpec);
                quietSpec.availableUnless(infoSpec, verboseSpec);
                accepts("version", "show the version");
                acceptsAll(asList("h", "?", "help"), "show help").forHelp();
            }
        };
    }


    /**
     * Set up the logging system to show logs at the right level and at the right detail.
     */
    protected static void setupLogging(@Nonnull OptionSet optionSet) {
        boolean quiet = optionSet.has("quiet");
        boolean verbose = optionSet.has("verbose");
        boolean info = optionSet.has("info") || !(quiet || verbose); //if nothing else is set, this is true

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        final Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        final ConsoleAppender<ILoggingEvent> consoleAppender = (ConsoleAppender<ILoggingEvent>)rootLogger.getAppender("console");
        consoleAppender.setContext(context);
        consoleAppender.setOutputStream(System.out);
        final EncoderBase<ILoggingEvent> layoutEncoder;

        PatternLayout patternLayout = new PatternLayout();
        patternLayout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        patternLayout.setContext(context);
        patternLayout.start();

        if (info) {
            rootLogger.setLevel(Level.INFO);
            layoutEncoder = new CustomLoggingEncoder(patternLayout);
            layoutEncoder.setContext(context);
        }
        else if (quiet) {
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


    /**
     * A customer log encoder that writes to STDOUT and knows to handle ERROR and WARN messages "specially"
     */
    private static class CustomLoggingEncoder extends EncoderBase<ILoggingEvent> {
        private final PatternLayout patternLayout;


        public CustomLoggingEncoder(PatternLayout patternLayout) {
            this.patternLayout = patternLayout;
        }


        @Override
        public void doEncode(ILoggingEvent event) throws IOException {
            if (event.getLevel().isGreaterOrEqual(Level.ERROR)) {
                outputStream.write(patternLayout.doLayout(event).getBytes());
            }
            else if (event.getLevel().isGreaterOrEqual(Level.WARN)) {
                outputStream.write(("WARN: " + event.getMessage() + System.lineSeparator()).getBytes());
            }
            else {
                outputStream.write(event.getMessage().getBytes());
                outputStream.write(System.lineSeparator().getBytes());
            }
        }


        @Override
        public void close() throws IOException {
            outputStream.flush();
        }
    }

}
