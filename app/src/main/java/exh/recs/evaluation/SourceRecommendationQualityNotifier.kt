package exh.recs.evaluation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> v0.7.43
/**
 * Foreground/progress notification for [SourceRecommendationQualityJob].
 *
 * Deliberately separate from [SourceEvaluationNotifier] — uses its own channel and notification IDs
 * ([Notifications.CHANNEL_SOURCE_RECOMMENDATION_QUALITY]) so a running compatibility check never
 * collides with a running full Source Evaluation notification. Tapping still deep-links into the
 * Source Evaluation screen, since that is where compatibility progress is displayed.
 */
class SourceRecommendationQualityNotifier(
    private val context: Context,
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : SourceRecommendationQualityWorkerNotifier {

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

    private val progressBuilder = context.notificationBuilder(
        Notifications.CHANNEL_SOURCE_RECOMMENDATION_QUALITY,
    ) {
        setSmallIcon(R.drawable.ic_komikku)
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
        setContentTitle(context.stringResource(KMR.strings.source_recommendation_quality_job_notification_title))
        setContentIntent(openSourceEvaluationIntent)
    }

    fun buildProgressNotification(queueState: SourceRecommendationQualityQueueState): NotificationCompat.Builder {
        val currentName = queueState.currentSourceName?.let {
            SourceEvaluationProgressLabelPolicy.sourceLabel(it, sourcePreferences.evaluationMode().get())
        }
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

    override fun updateProgress(queueState: SourceRecommendationQualityQueueState) {
        context.notify(
            Notifications.ID_SOURCE_RECOMMENDATION_QUALITY_PROGRESS,
            buildProgressNotification(queueState).build(),
        )
    }

    override fun dismissProgress() {
        context.cancelNotification(Notifications.ID_SOURCE_RECOMMENDATION_QUALITY_PROGRESS)
    }

    override fun showComplete(checkedCount: Int) {
        context.cancelNotification(Notifications.ID_SOURCE_RECOMMENDATION_QUALITY_PROGRESS)
        context.notify(
            Notifications.ID_SOURCE_RECOMMENDATION_QUALITY_COMPLETE,
            Notifications.CHANNEL_SOURCE_RECOMMENDATION_QUALITY,
        ) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setAutoCancel(true)
            setContentTitle(context.stringResource(KMR.strings.source_recommendation_quality_job_completed))
            setContentIntent(openSourceEvaluationIntent)
            if (checkedCount > 0) {
                setContentText(
                    context.pluralStringResource(
                        KMR.plurals.source_recommendation_quality_job_checked_count,
                        count = checkedCount,
                        checkedCount,
                    ),
                )
            }
        }
    }
}
// KMK <--
