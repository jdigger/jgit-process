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
package com.mooregreatsoftware.gitprocess.bin

import com.mooregreatsoftware.gitprocess.github.JettySupport
import com.mooregreatsoftware.gitprocess.lib.GitSpecification
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.eclipse.jgit.transport.URIish
import spock.lang.AutoCleanup

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static java.net.HttpURLConnection.HTTP_CREATED
import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class PullReqRunnerSpec extends GitSpecification {

    @AutoCleanup
    JettySupport jetty = new JettySupport()

    def prUrl = { "http://localhost:${jetty.serverPort}/jdigger/testproj/pull/123" }


    def setup() {
        System.setProperty("gitprocess.logging.testing", "true")

        jetty.addPostHandler({ HttpServletRequest request, HttpServletResponse response ->
            if (request.pathInfo == "/repos/jdigger/testproj/pulls") {
                def slurper = new JsonSlurper()
                def json = slurper.parse(request.getInputStream())
                response.status = HTTP_CREATED
                response.writer.println JsonOutput.toJson(
                    [
                        number  : 123,
                        html_url: prUrl.call(),
                        title   : json.title,
                        head    : [ref: json.head],
                        base    : [ref: json.base],
                    ]
                )
                return
            }
            else if (request.pathInfo == "/authorizations") {
                response.status = HTTP_CREATED
                response.writer.println JsonOutput.toJson([token: 'a_token'])
                return
            }
            response.status = HTTP_NOT_FOUND
        } as JettySupport.SimpleHandler)

        jetty.start()

        createCommit("a")
        origin.branches().createBranch("new_branch", "master").checkout()
        createCommit("b")
        createFakeRemoteBranch("testRemote/master")
        origin.remoteConfig().remoteAdd("testRemote", new URIish("http://localhost:${jetty.serverPort}/jdigger/testproj"))
    }


    def cleanup() {
        System.clearProperty("gitprocess.logging.testing")
    }


    def "CreatePR"() {
        def build = PullReqRunner.builder().gitLib(origin).cliArgs([] as String[]).build()
        origin.generalConfig().oauthToken("93abcd234234")

        when:
        def runner = build.run()

        then:
        runner == 0
        stdout.contains("Created \"new_branch\" at ${prUrl.call()}")
    }


    def "CreatePR with title given"() {
        def build = PullReqRunner.builder().gitLib(origin).cliArgs(["A fancy PR name", "--username", "test", "--password", "testpass"] as String[]).build()

        when:
        def runner = build.run()

        then:
        runner == 0
        stdout.contains("Created \"A fancy PR name\" at ${prUrl.call()}")
    }

}
