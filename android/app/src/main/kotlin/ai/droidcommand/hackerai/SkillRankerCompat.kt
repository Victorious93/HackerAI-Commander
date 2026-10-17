package ai.droidcommand.hackerai

/**
 * Compatibility shim.
 *
 * The app calls `SkillRanker.rankSkillsForTask(...)`, but the vendored source
 * (see VENDORED.md) exposes `rankSkillsForTask` as a top-level function. This
 * object forwards to it so the call site keeps working without editing the
 * vendored file.
 */
object SkillRanker {
    fun rankSkillsForTask(task: String, candidates: List<SubagentSkill>): List<SubagentSkill> =
        ai.droidcommand.hackerai.rankSkillsForTask(task, candidates)
}
