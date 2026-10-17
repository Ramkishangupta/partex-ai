package com.voicecare.ai.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.voicecare.ai.VoiceCareApplication

/**
 * Background WorkManager Worker responsible for scheduled and opportunistic offline sync.
 * Executes automatically when the device transitions to CONNECTED state or on a 15-minute periodic schedule.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker initiated by WorkManager. Triggering offline-to-cloud sync...")
        return try {
            val app = applicationContext as? VoiceCareApplication ?: return Result.failure()
            val syncResult = app.repository.triggerSync()
            if (syncResult.success) {
                Log.i(TAG, "SyncWorker finished successfully. Pushed: ${syncResult.pushedCount}, Pulled: ${syncResult.pulledCount}")
                Result.success()
            } else {
                Log.w(TAG, "Sync failed: ${syncResult.error}. Scheduling backoff retry.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled exception in SyncWorker", e)
            Result.retry()
        }
    }
}
