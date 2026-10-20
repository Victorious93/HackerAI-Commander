package ai.hackerai.companion

import ai.hackerai.companion.service.AgentTaskRunner
import ai.hackerai.companion.service.HackerAIBoundService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// HackerAIBoundService extends android.app.Service and its binder extends Binder; plain-JVM
// android.jar stubs throw "Method ... not mocked", so construct it under Robolectric. A plain
// Application avoids starting the Hilt app; injection is replaced by setting the fields.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HackerAIBoundServiceTest {

    private lateinit var agentTaskRunner: AgentTaskRunner
    private lateinit var dependencyGuard: DependencyGuard
    private lateinit var service: HackerAIBoundService

    @Before
    fun setUp() {
        agentTaskRunner = mockk(relaxed = true)
        dependencyGuard = mockk()
        service = HackerAIBoundService()
        service.agentTaskRunner = agentTaskRunner
        service.dependencyGuard = dependencyGuard
    }

    @Test
    fun `runAgentTask delegates to runner when DCA installed`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        val expected = """{"ok":true,"taskId":"abc","status":"queued"}"""
        every { agentTaskRunner.runTask(any()) } returns expected

        val result = service.runAgentTask("""{"task":"scan example.com"}""")

        assertEquals(expected, result)
        verify(exactly = 1) { agentTaskRunner.runTask("""{"task":"scan example.com"}""") }
    }

    @Test
    fun `runAgentTask returns error JSON when DCA not installed`() {
        every { dependencyGuard.isDcaInstalled() } returns false

        val result = service.runAgentTask("""{"task":"scan example.com"}""")

        assertTrue(result.contains("\"ok\":false"))
        assertTrue(result.contains("DroidCommand AI"))
        verify(exactly = 0) { agentTaskRunner.runTask(any()) }
    }

    @Test
    fun `getSkillCatalog delegates to runner when DCA installed`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        val catalog = """{"ok":true,"skills":[]}"""
        every { agentTaskRunner.getSkillCatalog() } returns catalog

        assertEquals(catalog, service.getSkillCatalog())
    }

    @Test
    fun `getSkillCatalog returns error when DCA absent`() {
        every { dependencyGuard.isDcaInstalled() } returns false

        val result = service.getSkillCatalog()

        assertTrue(result.contains("\"ok\":false"))
    }

    @Test
    fun `validateFinding delegates to runner when DCA installed`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        val verdict = """{"ok":true,"verdict":"CONFIRMED"}"""
        every { agentTaskRunner.validateFinding(any()) } returns verdict

        val input = """{"title":"XSS","reproduction_hint":"1. Open page 2. Alert fires"}"""
        assertEquals(verdict, service.validateFinding(input))
    }

    @Test
    fun `healthCheck returns runner result when DCA installed`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        every { agentTaskRunner.healthCheck() } returns "ok"

        assertEquals("ok", service.healthCheck())
    }

    @Test
    fun `healthCheck returns dca_not_installed when DCA absent`() {
        every { dependencyGuard.isDcaInstalled() } returns false

        assertEquals("dca_not_installed", service.healthCheck())
    }

    @Test
    fun `getTaskResult delegates to runner when DCA installed`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        val taskResult = """{"status":"done","result":"scan complete"}"""
        every { agentTaskRunner.getTaskResult("task-123") } returns taskResult

        assertEquals(taskResult, service.getTaskResult("task-123"))
    }

    @Test
    fun `getTaskResult returns error when DCA absent`() {
        every { dependencyGuard.isDcaInstalled() } returns false

        val result = service.getTaskResult("task-123")

        assertTrue(result.contains("\"ok\":false"))
        assertTrue(result.contains("DroidCommand AI"))
    }
}
