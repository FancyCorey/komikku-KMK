package exh.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// KMK -->
/**
 * Purely visual, debug-only source/repo/tag relabeling for capturing public screenshots and
 * screen recordings without exposing private source names or repository names. Gated by
 * [SourcePreferences.evaluationMode] -- never touches persisted data, network behavior, or any
 * non-display logic, and **never changes manga titles** (including disliked-manga titles --
 * those stay real; only source/repo identity is obfuscated). Labels are assigned in encounter
 * order and stay stable for the lifetime of the process (not persisted across app restarts).
 */
object EvaluationModeFormatter {

    private val sourceLabels = ConcurrentHashMap<String, String>()
    private val sourceCounter = AtomicInteger(0)
    private val repoLabels = ConcurrentHashMap<String, String>()
    private val repoCounter = AtomicInteger(0)
    private val likedTagLabels = ConcurrentHashMap<String, String>()
    private val likedTagCounter = AtomicInteger(0)
    private val blockedTagLabels = ConcurrentHashMap<String, String>()
    private val blockedTagCounter = AtomicInteger(0)

    /** Stable "Source A" / "Source B" / ... label for [sourceKey] -- same source, same label all session. */
    fun sourceLabel(sourceKey: String): String {
        return sourceLabels.getOrPut(sourceKey) { "Source ${indexToLetters(sourceCounter.getAndIncrement())}" }
    }

    /** Overload for numeric source ids. */
    fun sourceLabel(sourceId: Long): String = sourceLabel(sourceId.toString())

    /**
     * Stable "Repo 1" / "Repo 2" / ... label for [repoKey] (e.g. an extension repository's
     * display name or URL) -- same repo, same label all session. Used everywhere an extension
     * repository name (e.g. the "Keiyoushi" default repo) would otherwise be shown.
     */
    fun repoLabel(repoKey: String): String {
        return repoLabels.getOrPut(repoKey) { "Repo ${repoCounter.incrementAndGet()}" }
    }

    /** Stable "Like Tag 1" / ... label for a preferred tag identified by [key]. */
    fun likedTagLabel(key: String): String {
        return likedTagLabels.getOrPut(key) { "Like Tag ${likedTagCounter.incrementAndGet()}" }
    }

    /** Stable "Dislike Tag 1" / ... label for a blocked tag identified by [key]. */
    fun blockedTagLabel(key: String): String {
        return blockedTagLabels.getOrPut(key) { "Dislike Tag ${blockedTagCounter.incrementAndGet()}" }
    }

    private fun indexToLetters(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + (n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }
}

/** Reads the current value of [SourcePreferences.evaluationMode] as Compose state. */
@Composable
fun rememberEvaluationModeEnabled(): Boolean {
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val enabled by remember { sourcePreferences.evaluationMode() }.collectAsState()
    return enabled
}
// KMK <--
