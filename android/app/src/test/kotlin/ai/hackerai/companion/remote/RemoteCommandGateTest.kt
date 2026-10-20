package ai.hackerai.companion.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCommandGateTest {
    private val now = 1_760_920_000_000L
    private val trusted = CallerIdentity(uid = 10123, holdsBindPermission = true)

    private fun cmd(command: String = "capabilities.get", nonce: String = "a".repeat(32), ts: Long = now, ttl: Long = 120_000L, destructive: Boolean = false) =
        BoundCompanionCommand(requestId = "r1", command = command, nonce = nonce, timestampMs = ts, ttlMs = ttl, destructive = destructive, targetNodeRef = "hackerai")

    private fun gate(enabled: Boolean = true) = RemoteCommandGate(clock = { now }, externalControlEnabled = enabled)

    @Test fun `protocol is 1 dot 1`() {
        assertEquals(1, BRIDGE_PROTOCOL_MAJOR)
        assertEquals(1, BRIDGE_PROTOCOL_MINOR)
    }

    @Test fun `external control is off by default`() {
        assertEquals(RemoteErrorCode.EXTERNAL_CONTROL_DISABLED, RemoteCommandGate(clock = { now }).evaluate(trusted, "dca", cmd()).code)
    }

    @Test fun `caller without signature permission is unauthorized`() {
        assertEquals(RemoteErrorCode.UNAUTHORIZED, gate().evaluate(CallerIdentity(1, false), "dca", cmd()).code)
    }

    @Test fun `read op is allowed`() {
        assertEquals(BoundCompanionResult.STATUS_OK, gate().evaluate(trusted, "dca", cmd()).status)
    }

    @Test fun `pentest op is gated even when flagged non destructive`() {
        val r = gate().evaluate(trusted, "dca", cmd(command = "agent.run", destructive = false))
        assertEquals(RemoteErrorCode.CONFIRMATION_REQUIRED, r.code)
    }

    @Test fun `replay is rejected per source`() {
        val g = gate()
        g.evaluate(trusted, "dca", cmd())
        assertEquals(RemoteErrorCode.REPLAY_DETECTED, g.evaluate(trusted, "dca", cmd()).code)
        assertEquals("ok", g.evaluate(trusted, "other", cmd()).code)
    }

    @Test fun `ttl is past only and future skew is 120s`() {
        assertEquals("ok", gate().evaluate(trusted, "dca", cmd(ts = now - 200_000, ttl = 300_000)).code)
        assertEquals(RemoteErrorCode.EXPIRED, gate().evaluate(trusted, "dca", cmd(ts = now - 130_000)).code)
        assertEquals(RemoteErrorCode.EXPIRED, gate().evaluate(trusted, "dca", cmd(ts = now + 130_000)).code)
    }

    @Test fun `ttl above max and bad args are invalid`() {
        assertEquals(RemoteErrorCode.INVALID_PARAMETERS, gate().evaluate(trusted, "dca", cmd(ttl = 300_001)).code)
        val bad = cmd().copy(argsJson = "[]")
        assertEquals(RemoteErrorCode.INVALID_PARAMETERS, gate().evaluate(trusted, "dca", bad).code)
    }

    @Test fun `unknown op is unsupported`() {
        assertEquals(RemoteErrorCode.UNSUPPORTED_OP, gate().evaluate(trusted, "dca", cmd(command = "wipe")).code)
    }

    @Test fun `confirmed destructive agent run is allowed; shizuku never is`() {
        val g = gate()
        assertEquals(
            BoundCompanionResult.STATUS_OK,
            g.evaluate(trusted, "dca", cmd(command = "agent.run", nonce = "c".repeat(32)), confirmed = true).status,
        )
        assertEquals(
            RemoteErrorCode.CONFIRMATION_REQUIRED,
            g.evaluate(trusted, "dca", cmd(command = "shizuku.shell", nonce = "d".repeat(32)), confirmed = true).code,
        )
    }
}
