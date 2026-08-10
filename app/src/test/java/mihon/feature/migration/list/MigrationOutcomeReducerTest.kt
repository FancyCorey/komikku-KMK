package mihon.feature.migration.list

import mihon.domain.migration.usecases.MigrationOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// KMK>
class MigrationOutcomeReducerTest {

    @Test
    fun `Success reduces to SUCCESS`() {
        val outcome = MigrationOutcome.Success(emptySet(), emptySet(), emptySet())
        assertEquals(MigrationListItemState.SUCCESS, MigrationOutcomeReducer.reduce(outcome))
    }

    @Test
    fun `PartialFailure reduces to RETRYABLE_FAILURE`() {
        val outcome = MigrationOutcome.PartialFailure(emptySet(), emptySet(), emptySet(), null, false, RuntimeException())
        assertEquals(MigrationListItemState.RETRYABLE_FAILURE, MigrationOutcomeReducer.reduce(outcome))
    }

    @Test
    fun `NotStarted reduces to NOT_STARTED`() {
        val outcome = MigrationOutcome.NotStarted(null)
        assertEquals(MigrationListItemState.NOT_STARTED, MigrationOutcomeReducer.reduce(outcome))
    }

    @Test
    fun `RETRYABLE_FAILURE and NOT_STARTED are both classified as failures, SUCCESS and SKIPPED are not`() {
        assertEquals(true, MigrationListItemState.RETRYABLE_FAILURE.isFailure)
        assertEquals(true, MigrationListItemState.NOT_STARTED.isFailure)
        assertEquals(false, MigrationListItemState.SUCCESS.isFailure)
        assertEquals(false, MigrationListItemState.SKIPPED.isFailure)
    }
}
// KMK <--
