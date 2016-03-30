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

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.TrackingRefUpdate;

import java.util.Collection;
import java.util.stream.Collectors;

public class SimpleFetchResult {
    private FetchResult fetchResult;


    public SimpleFetchResult(FetchResult fetchResult) {
        this.fetchResult = fetchResult;
    }


    @Override
    public String toString() {
        final Collection<TrackingRefUpdate> trackingRefUpdates = fetchResult.getTrackingRefUpdates();

        return trackingRefUpdates.stream().
            map(tr ->
                String.format("  %s (%s): %s..%s %s",
                    Repository.shortenRefName(tr.getRemoteName()),
                    Repository.shortenRefName(tr.getLocalName()),
                    tr.getOldObjectId().abbreviate(7).name(),
                    tr.getNewObjectId().abbreviate(7).name(),
                    tr.getResult())).
            collect(Collectors.joining("\n"));
    }
}
