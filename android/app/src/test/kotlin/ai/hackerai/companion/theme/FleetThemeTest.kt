package ai.hackerai.companion.theme

import ai.hackerai.companion.ui.theme.Brand
import ai.hackerai.companion.ui.theme.Navy
import ai.hackerai.companion.ui.theme.colorSchemeFor
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val VALID = """{"schemaVersion":1,"mode":"NEON_FLEET","iconStyle":"SYSTEM","iconPackPackage":"","updatedAtMs":0,
 "palette":{"background":"#FF0B0C12","surface":"#FF161821","primary":"#FF00E5FF","secondary":"#FFE840FF",
 "onBackground":"#FFF5F7FF","muted":"#FF9AA3B5","danger":"#FFFF6B8A","stale":"#FFFFC14D","tile":"#CC1A1D2B"},"extra":1}"""

class FakeThemeProvider : ContentProvider() {
    var json: String? = VALID
    var throwSecurity = false
    var writes = 0
    override fun onCreate() = true
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
        if (throwSecurity) throw SecurityException("not pinned")
        return MatrixCursor(arrayOf("schemaVersion", "json", "unknownCol")).apply { addRow(arrayOf(1, json, "x")) }
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? { writes++; throw SecurityException() }
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int { writes++; throw SecurityException() }
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int { writes++; throw SecurityException() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FleetThemeTest {
    private fun provider() = Robolectric.buildContentProvider(FakeThemeProvider::class.java)
        .create(FleetThemeContract.AUTHORITY).get()
    private fun client(trusted: Boolean) =
        FleetThemeClient(RuntimeEnvironment.getApplication()) { _, _, _ -> trusted }

    @Test fun `decodes v1 palette and ignores unknown fields`() {
        val p = FleetThemeCodec.decode(VALID)!!
        assertEquals("NEON_FLEET", p.mode)
        assertEquals(0xFF00E5FF.toInt(), p.primary)
    }

    @Test fun `rejects other schema versions and bad hex`() {
        assertNull(FleetThemeCodec.decode(VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        assertNull(FleetThemeCodec.decode(VALID.replace("#FF00E5FF", "cyan")))
        assertNull(FleetThemeCodec.decode("not json"))
        assertEquals(0xFF0B1021.toInt(), FleetThemeCodec.parseHex("#0B1021"))
    }

    @Test fun `pin matches published VictorLaunch cert only`() {
        val pin = FleetThemeContract.PINNED_PROVIDER_CERTS.single()
        assertTrue(FleetThemeCodec.isPinned(listOf(pin.lowercase())))
        assertFalse(FleetThemeCodec.isPinned(listOf("1B:06:A8:00")))
        assertFalse(FleetThemeCodec.isPinned(listOf(pin, pin)))
        assertFalse(FleetThemeCodec.isPinned(emptyList()))
    }

    @Test fun `pinned provider palette is applied`() {
        provider()
        val c = client(true)
        c.start()
        assertNotNull(c.palette.value)
        assertEquals(Color(0xFF00E5FF.toInt()), colorSchemeFor(c.palette.value).primary)
    }

    @Test fun `unpinned provider falls back to default theme`() {
        provider()
        val c = client(false)
        c.refresh()
        assertNull(c.palette.value)
        assertEquals(Navy, colorSchemeFor(null).background)
        assertEquals(Brand, colorSchemeFor(null).primary)
    }

    @Test fun `security exception falls back and client never writes`() {
        val prov = provider()
        prov.throwSecurity = true
        val c = client(true)
        c.refresh()
        assertNull(c.palette.value)
        assertEquals(0, prov.writes)
    }

    @Test fun `observer and receiver refresh live`() {
        val prov = provider()
        prov.json = VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":9")
        val c = client(true)
        c.start()
        assertNull(c.palette.value)
        prov.json = VALID
        RuntimeEnvironment.getApplication().contentResolver.notifyChange(android.net.Uri.parse(FleetThemeContract.URI), null)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNotNull(c.palette.value)
    }
}
