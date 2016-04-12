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

public interface Runner {
    /**
     * exit value if options were consumed
     */
    int STOP_ON_OPTIONS_CODE = 1;
    /**
     * exit value if an issue happens running the function
     */
    int STOP_ON_FUNCTION_CODE = 2;

    /**
     * Run the command and return the appropriate exit code for the program.
     * <p>
     * If there's a problem while gathering the options, print the message to STDOUT and return {@link #STOP_ON_OPTIONS_CODE}.
     * If there's a problem while running the command, print the error to STDERR and return {@link #STOP_ON_FUNCTION_CODE}.
     *
     * @return 0 if successful; anything else if not
     */
    int run();
}
