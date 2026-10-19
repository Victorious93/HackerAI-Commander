package ai.hackerai.companion.shizuku

import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Privileged user service. Instantiated by the Shizuku server inside a process
 * that runs with Shizuku's identity (ADB shell uid 2000, or root). Anything this
 * class does happens with that elevated privilege, so every entry point here is
 * only reachable after the user has granted Shizuku permission AND explicitly
 * confirmed an action in the UI.
 *
 * Shizuku requires either a no-arg constructor or a (Context) constructor.
 */
class ShizukuUserService : IShizukuUserService.Stub() {

    override fun destroy() {
        // Called by the Shizuku server to tear the process down.
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    override fun execCommand(command: String, timeoutMs: Long): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()

            val out = StringBuilder()
            val err = StringBuilder()
            val outThread = Thread {
                process.inputStream.bufferedReader().use { out.append(it.readText()) }
            }
            val errThread = Thread {
                process.errorStream.bufferedReader().use { err.append(it.readText()) }
            }
            outThread.start()
            errThread.start()

            val finished =
                if (timeoutMs > 0) process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                else { process.waitFor(); true }

            if (!finished) {
                process.destroyForcibly()
                outThread.join(READER_JOIN_MS)
                errThread.join(READER_JOIN_MS)
                return ShellResult(
                    exitCode = -1,
                    stdout = out.toString(),
                    stderr = err.toString(),
                    timedOut = true,
                ).toJson()
            }

            outThread.join(READER_JOIN_MS)
            errThread.join(READER_JOIN_MS)
            ShellResult(process.exitValue(), out.toString(), err.toString()).toJson()
        } catch (t: Throwable) {
            ShellResult(exitCode = -1, stdout = "", stderr = t.message ?: t.toString()).toJson()
        }
    }

    private companion object {
        const val READER_JOIN_MS = 1_000L
    }
}
