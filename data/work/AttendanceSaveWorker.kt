package com.attendance.rollcheck.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.attendance.rollcheck.R
import com.attendance.rollcheck.data.repository.AttendanceRepository

class AttendanceSaveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val classId = inputData.getString(KEY_CLASS_ID).orEmpty()
        return createForegroundInfo(classId)
    }

    override suspend fun doWork(): Result {
        val classId = inputData.getString(KEY_CLASS_ID)
            ?: return Result.failure(workDataOf(KEY_ERROR to "Missing class id"))

        setForeground(getForegroundInfo())

        val repo = AttendanceRepository.getInstance(applicationContext)
        return try {
            val success = repo.finalSave(classId)
            if (success) {
                Result.success(workDataOf(KEY_CLASS_ID to classId))
            } else {
                Result.failure(workDataOf(KEY_CLASS_ID to classId, KEY_ERROR to "Excel write failed"))
            }
        } catch (t: Throwable) {
            Result.failure(
                workDataOf(
                    KEY_CLASS_ID to classId,
                    KEY_ERROR to (t.message ?: "Excel write failed")
                )
            )
        }
    }

    private fun createForegroundInfo(classId: String): ForegroundInfo {
        ensureNotificationChannel()
        val title = "Writing attendance to Excel"
        val message = if (classId.isBlank()) {
            "Please keep RollCheck open until this finishes."
        } else {
            "Please keep RollCheck open while $classId is being written."
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Excel Write",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while attendance is being written to Excel."
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "rollcheck_excel_write"
        private const val NOTIFICATION_ID = 4201
        private const val KEY_CLASS_ID = "class_id"
        const val KEY_ERROR = "error"

        fun uniqueWorkName(classId: String): String = "attendance-save-$classId"

        fun enqueue(context: Context, classId: String): String {
            val request = OneTimeWorkRequestBuilder<AttendanceSaveWorker>()
                .setInputData(inputData(classId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueWorkName(classId), ExistingWorkPolicy.KEEP, request)

            return request.id.toString()
        }

        private fun inputData(classId: String): Data = workDataOf(KEY_CLASS_ID to classId)
    }
}
