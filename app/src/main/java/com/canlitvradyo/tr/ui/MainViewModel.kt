package com.canlitvradyo.tr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canlitvradyo.tr.data.ChannelCache
import com.canlitvradyo.tr.data.ChannelRepository
import com.canlitvradyo.tr.model.Channel
import com.canlitvradyo.tr.model.ChannelType
import com.canlitvradyo.tr.model.LinkStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val selectedTab: ChannelType = ChannelType.TV,
    val tvChannels: List<Channel> = emptyList(),
    val radioChannels: List<Channel> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val searchQuery: String = "",
    val selectedCategory: String = "Tümü",
    val httpsOnlyMode: Boolean = true,
    val showOnlyOnline: Boolean = false,
    val isRefreshing: Boolean = false,
    val checkProgressText: String = "",
    val lastSyncTimeText: String = "",
    val errorMessage: String? = null,
    val currentChannel: Channel? = null,
    val isPlaying: Boolean = false,
    val playerError: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ChannelRepository()
    private val cache = ChannelCache(application.applicationContext)

    private val _uiState = MutableStateFlow(
        MainUiState(
            tvChannels = cache.loadChannels(ChannelType.TV),
            radioChannels = cache.loadChannels(ChannelType.RADIO),
            favorites = cache.loadFavorites(),
            httpsOnlyMode = cache.isHttpsOnlyMode(),
            lastSyncTimeText = formatTimestamp(
                maxOf(cache.getLastUpdated(ChannelType.TV), cache.getLastUpdated(ChannelType.RADIO))
            )
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

    init {
        // Her acilista listeleri guncelle ve linkleri mevcut agda kontrol et
        refreshAndVerifyAll()
    }

    fun selectTab(tab: ChannelType) {
        _uiState.update { it.copy(selectedTab = tab, selectedCategory = "Tümü") }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun toggleHttpsOnlyMode(enabled: Boolean) {
        cache.setHttpsOnlyMode(enabled)
        _uiState.update { it.copy(httpsOnlyMode = enabled) }
    }

    fun toggleShowOnlyOnline(enabled: Boolean) {
        _uiState.update { it.copy(showOnlyOnline = enabled) }
    }

    fun toggleFavorite(channelId: String) {
        _uiState.update { state ->
            val updated = state.favorites.toMutableSet()
            if (!updated.add(channelId)) {
                updated.remove(channelId)
            }
            cache.saveFavorites(updated)
            state.copy(favorites = updated)
        }
    }

    fun selectChannel(channel: Channel) {
        _uiState.update { it.copy(currentChannel = channel, playerError = null) }
    }

    fun stopPlayback() {
        _uiState.update { it.copy(currentChannel = null, isPlaying = false, playerError = null) }
    }

    fun updatePlaybackState(playing: Boolean, error: String? = null) {
        _uiState.update { it.copy(isPlaying = playing, playerError = error) }
    }

    fun refreshAndVerifyAll() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    errorMessage = null,
                    checkProgressText = "Güncel Türkiye TV ve radyo listeleri indiriliyor..."
                )
            }

            val tvResult = repository.fetchTurkishTvChannels()
            val radioResult = repository.fetchTurkishRadioStations()

            val latestTv = tvResult.getOrElse { _uiState.value.tvChannels }
            val latestRadio = radioResult.getOrElse { _uiState.value.radioChannels }

            val errors = mutableListOf<String>()
            if (tvResult.isFailure) errors.add("TV listesi güncellenemedi (önbellek kullanılıyor)")
            if (radioResult.isFailure) errors.add("Radyo listesi güncellenemedi (önbellek kullanılıyor)")

            _uiState.update {
                it.copy(
                    tvChannels = latestTv.map { ch -> ch.copy(status = LinkStatus.CHECKING) },
                    radioChannels = latestRadio.map { ch -> ch.copy(status = LinkStatus.CHECKING) },
                    errorMessage = errors.takeIf { e -> e.isNotEmpty() }?.joinToString(" • ")
                )
            }

            // Kurum agi modu aciksa oncelikle HTTPS (443) linklerini kontrol et
            val httpsOnly = _uiState.value.httpsOnlyMode
            val tvToCheck = if (httpsOnly) latestTv.filter { it.isHttps443 } else latestTv
            val radioToCheck = if (httpsOnly) latestRadio.filter { it.isHttps443 } else latestRadio

            val total = tvToCheck.size + radioToCheck.size
            var checkedCount = 0
            var onlineCount = 0

            val checkedTvMap = mutableMapOf<String, Channel>()
            val checkedRadioMap = mutableMapOf<String, Channel>()

            repository.checkChannelsHealth(tvToCheck) { checked ->
                synchronized(checkedTvMap) {
                    checkedTvMap[checked.streamUrl] = checked
                    checkedCount++
                    if (checked.status == LinkStatus.ONLINE) onlineCount++
                }
                if (checkedCount % 5 == 0 || checkedCount == total) {
                    _uiState.update { state ->
                        state.copy(
                            tvChannels = latestTv.map { ch ->
                                checkedTvMap[ch.streamUrl] ?: ch
                            },
                            checkProgressText = "Bağlantılar kontrol ediliyor: $checkedCount/$total ($onlineCount aktif)"
                        )
                    }
                }
            }

            repository.checkChannelsHealth(radioToCheck) { checked ->
                synchronized(checkedRadioMap) {
                    checkedRadioMap[checked.streamUrl] = checked
                    checkedCount++
                    if (checked.status == LinkStatus.ONLINE) onlineCount++
                }
                if (checkedCount % 5 == 0 || checkedCount == total) {
                    _uiState.update { state ->
                        state.copy(
                            radioChannels = latestRadio.map { ch ->
                                checkedRadioMap[ch.streamUrl] ?: ch
                            },
                            checkProgressText = "Bağlantılar kontrol ediliyor: $checkedCount/$total ($onlineCount aktif)"
                        )
                    }
                }
            }

            val finalTv = latestTv.map { ch -> checkedTvMap[ch.streamUrl] ?: ch }
                .sortedWith(compareBy<Channel> { it.status != LinkStatus.ONLINE }.thenBy { it.name })
            val finalRadio = latestRadio.map { ch -> checkedRadioMap[ch.streamUrl] ?: ch }
                .sortedWith(compareBy<Channel> { it.status != LinkStatus.ONLINE }.thenBy { it.name })

            cache.saveChannels(ChannelType.TV, finalTv)
            cache.saveChannels(ChannelType.RADIO, finalRadio)

            _uiState.update {
                it.copy(
                    tvChannels = finalTv,
                    radioChannels = finalRadio,
                    isRefreshing = false,
                    checkProgressText = "Kontrol tamamlandı: $onlineCount/$total yayın bu ağda erişilebilir",
                    lastSyncTimeText = formatTimestamp(System.currentTimeMillis())
                )
            }
        }
    }

    private fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "Henüz güncellenmedi"
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
        return formatter.format(Date(millis))
    }
}
