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
package com.mooregreatsoftware.gitprocess

import org.eclipse.jgit.transport.URIish
import spock.lang.Subject

import static java.util.Collections.emptySet

class StoredRemoteConfigSpec extends GitSpecification {

    @Subject
    def config = gitLib.remoteConfig()


    def "remotes"() {
        expect:
        !config.hasRemotes()
        config.remoteNames() as Set == emptySet()
        !config.remoteName().isPresent()

        when:
        config.remoteAdd("something", new URIish("ssh:fooble"))
        config.remoteAdd("something-else", new URIish("http://fooblez"))

        then:
        config.hasRemotes()
        config.remoteNames() as Set == ["something", "something-else"] as Set
        config.remoteName().get() == 'something' // first remote alphabetically

        when:
        config.remoteAdd("origin", new URIish("ssh:zfooble"))

        then:
        config.remoteName().get() == 'origin' // always 'origin' if it exists and hasn't been explicitly set

        when:
        config.remoteName("something-else")

        then:
        config.remoteName().get() == 'something-else' // config overrides everything
    }

}
