package ai.hackerai.companion.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NodeStatusTest {
    private val now = 1_760_920_000_000L
    private val trusted = CallerIdentity(uid = 10123, holdsBindPermission = true)
    private fun cmd(nonce: String = "b".repeat(32)) = BoundCompanionCommand(
        requestId = "r1", command = NodeStatus.OP, nonce = nonce, timestampMs = now, targetNodeRef = "hackerai",
    )
    private fun gate(enabled: Boolean = true) = RemoteCommandGate(clock = { now }, externalControlEnabled = enabled)
    private fun handle(g: RemoteCommandGate = gate(), caller: CallerIdentity = trusted, dca: Boolean = true, llm: Boolean = true) =
        NodeStatus.handle(g, caller, "dca", cmd(), now, dca, llm)

    @Test fun `node status get is allowed as read without confirmation`() {
        assertEquals(BoundCompanionResult.STATUS_OK, gate().evaluate(trusted, "dca", cmd()).status)
    }

    @Test fun `still requires identity and external control`() {
        assertEquals(RemoteErrorCode.UNAUTHORIZED, handle(caller = CallerIdentity(1, false)).code)
        assertEquals(RemoteErrorCode.EXTERNAL_CONTROL_DISABLED, handle(g = gate(enabled = false)).code)
    }

    @Test fun `returns snapshot with node_health`() {
        val r = handle()
        assertEquals("ok", r.code)
        val o = Json.parseToJsonElement(r.payloadJson).jsonObject
        assertEquals(setOf("nodeId", "packageName", "connectionClass", "updatedAtMs", "domains"), o.keys)
        assertEquals("BOUND_COMPANION", o["connectionClass"]!!.jsonPrimitive.content)
        val d = o["domains"]!!.jsonArray.single().jsonObject
        assertEquals("node_health", d["name"]!!.jsonPrimitive.content)
        assertEquals("ok", d["state"]!!.jsonPrimitive.content)
    }

    @Test fun `health degrades without llm or dca`() {
        assertEquals("degraded", NodeStatus.build("n", now, true, false).domains[0].state)
        assertEquals("down", NodeStatus.build("n", now, false, true).domains[0].state)
    }

    @Test fun `never leaks keys or chat content`() {
        val payload = handle().payloadJson.lowercase()
        listOf("apikey", "api_key", "token", "secret", "password", "message", "prompt", "result", "chat")
            .forEach { assertFalse("leaked $it", it in payload) }
    }

    @Test fun `encoder refuses secret-looking detail`() {
        val s = NodeStatus.build("n", now, true, true).copy(domains = listOf(StatusTrayDomainStatus("node_health", "ok", "apiKey=sk-123")))
        assertNull(NodeStatus.encode(s))
    }
}
