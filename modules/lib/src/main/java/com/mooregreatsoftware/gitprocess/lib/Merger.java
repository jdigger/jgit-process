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

import javaslang.control.Either;
import javaslang.control.Try;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javaslang.control.Either.left;
import static javaslang.control.Either.right;

public class Merger {
    private static final Logger LOG = LoggerFactory.getLogger(Merger.class);


    public static Either<String, SuccessfulMerge> merge(GitLib gitLib, Branch mergeBranch) {
        final Branch currentBranch = gitLib.branches().currentBranch();

        if (currentBranch == null) return left("There is no branch checked out");

        final ObjectId startCurrentOid = currentBranch.objectId();
        final ObjectId startIntegrationOid = mergeBranch.objectId();

        LOG.debug("Merging \"{}\"({}) with \"{}\"({})", currentBranch.shortName(), startCurrentOid.abbreviate(7).name(), mergeBranch.shortName(), startIntegrationOid.abbreviate(7).name());

        @SuppressWarnings("dereference.of.nullable")
        final MergeResult mergeResult = Try.of(() ->
            gitLib.jgit().merge().
                include(mergeBranch.objectId()).
                setCommit(true).
                setMessage("Sync merge from " + mergeBranch.shortName() + " into " + currentBranch.shortName()).
                call()).
            getOrElseThrow(ExecUtils.exceptionTranslator());

        return mergeResult.getMergeStatus().isSuccessful() ?
            right(new SuccessfulMerge(mergeResult)) :
            left(mergeResult.getMergeStatus().toString());
    }


    public static class SuccessfulMerge {
        private final MergeResult mergeResult;


        public SuccessfulMerge(MergeResult mergeResult) {
            this.mergeResult = mergeResult;
        }


        public String statusMsg() {
            return mergeResult.getMergeStatus().toString();
        }


        public ObjectId newHead() {
            return mergeResult.getNewHead();
        }


        public String toString() {
            return "SuccessfulMerge{" + statusMsg() + " - " + newHead().abbreviate(7).name() + "}";
        }

    }

}
