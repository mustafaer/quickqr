package net.mustafaer.quickqr.ui.viewmodel

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.mustafaer.quickqr.data.ImageReadResult
import net.mustafaer.quickqr.data.ScanEntity
import net.mustafaer.quickqr.data.ScanRepository
import net.mustafaer.quickqr.data.SettingsRepository
import net.mustafaer.quickqr.data.ThemeMode
import net.mustafaer.quickqr.utils.ScanContent
import net.mustafaer.quickqr.utils.ScanResultType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val recorded = mutableListOf<String>()
    private var buzzes = 0
    private var now = 0L
    private val continuous = MutableStateFlow(false)

    private val scans = object : ScanRepository {
        override val scans: Flow<List<ScanEntity>> = emptyFlow()
        override suspend fun record(text: String, type: ScanResultType) {
            recorded += text
        }
        override suspend fun delete(ids: Collection<Int>) = Unit
        override suspend fun clear() = Unit
        override suspend fun setPinned(id: Int, pinned: Boolean) = Unit
    }

    private val settings = object : SettingsRepository {
        override val hapticEnabled = MutableStateFlow(true)
        override val continuousMode = continuous
        override val privacyNoticeSeen = MutableStateFlow(true)
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val dynamicColor = MutableStateFlow(false)
        override suspend fun setHapticEnabled(enabled: Boolean) = Unit
        override suspend fun setContinuousMode(enabled: Boolean) = Unit
        override suspend fun setPrivacyNoticeSeen() = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setDynamicColor(enabled: Boolean) = Unit
    }

    private fun viewModel() = ScannerViewModel(
        scans = scans,
        settings = settings,
        imageReader = { _: Uri -> ImageReadResult.NotFound },
        feedback = { buzzes++ },
        parsingDispatcher = dispatcher,
        clock = { now }
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Feeds [frames] at 30 fps: one call every 33 ms. */
    private fun TestScope.feed(vm: ScannerViewModel, vararg frames: String?) {
        frames.forEach { frame ->
            now += 33
            vm.onFrame(frame)
            advanceUntilIdle()
        }
    }

    @Test
    fun `a code held in view fires once, not every few seconds`() = runTest(dispatcher) {
        continuous.value = true
        val vm = viewModel()
        advanceUntilIdle()

        // Five seconds of the same code in front of the camera.
        feed(vm, *Array(150) { "WIFI:S:Cafe;P:latte;;" })

        assertEquals(listOf("WIFI:S:Cafe;P:latte;;"), recorded)
        assertEquals(1, buzzes)
    }

    @Test
    fun `a single dropped frame does not re-fire the same code`() = runTest(dispatcher) {
        continuous.value = true
        val vm = viewModel()
        advanceUntilIdle()

        feed(vm, "A", "A", null, "A", null, null, "A")

        assertEquals(listOf("A"), recorded)
    }

    @Test
    fun `a code that leaves and comes back fires again`() = runTest(dispatcher) {
        continuous.value = true
        val vm = viewModel()
        advanceUntilIdle()

        feed(vm, "A", *Array(30) { null }, "A")

        assertEquals(listOf("A", "A"), recorded)
    }

    @Test
    fun `single mode opens one result and ignores codes until it is dismissed`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        feed(vm, "https://example.com", "B", "C")

        val session = vm.session.value
        assertTrue(session is ScanSession.Showing)
        session as ScanSession.Showing
        assertTrue(session.content is ScanContent.Link)
        assertEquals(ScanOrigin.CAMERA, session.origin)
        assertEquals(listOf("https://example.com"), recorded)

        vm.dismiss()
        assertEquals(ScanSession.Scanning, vm.session.value)
    }

    @Test
    fun `reopening a stored scan records nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.showStored("tel:+15551234567")
        advanceUntilIdle()

        assertEquals(ScanOrigin.HISTORY, (vm.session.value as ScanSession.Showing).origin)
        assertTrue(recorded.isEmpty())
        assertEquals(0, buzzes)
    }
}
