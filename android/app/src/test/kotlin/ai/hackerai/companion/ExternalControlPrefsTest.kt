package ai.hackerai.companion

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalControlPrefsTest {
    private lateinit var prefs: ExternalControlPrefs

    @Before fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences(ExternalControlPrefs.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = ExternalControlPrefs(ctx)
    }

    @Test fun `default is off`() {
        assertFalse(ExternalControlPrefs.DEFAULT)
        assertFalse(prefs.enabled)
    }

    @Test fun `toggle persists`() {
        prefs.enabled = true
        assertTrue(ExternalControlPrefs(RuntimeEnvironment.getApplication()).enabled)
        prefs.enabled = false
        assertFalse(ExternalControlPrefs(RuntimeEnvironment.getApplication()).enabled)
    }
}
