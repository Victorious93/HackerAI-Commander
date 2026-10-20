package ai.hackerai.companion.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesDescriptorsTest {
    @Test fun `advertises all live ops with correct flags`() {
        val byName = RemoteCommandGate.DESCRIPTORS.associateBy { it.name }
        assertEquals(setOf(
            "capabilities.get", NodeStatus.OP, "skills.catalog", "task.result", "health.check",
            ConfigBackup.OP_EXPORT, ConfigBackup.OP_IMPORT,
            "agent.run", "finding.validate", "task.cancel", "shizuku.shell",
        ), byName.keys)
        assertTrue(byName["capabilities.get"]!!.remoteAllowed)
        assertFalse(byName[ConfigBackup.OP_EXPORT]!!.remoteAllowed)
        assertFalse(byName[ConfigBackup.OP_IMPORT]!!.remoteAllowed)
        assertTrue(byName[ConfigBackup.OP_IMPORT]!!.requiresConfirm)
        assertEquals("destructive", byName["agent.run"]!!.kind)
        assertTrue(byName["agent.run"]!!.requiresConfirm)
        assertFalse(byName["shizuku.shell"]!!.remoteAllowed)
        assertTrue(byName["shizuku.shell"]!!.requiresConfirm)
    }

    @Test fun `every READ_OPS name is advertised as read`() {
        RemoteCommandGate.READ_OPS.forEach { name ->
            val d = RemoteCommandGate.DESCRIPTORS.first { it.name == name }
            assertEquals("read", d.kind)
            assertFalse(d.requiresConfirm)
        }
    }
}
