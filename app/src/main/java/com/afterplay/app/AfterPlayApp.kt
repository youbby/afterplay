package com.afterplay.app

import android.app.Application
import com.afterplay.app.data.LibraryStore
import com.afterplay.app.playback.YoutubeRepository
import com.afterplay.app.playback.PlaybackStore
import com.afterplay.app.playback.SleepTimer

class AfterPlayApp : Application() {
    val library by lazy { LibraryStore(this) }
    val youtube by lazy { YoutubeRepository() }
    val playbackStore by lazy { PlaybackStore(this) }
    val sleepTimer by lazy { SleepTimer(this) }
}
