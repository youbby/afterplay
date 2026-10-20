package com.afterplay.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map

private val Context.libraryDataStore by preferencesDataStore("library")

class LibraryStore(context: Context) {
    private val store = context.libraryDataStore
    private val key = stringPreferencesKey("playlists_v1")
    private val gson = Gson()
    private val type = object : TypeToken<List<Playlist>>() {}.type
    private val initial = listOf(Playlist(id = "default", name = "나의 첫 재생목록"))

    private fun decode(value: String?): List<Playlist> =
        if (value == null) initial else requireNotNull(gson.fromJson<List<Playlist>>(value, type))

    val playlists = store.data.map { decode(it[key]) }

    suspend fun update(transform: (List<Playlist>) -> List<Playlist>) {
        store.edit { preferences ->
            preferences[key] = gson.toJson(transform(decode(preferences[key])))
        }
    }
}
