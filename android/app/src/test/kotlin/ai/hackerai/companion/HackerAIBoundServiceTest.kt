package ai.hackerai.companion

import ai.hackerai.companion.service.AgentTaskRunner
import ai.hackerai.companion.llm.HttpLocalLlmProvider
import ai.hackerai.companion.remote.RemoteDispatcher
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
    private lateinit var remoteDispatcher: RemoteDispatcher
    private lateinit var agentTaskConfirmPolicy: AgentTaskConfirmPolicy
    private lateinit var llmProvider: HttpLocalLlmProvider
    private lateinit var service: HackerAIBoundService

    @Before
    fun setUp() {
        agentTaskRunner = mockk(relaxed = true)
        dependencyGuard = mockk()
        remoteDispatcher = mockk(relaxed = true)
        agentTaskConfirmPolicy = mockk(relaxed = true)
        llmProvider = mockk(relaxed = true)
        every { agentTaskConfirmPolicy.blockReason(any(), any()) } returns null
        every { llmProvider.hasApiKey() } returns true
        service = HackerAIBoundService()
        service.agentTaskRunner = agentTaskRunner
        service.dependencyGuard = dependencyGuard
        service.remoteDispatcher = remoteDispatcher
        service.agentTaskConfirmPolicy = agentTaskConfirmPolicy
        service.llmProvider = llmProvider
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

    @Test
    fun `executeEnvelope delegates to dispatcher when DCA installed`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        every { remoteDispatcher.dispatch(any()) } returns """{"requestId":"r","status":0,"code":"ok","message":"Allowed","payloadJson":"{}"}"""
        val envelope = """{"requestId":"r","command":"capabilities.get","argsJson":"{}","nonce":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","timestampMs":1,"ttlMs":120000,"destructive":false,"targetNodeRef":"hackerai"}"""
        val result = service.executeEnvelope(envelope)
        assertTrue(result.contains("\"ok\""))
        verify(exactly = 1) { remoteDispatcher.dispatch(envelope) }
    }

    @Test
    fun `executeEnvelope returns error when DCA absent`() {
        every { dependencyGuard.isDcaInstalled() } returns false
        val result = service.executeEnvelope("{}")
        assertTrue(result.contains("\"ok\":false"))
        verify(exactly = 0) { remoteDispatcher.dispatch(any()) }
    }

    @Test
    fun `runAgentTask returns confirmation_required when policy blocks`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        every { llmProvider.hasApiKey() } returns true
        every { agentTaskConfirmPolicy.blockReason(true, false) } returns AgentTaskConfirmPolicy.CODE_CONFIRMATION_REQUIRED

        val result = service.runAgentTask("""{"name":"t","task":"scan"}""")

        assertTrue(result.contains("\"ok\":false"))
        assertTrue(result.contains("confirmation_required"))
        verify(exactly = 0) { agentTaskRunner.runTask(any()) }
    }

    @Test
    fun `runAgentTask with confirmed true reaches the runner`() {
        every { dependencyGuard.isDcaInstalled() } returns true
        every { llmProvider.hasApiKey() } returns true
        every { agentTaskConfirmPolicy.blockReason(true, true) } returns null
        every { agentTaskRunner.runTask(any()) } returns """{"ok":true,"taskId":"x","status":"queued"}"""

        val result = service.runAgentTask("""{"name":"t","task":"scan"}""", confirmed = true)

        assertTrue(result.contains("\"ok\":true"))
        verify(exactly = 1) { agentTaskRunner.runTask(any()) }
    }
}
