package eu.kanade.presentation.more.settings.screen.about

// KMK --> v0.8.1-fix2
/**
 * Pure decision logic for the automatic KMK-Recs What's New dialog in `MainActivity.kt`. Extracted
 * so the show/sequence/mark-seen rules are directly unit-testable without a Compose/Activity host.
 *
 * The normal Komikku changelog dialog and the KMK-Recs changelog dialog must never render at the
 * same time — `MainActivity` renders them as a single `if (showChangelog) { ... } else if
 * (shouldShowKmkDialog(...)) { ... }` tree, so while the Komikku dialog is showing, the KMK dialog
 * stays pending: not shown, and critically not marked seen. Once the Komikku dialog is dismissed or
 * opened (`showChangelog` flips to `false`), Compose recomposes this tree and the KMK dialog renders
 * on that next pass if it still has unseen content — the sequencing falls naturally out of normal
 * recomposition, this policy just makes each individual decision a named, testable function instead
 * of inline boolean expressions.
 */
internal object KmkRecsWhatsNewPolicy {

    /** True when the current KMK-Recs build has changelog content the user hasn't seen yet. */
    fun hasUnseenChangelog(currentVersionCode: Int, lastSeenVersionCode: Int): Boolean =
        currentVersionCode > lastSeenVersionCode

    /**
     * True when the KMK dialog should render this composition pass. The normal Komikku changelog
     * dialog takes priority within the same launch: while it is showing, this always returns
     * `false`, keeping the KMK dialog pending rather than stacking both dialogs at once.
     */
    fun shouldShowKmkDialog(komikkuChangelogShowing: Boolean, hasUnseenKmkChangelog: Boolean): Boolean =
        !komikkuChangelogShowing && hasUnseenKmkChangelog

    /**
     * The version code to persist as "seen" when the user dismisses/opens the KMK dialog, or opens
     * `KmkRecsWhatsNewScreen` manually. Never called merely because the app launched or the normal
     * Komikku changelog was shown — only an explicit KMK acknowledgement marks it seen.
     */
    fun seenVersionCodeOnAcknowledge(currentVersionCode: Int): Int = currentVersionCode
}
// KMK <--
