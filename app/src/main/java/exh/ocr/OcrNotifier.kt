package exh.ocr

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

// KMK --> OCR v0.1.1 (updated from v0.1.0)

class OcrNotifier(private val context: Context) : OcrIndexWorkerNotifier {

    private val openOcrIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = Constants.OPEN_OCR_SEARCH
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private val progressBuilder = context.notificationBuilder(Notifications.CHANNEL_OCR_INDEXING) {
        setSmallIcon(R.drawable.ic_komikku)
        setColor(ContextCompat.getColor(context, R.color.ic_launcher))
        setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.komikku))
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
        setContentTitle(context.stringResource(KMR.strings.ocr_notification_indexing))
        setContentIntent(openOcrIntent)
    }

    fun buildProgressNotification(progress: OcrIndexProgress): NotificationCompat.Builder {
        val text = if (progress.totalPages > 0) {
            context.stringResource(
                KMR.strings.ocr_indexing_progress_v2,
                progress.completedPages,
                progress.totalPages,
                progress.recognizedPages,
            )
        } else {
            context.stringResource(KMR.strings.ocr_indexing_starting)
        }
        return progressBuilder.apply {
            setContentText(text)
            if (progress.totalPages > 0) {
                setProgress(progress.totalPages, progress.completedPages, false)
            } else {
                setProgress(0, 0, true)
            }
        }
    }

    override fun updateProgress(progress: OcrIndexProgress) {
        context.notify(Notifications.ID_OCR_INDEX_PROGRESS, buildProgressNotification(progress).build())
    }

    override fun dismissProgress() {
        context.cancelNotification(Notifications.ID_OCR_INDEX_PROGRESS)
    }

    override fun showComplete(recognizedPages: Int, emptyPages: Int, failedPages: Int) {
        context.cancelNotification(Notifications.ID_OCR_INDEX_PROGRESS)
        context.notify(Notifications.ID_OCR_INDEX_COMPLETE, Notifications.CHANNEL_OCR_INDEXING) {
            setSmallIcon(R.drawable.ic_komikku)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setAutoCancel(true)
            setContentTitle(context.stringResource(KMR.strings.ocr_indexing_complete))
            setContentText(
                context.stringResource(KMR.strings.ocr_notification_complete_v2, recognizedPages, emptyPages, failedPages),
            )
            setContentIntent(openOcrIntent)
        }
    }
}

// KMK <--
