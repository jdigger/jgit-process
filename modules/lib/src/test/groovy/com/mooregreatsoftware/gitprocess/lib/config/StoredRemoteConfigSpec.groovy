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
package com.mooregreatsoftware.gitprocess.lib.config

import com.mooregreatsoftware.gitprocess.lib.GitSpecification
import org.eclipse.jgit.transport.URIish
import spock.lang.Subject
import spock.lang.Unroll

import static com.mooregreatsoftware.gitprocess.lib.config.StoredRemoteConfig.normalizeUrl
import static java.util.Collections.emptySet

class StoredRemoteConfigSpec extends GitSpecification {

    @Subject
    def config = origin.remoteConfig()


    def "remotes"() {
        expect:
        !config.hasRemotes()
        config.remoteNames() as Set == emptySet()
        !config.remoteName()

        when:
        config.remoteAdd("something", new URIish("ssh:fooble"))
        config.remoteAdd("something-else", new URIish("http://fooblez"))

        then:
        config.hasRemotes()
        config.remoteNames() as Set == ["something", "something-else"] as Set
        config.remoteName() == 'something' // first remote alphabetically

        when:
        config.remoteAdd("origin", new URIish("ssh:zfooble"))

        then:
        config.remoteName() == 'origin' // always 'origin' if it exists and hasn't been explicitly set

        when:
        config.remoteName("something-else")

        then:
        config.remoteName() == 'something-else' // config overrides everything
    }


    def "remote URL"() {
        when:
        config.remoteAdd("origin", new URIish("https://github.com/jdigger/jgit-process.git"))
        config.remoteAdd("ssh-origin", new URIish("git@github.com:jdigger/jgit-process.git"))
        config.remoteAdd("http-internal", new URIish("http://github.internal.com:4447/jdigger/jgit-process.git"))
        config.remoteAdd("https-internal", new URIish("https://github.internal.com:4447/jdigger/jgit-process.git"))

        then:
        config.remoteUrl("origin").toString() == "https://github.com/jdigger/jgit-process.git"
        config.remoteUrl("ssh-origin").toString() == "ssh://git@github.com/jdigger/jgit-process.git"
        config.remoteUrl("http-internal").toString() == "http://github.internal.com:4447/jdigger/jgit-process.git"
        config.remoteUrl("https-internal").toString() == "https://github.internal.com:4447/jdigger/jgit-process.git"
    }


    @Unroll
    def "normalize #url -> #normalized"() {
        when:
        def sshConfigReader = new StringReader('''
Host github
    User git
    HostName github.com
    IdentityFile ~/.ssh/github_rsa

Host github.com
    HostName github.com
    IdentityFile ~/.ssh/github_rsa

Host gh
    HostName github.com
    IdentityFile ~/.ssh/github_rsa
''')

        then:
        normalizeUrl(url as String, sshConfigReader) == normalized

        where:
        url                                   || normalized
        "git@github.com:jdigger/jgit-process" || "ssh://git@github.com/jdigger/jgit-process"
        "github.com:jdigger/jgit-process"     || "ssh://github.com/jdigger/jgit-process"
        "github:jdigger/jgit-process"         || "ssh://git@github.com/jdigger/jgit-process"
        "jdigger@github:jdigger/jgit-process" || "ssh://jdigger@github.com/jdigger/jgit-process"
        "git@gh:jdigger/jgit-process"         || "ssh://git@github.com/jdigger/jgit-process"
        "jdigger@gh:jdigger/jgit-process"     || "ssh://jdigger@github.com/jdigger/jgit-process"
    }

}
