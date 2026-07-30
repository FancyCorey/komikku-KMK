package exh.md

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.source.SourceRuntime
import eu.kanade.tachiyomi.source.SourceRuntimeOperation
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import exh.md.utils.MdUtil
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                val sourceManager = Injekt.get<SourceManager>()
                sourceManager.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.let { mdex ->
                    SourceRuntime.run<Boolean>(mdex, SourceRuntimeOperation.MangaUpdate) {
                        (this as MangaDex).login(code)
                    }.onFailure { error ->
                        logcat(LogPriority.ERROR, error) { "MangaDex login failed" }
                    }
                }
                returnToSettings()
            }
        } else {
            lifecycleScope.launchIO {
                val sourceManager = Injekt.get<SourceManager>()
                sourceManager.isInitialized.first { it }
                MdUtil.getEnabledMangaDex(sourceManager = sourceManager)?.let { mdex ->
                    SourceRuntime.run<Boolean>(mdex, SourceRuntimeOperation.MangaUpdate) {
                        (this as MangaDex).logout()
                    }.onFailure { error ->
                        logcat(LogPriority.ERROR, error) { "MangaDex logout failed" }
                    }
                }
                returnToSettings()
            }
        }
    }
}
