package exh.recs.loved

// KMK --> v0.8.0
/**
 * Pure resolution of which group member string-key ("source|url") should be treated as the
 * displayed primary for a confirmed link group. No Android/DB dependencies — fully unit-testable.
 *
 * Rule (plan §Primary Selection Rules):
 * 1. if the group has a stored primary and that primary is present among this group's currently
 *    loaded members (installed/visible, same rating tier), use it;
 * 2. otherwise fall back to the grouper's own primary-key choice;
 * 3. a missing/uninstalled stored primary never crashes — it simply falls back to (2).
 */
object RatedGroupPrimaryResolver {

    fun resolve(
        grouperPrimaryKey: String,
        memberKeys: List<String>,
        storedPrimary: RatedMangaKey?,
    ): String {
        val storedPrimaryStringKey = storedPrimary?.let { "${it.source}|${it.url}" }
        return if (storedPrimaryStringKey != null && storedPrimaryStringKey in memberKeys) {
            storedPrimaryStringKey
        } else {
            grouperPrimaryKey
        }
    }
}
// KMK <--
