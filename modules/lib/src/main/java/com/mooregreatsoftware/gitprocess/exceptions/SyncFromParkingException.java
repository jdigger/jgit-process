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
package com.mooregreatsoftware.gitprocess.exceptions;

public class SyncFromParkingException extends GitProcessException {
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public static final SyncFromParkingException INSTANCE = new SyncFromParkingException();

    static {
        INSTANCE.setStackTrace(new StackTraceElement[0]);
    }

    private SyncFromParkingException() {
        super("You can not do a sync while on _parking_");
    }
}
