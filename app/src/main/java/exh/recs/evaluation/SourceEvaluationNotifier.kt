package exh.recs.evaluation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR

// KMK -->
class SourceEvaluationNotifier(private val context: Context) {

    // KMK --> v0.6.19 follow-up: deep link — tap notification opens Source Evaluation screen
    private val openSourceEvaluationIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = Constants.OPEN_SOURCE_EVALUATION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    // KMK <--

    private val progressBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SOURCE_EVALUATION,
    ) {
        setSmallIcon(R.drawable.ic_komikku)
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
        setContentTitle(context.stringResource(KMR.strings.source_evaluation_job_notification_title))
        // KMK --> v0.6.19 follow-up: tap opens Source Evaluation screen
        setContentIntent(openSourceEvaluationIntent)
        // KMK <--
    }

    fun buildProgressNotification(queueState: SourceEvaluationQueueState): NotificationCompat.Builder {
        val currentName = queueState.currentExtensionName
            ?: context.stringResource(KMR.strings.source_evaluation_starting)
        return progressBuilder.apply {
            setContentText(currentName)
            if (queueState.totalCount > 0) {
                setProgress(queueState.totalCount, queueState.completedCount, false)
            } else {
                setProgress(0, 0, true)
            }
        }
    }

    fun updateProgress(queueState: SourceEvaluationQueueState) {
        context.notify(
            Notifications.ID_SOURCE_EVALUATION_PROGRESS,
            buildProgressNotification(queueState).build(),
        )
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_SOURCE_EVALUATION_PROGRESS)
    }

    fun showComplete(strongFitCount: Int) {
        context.cancelNotification(Notifications.ID_SOURCE_EVALUATION_PROGRESS)
        context.notify(Notifications.ID_SOURCE_EVALUATION_COMPLETE, Notifications.CHANNEL_SOURCE_EVALUATION) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setAutoCancel(true)
            setContentTitle(context.stringResource(KMR.strings.source_evaluation_completed))
            // KMK --> v0.6.19 follow-up: tap opens Source Evaluation screen
            setContentIntent(openSourceEvaluationIntent)
            // KMK <--
            if (strongFitCount > 0) {
                setContentText(
                    context.stringResource(KMR.strings.source_evaluation_strong_fit_count, strongFitCount),
                )
            }
        }
    }
}
// KMK <--
