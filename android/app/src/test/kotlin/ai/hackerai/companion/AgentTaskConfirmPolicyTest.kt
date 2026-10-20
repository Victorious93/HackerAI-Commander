package ai.hackerai.companion

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentTaskConfirmPolicyTest {
    private lateinit var policy: AgentTaskConfirmPolicy

    @Before fun setUp() {
        val ctx = RuntimeEnvironment.getApplication()
        ctx.getSharedPreferences(AgentTaskConfirmPolicy.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        policy = AgentTaskConfirmPolicy(ctx)
    }

    @Test fun `default require confirm is on`() {
        assertTrue(policy.requireConfirm)
        assertEquals(AgentTaskConfirmPolicy.CODE_CONFIRMATION_REQUIRED, policy.blockReason(hasApiKey = true, confirmed = false))
    }

    @Test fun `confirmed start is allowed when toggle on`() {
        assertNull(policy.blockReason(hasApiKey = true, confirmed = true))
    }

    @Test fun `toggle off allows unconfirmed start`() {
        policy.requireConfirm = false
        assertNull(policy.blockReason(hasApiKey = true, confirmed = false))
    }

    @Test fun `no api key is not blocked by confirm policy`() {
        assertNull(policy.blockReason(hasApiKey = false, confirmed = false))
    }
}
