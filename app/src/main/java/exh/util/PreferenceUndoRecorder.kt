package exh.util

import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.taste.interactor.ClearTagTaste
import tachiyomi.domain.taste.interactor.GetTagTaste
import tachiyomi.domain.taste.interactor.SetTagTaste
import tachiyomi.domain.taste.model.TagPreference

// KMK Undo Expansion Phase 1 -->
/**
 * Builds not-yet-committed [PreferenceUndoEntry] snapshots. Same build-before-write/commit-after-success
 * contract as every other recorder in this package: callers read the previous value, perform their
 * write, and only call [PreferenceUndoJournal.record] with the built entry after the write succeeds.
 *
 * No-ops (returns `null`) when Evaluation Mode is disabled.
 */
object PreferenceUndoRecorder {

    /**
     * Generic builder for any preference already backed by a real [Preference]`<T>` object (every
     * simple Boolean/Int/String/Set<String> recommendation setting in this codebase). `T` is whatever
     * concrete type the real preference already uses -- never a generic/opaque payload.
     */
    fun <T> buildPreferenceEntry(
        sourcePreferences: SourcePreferences,
        actionType: PreferenceJournalActionType,
        identityKey: String,
        preference: Preference<T>,
        previousValue: T,
        newValue: T,
    ): PreferenceUndoEntry<T>? {
        if (!sourcePreferences.evaluationMode().get() || previousValue == newValue) return null
        return PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = actionType,
            identityKey = identityKey,
            previousValue = previousValue,
            expectedPostValue = newValue,
            readCurrent = { preference.get() },
            restore = { preference.set(it) },
        )
    }

    /** Tag preference is a `tag_taste` DB row, not a [Preference], so it needs its own read/write pair. */
    fun buildTagPreferenceEntry(
        sourcePreferences: SourcePreferences,
        getTagTaste: GetTagTaste,
        setTagTaste: SetTagTaste,
        clearTagTaste: ClearTagTaste,
        displayName: String,
        normalizedTag: String,
        previousPreference: TagPreference?,
        newPreference: TagPreference?,
    ): PreferenceUndoEntry<TagPreference?>? {
        if (!sourcePreferences.evaluationMode().get() || previousPreference == newPreference) return null
        return PreferenceUndoEntry(
            id = PreferenceUndoEntry.newId(),
            timestamp = System.currentTimeMillis(),
            actionType = PreferenceJournalActionType.TAG_PREFERENCE,
            identityKey = normalizedTag,
            previousValue = previousPreference,
            expectedPostValue = newPreference,
            readCurrent = { getTagTaste.await(normalizedTag)?.preference?.let { TagPreference.fromValue(it) } },
            restore = { value ->
                if (value == null) clearTagTaste.await(normalizedTag) else setTagTaste.await(displayName, value)
            },
        )
    }
}
// KMK <--
