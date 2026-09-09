package exh.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EvaluationModeFormatterTest {

    @Test
    fun `base resolver formats every generic label family without raw identity`() {
        val registry = EvaluationModeLabelRegistry { EvaluationModeBaseLabelResolver }

        val labels = listOf(
            registry.sourceLabel("private-source"),
            registry.repoLabel("private-repository"),
            registry.likedTagLabel("private-liked-tag"),
            registry.blockedTagLabel("private-blocked-tag"),
        )

        assertEquals(listOf("Source A", "Repo 1", "Like Tag 1", "Dislike Tag 1"), labels)
        labels.forEach { label -> assertFalse(label.contains("private")) }
    }

    @Test
    fun `same identity keeps its ordinal while distinct identities advance independently`() {
        val registry = EvaluationModeLabelRegistry { EvaluationModeBaseLabelResolver }

        assertEquals("Source A", registry.sourceLabel("one"))
        assertEquals("Source A", registry.sourceLabel("one"))
        assertEquals("Source B", registry.sourceLabel("two"))
        assertEquals("Repo 1", registry.repoLabel("one"))
        assertEquals("Repo 2", registry.repoLabel("two"))
        assertEquals("Like Tag 1", registry.likedTagLabel("one"))
        assertEquals("Dislike Tag 1", registry.blockedTagLabel("one"))
    }

    @Test
    fun `source letters roll over after Z`() {
        assertEquals("A", indexToLetters(0))
        assertEquals("Z", indexToLetters(25))
        assertEquals("AA", indexToLetters(26))
        assertEquals("AZ", indexToLetters(51))
        assertEquals("BA", indexToLetters(52))
        assertEquals("ZZ", indexToLetters(701))
        assertEquals("AAA", indexToLetters(702))
        assertThrows(IllegalArgumentException::class.java) { indexToLetters(-1) }
    }

    @Test
    fun `resolver changes relocalize existing identities without changing their ordinal`() {
        var resolver: EvaluationModeLabelResolver = prefixedResolver("English")
        val registry = EvaluationModeLabelRegistry { resolver }

        assertEquals("English source A", registry.sourceLabel("same"))
        assertEquals("English repo 1", registry.repoLabel("same"))

        resolver = prefixedResolver("Localized")

        assertEquals("Localized source A", registry.sourceLabel("same"))
        assertEquals("Localized repo 1", registry.repoLabel("same"))
        assertEquals("Localized source B", registry.sourceLabel("next"))
    }

    @Test
    fun `a new process registry restarts every anonymous sequence`() {
        val firstSession = EvaluationModeLabelRegistry { EvaluationModeBaseLabelResolver }
        firstSession.sourceLabel("first")
        firstSession.sourceLabel("second")
        firstSession.repoLabel("first")

        val restartedSession = EvaluationModeLabelRegistry { EvaluationModeBaseLabelResolver }

        assertEquals("Source A", restartedSession.sourceLabel("different"))
        assertEquals("Repo 1", restartedSession.repoLabel("different"))
        assertEquals("Like Tag 1", restartedSession.likedTagLabel("different"))
        assertEquals("Dislike Tag 1", restartedSession.blockedTagLabel("different"))
    }

    @Test
    fun `concurrent callers receive one stable unique source label per identity`() {
        val registry = EvaluationModeLabelRegistry { EvaluationModeBaseLabelResolver }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val labels = ConcurrentHashMap<String, MutableSet<String>>()

        try {
            repeat(100) { index ->
                executor.execute {
                    start.await()
                    val key = "source-${index % 20}"
                    val label = registry.sourceLabel(key)
                    labels.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(label)
                }
            }
            start.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }

        assertEquals(20, labels.size)
        assertTrue(labels.values.all { it.size == 1 })
        assertEquals(20, labels.values.flatten().toSet().size)
    }

    private fun prefixedResolver(prefix: String) = object : EvaluationModeLabelResolver {
        override fun source(letters: String) = "$prefix source $letters"
        override fun repository(index: Int) = "$prefix repo $index"
        override fun likedTag(index: Int) = "$prefix liked $index"
        override fun blockedTag(index: Int) = "$prefix blocked $index"
    }
}
