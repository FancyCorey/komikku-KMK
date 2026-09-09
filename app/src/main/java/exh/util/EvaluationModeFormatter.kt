package exh.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

    @Volatile
    private var labelResolver: EvaluationModeLabelResolver = EvaluationModeBaseLabelResolver

    private val registry = EvaluationModeLabelRegistry { labelResolver }

    /**
     * Installs the Android resource resolver once per process. The registry stores only anonymous
     * ordinals, so resolving on every call also follows an in-process locale change without exposing
     * or re-keying the real source, repository, or tag identity.
     */
    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        labelResolver = object : EvaluationModeLabelResolver {
            override fun source(letters: String): String =
                applicationContext.stringResource(KMR.strings.evaluation_mode_source_label, letters)

            override fun repository(index: Int): String =
                applicationContext.stringResource(KMR.strings.evaluation_mode_repo_label, index)

            override fun likedTag(index: Int): String =
                applicationContext.stringResource(KMR.strings.evaluation_mode_liked_tag_label, index)

            override fun blockedTag(index: Int): String =
                applicationContext.stringResource(KMR.strings.evaluation_mode_blocked_tag_label, index)
        }
    }

    /** Stable "Source A" / "Source B" / ... label for [sourceKey] -- same source, same label all session. */
    fun sourceLabel(sourceKey: String): String = registry.sourceLabel(sourceKey)

    /** Overload for numeric source ids. */
    fun sourceLabel(sourceId: Long): String = sourceLabel(sourceId.toString())

    /**
     * Stable "Repo 1" / "Repo 2" / ... label for [repoKey] (e.g. an extension repository's
     * display name or URL) -- same repo, same label all session. Used everywhere an extension
     * repository name (e.g. the "Keiyoushi" default repo) would otherwise be shown.
     */
    fun repoLabel(repoKey: String): String = registry.repoLabel(repoKey)

    /** Stable "Like Tag 1" / ... label for a preferred tag identified by [key]. */
    fun likedTagLabel(key: String): String = registry.likedTagLabel(key)

    /** Stable "Dislike Tag 1" / ... label for a blocked tag identified by [key]. */
    fun blockedTagLabel(key: String): String = registry.blockedTagLabel(key)
}

internal interface EvaluationModeLabelResolver {
    fun source(letters: String): String
    fun repository(index: Int): String
    fun likedTag(index: Int): String
    fun blockedTag(index: Int): String
}

/** Privacy-safe base fallback for host tests and calls made before Android application startup completes. */
internal object EvaluationModeBaseLabelResolver : EvaluationModeLabelResolver {
    override fun source(letters: String): String = "Source $letters"
    override fun repository(index: Int): String = "Repo $index"
    override fun likedTag(index: Int): String = "Like Tag $index"
    override fun blockedTag(index: Int): String = "Dislike Tag $index"
}

/** Stores only opaque ordinals and resolves labels at display time so locale changes are not stale. */
internal class EvaluationModeLabelRegistry(
    private val resolver: () -> EvaluationModeLabelResolver,
) {
    private val lock = Any()
    private val sourceIndices = mutableMapOf<String, Int>()
    private val repoIndices = mutableMapOf<String, Int>()
    private val likedTagIndices = mutableMapOf<String, Int>()
    private val blockedTagIndices = mutableMapOf<String, Int>()
    private var sourceCounter = 0
    private var repoCounter = 0
    private var likedTagCounter = 0
    private var blockedTagCounter = 0

    fun sourceLabel(key: String): String = resolver().source(indexToLetters(indexForSource(key)))

    fun repoLabel(key: String): String = resolver().repository(indexForRepo(key) + 1)

    fun likedTagLabel(key: String): String = resolver().likedTag(indexForLikedTag(key) + 1)

    fun blockedTagLabel(key: String): String = resolver().blockedTag(indexForBlockedTag(key) + 1)

    private fun indexForSource(key: String): Int = synchronized(lock) {
        sourceIndices.getOrPut(key) { sourceCounter++ }
    }

    private fun indexForRepo(key: String): Int = synchronized(lock) {
        repoIndices.getOrPut(key) { repoCounter++ }
    }

    private fun indexForLikedTag(key: String): Int = synchronized(lock) {
        likedTagIndices.getOrPut(key) { likedTagCounter++ }
    }

    private fun indexForBlockedTag(key: String): Int = synchronized(lock) {
        blockedTagIndices.getOrPut(key) { blockedTagCounter++ }
    }
}

internal fun indexToLetters(index: Int): String {
    require(index >= 0) { "index must be non-negative" }
    var n = index
    val result = StringBuilder()
    do {
        result.insert(0, 'A' + (n % 26))
        n = n / 26 - 1
    } while (n >= 0)
    return result.toString()
}

/** Reads the current value of [SourcePreferences.evaluationMode] as Compose state. */
@Composable
fun rememberEvaluationModeEnabled(): Boolean {
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val enabled by remember { sourcePreferences.evaluationMode() }.collectAsState()
    return enabled
}
// KMK <--
