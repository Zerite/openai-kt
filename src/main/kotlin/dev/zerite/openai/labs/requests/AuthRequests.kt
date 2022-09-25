package dev.zerite.openai.labs.requests

import dev.zerite.openai.labs.OpenAILabsAuth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LabsAccessTokenRequest(
    val code: String,
    @SerialName("code_verifier") val codeVerifier: String,
    @SerialName("redirect_uri") val redirectUri: String = "https://labs.openai.com/auth/callback",
    @SerialName("client_id") val clientId: String = OpenAILabsAuth.CLIENT_ID,
    @SerialName("grant_type") val grantType: String = "authorization_code"
)

@Serializable
data class LabsAccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    val scope: String,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class LabsLoginResponse(
    val user: User
) {
    @Serializable
    data class User(
        val session: Session
    )

    @Serializable
    data class Session(
        @SerialName("sensitive_id") val sensitiveId: String,
    )
}