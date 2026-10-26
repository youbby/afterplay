package com.afterplay.app

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afterplay.app.data.AppRelease
import com.afterplay.app.data.ThemePreset
import com.afterplay.app.data.UpdateStatus
import com.afterplay.app.ui.AfterPlayTheme
import com.afterplay.app.ui.AppInfoDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppInfoDialogTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf<UpdateStatus>(UpdateStatus.Idle)
    private val opened = mutableListOf<String>()
    private var checks = 0
    private var problems = 0
    private val release = AppRelease("1.7.0", "https://github.com/youbby/afterplay/releases/tag/v1.7.0",
        "https://github.com/youbby/afterplay/releases/download/v1.7.0/AfterPlay-1.7.0-release.apk")

    private fun show() {
        compose.setContent {
            AfterPlayTheme(ThemePreset.MINT) {
                AppInfoDialog(state.value, onCheckUpdates = { checks++; state.value = UpdateStatus.Checking },
                    onOpenUrl = { opened.add(it) }, onAddQuickSettingsTile = {}, onDismiss = {}, onShowDiagnostics = { problems++ })
            }
        }
    }

    @Test fun checkIsManualAndRepeatedTapsAreDisabledWhileChecking() {
        show()
        compose.onNodeWithTag("app_version").assertTextEquals("현재 버전 ${BuildConfig.VERSION_NAME}")
        assertEquals(0, checks)
        compose.onNodeWithTag("check_updates").performClick()
        compose.onNodeWithTag("check_updates").assertIsNotEnabled()
        compose.onNodeWithTag("update_check_progress").performScrollTo().assertIsDisplayed()
        assertEquals(1, checks)
        assertTrue(opened.isEmpty())
    }

    @Test fun availableUpdateOpensTheApkOnlyAfterTheUserChoosesIt() {
        show()
        compose.runOnIdle { state.value = UpdateStatus.Available(release) }
        compose.onNodeWithText("새 버전 1.7.0이 있습니다.").performScrollTo().assertIsDisplayed()
        assertTrue(opened.isEmpty())
        compose.onNodeWithTag("download_update").performScrollTo().performClick()
        assertEquals(listOf(release.apkUrl), opened)
        compose.onNodeWithTag("open_release").performScrollTo().performClick()
        assertEquals(release.releaseUrl, opened.last())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(instrumentation.targetContext.getExternalFilesDir(null), "app-info-update-available.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } } finally { bitmap.recycle() }
    }

    @Test fun currentVersionAndConnectionFailureBothAllowAnotherCheck() {
        show()
        compose.runOnIdle { state.value = UpdateStatus.Current(BuildConfig.VERSION_NAME) }
        compose.onNodeWithText("사용 가능한 업데이트가 없습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("download_update").assertDoesNotExist()
        compose.runOnIdle { state.value = UpdateStatus.Error("인터넷 연결을 확인해 주세요.") }
        compose.onNodeWithText("인터넷 연결을 확인해 주세요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("check_updates").performScrollTo().performClick()
        assertEquals(UpdateStatus.Checking, state.value)
    }

    @Test fun missingApkStillOffersTheReleasePage() {
        show()
        compose.runOnIdle { state.value = UpdateStatus.Available(release.copy(apkUrl = null)) }
        compose.onNodeWithTag("download_update").assertDoesNotExist()
        compose.onNodeWithText("릴리스 페이지 열기").performScrollTo().performClick()
        assertEquals(listOf(release.releaseUrl), opened)
    }

    @Test fun infoShowsOnlyActionsUntilMoreIsExpanded() {
        show()
        val quick = compose.onNodeWithTag("add_quick_settings").fetchSemanticsNode().boundsInRoot
        val update = compose.onNodeWithTag("check_updates").fetchSemanticsNode().boundsInRoot
        val problem = compose.onNodeWithTag("open_diagnostics").fetchSemanticsNode().boundsInRoot
        assertTrue(quick.top < update.top && update.top < problem.top)
        compose.onNodeWithTag("open_diagnostics").performScrollTo().performClick()
        assertEquals(1, problems)
        compose.onNodeWithText("기능 설명").assertDoesNotExist()
        compose.onNodeWithTag("app_info_more").performScrollTo().performClick()
        compose.onNodeWithText("기능 설명").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("YouTube 정책 변경").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GitHub 앱 소스 보기").performScrollTo().performClick()
        assertEquals(listOf("https://github.com/youbby/afterplay"), opened)
    }
}
