package exh.util

import java.util.UUID

// KMK Undo Expansion Phase 1 -->
/**
 * Evaluation Mode Undo Journal for single-key local preference/small-row mutations: recommendation
 * numeric/boolean settings, language selection, source order, source exclusion, tag preferences,
 * Sources To Try dismissal/quality marks, Source Evaluation quality marks, and reading-schedule state.
 *
 * Unlike the taste/group/library journals, the state shape genuinely varies per preference (Boolean,
 * Int, a serialized string, a string set, a tag-preference row...). Rather than one journal type per
 * key (which the audit warned against as unbounded proliferation) or a generic `Map<String, Any>`/JSON
 * blob (explicitly disallowed), each entry is a small generic [PreferenceUndoEntry]`<T>` bound to one
 * concrete value type `T` already used elsewhere in this codebase for that exact preference (Boolean,
 * Int, String, or `Set<String>` -- never a dynamic/opaque type). The entry captures typed `readCurrent`
 * and `restore` functions supplied by the specific caller that knows how to read/write that preference
 * or row, so the journal and service stay generic while every stored value remains concretely typed.
 */
data class RecommendationSourcePreferenceUndoState(
    val liked: Set<String>,
    val disliked: Set<String>,
)

enum class PreferenceJournalActionType {
    RATED_MANGA_VISIBILITY,
    HIDE_KNOWN_MANGA,
    MIN_CHAPTER_COUNT,
    ENRICHMENT_CAP,
    RESULT_BUDGET,
    GROUP_PREVIEW_BUDGET,
    RECOMMENDATION_LANGUAGES,
    SOURCE_ORDER,
    SOURCE_EXCLUSION,
    SAME_MANGA_MATCHING,
    BEST_VERSION_PREVIEW,
    SOURCE_PREFERENCE,
    TAG_PREFERENCE,
    SUGGESTION_DISMISSAL,
    DISMISSED_SUGGESTIONS_CLEAR,
    SOURCE_QUALITY_MARK,
    SOURCE_QUALITY_CLEAR_ALL,
    READING_SCHEDULE,
}

/**
 * @param T the concrete value type for this preference (Boolean/Int/String/Set<String>/a small typed
 * value class such as [tachiyomi.domain.taste.model.TagPreference]?). Never a generic map or JSON string.
 * @param identityKey stable identity for the touched preference/row (e.g. a normalized tag, a source id
 * string, or a fixed constant for a singleton preference), used only for display/grouping -- restore
 * always goes through [readCurrent]/[restore], never a lookup by this key into another store.
 * @param readCurrent reads the live current value, used for conflict detection at undo time.
 * @param restore writes the given (previous) value back, used only after a successful conflict check.
 */
data class PreferenceUndoEntry<T>(
    val id: String,
    val timestamp: Long,
    val actionType: PreferenceJournalActionType,
    val identityKey: String,
    val previousValue: T,
    val expectedPostValue: T,
    val readCurrent: suspend () -> T,
    val restore: suspend (T) -> Unit,
    val reversible: Boolean = true,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Bounded, thread-safe, in-memory journal. Each entry is one whole preference change (never split by
 * eviction, since a preference write is never a multi-key bulk operation in this codebase today).
 */
object PreferenceUndoJournal {
    const val MAX_ENTRIES = 20

    private val lock = Any()
    private val entries = ArrayDeque<PreferenceUndoEntry<*>>()

    fun record(entry: PreferenceUndoEntry<*>) {
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
        }
    }

    /** Most recent first. */
    fun snapshot(): List<PreferenceUndoEntry<*>> = synchronized(lock) { entries.toList().asReversed() }

    fun removeById(id: String) {
        synchronized(lock) { entries.removeAll { it.id == id } }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }
}
// KMK <--
