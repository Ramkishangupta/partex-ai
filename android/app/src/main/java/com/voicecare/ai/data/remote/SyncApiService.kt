package com.voicecare.ai.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class SyncChanges(
    val patients: List<Any> = emptyList(),
    val consultations: List<Any> = emptyList(),
    val prescriptions: List<Any> = emptyList()
)

data class SyncRequest(
    val lastSyncTimestamp: String?,
    val changes: SyncChanges
)

data class ServerUpdates(
    val patients: List<Map<String, Any>> = emptyList(),
    val consultations: List<Map<String, Any>> = emptyList(),
    val prescriptions: List<Map<String, Any>> = emptyList()
)

data class SyncResponse(
    val success: Boolean,
    val syncTimestamp: String,
    val report: Map<String, Any>?,
    val serverUpdates: ServerUpdates?
)

interface SyncApiService {

    @POST("api/sync")
    suspend fun syncAll(@Body request: SyncRequest): Response<SyncResponse>

    @GET("api/sync/pull")
    suspend fun pullUpdates(@Query("since") since: String?): Response<SyncResponse>
}
