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

import com.mooregreatsoftware.gitprocess.lib.JgitGitLib;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReadVersionFromClasspath {
    private static final Logger LOG = LoggerFactory.getLogger(ReadVersionFromClasspath.class);


    /**
     * Extract from the JAR file name that is providing the {@link JgitGitLib} class the version information.
     *
     * @return null if it can't derive the version
     */
    @Nullable
    @SuppressWarnings("RedundantCast")
    public static String version() {
        final String classFile = JgitGitLib.class.getName().replace('.', '/') + ".class";

        final ClassLoader classLoader = (@NonNull ClassLoader)JgitGitLib.class.getClassLoader();
        final URL resource = classLoader.getResource(classFile);
        if (resource == null) {
            LOG.warn("Could not find {} in the classloader", classFile);
            return null;
        }
        final String filename = resource.getFile();

        return versionFromJarFilename(filename, classFile);
    }


    @Nullable
    static String versionFromJarFilename(String filename, String classFile) {
        // What could be simpler?
        //
        // See the test case for details on how this is expected to work
        final Pattern pattern = Pattern.compile("^.*?\\-?(?<prefix>(?<version>\\d+\\.\\d\\.\\d(?:\\-[^/\\\\]*?)?)?\\.jar!)?/" + classFile + "$");
        final Matcher matcher = pattern.matcher(filename);
        if (matcher.matches()) {
            return matcher.group("prefix") != null ? matcher.group("version") : null;
        }
        else {
            LOG.warn("Do not know how to parse \"{}\" for its version information", filename);
            return null;
        }
    }

}
