# HackerAI Companion — User Manual

This manual covers the **Android companion app** in [`android/`](../android/)
(`applicationId` `ai.hackerai.companion`, `minSdk` 26, `targetSdk` 35). For the
web app (upstream `hackerai-tech/hackerai`) see the [README](../README.md).

**How to read this manual.** Statements without a tag are checked against the
code in this repository. **[Likely]** marks claims about the DroidCommand
controller or fleet taxonomy that live outside this repo and could not be
verified here. **[Roadmap]** marks planned, not-yet-implemented features.

> **Defensive use only.** Use HackerAI only against systems you own or are
> explicitly authorized to test.

---

## 1. What the app is and its fleet role

HackerAI Companion exposes HackerAI-style security agent capabilities (agent
tasks, a skill catalog, finding validation) to the **DroidCommand AI**
controller app (`ai.droidcommand.app`) through an AIDL **bound service**
(`HackerAIBoundService`, interface `ai.droidcommand.companion.IHackerAIService`).

- Fleet connection class: **`BOUND_COMPANION`** — it is bound to the controller
  process rather than running as a standalone app. **[Likely]** this name is
  fleet taxonomy; it does not appear as a literal token in this repo's code.
- The app also has its own small UI: Status, Skills, Settings, Shizuku
  (privileged console) and Help tabs.
- The app depends on DroidCommand AI being installed. If it is not, the app
  shows only a "Waiting for DroidCommand AI..." screen, and every bound-service
  call returns an error (`DroidCommand AI is not installed`, or
  `dca_not_installed` from `healthCheck`).

[placeholder: screenshot — Status tab showing "Connected to DroidCommand AI"]

## 2. Install and setup

1. **Install DroidCommand AI** (`ai.droidcommand.app`) first. The companion
   checks for it at launch and whenever the app resumes.
2. **Install the companion APK.** Get it from this repo's GitHub Releases or the
   *Android CI* workflow artifact (`app-debug-apk`), or build it yourself:
   ```bash
   cd android
   ./gradlew :app:assembleDebug
   # output: android/app/build/outputs/apk/debug/*.apk
   ```
   Install with `adb install <apk>` or by opening the APK on the device.
3. **Signing must match the controller.** The bound service is protected by the
   `ai.droidcommand.permission.BIND_HACKERAI` permission. **[Likely]** the
   controller declares this as `protectionLevel="signature"`, so the companion
   and controller must be signed with the same key (debug builds with the
   committed fleet debug keystore; release builds with the shared fleet release
   key). Do not mix a debug companion with a release controller.
4. **Open the app** and confirm the Status tab reads
   *Connected to DroidCommand AI*.
5. **Add an LLM API key** in the Settings tab (see §3.3). Without it the
   service's `healthCheck` reports `no_llm_provider` and agent tasks fail.
6. *(Optional)* **Set up Shizuku** for privileged commands (see §3.4).

[placeholder: screenshot — "Waiting for DroidCommand AI..." screen]

## 3. Features and how to use them

The bottom navigation bar has five tabs: **Status, Skills, Settings, Shizuku,
Help**.

### 3.1 Status

Shows the app name and whether DroidCommand AI is detected. Nothing to
configure. If you install or uninstall DroidCommand AI while the app is in the
background, returning to the app re-checks and refreshes the screen.

### 3.2 Skills (Skill Browser)

Browse the built-in security skill catalog the agent can use.

1. Tap **Skills**.
2. Type in **Search skills…** to filter by name, category or description.
3. The counter shows *N of M skills*. Each card shows the skill name, category
   and a two-line description.

Tapping a card currently does nothing (there is no skill detail screen yet).

[placeholder: screenshot — Skill Browser with a search filter applied]

### 3.3 Settings (LLM provider)

Configures the model the agent tasks call.

- **API Key** — enter a key and tap **Save Key**. Afterwards only the last 4
  characters are shown (*Key configured (ends in …abcd)*). Tap **Clear Key** to
  remove it. To change a key, clear it and enter a new one.
- **Model** — model ID (default `claude-haiku-4-5-20251001`). Edit and tap
  **Save Model**.
- **Endpoint** — default `https://api.anthropic.com/v1/messages`. Edit and tap
  **Save Endpoint**. The UI suggests a local server (Ollama, LM Studio); note
  the request is sent in **Anthropic Messages** format with an `x-api-key`
  header, so the endpoint must accept that format.

[placeholder: screenshot — Settings tab with key configured]

### 3.4 Shizuku (privileged console)

Lets you run a shell command with Shizuku's elevated identity (ADB shell, or
root if Shizuku runs as root).

The status card shows one of four states:

| State | Meaning | What to do |
|---|---|---|
| Shizuku not installed | Neither Shizuku nor Sui found | Install Shizuku (or enable Sui) |
| Shizuku not running | Installed, service stopped | Start it in the Shizuku app (ADB or root), tap **Refresh** |
| Permission not granted | Running, app not authorized | Tap **Grant access**, approve in the Shizuku prompt |
| Privileged access ready | Granted | You can run commands |

To run a command:

1. Reach *Privileged access ready*.
2. Type the command in **Shell command**.
3. Tap **Run (with confirmation)**.
4. A **Run privileged command?** dialog shows the exact command. Tap **Run** to
   execute or **Cancel** to abort. Nothing runs without this confirmation.
5. Output appears below as `exit=<code>`, stdout and a `[stderr]` section. The
   default timeout is 30 s; a timed-out command is marked `(timed out)`.

[placeholder: screenshot — confirmation dialog showing the command]

### 3.5 Help

Shows a condensed in-app version of this manual.

### 3.6 Bound service (used by the controller, no UI)

These calls are made by DroidCommand AI, not by you:

| Method | What it does |
|---|---|
| `runAgentTask(inputJson)` | Queues an agent task; returns a `taskId` |
| `getTaskResult(taskId)` | `queued` / `running` / `done` / `cancelled` / `error` |
| `cancelTask(taskId)` | Cancels a running task |
| `getSkillCatalog()` | Returns the skill catalog JSON |
| `validateFinding(candidateJson)` | Returns `CONFIRMED` or `NEEDS_MORE_EVIDENCE` (currently a simple check on the reproduction hint length) |
| `healthCheck()` | `ok`, `no_llm_provider`, or `dca_not_installed` |

Agent tasks stop on step budget, repeated identical responses (doom-loop
detection), cancellation, or a non-retryable provider error. Up to 200 task
results are kept in memory.

### 3.7 Roadmap (not implemented)

- **[Roadmap] Notebook mode** — source-grounded Q&A over your own documents.
  **Not implemented** in this codebase.

## 4. Permissions and why they are needed

| Permission | Why |
|---|---|
| `android.permission.INTERNET` | Call the configured LLM endpoint. |
| `ai.droidcommand.permission.BIND_HACKERAI` | Fleet permission; also guards `HackerAIBoundService` so only an app holding it (the controller) can bind. **[Likely]** signature-level, declared by the controller. |
| `moe.shizuku.manager.permission.API_V23` | Ask Shizuku for privileged access. Requested only when you tap **Grant access**; on old (pre-v11) Shizuku it falls back to the normal Android permission dialog. |

The manifest also registers `rikka.shizuku.ShizukuProvider` (so Shizuku can
deliver its binder to the app), guarded by `INTERACT_ACROSS_USERS_FULL`.

**Privileged-command confirm flow (PR #17).** Every privileged command must be
typed by you and confirmed in a dialog that shows the exact command; the code
never auto-runs commands. If Shizuku is missing, stopped or denied, the app
keeps working unprivileged and the console is disabled. The privileged shell is
**not** exposed through the AIDL interface — the controller cannot run Shizuku
commands through this app.

## 5. Fleet integration

- The controller binds via action `ai.hackerai.companion.IHackerAIService`.
- **Remote control is OFF by default / gated.** Verifiable here:
  - only callers holding `BIND_HACKERAI` can bind;
  - every call is refused when DroidCommand AI is not installed;
  - no AIDL method runs shell or privileged commands; the Shizuku console is
    local-only and requires on-screen confirmation for each command.
  - Note: the controller *can* run LLM agent tasks via `runAgentTask` without an
    on-device prompt once bound, using your configured API key.
- **[Likely]** Controller-side safety gates (remote control disabled by default,
  per-action approval, which companions are enabled) are implemented in the
  `Victorious93/droidcommand-AI` controller and were not verified from this repo.
- **[Likely]** Sibling companions in the fleet: `Pentest-Swarm-AI`, `VictorSuite`.

## 6. Troubleshooting / FAQ

**Stuck on "Waiting for DroidCommand AI..."** — Install `ai.droidcommand.app`,
then return to the app (it re-checks on resume).

**Controller can't bind / SecurityException** — Signing mismatch. Install
companion and controller from the same build channel (both debug or both
release). **[Likely]**

**`healthCheck` returns `no_llm_provider`** — Add an API key in Settings.

**Task ends in `error` with "LLM API error 401/403"** — Key invalid or wrong for
the endpoint. **4xx/404** — check the endpoint URL and model ID. Remember the
request format is Anthropic Messages.

**Shizuku shows "not running" after reboot** — Shizuku (non-root) must be
restarted after each reboot via ADB or wireless debugging; then tap **Refresh**.

**"Grant access" does nothing** — Make sure Shizuku is running, then try again;
check Shizuku's own app list for HackerAI and allow it there.

**Command output shows `(timed out)`** — The command exceeded 30 s; it was
killed.

**Is Notebook mode available?** — No, roadmap only.

## 7. Privacy and data handling

- **API key** is stored with `EncryptedSharedPreferences` (Android Keystore,
  AES-256-GCM values), file `hackerai_secure_prefs`; it is never shown in full.
  Model and endpoint are stored in the same encrypted file.
- `android:allowBackup="false"` — app data is excluded from Android backup.
- **What leaves the device:** the agent's system prompt and the task text are
  sent to the endpoint you configured (default Anthropic). Nothing else is
  uploaded by the app; there is no analytics code in the companion.
- **Task results** live in memory only (max 200) and are lost when the process
  dies.
- **Shizuku output** is shown on screen only and is not saved.
- Clearing the key (Settings → **Clear Key**) or uninstalling removes stored
  credentials.

---

*Licensing: this fork keeps the upstream `hackerai-tech/hackerai` LICENSE and
attribution unchanged.*
