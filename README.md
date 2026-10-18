<p align="center">
  <a href="https://hackerai.co/">
    <img src="docs/assets/logo.png" width="150" alt="HackerAI Logo">
  </a>
</p>

<h1 align="center">HackerAI</h1>

<h2 align="center">Your AI-Powered Penetration Testing Assistant</h2>

<div align="center">

[![License](https://img.shields.io/badge/License-Apache%202.0%20with%20Commercial%20Restrictions-red.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-hackerai.co-2d3748.svg)](https://hackerai.co)

</div>

> This repository (`Victorious93/HackerAI-Commander`) is a fork of
> [`hackerai-tech/hackerai`](https://github.com/hackerai-tech/hackerai). The web
> product, its design, and its license remain the upstream project's; see
> [Credits & license](#credits--license).

## What this is

HackerAI is an AI-powered security assistant: you describe or point it at a
target and chat with it to **find and fix vulnerabilities**. This repo has two
parts:

1. **The web app** — a TypeScript / Next.js + React application (the upstream
   `hackerai-tech/hackerai` product). You run security workflows by chatting with
   an AI that can plan, use tools, and execute in isolated sandboxes. See
   [Web app: getting started](#web-app-getting-started).
2. **The Android companion APK** — a Kotlin / Jetpack Compose app under
   [`android/`](android/) that exposes HackerAI's agent capabilities to the
   DroidCommand AI controller over an AIDL bound service. See
   [Android companion](#android-companion).

### Planned: Notebook mode

A **Notebook mode** — source-grounded Q&A and summaries over the user's *own*
uploaded documents — is **planned and not yet implemented**. There is no
Notebook feature in this codebase today; this line is a roadmap note only, so
treat any mention of it as forthcoming rather than available.

> **Defensive use only.** Point HackerAI (web or companion) only at systems you
> own or are explicitly authorized to test. You are responsible for staying
> within the law and your authorization scope.

## Where it fits in the Victorious / DroidCommand fleet

This repo is one of three companion apps bound to the
[`Victorious93/droidcommand-AI`](https://github.com/Victorious93/droidcommand-AI)
controller (alongside `Victorious93/Pentest-Swarm-AI` and
`Victorious93/VictorSuite`). Its fleet connection class is **`BOUND_COMPANION`**
— it does not run standalone agent tasks for the controller; it is *bound* to the
controller process over AIDL. **[Likely]** `BOUND_COMPANION` is fleet-level
taxonomy and does not appear as a literal token anywhere in this repository; it
is documented here for context, not enforced by code in this repo.

What **is** verifiable in this repo's code:

- **AIDL bound service.** The contract
  [`android/app/src/main/aidl/ai/droidcommand/companion/IHackerAIService.aidl`](android/app/src/main/aidl/ai/droidcommand/companion/IHackerAIService.aidl)
  declares `interface IHackerAIService` (package `ai.droidcommand.companion`)
  with JSON-string methods: `runAgentTask`, `getSkillCatalog`,
  `validateFinding`, `cancelTask` (`oneway`), `getTaskResult`, and
  `healthCheck`. It is implemented by `HackerAIBoundService` (an
  `IHackerAIService.Stub`) in
  [`android/app/src/main/kotlin/ai/hackerai/companion/service/HackerAIBoundService.kt`](android/app/src/main/kotlin/ai/hackerai/companion/service/HackerAIBoundService.kt),
  which returns the binder from `onBind`. The AIDL comment says to keep it in
  sync with `core-companion` in the droidcommand-AI repo.
- **Signature permission.** `AndroidManifest.xml` requests
  `<uses-permission android:name="ai.droidcommand.permission.BIND_HACKERAI" />`,
  and the exported `HackerAIBoundService` is guarded by
  `android:permission="ai.droidcommand.permission.BIND_HACKERAI"` with an
  intent-filter action `ai.hackerai.companion.IHackerAIService`. A manifest
  comment states that permission is declared `protectionLevel="signature"` by
  the DroidCommand AI controller, so only an app signed with the same key can
  hold it and bind. **[Likely]** the `signature`-level declaration and the
  "same signing key" rule live in the *controller* repo, which is not part of
  this repository and was not inspected, so that half is taken from the in-repo
  comment rather than verified here.
- **DependencyGuard.** `ai.hackerai.companion.DependencyGuard`
  ([`DependencyGuard.kt`](android/app/src/main/kotlin/ai/hackerai/companion/DependencyGuard.kt))
  checks whether the controller package `ai.droidcommand.app` (`DCA_PACKAGE`) is
  installed via `PackageManager.getPackageInfo`. `HackerAIBoundService` calls
  `dependencyGuard.isDcaInstalled()` at the top of every method and returns an
  error (e.g. `{"ok":false,"error":"DroidCommand AI is not installed"}`, or
  `"dca_not_installed"` from `healthCheck`) when the controller is absent.
  `MainActivity` also uses it to render an install-DroidCommand state.
- **Shared fleet release signing key.** Release builds are signed with the shared
  fleet release key (see [Signing](#signing)). **[Likely]** this is the same
  certificate the controller uses to declare the `signature` permission — that is
  the intent of the shared key — but cert parity with the controller cannot be
  confirmed from this repo alone.

## Web app: getting started

The web app is the upstream `hackerai-tech/hackerai` product. The setup below is
the upstream flow and clones from upstream.

### Prerequisites

You'll need the following accounts:

**Required:**

- [OpenRouter](https://openrouter.ai/) - AI model provider
- [OpenAI](https://platform.openai.com/) - Identifies security requests that should use [abliteration.ai](https://abliteration.ai/) models
- [E2B](https://e2b.dev/) - Isolated cloud execution in Agent mode
- [Convex](https://www.convex.dev/) - Database and backend
- [Amazon S3](https://aws.amazon.com/s3/) - File storage
- [WorkOS](https://workos.com/) - Authentication and user management
- [Trigger.dev](https://trigger.dev/) - Required durable runtime for agent tasks

**Optional:**

- [abliteration.ai](https://abliteration.ai/) - AI models for security requests that standard models may refuse
- [Perplexity](https://perplexity.ai/) - Web search functionality
- [Jina AI](https://jina.ai/reader) - Web URL content retrieval
- [Redis](https://redis.io/) - Stream resumption
- [Upstash Redis](https://upstash.com/) - Rate limiting
- [PostHog](https://posthog.com/) - Analytics
- [Stripe](https://stripe.com/) - Payment processing

### Clone the repo

```bash
git clone https://github.com/hackerai-tech/hackerai.git
```

### Navigate to the project directory

```bash
cd hackerai
```

### Install dependencies

```bash
pnpm install
```

### Run the setup script

```bash
pnpm run setup
```

To use abliteration.ai for eligible security requests, create an API key in the
[abliteration.ai console](https://abliteration.ai/console) and set
`ABLITERATION_API_KEY` in `.env.local`, Vercel, and Trigger.dev. Without this
optional key, HackerAI continues using its standard models.

### Start the development server

This runs both Next.js and Convex dev servers:

```bash
pnpm run dev
```

Or run them separately in two terminals:

```bash
pnpm run dev:next
pnpm run dev:convex
```

### Run the Trigger.dev worker

Agent mode runs the agent loop on a [Trigger.dev](https://trigger.dev/) task.
To use the agent locally:

1. Create a project at https://cloud.trigger.dev and copy your **dev** secret
   key (`tr_dev_…`) into `.env.local` as `TRIGGER_SECRET_KEY`.
2. In the Trigger.dev dashboard → your project → **Environment Variables**,
   add the env vars the task needs to run (these live on the worker, not on
   Vercel): `NEXT_PUBLIC_CONVEX_URL`, `CONVEX_SERVICE_ROLE_KEY`,
   `OPENROUTER_API_KEY`, `OPENAI_API_KEY`, `AWS_S3_ACCESS_KEY_ID`,
   `AWS_S3_SECRET_ACCESS_KEY`, `AWS_S3_REGION`, `AWS_S3_BUCKET_NAME`, and
   `E2B_API_KEY`. Add
   `MIOSA_API_KEY` for the MIOSA rollout or explicit MIOSA testing. New Miosa
   workspaces default to the native `hackerai-tools` template; optionally set
   `MIOSA_TEMPLATE_ID` to override it. An existing `miosa-sandbox-docker`
   override still selects the Docker template, so remove or update that value
   in each intended runtime to use the native default. Existing workspaces
   retain their original runtime and files; E2B remains the cloud fallback.
   Add any optional keys you use
   (`ABLITERATION_API_KEY`, `PERPLEXITY_API_KEY`, `JINA_API_KEY`, etc.).
3. Start the worker in a third terminal:

   ```bash
   pnpm dev:trigger
   ```

   This starts the default Trigger.dev worker used by local Agent requests.
   To start an explicitly routed Trigger.dev branch instead, set a stable
   branch name with
   `TRIGGER_DEV_BRANCH=my-local-agent pnpm dev:trigger`. Only use that override
   when the request path is configured to target the same Trigger.dev branch.

### Useful web scripts

From [`package.json`](package.json):

| Script | What it does |
| --- | --- |
| `pnpm dev` | Run Next.js (`next dev --turbopack`) and Convex together |
| `pnpm dev:next` / `pnpm dev:convex` | Run each dev server on its own |
| `pnpm dev:trigger` | Start the Trigger.dev worker |
| `pnpm build` | `next build` (plus PostHog sourcemap upload) |
| `pnpm start` | `next start` (serve the production build) |
| `pnpm lint` | ESLint over `app lib types __mocks__` |
| `pnpm typecheck` | `tsc --noEmit` |
| `pnpm test` / `pnpm test:ci` | Jest unit tests (CI variant adds coverage + skills check) |
| `pnpm test:e2e` | Playwright end-to-end tests |
| `pnpm format` / `pnpm format:check` | Prettier write / check |

## Android companion

The companion lives in [`android/`](android/). Toolchain, verified from the repo:

- **JDK 17** (`sourceCompatibility`/`targetCompatibility = VERSION_17`, `jvmTarget = "17"`).
- **Gradle 8.13** via the committed wrapper (`android/gradlew`,
  `android/gradle/wrapper/gradle-wrapper.properties`).
- **Android Gradle Plugin 8.7.3**, **Kotlin 2.1.0** (`android/gradle/libs.versions.toml`).
- `compileSdk`/`targetSdk` **35**, `minSdk` **26**, `applicationId`
  `ai.hackerai.companion`.

### Build the debug APK

```bash
cd android
./gradlew assembleDebug
# output: android/app/build/outputs/apk/debug/app-debug.apk
```

Other common tasks:

```bash
./gradlew :app:assembleRelease   # release build (unsigned unless the signing env vars are set)
./gradlew :app:signingReport     # show the resolved signing configs
./gradlew test                   # JVM unit tests
```

> The `ai.droidcommand.hackerai` sources under
> `android/app/src/main/kotlin/ai/droidcommand/hackerai/` are **vendored** from
> the droidcommand-AI `core-hackerai` module; see
> [`VENDORED.md`](android/app/src/main/kotlin/ai/droidcommand/hackerai/VENDORED.md)
> for provenance and why they are vendored rather than pulled as a dependency.

### Continuous integration

- [`.github/workflows/android.yml`](.github/workflows/android.yml) — on
  push/PR touching `android/**`: sets up JDK 17 + Android SDK, builds
  `assembleDebug`, verifies the debug signer fingerprint, and uploads the debug
  APK as an artifact.
- [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)
  — on `v*` tags: builds `assembleRelease` with the fleet release key, verifies
  the signer against a repo variable, uploads the R8 `mapping.txt`, and attaches
  the signed APK to a **draft** GitHub release.

## Signing

### Debug (committed, fixed, not a secret)

Debug builds use a **dedicated, stable debug keystore committed to the repo** at
[`android/keystore/hackeraietc-debug.keystore`](android/keystore/hackeraietc-debug.keystore)
(alias `androiddebugkey`, store/key password `android`). A committed debug key is
not a secret; it keeps the debug signer stable so new debug builds install over
old ones. Its certificate SHA-256 is:

```
AF:1C:8F:C5:44:9F:58:4A:46:C8:96:CC:C9:7F:35:B0:7B:35:7E:96:EE:66:5F:3A:BF:CB:0D:28:4D:F5:23:55
```

### Release (shared fleet key, CI secrets only — never committed)

Release builds are signed with the **shared fleet release key**, which exists
**only in CI secrets** and is never committed to the repo. The release
`signingConfig` in [`android/app/build.gradle.kts`](android/app/build.gradle.kts)
reads it from environment variables and, if any are absent (local builds, PRs,
forks), produces an **unsigned** release instead of failing.

Secrets (added by the repo owner / fleet key custodian — values are never printed
here):

| Secret | Purpose |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_B64` | base64 of the PKCS12 keystore |
| `ANDROID_RELEASE_STORE_PASSWORD` | keystore (store) password |
| `ANDROID_RELEASE_KEY_ALIAS` | key alias |
| `ANDROID_RELEASE_KEY_PASSWORD` | key password |

Repository variable (non-secret, used by CI to verify the signer):

| Variable | Value |
| --- | --- |
| `ANDROID_RELEASE_CERT_SHA256` | `9E:A7:B3:14:94:5B:0A:62:97:15:6F:F7:06:E7:0C:0A:B1:B7:24:DE:06:D5:09:9C:A5:12:8A:B4:43:F7:2D:8D` |

> **PKCS12 note:** the fleet release keystore is PKCS12. PKCS12 does not support a
> separate per-entry password, so the release **key** password must equal the
> **store** password — set `ANDROID_RELEASE_KEY_PASSWORD` to the same value as
> `ANDROID_RELEASE_STORE_PASSWORD`. The signing config defaults `storeType` to
> `pkcs12`.

### Cutting a release

Releases are produced by **pushing a `v*` tag** (e.g. `git tag v0.1.0 && git push
origin v0.1.0`). The tag-triggered workflow builds the signed APK, verifies the
signer against `ANDROID_RELEASE_CERT_SHA256`, and creates a **draft** release for
a human to review and publish — nothing is published automatically.

Versioning is derived from the tag (`vMAJOR.MINOR.PATCH`):

- `versionName` = `MAJOR.MINOR.PATCH`
- `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH` (strictly increasing)

Local and non-tag builds fall back to `versionName 0.1.0` / `versionCode 1`.

## Releases

- **v0.1.0** — [release page](https://github.com/Victorious93/HackerAI-Commander/releases/tag/v0.1.0).
  Asset: `hackerai-companion-v0.1.0.apk` (`versionName 0.1.0`, `versionCode 100`),
  signed by the shared fleet release key (signer SHA-256 verified to equal
  `9E:A7:B3:14:…:2D:8D`).

## Credits & license

This project is a fork of
[`hackerai-tech/hackerai`](https://github.com/hackerai-tech/hackerai); credit for
the web product and its design belongs to HackerAI, LLC. Licensed under the
**Apache License 2.0 with Commercial Restrictions** — see [LICENSE](LICENSE) for
the full terms and `Copyright (c) 2025 HackerAI, LLC`. Third-party notices are in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## For coding agents

Coding agents should start with [AGENTS.md](AGENTS.md), which covers the fleet
relationship, the AIDL contract's source of truth, and the security model in more
detail.
