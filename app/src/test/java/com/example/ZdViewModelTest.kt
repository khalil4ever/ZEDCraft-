package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.YouTubeCacheDatabase
import com.example.ui.viewmodel.ZedCraftViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ZdViewModelTest {

    @Test
    fun `blank zd prompt is ignored and does not trigger thinking state`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ZedCraftViewModel(app)
        shadowOf(Looper.getMainLooper()).idle()

        vm.sendZdPrompt("   \n  ")
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(vm.uiState.value.isZdThinking)
        assertNull(vm.uiState.value.zdError)
    }

    @Test
    fun `clearZdChat seeds single welcome message without duplicates`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dao = YouTubeCacheDatabase.getInstance(app).dao()
        val vm = ZedCraftViewModel(app)

        // Wait for initial init coroutine + Room IO to finish
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (dao.getZdChatMessageCount() > 0) return@repeat
            delay(50L)
        }

        vm.clearZdChat()
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            if (dao.getZdChatMessageCount() == 1) return@repeat
            delay(50L)
        }

        val count = dao.getZdChatMessageCount()
        assertEquals(1, count)
        val messages = dao.getRecentZdChatMessages(10)
        assertEquals("assistant", messages.first().role)
    }

    @Test
    fun `stopZdGeneration and dismissZdError reset active state cleanly`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ZedCraftViewModel(app)

        vm.stopZdGeneration()
        vm.dismissZdError()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(vm.uiState.value.isZdThinking)
        assertNull(vm.uiState.value.streamingZdMessage)
        assertNull(vm.uiState.value.zdError)
    }
}
