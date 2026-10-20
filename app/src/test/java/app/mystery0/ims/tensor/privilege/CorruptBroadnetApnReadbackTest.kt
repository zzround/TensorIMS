package app.mystery0.ims.tensor.privilege

import app.mystery0.ims.tensor.model.BroadnetApnResult
import app.mystery0.ims.tensor.model.BroadnetApnState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorruptBroadnetApnReadbackTest {
    private val identity = "a".repeat(64)

    private fun observation(
        state: BroadnetApnState = BroadnetApnState.COMPLETE,
        readComplete: Boolean = true,
        cleanupConfirmed: Boolean = true,
        subId: Int = 14,
        resultIdentity: String = identity,
    ) = CorruptBroadnetApnObservation(
        BroadnetApnResult(subId, resultIdentity, state,
            reused = if (state == BroadnetApnState.COMPLETE) 2 else 0,
            error = if (state == BroadnetApnState.COMPLETE) null else "APN_STATE_OBSERVED",
            readComplete = readComplete),
        cleanupConfirmed,
    )

    @Test fun nonBroadnetAndUnknownHomeDoNotRequestApnPermissions() = runBlocking {
        for (home in listOf("46000", "46013", "45403", "", null)) {
            var reads = 0
            assertTrue(verifyCorruptBroadnetApnReadback(14, identity, home) { _, _ ->
                reads++
                observation()
            })
            assertEquals(0, reads)
        }
    }

    @Test fun invalidCurrentTargetDoesNotReachTheReader() = runBlocking {
        for ((subId, digest) in listOf(-1 to identity, 14 to "", 14 to "changed")) {
            var reads = 0
            assertFalse(verifyCorruptBroadnetApnReadback(subId, digest, "46015") { _, _ ->
                reads++
                observation()
            })
            assertEquals(0, reads)
        }
    }

    @Test fun verifiedBroadnetReadUsesOnlyTheCurrentTargetAndIdentity() = runBlocking {
        val requested = mutableListOf<Pair<Int, String>>()
        assertTrue(verifyCorruptBroadnetApnReadback(14, identity, "46015") { subId, digest ->
            requested += subId to digest
            observation()
        })
        assertEquals(listOf(14 to identity), requested)
    }

    @Test fun missingOrConflictingApnsStillPermitAnExplicitCurrentStateReadback() = runBlocking {
        for (state in listOf(BroadnetApnState.FAILED, BroadnetApnState.CONFLICT)) {
            assertTrue(verifyCorruptBroadnetApnReadback(14, identity, "46015") { _, _ ->
                observation(state = state)
            })
        }
    }

    @Test fun missingOrUncleanRepliesKeepRecoveryBlocked() = runBlocking {
        assertFalse(verifyCorruptBroadnetApnReadback(14, identity, "46015") { _, _ -> null })
        assertFalse(verifyCorruptBroadnetApnReadback(14, identity, "46015") { _, _ ->
            observation(cleanupConfirmed = false)
        })
    }

    @Test fun incompleteOrUnknownObservationsCannotClearRecovery() = runBlocking {
        assertFalse(verifyCorruptBroadnetApnReadback(14, identity, "46015") { _, _ ->
            observation(state = BroadnetApnState.FAILED, readComplete = false)
        })
        assertFalse(verifyCorruptBroadnetApnReadback(14, identity, "46015") { _, _ ->
            observation(state = BroadnetApnState.UNKNOWN, readComplete = true)
        })
    }

    @Test fun changedSimInTheReplyCannotConfirmTheOriginalReadbackTarget() = runBlocking {
        for (reply in listOf(observation(subId = 12), observation(resultIdentity = "b".repeat(64)))) {
            assertFalse(verifyCorruptBroadnetApnReadback(14, identity, "46015") { _, _ -> reply })
        }
    }
}
