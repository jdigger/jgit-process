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
package com.mooregreatsoftware.gitprocess.lib;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class StreamUtils {

    /**
     * Wrap the {@link Iterable} in a {@link Stream}
     */
    @Nonnull
    public static <T> Stream<T> stream(@Nonnull Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }


    /**
     * Wrap the {@link Iterator} in a {@link Stream}
     */
    @Nonnull
    public static <T> Stream<T> stream(@Nonnull Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL), false);
    }

}
