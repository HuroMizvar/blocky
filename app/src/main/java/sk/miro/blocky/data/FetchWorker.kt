package sk.miro.blocky.data

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Načíta položky naskenovaných bločkov z eKasa na pozadí.
 * Beží aj po zatvorení aplikácie; bez internetu počká na pripojenie a potom to skúsi znova.
 */
class FetchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (Repository(applicationContext).processPending()) Result.retry() else Result.success()

    companion object {
        private const val NAME = "fetch-receipts"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<FetchWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // APPEND_OR_REPLACE: nový sken počas behu sa spracuje hneď po aktuálnom behu
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
