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
package com.mooregreatsoftware.gitprocess.github;

import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public final class CreatePrOptions {
    @Nullable
    private String title;
    @Nullable
    private String body;
    private int issueId = -99;
    private String headBranchName;
    private String baseRef;


    private CreatePrOptions(@Nullable String title, @Nullable String body, int issueId, String headBranchName, String baseRef) {
        this.title = title;
        this.body = body;
        this.issueId = issueId;
        this.headBranchName = headBranchName;
        this.baseRef = baseRef;
    }


    @Nullable
    public String title() {
        return this.title;
    }


    @Nullable
    public String body() {
        return this.body;
    }


    /**
     * The issue id to create the PR off of
     *
     * @return < 1 if this has not been set
     */
    public int issueId() {
        return this.issueId;
    }


    public String headBranchName() {
        return this.headBranchName;
    }


    public String baseRef() {
        return this.baseRef;
    }


    public static B.BodyOrHead title(String title) {
        return new B.Builder().title(title);
    }


    public static B.HeadBranch issue(int issueId) {
        return new B.Builder().issueId(issueId);
    }


    public interface B {

        final class Builder implements Build, BaseRef, BodyOrHead, Title {
            @Nullable
            private String title;
            @Nullable
            private String body;
            private int issueId = -99;
            @Nullable
            private String headBranchName;
            @Nullable
            private String baseRef;


            private Builder() {
            }


            @EnsuresNonNull("this.title")
            public BodyOrHead title(String title) {
                this.title = title;
                return this;
            }


            @EnsuresNonNull("this.body")
            public HeadBranch body(String body) {
                this.body = body;
                return this;
            }


            public HeadBranch issueId(int issueId) {
                this.issueId = issueId;
                return this;
            }


            @EnsuresNonNull("this.headBranchName")
            public BaseRef headBranchName(String headBranchName) {
                this.headBranchName = headBranchName;
                return this;
            }


            @EnsuresNonNull("this.baseRef")
            public Build baseRef(String baseRef) {
                this.baseRef = baseRef;
                return this;
            }


            @SuppressWarnings("RedundantCast")
            public CreatePrOptions build() {
                return new CreatePrOptions((@NonNull String)title, body, issueId,
                    (@NonNull String)headBranchName, (@NonNull String)baseRef);
            }
        }

        interface Build {
            CreatePrOptions build();
        }

        interface Issue {
            HeadBranch issue(int issueId);
        }

        interface Body {
            HeadBranch body(String body);
        }

        interface HeadBranch {
            BaseRef headBranchName(String headBranchName);
        }

        interface BodyOrHead extends Body, HeadBranch {
        }

        interface BaseRef {
            Build baseRef(String baseRef);
        }

        interface Title {
            BodyOrHead title(String title);
        }

    }
}
