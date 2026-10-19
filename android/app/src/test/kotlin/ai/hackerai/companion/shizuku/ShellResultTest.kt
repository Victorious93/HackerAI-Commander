package ai.hackerai.companion.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the cross-process ShellResult JSON contract (no Android deps),
 * so they run under the standard `test` task.
 */
class ShellResultTest {
    @Test
    fun roundTripsThroughJson() {
        val original = ShellResult(exitCode = 0, stdout = "hello\n", stderr = "", timedOut = false)
        val decoded = ShellResult.fromJson(original.toJson())
        assertEquals(original, decoded)
    }

    @Test
    fun successOnlyWhenZeroExitAndNotTimedOut() {
        assertTrue(ShellResult(0, "ok", "").isSuccess)
        assertFalse(ShellResult(1, "", "boom").isSuccess)
        assertFalse(ShellResult(0, "", "", timedOut = true).isSuccess)
    }

    @Test
    fun decodesTimedOutAndStderrPayloads() {
        val json = ShellResult(exitCode = -1, stdout = "partial", stderr = "killed", timedOut = true).toJson()
        val decoded = ShellResult.fromJson(json)
        assertEquals(-1, decoded.exitCode)
        assertEquals("partial", decoded.stdout)
        assertEquals("killed", decoded.stderr)
        assertTrue(decoded.timedOut)
    }

    @Test
    fun ignoresUnknownKeys() {
        val decoded = ShellResult.fromJson(
            """{"exitCode":0,"stdout":"x","stderr":"","timedOut":false,"extra":"ignored"}""",
        )
        assertEquals(0, decoded.exitCode)
        assertEquals("x", decoded.stdout)
    }
}
