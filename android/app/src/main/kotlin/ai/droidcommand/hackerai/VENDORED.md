# Vendored `ai.droidcommand.hackerai` sources

These files are vendored from the `core-hackerai` module of the
`Victorious93/droidcommand-AI` repository (both repos share the same owner).

- Source commit: `85c6cb452c84c755fdc650e717c208ba30ea803f`
- Source path: `core-hackerai/src/main/kotlin/ai/droidcommand/hackerai/`

## Why vendored instead of a Maven dependency

The app previously depended on `ai.droidcommand:core-hackerai:0.1.0-SNAPSHOT`,
which only ever existed in a developer's local Maven cache
(`./gradlew :core-hackerai:publishToMavenLocal`). It is not published to any
repository, so neither CI nor a fresh checkout could resolve it, and the build
failed. Publishing it properly requires changes in the droidcommand-AI repo,
which are out of scope for this change.

Only the self-contained files are vendored. The upstream module's
`HackerAiTool.kt` is intentionally omitted because it pulls in `core-agent` /
`core-security` and the companion app does not use it. The remaining files
depend only on the Kotlin stdlib and `kotlinx-serialization-json` (already a
dependency of the app).

`SkillRankerCompat.kt` is NOT vendored; it is a thin local shim that exposes the
top-level `rankSkillsForTask` function as `SkillRanker.rankSkillsForTask`, which
is how the app calls it.

## Follow-up

If/when `core-hackerai` is published to a real Maven repository, these vendored
files can be deleted and the dependency restored.
