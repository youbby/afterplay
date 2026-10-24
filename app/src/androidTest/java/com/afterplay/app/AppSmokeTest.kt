package com.afterplay.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun createPlaylistAndValidateBadLink() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("나의 첫 재생목록").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(" 새 목록").performClick()
        compose.onNodeWithText("재생목록 이름").performTextInput("밤 산책 테스트")
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("밤 산책 테스트").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("목록추가").performScrollTo().performClick()
        compose.onNodeWithText("영상 링크").performTextInput("https://example.com/not-youtube")
        compose.onNodeWithText("추가", useUnmergedTree = true).performClick()
        compose.onNodeWithText("유튜브 영상 링크를 입력해 주세요. 재생목록 주소만으로는 추가할 수 없습니다.").assertIsDisplayed()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithContentDescription("재생목록 관리").performScrollTo().performClick()
        compose.onNodeWithText("재생목록 삭제").performClick()
        compose.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("밤 산책 테스트").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun validYoutubeLinkCanBeAddedAndPlayed() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("youtubeNetwork") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AfterPlayApp
        val track = app.youtube.resolve("jNQXAC9IVRw").track
        compose.onNodeWithText(" 새 목록").performScrollTo().performClick()
        compose.onNodeWithText("재생목록 이름").performTextInput("실제 영상 테스트")
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("실제 영상 테스트").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("목록추가").performScrollTo().performClick()
        compose.onNodeWithText("영상 링크").performTextInput(track.url)
        compose.onNodeWithText("추가", useUnmergedTree = true).performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText(track.title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(track.title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("전체 재생").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("일시정지").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("일시정지").performClick()
        compose.onNodeWithContentDescription("재생목록 관리").performScrollTo().performClick()
        compose.onNodeWithText("재생목록 삭제").performClick()
        compose.onNodeWithText("삭제", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("실제 영상 테스트").fetchSemanticsNodes().isEmpty() }
    }
}
