package ai.hackerai.companion

import ai.hackerai.companion.shizuku.ShizukuManager
import ai.hackerai.companion.ui.DcaSetup
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DcaSetupTest {

    @Test
    fun `releases uri points at the droidcommand-AI GitHub Releases page`() {
        assertEquals(
            "https://github.com/Victorious93/droidcommand-AI/releases",
            DcaSetup.RELEASES_URI,
        )
    }

    @Test
    fun `setup copy explains the companion and how to get the controller`() {
        assertTrue("HackerAI companion" in DcaSetup.INTRO)
        assertTrue("DroidCommand AI" in DcaSetup.INTRO)
        assertTrue("controller" in DcaSetup.INTRO)
        assertTrue("Victorious93/droidcommand-AI" in DcaSetup.HOW_TO_GET)
        assertTrue(DcaSetup.REFRESH_BUTTON.contains("Refresh"))
        assertEquals("Help", DcaSetup.HELP_BUTTON)
    }

    @Test
    fun `manifest queries include controller, Shizuku, Sui, and VictorLaunch`() {
        val xml = File("src/main/AndroidManifest.xml").readText()
        listOf(
            "ai.droidcommand.app",
            "moe.shizuku.privileged.api",
            "rikka.sui",
            "com.victorious.launch",
        ).forEach { pkg ->
            assertTrue("missing queries package $pkg", xml.contains("android:name=\"$pkg\""))
        }
    }

    @Test
    fun `isProviderPackageInstalled is true if Shizuku or Sui is present`() {
        val pm = mockk<PackageManager>()
        @Suppress("DEPRECATION")
        every { pm.getPackageInfo(ShizukuManager.SHIZUKU_MANAGER_PACKAGE, 0) } throws
            PackageManager.NameNotFoundException()
        @Suppress("DEPRECATION")
        every { pm.getPackageInfo(ShizukuManager.SUI_PACKAGE, 0) } returns mockk()
        assertTrue(ShizukuManager.isProviderPackageInstalled(pm))
        assertEquals("rikka.sui", ShizukuManager.SUI_PACKAGE)
    }

    @Test
    fun `isProviderPackageInstalled is false when neither package is present`() {
        val pm = mockk<PackageManager>()
        @Suppress("DEPRECATION")
        every { pm.getPackageInfo(any<String>(), any<Int>()) } throws
            PackageManager.NameNotFoundException()
        assertFalse(ShizukuManager.isProviderPackageInstalled(pm))
    }
}
