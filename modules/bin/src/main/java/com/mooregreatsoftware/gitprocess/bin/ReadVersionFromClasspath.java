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

import com.mooregreatsoftware.gitprocess.lib.GitLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.empty;

public class ReadVersionFromClasspath {
    private static final Logger LOG = LoggerFactory.getLogger(ReadVersionFromClasspath.class);


    /**
     * Extract from the JAR file name that is providing the {@link GitLib} class the version information.
     *
     * @return empty() if it can't derive the version
     */
    public static Optional<String> version() {
        final String classFile = GitLib.class.getName().replace('.', '/') + ".class";

        @SuppressWarnings("ConstantConditions")
        final String filename = GitLib.class.getClassLoader().getResource(classFile).getFile();

        return versionFromJarFilename(filename, classFile);
    }


    static Optional<String> versionFromJarFilename(String filename, String classFile) {
        // What could be simpler?
        //
        // See the test case for details on how this is expected to work
        final Pattern pattern = Pattern.compile("^.*?\\-?(?<prefix>(?<version>\\d+\\.\\d\\.\\d(?:\\-[^/\\\\]*?)?)?\\.jar!)?/" + classFile + "$");
        final Matcher matcher = pattern.matcher(filename);
        if (matcher.matches()) {
            if (matcher.group("prefix") == null) return empty();
            return Optional.of(matcher.group("version"));
        }
        else {
            LOG.warn("Do not know how to parse \"{}\" for its version information", filename);
            return empty();
        }
    }

}
