package com.afterplay.app.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

class DiagnosticLogTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun recordsAreBoundedPersistedAndCanBeCleared() {
        val folder = temporary.newFolder()
        val log = DiagnosticLog(folder, maxFileBytes = 2048, maxEntries = 20)
        repeat(90) { log.record(DiagnosticKind.SAMPLE, "event-$it " + "x".repeat(200)) }
        log.awaitIdle()
        assertEquals(20, log.state.value.events.size)
        assertTrue(log.state.value.events.last().detail.startsWith("event-89"))
        assertFalse(log.state.value.storageError)
        assertTrue(folder.listFiles()!!.size <= 2)
        assertTrue(folder.listFiles()!!.sumOf { it.length() } <= 4096)
        log.close()
        val restored = DiagnosticLog(folder, maxFileBytes = 2048, maxEntries = 20)
        try {
            restored.awaitIdle()
            assertTrue(restored.state.value.events.last().detail.startsWith("event-89"))
            restored.clear(); restored.awaitIdle()
            assertTrue(restored.state.value.events.isEmpty())
            assertTrue(folder.listFiles()!!.isEmpty())
        } finally { restored.close() }
    }

    @Test fun urlsIpsAndCredentialsNeverReachDisk() {
        val folder = temporary.newFolder()
        val log = DiagnosticLog(folder)
        try {
            log.record(DiagnosticKind.NETWORK_ERROR, "https://cdn.example/audio?sig=private-secret 192.168.1.2 token=private-token")
            log.record(DiagnosticKind.HTTP, "Authorization: Bearer private-bearer")
            log.awaitIdle()
            val text = folder.listFiles()!!.joinToString { it.readText() }
            for (secret in listOf("cdn.example", "private-secret", "192.168.1.2", "private-token", "private-bearer")) assertFalse(text.contains(secret))
            assertEquals(2, log.state.value.events.size)
        } finally { log.close() }
    }

    @Test fun storageFailureDoesNotLoseTheInMemoryReportOrThrowOnPlaybackThread() {
        val file = temporary.newFile()
        val log = DiagnosticLog(file)
        try {
            log.record(DiagnosticKind.PLAYER_ERROR, "sample failure")
            log.awaitIdle()
            assertTrue(log.state.value.storageError)
            assertEquals("sample failure", log.state.value.events.single().detail)
        } finally { log.close() }
    }

    @Test fun corruptedJournalLineIsIgnoredAndValidEntriesSurvive() {
        val folder = temporary.newFolder()
        File(folder, "playback.jsonl").writeText("not-json\n{\"time\":123,\"kind\":\"PLAYER\",\"detail\":\"good\"}\n")
        val log = DiagnosticLog(folder)
        try { log.awaitIdle(); assertEquals("good", log.state.value.events.single().detail) }
        finally { log.close() }
    }

    @Test fun networkAssessmentRequiresEvidenceNearThePlaybackSymptom() {
        val oldNetwork = DiagnosticEvent(1_000, DiagnosticKind.NETWORK_ERROR, "timeout")
        val nearby = DiagnosticEvent(2_000, DiagnosticKind.REBUFFER, "buffer empty")
        assertTrue(diagnosticHint(listOf(oldNetwork, nearby)).contains("네트워크 영향 가능성"))
        assertTrue(diagnosticHint(listOf(oldNetwork, nearby.copy(time = 40_000))).contains("단정할 근거는 아직"))
        val system = nearby.copy(kind = DiagnosticKind.SYSTEM_PAUSE)
        assertTrue(diagnosticHint(listOf(oldNetwork, system)).contains("시스템 제어"))
        val http = oldNetwork.copy(kind = DiagnosticKind.HTTP_ERROR, detail = "HTTP=403")
        assertTrue(diagnosticHint(listOf(http, nearby)).contains("서버 응답 오류"))
    }

    @Test fun nestedTimeoutIsRecognizedWithoutRecordingItsSensitiveMessage() {
        val error = IOException("https://private.example/?token=secret", SocketTimeoutException("private IP"))
        assertEquals("연결/수신 시간 초과", networkFailure(error))
        assertEquals(listOf("IOException", "SocketTimeoutException"), errorChain(error).map { it.javaClass.simpleName })
    }
}
