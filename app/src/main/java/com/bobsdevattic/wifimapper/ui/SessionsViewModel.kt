package com.bobsdevattic.wifimapper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.model.MapSummary
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SessionsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as WifiMapperApp).repository

    val settings = (app as WifiMapperApp).settings

    /** Saved maps with their floor and point counts (unsorted; the screen applies the chosen sort). */
    val sessions: StateFlow<List<MapSummary>> =
        combine(repo.observeSessions(), repo.observeSessionStats()) { sessions, stats ->
            val byId = stats.associateBy { it.sessionId }
            sessions.map { s ->
                val st = byId[s.id]
                MapSummary(
                    id = s.id, name = s.name, createdAt = s.createdAt,
                    geoAnchored = s.anchorLat != null,
                    floors = st?.floors ?: 0, points = st?.points ?: 0,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }
}
