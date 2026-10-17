package com.voicecare.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.voicecare.ai.data.local.dao.VoiceCareDao
import com.voicecare.ai.data.local.entities.ConsultationEntity
import com.voicecare.ai.data.local.entities.DoctorEntity
import com.voicecare.ai.data.local.entities.PatientEntity
import com.voicecare.ai.data.local.entities.PrescriptionEntity
import com.voicecare.ai.data.local.entities.SyncQueueEntity

@Database(
    entities = [
        DoctorEntity::class,
        PatientEntity::class,
        ConsultationEntity::class,
        PrescriptionEntity::class,
        SyncQueueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun voiceCareDao(): VoiceCareDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voicecare_offline.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
