package com.example.hybridmind.workers

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.hybridmind.data.local.AppDatabase
import java.util.concurrent.TimeUnit

class AutoPruneWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "hybridmind_database"
            ).build()

            // Delete offline messages older than 90 days
            val ninetyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)
            database.chatDao().pruneOfflineMessages(ninetyDaysAgo)

            database.close()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
