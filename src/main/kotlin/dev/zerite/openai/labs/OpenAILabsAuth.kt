package dev.zerite.openai.labs

import dev.zerite.openai.labs.requests.LabsAccessTokenRequest
import dev.zerite.openai.labs.requests.LabsAccessTokenResponse
import dev.zerite.openai.labs.requests.LabsLoginResponse
import dev.zerite.openai.util.nextString
import dev.zerite.openai.util.sha256
import dev.zerite.openai.util.toBase64
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.random.Random

object OpenAILabsAuth {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36"

    const val CLIENT_ID = "DMg91f5PCHQtc7u018WKiL0zopKdiHle"

    /**
     * Logs into the OpenAI Labs API and returns the access token response.
     *
     * A bit of rambling here, but this API is designed super poorly (thanks Auth0).
     * We currently need 3 different clients to make this work, and we need to
     * make a number of requests to get the access token.
     *
     * The flow to get the access token is as follows:
     *
     *   1. (Client 1 - No Redirects) Make a request to the /authorize endpoint with the client ID and
     *   the response type of "code". This will redirect us to the Auth0 login page. This can't have
     *   automatic redirects enabled as we need to get the location header for later.
     *
     *   2. (Client 2 - Redirects Enabled) Post some browser metadata to the Auth0 login page. This
     *   is required, otherwise we get an internal server error.
     *
     *   3. (Client 2 - Redirects Enabled) Post the username and password to the Auth0 login page.
     *
     *   4. (Client 2 - Redirects Enabled) Get the location header from the response and send a
     *   final request to the /resume endpoint with the code from the location header.
     *
     *   5. (Client 3 - JSON) Send a request to the /oauth/token endpoint with the code from the
     *   previous request's final redirect URL. This will return the access token.
     *
     * All of this is done in the [login] function.
     *
     * @param username The username to log in with.
     * @param password The password to log in with.
     * @return The access token response.
     */
    suspend fun login(username: String, password: String): LabsAccessTokenResponse {
        // Create cookie storage
        val storage = AcceptAllCookiesStorage()

        // Create a state, nonce, and code verifier
        val stateIn = Random.nextString().toBase64()
        val nonceIn = Random.nextString().toBase64()
        val codeChallenge = Random.nextString()

        // Create the first client which needs to have redirects disabled
        val noRedirectClient = createClient(storage) {
            followRedirects = false
        }

        // Send a request to get the state - #1
        val stateReq = noRedirectClient.get("https://auth0.openai.com/authorize") {
            header("Accept", "*/*")

            parameter("client_id", CLIENT_ID)
            parameter("audience", "https://api.openai.com/v1")
            parameter("redirect_uri", "https://labs.openai.com/auth/callback")
            parameter("max_age", "0")
            parameter("scope", "openid profile email offline_access")
            parameter("response_type", "code")
            parameter("response_mode", "query")
            parameter("auth0Client", "eyJuYW1lIjoiYXV0aDAtc3BhLWpzIiwidmVyc2lvbiI6IjEuMjAuMSJ9")

            parameter("state", stateIn)
            parameter("nonce", nonceIn)
            parameter("code_challenge", codeChallenge.toByteArray().sha256().toBase64())
            parameter("code_challenge_method", "S256")
        }

        // Get the state from the response URL
        val stateUrl = Url(stateReq.headers["Location"] ?: error("No location header found for state request"))
        val state = stateUrl.parameters["state"] ?: error("No state found in state request")

        // Create a new client with redirects enabled
        val redirectClient = createClient(storage) {
            followRedirects = true
        }

        // Send identifier request - #2
        redirectClient.post("https://auth0.openai.com/u/login/identifier") {
            contentType(ContentType.Application.FormUrlEncoded)
            parameter("state", state)

            setBody(FormDataContent(Parameters.build {
                append("state", state)
                append("username", username)
                append("js-available", "true")
                append("webauthn-available", "true")
                append("is-brave", "false")
                append("webauthn-platform-available", "true")
                append("action", "default")
            }))
        }

        // Send a request to log in with the username and password - #3
        val passwordReq = redirectClient.post("https://auth0.openai.com/u/login/password") {
            header("Accept", "*/*")
            contentType(ContentType.Application.FormUrlEncoded)
            parameter("state", state)

            setBody(FormDataContent(Parameters.build {
                append("state", state)
                append("username", username)
                append("password", password)
                append("action", "default")
            }))
        }

        // Follow the redirect to get the code - #4
        val resumeUrl = passwordReq.headers["Location"] ?: error("No location header found for password request")
        val resumeReq = redirectClient.get("https://auth0.openai.com$resumeUrl") {
            header("Accept", "*/*")
        }

        // Get the code from the response URL
        val code = resumeReq.request.url.parameters["code"] ?: error("No code found in resume request")

        // Create a JSON client
        val jsonClient = createClient(storage) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }

        // Send a request to get the access token
        val tokenReq = jsonClient.post("https://auth0.openai.com/oauth/token") {
            contentType(ContentType.Application.Json)
            setBody(LabsAccessTokenRequest(code, codeChallenge))
        }

        // Return the access token
        return tokenReq.body()
    }

    /**
     * Creates a new session for the given labs token.
     *
     * @param token The labs token to create a session for.
     * @return The session response.
     */
    suspend fun createSession(token: LabsAccessTokenResponse): LabsLoginResponse {
        // Create a temporary client
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }

        // Send a request to create a session
        val sessionReq = client.post("https://labs.openai.com/api/labs/auth/login") {
            header("Authorization", "Bearer ${token.accessToken}")
        }

        // Return the session
        return sessionReq.body()
    }

    /**
     * Create a new HTTP client.
     *
     * @param storage The cookie storage to use.
     * @param block The block to configure the client with.
     */
    private fun createClient(storage: CookiesStorage, block: HttpClientConfig<OkHttpConfig>.() -> Unit): HttpClient =
        HttpClient(OkHttp) {
            install(HttpCookies) {
                this.storage = storage
            }

            install(DefaultRequest) {
                header("User-Agent", USER_AGENT)
            }

            this.block()
        }
}