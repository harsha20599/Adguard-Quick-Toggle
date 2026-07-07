package com.example.data

import android.util.Base64
import android.util.Log
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class AdGuardRepository(private val credentialsStore: AdGuardCredentialsStore) {

    private val tag = "AdGuardRepository"

    private fun buildClient(
        username: String?,
        password: String?,
        trustSelfSigned: Boolean,
        enableLogs: Boolean
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)

        // Inject Authentication Header if credentials are provided
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            builder.addInterceptor { chain ->
                val request = chain.request()
                val authenticatedRequest = request.newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
                chain.proceed(authenticatedRequest)
            }
        }

        // SSL Trust for self-signed certificates if requested
        if (trustSelfSigned) {
            try {
                builder.sslSocketFactory(TrustAllCerts.sslSocketFactory, TrustAllCerts.trustManager)
                builder.hostnameVerifier(TrustAllCerts.hostnameVerifier)
            } catch (e: Exception) {
                Log.e(tag, "Failed to apply self-signed SSL trust config", e)
            }
        }

        // Logging interceptor
        if (enableLogs) {
            val logging = HttpLoggingInterceptor { message ->
                Log.d("AdGuardHttpLog", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    private fun getService(): AdGuardService {
        val baseUrl = credentialsStore.getServerUrl() ?: throw IllegalStateException("Server URL not configured")
        val username = credentialsStore.getUsername()
        val password = credentialsStore.getPassword()
        val trustSelfSigned = credentialsStore.isTrustSelfSigned()
        val enableLogs = credentialsStore.isLogsEnabled()

        // AdGuard APIs require base URL to end with a slash.
        val formattedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val client = buildClient(username, password, trustSelfSigned, enableLogs)

        val retrofit = Retrofit.Builder()
            .baseUrl(formattedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        return retrofit.create(AdGuardService::class.java)
    }

    /**
     * Test the connection to AdGuard Home with explicit parameters (prior to saving).
     */
    suspend fun testConnection(
        url: String,
        username: String,
        password: String,
        trustSelfSigned: Boolean
    ): Result<AdGuardStatus> {
        return try {
            val cleanUrl = credentialsStore.cleanUrl(url)
            val formattedBaseUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
            val client = buildClient(
                username = username,
                password = password,
                trustSelfSigned = trustSelfSigned,
                enableLogs = true // Always log tests to help debug
            )

            val retrofit = Retrofit.Builder()
                .baseUrl(formattedBaseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            val testService = retrofit.create(AdGuardService::class.java)
            val status = testService.getStatus()
            Result.success(status)
        } catch (e: Exception) {
            Log.e(tag, "Connection test failed", e)
            Result.failure(mapException(e))
        }
    }

    suspend fun fetchStatus(): Result<AdGuardStatus> {
        return try {
            val status = getService().getStatus()
            Result.success(status)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch protection status", e)
            Result.failure(mapException(e))
        }
    }

    suspend fun setProtection(enabled: Boolean): Result<Boolean> {
        return try {
            val response = getService().setProtection(AdGuardProtectionRequest(enabled))
            if (response.isSuccessful) {
                Result.success(enabled)
            } else {
                throw retrofit2.HttpException(response)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to set protection state to $enabled", e)
            Result.failure(mapException(e))
        }
    }

    private fun mapException(e: Throwable): Throwable {
        return when (e) {
            is retrofit2.HttpException -> {
                when (e.code()) {
                    401 -> Exception("Authentication failed: Incorrect username or password.")
                    403 -> Exception("Access forbidden: You don't have permission to access this.")
                    404 -> Exception("Not found: Verify the server URL (is it an AdGuard Home instance?)")
                    415 -> Exception("Unsupported Media Type (415): The server rejected the request format. Ensure your AdGuard Home version is up to date.")
                    else -> Exception("Server error: ${e.code()} ${e.message()}")
                }
            }
            is SocketTimeoutException -> Exception("Request timed out. Please verify host and network.")
            is ConnectException -> Exception("Cannot reach server. Verify server URL/IP and port.")
            is SSLHandshakeException -> Exception("SSL certificate error. Try enabling 'Trust self-signed certificates' in settings.")
            is CertificateException -> Exception("Untrusted certificate. Enable self-signed trust settings.")
            is com.squareup.moshi.JsonDataException -> Exception("Invalid response from server. Are you sure this is an AdGuard Home instance?")
            is java.io.IOException -> {
                if (e.message?.contains("Cleartext HTTP traffic", ignoreCase = true) == true) {
                    Exception("Cleartext HTTP traffic not permitted. Use HTTPS or enable cleartext in manifest.")
                } else {
                    e
                }
            }
            else -> e
        }
    }
}
