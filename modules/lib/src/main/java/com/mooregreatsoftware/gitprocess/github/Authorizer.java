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

import com.jcabi.github.RtGithub;
import com.jcabi.http.Request;
import com.jcabi.http.Response;
import com.jcabi.http.response.JsonResponse;
import com.mooregreatsoftware.gitprocess.lib.GitLib;
import javaslang.control.Try;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.function.Function;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

public class Authorizer {
    private static final Logger LOG = LoggerFactory.getLogger(Authorizer.class);

    public static final String GITHUB_2FA_HEADER = "X-GitHub-OTP";

    private GitLib gitLib;
    private final PrintStream out;
    private final InputStream in;
    private @Nullable String username;
    private @Nullable String password;


    public Authorizer(GitLib gitLib) {
        this(gitLib, System.out, System.in);
    }


    public Authorizer(GitLib gitLib, PrintStream out, InputStream in) {
        this.gitLib = gitLib;
        this.out = out;
        this.in = in;
    }


    public String getOauthToken() {
        Optional<String> oauthToken = gitLib.generalConfig().oauthToken();
        return oauthToken.orElseGet(() -> {
            this.out.println("Need to generate an OAuth token");
            final String token = generateOauthToken();
            gitLib.generalConfig().oauthToken(token);
            return token;
        });
    }


    private String generateOauthToken() {
        return generateOauthToken(null, null, null);
    }


    private String generateOauthToken(@Nullable String givenUsername, @Nullable String givenPassword, @Nullable String twoFactorToken) {
        return Try.of(() -> {
            String username = givenUsername != null ? givenUsername : getUsername();
            String password = givenPassword != null ? givenPassword : getPassword();
            final JsonObject jsonObject = oathTokenPostBody();

            if (twoFactorToken == null)
                LOG.debug("Sending authorization request for {}: {}", username, jsonObject);
            else
                LOG.debug("Sending authorization request for {} - {}: {}", username, twoFactorToken, jsonObject);

            final JsonResponse resp = postTokenRequest(username, password, twoFactorToken, jsonObject).as(JsonResponse.class);
            if (resp.status() == HTTP_UNAUTHORIZED) {
                return unauthorized(resp, username, password);
            }
            else if (resp.status() == HTTP_CREATED) {
                final JsonReader jsonReader = resp.json();
                return jsonReader.readObject().getString("token");
            }
            throw new IllegalStateException(resp.toString());
        }).getOrElseThrow(exceptionTranslator());
    }


    private String unauthorized(JsonResponse resp, String username, String password) {
        if (resp.headers().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(GITHUB_2FA_HEADER))) {
            String tfaToken = getTwoFactorToken();
            return generateOauthToken(username, password, tfaToken);
        }
        else {
            // bad username/password
            return generateOauthToken(null, null, null);
        }
    }


    private static Function<Throwable, RuntimeException> exceptionTranslator() {
        return exp -> (exp instanceof RuntimeException) ? (RuntimeException)exp : new IllegalStateException(exp);
    }


    private JsonObject oathTokenPostBody() {
        @SuppressWarnings("argument.type.incompatible")
        final JsonBuilderFactory factory = Json.createBuilderFactory(null);
        final String note = String.format("Git-Process for %s on %s", gitLib.remoteConfig().repositoryName(), macAddress());
        final String fingerprint = fingerprint(note);
        return factory.createObjectBuilder().
            add("scopes", factory.createArrayBuilder().add("repo")).
            add("note", note).
            add("fingerprint", fingerprint).
            add("note_url", "https://jdigger.github.com/jgit-process").
            build();
    }


    private Response postTokenRequest(String username, String password, @Nullable String twoFactorToken, JsonObject jsonObject) throws IOException {
        return Try.of(() -> {
                final String remoteName = gitLib.remoteConfig().remoteName();
                if (remoteName == null)
                    throw new IllegalStateException("Could not find a remote");
                final URI serverApiUri = GitHubRepo.getServerApiUri(remoteName, gitLib);
                Request request = new RtGithub(username, password).entry().
                    uri().set(serverApiUri).path("/authorizations").back().
                    method(Request.POST).
                    body().set(jsonObject).back();
                if (twoFactorToken != null) {
                    request = request.header(GITHUB_2FA_HEADER, twoFactorToken);
                }
                return request.fetch();
            }
        ).getOrElseThrow(exceptionTranslator());
    }


    private String fingerprint(String note) {
        return Try.of(() -> {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(note.getBytes(StandardCharsets.UTF_8));
            return toHex(hash, false);
        }).getOrElseThrow(exceptionTranslator());
    }


    private String macAddress() {
        return Try.of(() -> {
            final long startLocalhost = System.currentTimeMillis();
            final InetAddress ip = InetAddress.getLocalHost();
            final long endLocalhost = System.currentTimeMillis();
            if ((endLocalhost - startLocalhost) > 1_000L) {
                LOG.warn("It took a long time to get the localhost entry.\n" +
                    "Your /etc/hosts likely needs to be updated to something like\n" +
                    "127.0.0.1\tlocalhost\t{}\n" +
                    "::1\tlocalhost\t{}", ip.getHostName(), ip.getHostName());
            }
            LOG.debug("Current IP address : {}", ip.getHostAddress());

            final NetworkInterface network = NetworkInterface.getByInetAddress(ip);

            final byte[] mac = network.getHardwareAddress();

            return mac != null ? toHex(mac, true) : ip.getHostName();
        }).getOrElseThrow(exceptionTranslator());
    }


    private static String toHex(byte[] mac, boolean dashDelim) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (dashDelim && i < mac.length - 1) ? "-" : ""));
        }
        return sb.toString();
    }


    @EnsuresNonNull("this.username")
    protected String getUsername() {
        if (this.username == null) {
            final Optional<String> defaultUsername = gitLib.generalConfig().username();
            this.username = askForUsername(defaultUsername.orElse(null));
        }
        return this.username;
    }


    @EnsuresNonNull("this.username")
    public Authorizer username(String username) {
        this.username = username;
        return this;
    }


    protected String askForUsername(@Nullable String guessedUsername) {
        final BufferedReader input = new BufferedReader(new InputStreamReader(this.in));
        if (guessedUsername == null) {
            this.out.print("User name: ");
        }
        else {
            this.out.printf("User name: [%s]", guessedUsername);
        }

        final String userInput = Try.of(input::readLine).
            getOrElseThrow(exceptionTranslator()).trim();

        if ("".equals(userInput)) { // they just hit RETURN
            return guessedUsername != null ? guessedUsername : askForUsername(null);
        }
        return userInput;
    }


    protected String getPassword() {
        if (this.password == null) {
            this.password = askForPassword();
        }
        return this.password;
    }


    public Authorizer password(String password) {
        this.password = password;
        return this;
    }


    protected String askForPassword() {
        final BufferedReader input = new BufferedReader(new InputStreamReader(this.in));
        this.out.printf("Password: ");

        final String userInput = Try.of(input::readLine).
            getOrElseThrow(exceptionTranslator()).trim();

        if ("".equals(userInput)) { // they just hit RETURN
            return askForPassword();
        }
        return userInput;
    }


    protected String getTwoFactorToken() {
        return askForTwoFactorToken();
    }


    protected String askForTwoFactorToken() {
        final BufferedReader input = new BufferedReader(new InputStreamReader(this.in));
        this.out.printf("Two Factor Token: ");

        final String userInput = Try.of(input::readLine).
            getOrElseThrow(exceptionTranslator()).trim();

        if ("".equals(userInput)) { // they just hit RETURN
            return askForPassword();
        }
        return userInput;
    }


    // manual testing
    public static void main(String[] args) throws IOException {
        Authorizer authorizer = new Authorizer(GitLib.of(new File(".")));
        final String oauthToken = authorizer.getOauthToken();
        System.out.println("Token: " + oauthToken);
    }

}
