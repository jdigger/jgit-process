package com.mooregreatsoftware.gitprocess;

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
