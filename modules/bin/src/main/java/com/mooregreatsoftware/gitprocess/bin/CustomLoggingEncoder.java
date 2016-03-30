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
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

import java.io.IOException;

/**
 * A customer log encoder that writes to STDOUT and knows to handle ERROR and WARN messages "specially"
 */
public class CustomLoggingEncoder extends EncoderBase<ILoggingEvent> {
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
            outputStream.write(("WARN: " + event.getFormattedMessage() + System.lineSeparator()).getBytes());
        }
        else {
            outputStream.write(event.getFormattedMessage().getBytes());
            outputStream.write(System.lineSeparator().getBytes());
        }
    }


    @Override
    public void close() throws IOException {
        outputStream.flush();
    }

}
