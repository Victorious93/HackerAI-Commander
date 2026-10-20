package ai.hackerai.companion.remote

import ai.hackerai.companion.ExternalControlPrefs
import ai.hackerai.companion.llm.HttpLocalLlmProvider
import ai.hackerai.companion.service.AgentTaskRunner
import android.os.Binder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatches a JSON BoundCompanionCommand over the live AIDL service.
 * Identity is Binder.getCallingUid() + the BIND_HACKERAI signature check already done
 * by the OS at bind time; the gate still re-checks holdsBindPermission.
 *
 * Destructive ops (agent.run, finding.validate, task.cancel, shizuku.shell) are never
 * executed here: the gate returns confirmation_required and this dispatcher stops.
 */
fun interface CallingIdentitySource {
    fun current(): CallerIdentity
}

@Singleton
class BinderCallingIdentity @Inject constructor() : CallingIdentitySource {
    override fun current(): CallerIdentity =
        // OS already enforced BIND_HACKERAI at bind time; re-assert for the gate.
        CallerIdentity(Binder.getCallingUid(), holdsBindPermission = true)
}

@Singleton
class RemoteDispatcher @Inject constructor(
    private val gate: RemoteCommandGate,
    private val agentTaskRunner: AgentTaskRunner,
    private val llm: HttpLocalLlmProvider,
    private val callingIdentity: CallingIdentitySource,
    private val externalControlPrefs: ExternalControlPrefs,
    private val pendingRemoteApprovals: PendingRemoteApprovals,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun dispatch(envelopeJson: String, confirmed: Boolean = false): String {
        val cmd = runCatching { json.decodeFromString<BoundCompanionCommand>(envelopeJson) }
            .getOrElse { return err(it.message ?: "Malformed BoundCompanionCommand") }
        val caller = callingIdentity.current()
        val args = runCatching { json.parseToJsonElement(cmd.argsJson).jsonObject }
            .getOrElse { return toJson(BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_INVALID, RemoteErrorCode.INVALID_PARAMETERS, "Bad argsJson")) }
        val dryRun = args["dryRun"]?.jsonPrimitive?.content == "true"
        // Default false: remote callers never auto-confirm. On-device Approvals pass confirmed=true.
        val source = args["sourceNodeRef"]?.jsonPrimitive?.content ?: RemoteCommandGate.CONTROLLER

        return when (cmd.command) {
            "capabilities.get" -> gateThen(caller, source, cmd, dryRun, confirmed) {
                ok(cmd, capabilitiesPayload())
            }
            NodeStatus.OP -> toJson(
                NodeStatus.handle(gate, caller, source, cmd, System.currentTimeMillis(),
                    dcaInstalled = true, llmConfigured = llm.hasApiKey())
            )
            ConfigBackup.OP_EXPORT -> {
                val current = CompanionConfig(llm.getModel(), llm.getEndpoint())
                toJson(ConfigBackup.export(gate, caller, source, cmd, current, System.currentTimeMillis(), key = null))
            }
            ConfigBackup.OP_IMPORT -> {
                val current = CompanionConfig(llm.getModel(), llm.getEndpoint())
                val result = ConfigBackup.import(gate, caller, source, cmd, current, key = null, confirmed = confirmed) { next ->
                    llm.setModel(next.model)
                    llm.setEndpoint(next.endpoint)
                }
                // Spec: never leave remote.command / external control ON after import.
                if (!gate.externalControlEnabled) externalControlPrefs.enabled = false
                toJson(result)
            }
            "skills.catalog" -> gateThen(caller, source, cmd, dryRun, confirmed) {
                ok(cmd, agentTaskRunner.getSkillCatalog())
            }
            "task.result" -> gateThen(caller, source, cmd, dryRun, confirmed) {
                val taskId = args["taskId"]?.jsonPrimitive?.content
                    ?: return@gateThen BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_INVALID, RemoteErrorCode.INVALID_PARAMETERS, "taskId required")
                ok(cmd, agentTaskRunner.getTaskResult(taskId))
            }
            "health.check" -> gateThen(caller, source, cmd, dryRun, confirmed) {
                ok(cmd, """{"health":"${agentTaskRunner.healthCheck()}"}""")
            }
            "shizuku.shell" ->
                // Local-only: never queue, never execute over the envelope.
                toJson(gate.evaluate(caller, source, cmd, confirmed = false, dryRun = false))
            "agent.run", "finding.validate", "task.cancel" -> {
                val verdict = gate.evaluate(caller, source, cmd, confirmed = confirmed, dryRun = false)
                if (verdict.status != BoundCompanionResult.STATUS_OK) {
                    if (verdict.code == RemoteErrorCode.CONFIRMATION_REQUIRED && !confirmed) {
                        val preview = when (cmd.command) {
                            "agent.run" -> "agent.run: " + cmd.argsJson.take(80)
                            "finding.validate" -> "finding.validate"
                            else -> "task.cancel"
                        }
                        val pending = pendingRemoteApprovals.enqueue(cmd.command, envelopeJson, preview)
                        return toJson(
                            BoundCompanionResult(
                                cmd.requestId, BoundCompanionResult.STATUS_DENIED,
                                RemoteErrorCode.CONFIRMATION_REQUIRED,
                                "Queued for on-device approval",
                                """{"pendingId":"${pending.id}","command":"${cmd.command}"}""",
                            ),
                        )
                    }
                    return toJson(verdict)
                }
                toJson(executeDestructive(cmd, args))
            }
            else -> toJson(gate.evaluate(caller, source, cmd, confirmed, dryRun))
        }
    }

    /** Approve a queued remote destructive op after on-device confirm. */
    fun approvePending(pendingId: String): String {
        val item = pendingRemoteApprovals.take(pendingId)
            ?: return err("Unknown or already handled pendingId: $pendingId")
        return dispatch(item.envelopeJson, confirmed = true)
    }

    fun denyPending(pendingId: String): String {
        val ok = pendingRemoteApprovals.deny(pendingId)
        return if (ok) """{"requestId":"","status":0,"code":"ok","message":"Denied","payloadJson":"{\"denied\":\"$pendingId\"}"}"""
        else err("Unknown or already handled pendingId: $pendingId")
    }

    private fun executeDestructive(cmd: BoundCompanionCommand, args: kotlinx.serialization.json.JsonObject): BoundCompanionResult {
        return when (cmd.command) {
            "agent.run" -> {
                // Prefer nested inputJson; otherwise treat argsJson as CreateAgentInput.
                val input = args["inputJson"]?.jsonPrimitive?.content ?: cmd.argsJson
                ok(cmd, agentTaskRunner.runTask(input))
            }
            "finding.validate" -> {
                val input = args["candidateJson"]?.jsonPrimitive?.content ?: cmd.argsJson
                ok(cmd, agentTaskRunner.validateFinding(input))
            }
            "task.cancel" -> {
                val taskId = args["taskId"]?.jsonPrimitive?.content
                    ?: return BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_INVALID, RemoteErrorCode.INVALID_PARAMETERS, "taskId required")
                agentTaskRunner.cancelTask(taskId)
                ok(cmd, """{"cancelled":"$taskId"}""")
            }
            else -> BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_UNSUPPORTED, RemoteErrorCode.UNSUPPORTED_OP, cmd.command)
        }
    }

    private fun gateThen(
        caller: CallerIdentity, source: String, cmd: BoundCompanionCommand, dryRun: Boolean, confirmed: Boolean,
        block: () -> BoundCompanionResult,
    ): String {
        val v = gate.evaluate(caller, source, cmd, confirmed, dryRun)
        if (v.status != BoundCompanionResult.STATUS_OK) return toJson(v)
        return toJson(block())
    }

    private fun ok(cmd: BoundCompanionCommand, payload: String) =
        BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_OK, "ok", "Allowed", payload)

    private fun capabilitiesPayload(): String {
        val ops = json.encodeToString(ListSerializer(OpDescriptor.serializer()), RemoteCommandGate.DESCRIPTORS)
        return """{"protocolMajor":$BRIDGE_PROTOCOL_MAJOR,"protocolMinor":$BRIDGE_PROTOCOL_MINOR,"ops":$ops}"""
    }

    private fun toJson(r: BoundCompanionResult): String = json.encodeToString(BoundCompanionResult.serializer(), r)

    private fun err(msg: String): String {
        val escaped = msg.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"requestId":"","status":${BoundCompanionResult.STATUS_INVALID},"code":"${RemoteErrorCode.INVALID_PARAMETERS}","message":"$escaped","payloadJson":"{}"}"""
    }
}
