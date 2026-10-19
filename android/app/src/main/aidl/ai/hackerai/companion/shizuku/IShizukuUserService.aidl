// AIDL contract for the privileged Shizuku UserService.
// Runs inside the Shizuku-spawned process (adb-shell or root uid), NOT the app process.
// All transaction ids are explicit because Shizuku reserves 16777114 for destroy().
package ai.hackerai.companion.shizuku;

interface IShizukuUserService {
    // Reserved by the Shizuku server to tear down the user service process.
    void destroy() = 16777114;

    // Explicit stop requested by the app.
    void exit() = 1;

    // Execute a shell command in the privileged process.
    // Returns a JSON ShellResult: {"exitCode":int,"stdout":string,"stderr":string,"timedOut":bool}.
    // timeoutMs <= 0 means wait indefinitely.
    String execCommand(String command, long timeoutMs) = 2;
}
