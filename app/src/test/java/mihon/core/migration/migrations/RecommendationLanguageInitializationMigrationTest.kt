package mihon.core.migration.migrations

import eu.kanade.domain.source.service.SourcePreferences
import exh.recs.RecommendationSourceFilter
import exh.util.FakePreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import mihon.core.migration.MigrationContext
import mihon.core.migration.MigrationJobFactory
import mihon.core.migration.MigrationStrategyFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Direct production-boundary tests for [RecommendationLanguageInitializationMigration].
 *
 * These drive the **real** migration through a real [MigrationContext] backed by a real
 * [SourcePreferences] over [FakePreferenceStore] -- not a mirrored copy of the rule. The migration's
 * own `isSet()`/version-code branching is what executes.
 *
 * Run with:
 * `./gradlew :app:testDebugUnitTest --tests "*.RecommendationLanguageInitializationMigrationTest"`
 */
class RecommendationLanguageInitializationMigrationTest {

    private lateinit var preferenceStore: FakePreferenceStore
    private lateinit var sourcePreferences: SourcePreferences

    private lateinit var migration: RecommendationLanguageInitializationMigration

    private fun lastVersionCodePreference() = preferenceStore.getInt(
        Preference.appStateKey(RecommendationLanguageInitializationMigration.LAST_VERSION_CODE_KEY),
        0,
    )

    @BeforeEach
    fun setUp() {
        // Injekt is process-global and its singletons are cached on first resolution, so this class must
        // NOT overwrite an existing PreferenceStore binding -- doing so breaks whichever other test class
        // resolved first (BackupCleanupRecoveryStoreTest casts its readback to FakePreferenceStore).
        // Instead: register only if absent, then adopt whatever Injekt actually hands out and reset it.
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingletonFactory<PreferenceStore> { FakePreferenceStore() }
                }
            },
        )
        preferenceStore = Injekt.get<PreferenceStore>() as FakePreferenceStore
        // clearAll() -- not delete() -- because delete() keeps the key present, and every branch under
        // test is guarded by isSet().
        preferenceStore.clearAll()

        sourcePreferences = SourcePreferences(preferenceStore)
        migration = RecommendationLanguageInitializationMigration(preferenceStore, sourcePreferences)
        val prefs = sourcePreferences
        Injekt.importModule(
            object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    // addSingleton (overwriting) rather than addSingletonFactory (first-resolution wins):
                    // UpdateMangaFromRemoteRegistrationTest binds SourcePreferences to a relaxed mockk,
                    // whose recommendationSourceLanguages().isSet() answers false, which would make this
                    // migration re-run and re-materialize on every case. This claims the binding back for
                    // each test here, over the same store the assertions read.
                    addSingleton(prefs)
                }
            },
        )
    }

    private suspend fun runMigration(): Boolean = migration.invoke(MigrationContext(dryrun = false))

    private fun markFreshInstall() {
        // App.initializeMigrator() reads 0 for a fresh install; leave the key unset OR explicitly 0.
        lastVersionCodePreference().set(RecommendationLanguageInitializationMigration.FRESH_INSTALL_VERSION_CODE)
    }

    private fun markLegacyUpgradeFrom(versionCode: Int) {
        lastVersionCodePreference().set(versionCode)
    }

    // ---- New install: snapshot normalized global source languages ----

    @Test
    fun `a fresh install snapshots the normalized global source languages`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf("en", "ja", "fr"))

        assertTrue(runMigration())

        assertEquals(setOf("en", "ja", "fr"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a fresh install normalizes case and blanks in the global languages`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf(" EN ", "Ja", "", "   ", "FR"))

        runMigration()

        assertEquals(setOf("en", "ja", "fr"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a fresh install preserves a valid all selection`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf("all"))

        runMigration()

        assertEquals(setOf("all"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a fresh install with empty global languages falls back to English`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(emptySet())

        runMigration()

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a fresh install with only blank global languages falls back to English`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf("", "   ", "\t"))

        runMigration()

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `an unset version-code key is treated as a fresh install`() = runTest {
        // The app-state key does not exist at all on a genuinely new device.
        sourcePreferences.enabledLanguages().set(setOf("de"))

        runMigration()

        assertEquals(setOf("de"), sourcePreferences.recommendationSourceLanguages().get())
    }

    // ---- Legacy upgrade with absent key: retain English ----

    @Test
    fun `a legacy upgrade with an absent recommendation key retains English`() = runTest {
        markLegacyUpgradeFrom(120)
        // The legacy user had a wide global catalogue; recommendations must not silently widen.
        sourcePreferences.enabledLanguages().set(setOf("en", "ja", "ko", "zh"))

        assertTrue(runMigration())

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a legacy upgrade does not widen the catalogue even when global languages are all`() = runTest {
        markLegacyUpgradeFrom(120)
        sourcePreferences.enabledLanguages().set(setOf("all"))

        runMigration()

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `the materialized legacy value equals the declared read-time default`() = runTest {
        // Proves the legacy path is behaviorally inert: the stored value is exactly what the
        // preference already returned by default, so no consumer's resolved languages change and no
        // recommendation cache becomes stale. `defaultValue()` is read rather than `get()` because
        // reading the value would itself mark the key present in the fake store.
        val declaredDefault = sourcePreferences.recommendationSourceLanguages().defaultValue()
        markLegacyUpgradeFrom(200)
        sourcePreferences.enabledLanguages().set(setOf("en", "ja"))

        runMigration()

        assertEquals(declaredDefault, sourcePreferences.recommendationSourceLanguages().get())
    }

    // ---- Explicit choice always wins ----

    @Test
    fun `an explicit recommendation choice is never rewritten on a fresh install`() = runTest {
        markFreshInstall()
        sourcePreferences.recommendationSourceLanguages().set(setOf("ja"))
        sourcePreferences.enabledLanguages().set(setOf("en", "fr", "de"))

        assertFalse(runMigration())

        assertEquals(setOf("ja"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `an explicit recommendation choice is never rewritten on upgrade`() = runTest {
        markLegacyUpgradeFrom(150)
        sourcePreferences.recommendationSourceLanguages().set(setOf("ja", "ko"))

        assertFalse(runMigration())

        assertEquals(setOf("ja", "ko"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `an explicitly stored English choice is preserved and not confused with an absent key`() = runTest {
        markFreshInstall()
        sourcePreferences.recommendationSourceLanguages().set(setOf("en"))
        sourcePreferences.enabledLanguages().set(setOf("ja", "ko"))

        assertFalse(runMigration())

        // Had the migration mistaken this for an absent key it would have snapshotted ja/ko.
        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `an explicitly stored empty selection is still an explicit choice and is not overwritten`() = runTest {
        markFreshInstall()
        sourcePreferences.recommendationSourceLanguages().set(emptySet())
        sourcePreferences.enabledLanguages().set(setOf("ja"))

        assertFalse(runMigration())

        assertEquals(emptySet<String>(), sourcePreferences.recommendationSourceLanguages().get())
    }

    // ---- Idempotence: duplicate migration, process recreation, later global changes ----

    @Test
    fun `running the migration twice does not change the snapshot`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf("en", "ja"))

        assertTrue(runMigration())
        val afterFirst = sourcePreferences.recommendationSourceLanguages().get()
        assertFalse(runMigration())

        assertEquals(afterFirst, sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a later global-language change never rewrites recommendation languages`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf("en"))
        runMigration()

        // User later widens their global catalogue, then the app restarts and migrations run again.
        sourcePreferences.enabledLanguages().set(setOf("en", "ja", "ko", "zh"))
        assertFalse(runMigration())

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a legacy materialization is not re-evaluated as a fresh install on a later upgrade`() = runTest {
        markLegacyUpgradeFrom(100)
        sourcePreferences.enabledLanguages().set(setOf("en", "ja"))
        runMigration()

        // Next release: version code advances, migrations run again.
        markLegacyUpgradeFrom(200)
        assertFalse(runMigration())

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    // ---- Restore ordering ----

    @Test
    fun `a restore that lands before the migration is treated as an explicit choice`() = runTest {
        // Backup restore writes the key, then migrations run on next start.
        markLegacyUpgradeFrom(150)
        sourcePreferences.recommendationSourceLanguages().set(setOf("fr", "de"))

        assertFalse(runMigration())

        assertEquals(setOf("fr", "de"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `a restore that lands after the migration wins over the materialized default`() = runTest {
        markFreshInstall()
        sourcePreferences.enabledLanguages().set(setOf("en"))
        runMigration()

        // Restore then overwrites, and a subsequent migration pass must not undo it.
        sourcePreferences.recommendationSourceLanguages().set(setOf("es"))
        assertFalse(runMigration())

        assertEquals(setOf("es"), sourcePreferences.recommendationSourceLanguages().get())
    }

    // ---- Structural guards ----
    //
    // The "missing dependency returns false" branch is deliberately NOT tested here: Injekt's
    // `importModule` is additive and cannot un-register an already-bound type, so any such test would
    // pass without ever exercising the null branch. That branch is verified by source inspection only
    // (`?: return@withIOContext false` on both lookups) and is recorded as such in the Batch 08 report.

    @Test
    fun `the migration is registered exactly once in the production migration list`() = runTest {
        val registered = migrations.filterIsInstance<RecommendationLanguageInitializationMigration>()
        assertEquals(1, registered.size)
    }

    @Test
    fun `the migration runs on both fresh installs and upgrades by being an ALWAYS migration`() = runTest {
        // InitialMigrationStrategy filters to isAlways, so a versioned migration would never reach a
        // new install. This guards that structural requirement.
        assertTrue(migration.isAlways)
    }

    // ---- Malformed / blank / unsupported stored input contract ----
    //
    // The documented decision: this migration does NOT repair stored values, because
    // `RecommendationSourceFilter.filterForRecommendations` normalizes on every read, so a blank or
    // malformed stored value is already repaired at each consumer. These tests pin both halves of that
    // contract -- the migration's non-interference AND the consumer-boundary repair that justifies it.

    @Test
    fun `an explicitly stored empty set is retained and resolves to English at the consumer boundary`() =
        runTest {
            sourcePreferences.recommendationSourceLanguages().set(emptySet())
            lastVersionCodePreference().set(0)

            assertFalse(migration(MigrationContext(dryrun = false)))
            assertEquals(emptySet<String>(), sourcePreferences.recommendationSourceLanguages().get())
            // Semantic preservation: the stored bytes are untouched, and the resolved set is still English.
            assertEquals(
                setOf("en"),
                RecommendationSourceFilter.normalizeLanguages(
                    sourcePreferences.recommendationSourceLanguages().get(),
                ),
            )
        }

    @Test
    fun `a malformed stored value is retained and repaired on read, not rewritten`() = runTest {
        val malformed = setOf("  EN  ", "", "   ", "Ja")
        sourcePreferences.recommendationSourceLanguages().set(malformed)
        lastVersionCodePreference().set(500)

        assertFalse(migration(MigrationContext(dryrun = false)))
        assertEquals(malformed, sourcePreferences.recommendationSourceLanguages().get())
        assertEquals(
            setOf("en", "ja"),
            RecommendationSourceFilter.normalizeLanguages(
                sourcePreferences.recommendationSourceLanguages().get(),
            ),
        )
    }

    @Test
    fun `an unsupported language code is retained rather than deleted`() = runTest {
        // "Unsupported" is not truthfully knowable: the only owner that could answer it,
        // availableLanguages(), derives from the *currently installed* sources. Deleting the code would
        // destroy a valid choice merely because an extension was absent at migration time. It is inert
        // instead -- isAllowedLanguage simply never matches it.
        val stored = setOf("en", "zz")
        sourcePreferences.recommendationSourceLanguages().set(stored)
        lastVersionCodePreference().set(500)

        assertFalse(migration(MigrationContext(dryrun = false)))
        assertEquals(stored, sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `equivalent language selections normalize to one identical cache key`() {
        // Regression guard for the confirmed defect repaired in RecommendsScreenModel: the group-preview
        // cache key was built from the RAW stored set, so these three equivalent selections produced
        // three different keys and fragmented the cache.
        fun key(languages: Set<String>) =
            RecommendationSourceFilter.normalizeLanguages(languages).sorted().joinToString(",")

        assertEquals("en", key(setOf("EN")))
        assertEquals("en", key(setOf("en", "")))
        assertEquals("en", key(setOf("  en  ")))
        assertEquals("en", key(emptySet()))
    }

    // ---- Execution through the real migration strategies ----

    private fun realStrategyRunner(scope: CoroutineScope): Pair<MigrationStrategyFactory, MutableList<Unit>> {
        val completions = mutableListOf<Unit>()
        val jobFactory = MigrationJobFactory(MigrationContext(dryrun = false), scope)
        val factory = MigrationStrategyFactory(jobFactory) { completions += Unit }
        return factory to completions
    }

    @Test
    fun `the real fresh-install strategy executes this migration and snapshots global languages`() =
        runTest {
            sourcePreferences.enabledLanguages().set(setOf("en", "ko"))
            lastVersionCodePreference().set(0)
            val (factory, _) = realStrategyRunner(this)

            // old == 0 routes to InitialMigrationStrategy, which filters to isAlways only.
            val strategy = factory.create(old = 0, new = 500)
            assertTrue(strategy(listOf(migration)).await())

            assertEquals(setOf("en", "ko"), sourcePreferences.recommendationSourceLanguages().get())
        }

    @Test
    fun `the real upgrade strategy executes this migration and retains English`() = runTest {
        sourcePreferences.enabledLanguages().set(setOf("en", "ko"))
        lastVersionCodePreference().set(400)
        val (factory, _) = realStrategyRunner(this)

        val strategy = factory.create(old = 400, new = 500)
        strategy(listOf(migration)).await()

        assertEquals(setOf("en"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `the real strategy does not rewrite an existing value on a later upgrade`() = runTest {
        sourcePreferences.recommendationSourceLanguages().set(setOf("fr"))
        lastVersionCodePreference().set(400)
        val (factory, _) = realStrategyRunner(this)

        factory.create(old = 400, new = 500).invoke(listOf(migration)).await()
        factory.create(old = 500, new = 600).invoke(listOf(migration)).await()

        assertEquals(setOf("fr"), sourcePreferences.recommendationSourceLanguages().get())
    }

    @Test
    fun `this migration is ordered before every versioned migration in the real list`() {
        // MigrationJobFactory sorts by version and ALWAYS is -1f. Running after a versioned migration
        // would break the fresh-install/upgrade discrimination.
        val sorted = migrations.sortedBy { it.version }
        val index = sorted.indexOfFirst { it is RecommendationLanguageInitializationMigration }
        assertTrue(index >= 0, "migration must be registered in the production list")
        val firstVersioned = sorted.indexOfFirst { !it.isAlways }
        assertTrue(index < firstVersioned, "ALWAYS migration must precede every versioned migration")
    }

    @Test
    fun `the noop strategy for a downgrade never runs this migration`() = runTest {
        lastVersionCodePreference().set(600)
        val (factory, _) = realStrategyRunner(this)

        // old >= new routes to NoopMigrationStrategy.
        factory.create(old = 600, new = 500).invoke(listOf(migration)).await()

        assertFalse(sourcePreferences.recommendationSourceLanguages().isSet())
    }
}
// KMK <--
