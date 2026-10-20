# HackerAI Companion APK — Architecture

## Component diagram

```
DroidCommand AI (:app)
  └── bindService(IHackerAIService, BIND_HACKERAI)  [signature permission]
        └── HackerAIBoundService                     [Service + IHackerAIService.Stub]
              ├── DependencyGuard                     [PackageManager check at every call]
              └── AgentTaskRunner                     [Hilt @Singleton]
                    ├── SkillCatalog                  [core-hackerai: loads strix catalog]
                    ├── SkillRanker                   [rankSkillsForTask() if > MAX_SUBAGENT_SKILLS]
                    ├── DoomLoopDetector              [core-hackerai: warn@3, halt@5]
                    ├── RuntimeRecovery               [core-hackerai: provider error retry]
                    ├── taskResults map               [Phase 5: Queued→Running→Done/Cancelled/Error]
                    └── LocalLlmProvider              [HttpLocalLlmProvider via Hilt @Binds]
```

## Runtime security model

1. **Bind-time**: Android OS checks that the binding app holds
   `ai.droidcommand.permission.BIND_HACKERAI`. This is a `protectionLevel="signature"`
   permission declared in DCA's `AndroidManifest.xml`. Only apps signed with the same
   signing certificate as DCA receive this permission — all others are rejected at the
   OS level before any Java/Kotlin code runs.

2. **Call-time**: `HackerAIBoundService` delegates every AIDL call to `AgentTaskRunner`
   after a `DependencyGuard.isDcaInstalled()` check. If DCA was uninstalled after binding,
   the check returns `false` and the call returns an error JSON without executing.

3. **No inbound network**: The companion has no HTTP server. All communication is via
   AIDL on the local device — there is no loopback port to attack.

## Data flow for a runAgentTask call

```
DCA: agentTool.invoke(inputJson)
  → IHackerAIService.Stub.runAgentTask(inputJson)          [Binder thread]
  → DependencyGuard.isDcaInstalled() → true
  → AgentTaskRunner.runTask(inputJson)
    → parse CreateAgentInput (core-hackerai)
    → SkillCatalog.resolveSkills / SkillRanker.rankSkillsForTask()
    → taskResults[taskId] = Queued
    → executor.submit { runTaskInternal(...) }              [background thread]
    → return {"ok":true,"taskId":"...","status":"queued"}   [immediately]

DCA: poll for result
  → IHackerAIService.Stub.getTaskResult(taskId)
  → AgentTaskRunner.getTaskResult(taskId)
    → taskResults[taskId] → Queued | Running | Done | Cancelled | Error
    → return {"status":"done","result":"..."}

runTaskInternal (background):
  → taskResults[taskId] = Running
  → loop up to SUBAGENT_MAX_STEPS:
      LlmProvider.chat() → DoomLoopDetector.check() → step++
      break on: doom-loop HALT, step budget, cancellation, fatal provider error
  → taskResults[taskId] = Done(lastResponse) | Cancelled | Error(msg)
```

## Component status (updated 2026-09-30)

| Component                 | Status      | Notes                                                                               |
| ------------------------- | ----------- | ----------------------------------------------------------------------------------- |
| IHackerAIService.aidl     | IMPLEMENTED | JSON transport; added `getTaskResult` for Phase 5 async polling                     |
| HackerAIBoundService      | IMPLEMENTED | DependencyGuard + Hilt injection; all AIDL methods delegated                        |
| DependencyGuard           | IMPLEMENTED | `@Singleton @Inject constructor(@ApplicationContext)`; Hilt-managed; mockk-tested  |
| AppModule (Hilt)          | IMPLEMENTED | Binds `LocalLlmProvider → HttpLocalLlmProvider`; fixes missing DI binding           |
| AgentTaskRunner           | IMPLEMENTED | Phase 5: `taskResults` map stores Queued/Running/Done/Cancelled/Error per task     |
| LocalLlmProvider          | IMPLEMENTED | EncryptedSharedPreferences + HttpURLConnection; injected via Hilt                   |
| StatusScreen              | IMPLEMENTED | Health strip: DCA, LLM key present, Shizuku, external control, pending count, protocol |
| SkillBrowserScreen        | IMPLEMENTED | Filter search over SkillCatalog; LazyColumn with category badges                    |
| SettingsScreen            | IMPLEMENTED | API key, model, endpoint; agent-confirm toggle; external-control toggle (default OFF) |
| ApprovalsScreen           | IMPLEMENTED | On-device Approve/Deny for AIDL agent tasks + remote destructive envelope ops       |
| HackerAINavHost           | IMPLEMENTED | Bottom nav: Status / Skills / Settings / Shizuku / Approve / Help                   |
| MainActivity              | IMPLEMENTED | Fixed infinite `recreate()` loop; injects DependencyGuard + HttpLocalLlmProvider   |
| proguard-rules.pro        | IMPLEMENTED | Keeps AIDL stubs, Hilt entry points, serialization, and Tink classes                |
| core-hackerai integration | IMPLEMENTED | Requires `./gradlew :core-hackerai:publishToMavenLocal` before AGP build            |

## Remote-control envelope v1 (`remote/`)

Protocol `BRIDGE_PROTOCOL_MAJOR = 1`, `BRIDGE_PROTOCOL_MINOR = 1` (spec: droidcommand-AI
`docs/specs/REMOTE_CONTROL.md`, Appendix B). BoundCompanion JSON field map over AIDL
`IHackerAIService.executeEnvelope`. `RemoteDispatcher` routes after `RemoteCommandGate`:
identity (Binder uid + `BIND_HACKERAI`) → external control (**OFF by default**, Settings
toggle; config import forces OFF) → replay/TTL → policy.

- Reads (`capabilities.get`, `node.status.get`, `skills.catalog`, `task.result`, `health.check`):
  allowed when external control is on.
- `config.export` / `config.import`: `remoteAllowed=false` (controller source only); import
  needs confirm / dry-run.
- Destructive `agent.run` / `finding.validate` / `task.cancel`: queue for on-device Approvals;
  execute only after Approve (`confirmed=true`). Replay nonce is not re-checked on approve.
- `shizuku.shell`: local-only — never queued, never executed over the envelope.
- Advisory `destructive` flag never authorizes. Snapshots/backups never include API keys or chat.
