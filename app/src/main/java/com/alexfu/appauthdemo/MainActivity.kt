package com.alexfu.appauthdemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import net.openid.appauth.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val REQUEST_AUTH = 1337
    }

    private val authButton by lazy { findViewById<Button>(R.id.auth) }
    private val authInfoText by lazy { findViewById<TextView>(R.id.auth_info) }
    private val authService by lazy { AuthorizationService(this) }
    private lateinit var authState: AuthState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authButton.setOnClickListener {
            beginAuthFlow()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTH) {
            val response = AuthorizationResponse.fromIntent(data)
            val error = AuthorizationException.fromIntent(data)
            handleAuthResponse(response, error)
        }
    }

    private fun beginAuthFlow() {
        val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
        val tokenUri = Uri.parse("https://www.googleapis.com/oauth2/v4/token")
        val serviceConfig = AuthorizationServiceConfiguration(authUri, tokenUri)
        authState = AuthState(serviceConfig)

        requestAuthCode(serviceConfig)
    }

    private fun requestAuthCode(serviceConfig: AuthorizationServiceConfiguration) {
        val request = buildRequest(serviceConfig)
        val requestIntent = authService.getAuthorizationRequestIntent(request)
        startActivityForResult(requestIntent, REQUEST_AUTH)
    }

    private fun buildRequest(serviceConfig: AuthorizationServiceConfiguration): AuthorizationRequest {
        return AuthorizationRequest.Builder(
                serviceConfig,
                BuildConfig.CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(BuildConfig.REDIRECT_URI)
        ).setScope("profile").build()
    }

    private fun handleAuthResponse(response: AuthorizationResponse?, error: AuthorizationException?) {
        if (response != null) {
            handleAuthSuccess(response)
        } else if (error != null) {
            handleAuthError(error)
        }
        authState.update(response, error)
    }

    private fun handleAuthSuccess(response: AuthorizationResponse) {
        Log.i("AppAuthDemo", "Auth response: ${response.jsonSerializeString()}")
        // Received auth code, now we need to exchange it for an access token
        authService.performTokenRequest(response.createTokenExchangeRequest(), { tokenResponse, error ->
            if (tokenResponse != null) {
                handleTokenExchangeSuccess(tokenResponse)
            } else if (error != null) {
                handleTokenExchangeError(error)
            }
            authState.update(tokenResponse as TokenResponse, error)
        })
    }

    private fun handleAuthError(error: AuthorizationException) {
        Toast.makeText(this, "Auth failed! See Logcat.", Toast.LENGTH_LONG).show()
        error.printStackTrace()
    }

    private fun handleTokenExchangeSuccess(response: TokenResponse) {
        Log.i("AppAuthDemo", "Token exchange response: ${response.jsonSerializeString()}")
        authInfoText.text = prettyFormatTokenResponse(response)
    }

    private fun handleTokenExchangeError(error: AuthorizationException) {
        Toast.makeText(this, "Token exchange failed! See Logcat.", Toast.LENGTH_LONG).show()
        error.printStackTrace()
    }

    private fun prettyFormatTokenResponse(response: TokenResponse): Spanned {
        val data = mapOf(
                "Token Type" to response.tokenType,
                "Refresh Token" to response.refreshToken,
                "Access Token" to response.accessToken,
                "Expires At" to response.accessTokenExpirationTime
        )

        val html = data.map { entry -> "<b>${entry.key}:</b> ${entry.value}" }.joinToString("<p/>")
        return Html.fromHtml(html)
    }
}
