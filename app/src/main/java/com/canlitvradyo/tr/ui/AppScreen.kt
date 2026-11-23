package com.canlitvradyo.tr.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.canlitvradyo.tr.data.ChannelRepository
import com.canlitvradyo.tr.model.Channel
import com.canlitvradyo.tr.model.ChannelType
import com.canlitvradyo.tr.model.LinkStatus

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Media3 ExoPlayer (OkHttp uzerinden sistem agi ve HTTPS 443 uyumlu)
    val exoPlayer = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(viewModel.repository.sharedHttpClient)
            .setUserAgent(ChannelRepository.USER_AGENT)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                viewModel.updatePlaybackState(isPlaying, null)
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.updatePlaybackState(
                    playing = false,
                    error = "Yayın açılamadı (${error.errorCodeName}). Kurum ağı bu yayını engelliyor veya kaynak geçici olarak kapalı olabilir."
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(state.currentChannel?.streamUrl) {
        val ch = state.currentChannel
        if (ch == null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } else {
            exoPlayer.setMediaItem(MediaItem.fromUri(ch.streamUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    val rawList = if (state.selectedTab == ChannelType.TV) state.tvChannels else state.radioChannels

    val categories = remember(rawList) {
        listOf("Tümü", "Favoriler") + rawList.map { it.category }.distinct().sorted().take(10)
    }

    val filteredList = remember(
        rawList,
        state.searchQuery,
        state.selectedCategory,
        state.httpsOnlyMode,
        state.showOnlyOnline,
        state.favorites
    ) {
        rawList.filter { ch ->
            val matchesHttps = !state.httpsOnlyMode || ch.isHttps443
            val matchesOnline = !state.showOnlyOnline || ch.status == LinkStatus.ONLINE
            val matchesQuery = state.searchQuery.isBlank() ||
                ch.name.contains(state.searchQuery, ignoreCase = true) ||
                ch.category.contains(state.searchQuery, ignoreCase = true)
            val matchesCategory = when (state.selectedCategory) {
                "Tümü" -> true
                "Favoriler" -> ch.id in state.favorites
                else -> ch.category.equals(state.selectedCategory, ignoreCase = true)
            }
            matchesHttps && matchesOnline && matchesQuery && matchesCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Canlı TV & Radyo TR", fontWeight = FontWeight.Bold)
                        if (state.lastSyncTimeText.isNotBlank()) {
                            Text(
                                text = "Son güncelleme: ${state.lastSyncTimeText}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAndVerifyAll() },
                        enabled = !state.isRefreshing
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Linkleri Kontrol Et")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Video oynatici (TV kanali seciliyse ustte sabit)
            val current = state.currentChannel
            if (current != null && current.type == ChannelType.TV) {
                TvPlayerCard(
                    channel = current,
                    exoPlayer = exoPlayer,
                    errorText = state.playerError,
                    onClose = { viewModel.stopPlayback() }
                )
            }

            // TV / Radyo sekmeleri
            TabRow(selectedTabIndex = if (state.selectedTab == ChannelType.TV) 0 else 1) {
                Tab(
                    selected = state.selectedTab == ChannelType.TV,
                    onClick = { viewModel.selectTab(ChannelType.TV) },
                    text = { Text("Canlı TV (${state.tvChannels.size})") },
                    icon = { Icon(Icons.Default.LiveTv, contentDescription = null) }
                )
                Tab(
                    selected = state.selectedTab == ChannelType.RADIO,
                    onClick = { viewModel.selectTab(ChannelType.RADIO) },
                    text = { Text("Radyo (${state.radioChannels.size})") },
                    icon = { Icon(Icons.Default.Radio, contentDescription = null) }
                )
            }

            // Kurum agi uyumluluk cubugu & durum bilgisi
            NetworkStatusBar(
                httpsOnlyMode = state.httpsOnlyMode,
                showOnlyOnline = state.showOnlyOnline,
                progressText = state.checkProgressText,
                errorMessage = state.errorMessage,
                onToggleHttpsOnly = { viewModel.toggleHttpsOnlyMode(it) },
                onToggleOnlineOnly = { viewModel.toggleShowOnlyOnline(it) }
            )

            // Arama kutusu
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("Kanal veya radyo ara...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Kategori filtreleri
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { viewModel.selectCategory(category) },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Kanal listesi
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isRefreshing) {
                            "Kanallar yükleniyor ve bağlantılar test ediliyor..."
                        } else {
                            "Filtrelere uygun kanal bulunamadı. 'Sadece Çalışanlar' veya 'HTTPS 443' filtresini değiştirerek tekrar deneyebilirsiniz."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { "${it.type}_${it.streamUrl}" }) { channel ->
                        ChannelItemCard(
                            channel = channel,
                            isSelected = state.currentChannel?.streamUrl == channel.streamUrl,
                            isFavorite = channel.id in state.favorites,
                            onSelect = { viewModel.selectChannel(channel) },
                            onToggleFavorite = { viewModel.toggleFavorite(channel.id) },
                            onOpenHomepage = channel.homepageUrl?.let { url ->
                                {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                        )
                    }
                }
            }

            // Radyo mini oynatici cubugu
            if (current != null && current.type == ChannelType.RADIO) {
                RadioMiniPlayer(
                    channel = current,
                    isPlaying = state.isPlaying,
                    errorText = state.playerError,
                    onTogglePlay = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onClose = { viewModel.stopPlayback() }
                )
            }
        }
    }
}

@Composable
private fun NetworkStatusBar(
    httpsOnlyMode: Boolean,
    showOnlyOnline: Boolean,
    progressText: String,
    errorMessage: String?,
    onToggleHttpsOnly: (Boolean) -> Unit,
    onToggleOnlineOnly: (Boolean) -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Kurum Ağı (Yalnızca HTTPS 443)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Switch(checked = httpsOnlyMode, onCheckedChange = onToggleHttpsOnly)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sadece bu ağda çalışanları göster",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(checked = showOnlyOnline, onCheckedChange = onToggleOnlineOnly)
            }

            if (progressText.isNotBlank()) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TvPlayerCard(
    channel: Channel,
    exoPlayer: ExoPlayer,
    errorText: String?,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Oynatıcıyı Kapat", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText,
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun ChannelItemCard(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenHomepage: (() -> Unit)?
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (channel.type == ChannelType.TV) Icons.Default.LiveTv else Icons.Default.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = channel.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusBadge(status = channel.status, isHttps443 = channel.isHttps443)
                }
            }

            if (onOpenHomepage != null) {
                IconButton(onClick = onOpenHomepage) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Resmi Web Sitesi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (isFavorite) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: LinkStatus, isHttps443: Boolean) {
    val (label, color) = when (status) {
        LinkStatus.ONLINE -> "Aktif" to Color(0xFF22C55E)
        LinkStatus.OFFLINE -> "Erişilemiyor" to Color(0xFFEF4444)
        LinkStatus.CHECKING -> "Kontrol..." to Color(0xFFF59E0B)
        LinkStatus.UNCHECKED -> (if (isHttps443) "HTTPS" else "HTTP") to Color(0xFF94A3B8)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun RadioMiniPlayer(
    channel: Channel,
    isPlaying: Boolean,
    errorText: String?,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Radio, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = errorText ?: if (isPlaying) "Canlı çalıyor..." else "Duraklatıldı",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Duraklat" else "Oynat"
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Kapat")
            }
        }
    }
}
