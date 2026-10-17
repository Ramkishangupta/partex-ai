package com.voicecare.ai.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val isEmulator: Boolean
        get() = android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")

    var baseUrl: String = "https://partex-ai.onrender.com/"
        set(value) {
            var trimmed = value.trim()
            if (trimmed.endsWith("/api/")) {
                trimmed = trimmed.substring(0, trimmed.length - 4)
            } else if (trimmed.endsWith("/api")) {
                trimmed = trimmed.substring(0, trimmed.length - 3)
            }
            val formatted = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            field = formatted
            retrofit = null // Rebuild on change
        }

    @Volatile
    var authToken: String? = null

    // Callback invoked when server responds that the doctor has been deleted/revoked
    @Volatile
    var onAccountRevokedListener: ((String) -> Unit)? = null

    private var retrofit: Retrofit? = null

    private fun getRetrofit(): Retrofit {
        return retrofit ?: synchronized(this) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")

                authToken?.let { token ->
                    if (token.isNotBlank()) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                }

                val response = chain.proceed(requestBuilder.build())

                // Check for Central Registry Revocation signal
                if (response.code == 401) {
                    val peekBody = response.peekBody(1024).string()
                    if (peekBody.contains("ACCOUNT_REVOKED") || peekBody.contains("revoked")) {
                        authToken = null
                        onAccountRevokedListener?.invoke(
                            "Doctor account has been revoked or removed from central registry by administrator."
                        )
                    }
                }

                response
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build()

            val instance = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            retrofit = instance
            instance
        }
    }

    val authApi: AuthApiService
        get() = getRetrofit().create(AuthApiService::class.java)

    val syncApi: SyncApiService
        get() = getRetrofit().create(SyncApiService::class.java)
}
