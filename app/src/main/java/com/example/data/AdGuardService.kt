package com.example.data

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface AdGuardService {

    @GET("control/status")
    suspend fun getStatus(): AdGuardStatus

    @Headers(
        "Content-Type: application/json",
        "X-Requested-With: XMLHttpRequest"
    )
    @POST("control/protection")
    suspend fun setProtection(
        @Body request: AdGuardProtectionRequest
    ): Response<Unit>

    @Headers(
        "Content-Type: application/json",
        "X-Requested-With: XMLHttpRequest"
    )
    @POST("control/login")
    suspend fun login(
        @Body request: AdGuardLoginRequest
    ): Response<Unit>
}

@JsonClass(generateAdapter = true)
data class AdGuardStatus(
    val protection_enabled: Boolean,
    val version: String,
    val running: Boolean = true,
    val dns_addresses: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class AdGuardProtectionRequest(
    val enabled: Boolean
)

@JsonClass(generateAdapter = true)
data class AdGuardLoginRequest(
    val name: String,
    val password: String
)
