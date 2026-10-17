package com.voicecare.ai.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class DoctorDto(
    @SerializedName("doctorId") val doctorId: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("specialization") val specialization: String? = null
)

data class LoginResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("doctor") val doctor: DoctorDto? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("leaseDays") val leaseDays: Int = 7,
    @SerializedName("error") val error: String? = null,
    @SerializedName("code") val code: String? = null
)

data class VerifyResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("valid") val valid: Boolean = false,
    @SerializedName("doctor") val doctor: DoctorDto? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("leaseDays") val leaseDays: Int = 7,
    @SerializedName("error") val error: String? = null,
    @SerializedName("code") val code: String? = null
)

interface AuthApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/auth/verify")
    suspend fun verifyDoctor(): Response<VerifyResponse>
}
