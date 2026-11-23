package com.canlitvradyo.tr.data

import com.canlitvradyo.tr.model.Channel
import com.canlitvradyo.tr.model.ChannelType
import com.canlitvradyo.tr.model.LinkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

class ChannelRepository {

    companion object {
        // Herkese acik, HTTPS (443) uzerinden guncel Turkiye TV listesi (iptv-org)
        const val TV_PLAYLIST_URL = "https://iptv-org.github.io/iptv/countries/tr.m3u"

        // Radio Browser API HTTPS (443) sunucu yedekleri
        val RADIO_API_MIRRORS = listOf(
            "https://de1.api.radio-browser.info/json/stations/bycountrycodeexact/TR?hidebroken=true&order=clickcount&reverse=true&limit=150",
            "https://nl1.api.radio-browser.info/json/stations/bycountrycodeexact/TR?hidebroken=true&order=clickcount&reverse=true&limit=150",
            "https://at1.api.radio-browser.info/json/stations/bycountrycodeexact/TR?hidebroken=true&order=clickcount&reverse=true&limit=150"
        )

        const val USER_AGENT = "CanliTvRadyoTR/1.0 (Android)"
    }

    // Sistem ag/proxy ayarlarina uyumlu standart HTTPS istemcisi
    val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(ProxySelector.getDefault())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Link saglik kontrolu icin kisa zaman asimli istemci
    private val probeClient: OkHttpClient = sharedHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun fetchTurkishTvChannels(): Result<List<Channel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(TV_PLAYLIST_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            sharedHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("TV listesi alınamadı (HTTP ${response.code})")
                    )
                }
                val body = response.body?.string().orEmpty()
                val parsed = M3uParser.parse(body, ChannelType.TV)
                if (parsed.isEmpty()) {
                    Result.failure(IllegalStateException("TV listesi boş döndü"))
                } else {
                    Result.success(parsed)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTurkishRadioStations(): Result<List<Channel>> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (mirrorUrl in RADIO_API_MIRRORS) {
            try {
                val request = Request.Builder()
                    .url(mirrorUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                sharedHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = IllegalStateException("Radyo API HTTP ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    val jsonArray = JSONArray(body)
                    val stations = mutableListOf<Channel>()
                    val seen = HashSet<String>()

                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val lastCheckOk = item.optInt("lastcheckok", 0) == 1
                        val sslError = item.optInt("ssl_error", 0) == 1
                        if (!lastCheckOk || sslError) continue

                        val resolvedUrl = item.optString("url_resolved").ifBlank {
                            item.optString("url")
                        }.trim()
                        if (resolvedUrl.isBlank() || !seen.add(resolvedUrl)) continue

                        val name = item.optString("name").trim().ifEmpty { "İsimsiz Radyo" }
                        val tags = item.optString("tags").split(',')
                            .firstOrNull { it.isNotBlank() }
                            ?.trim()
                            ?.replaceFirstChar { it.uppercase() }
                            ?: "Radyo"

                        stations.add(
                            Channel(
                                id = item.optString("stationuuid").ifBlank { "radio_$i" },
                                name = name,
                                streamUrl = resolvedUrl,
                                type = ChannelType.RADIO,
                                category = tags,
                                logoUrl = item.optString("favicon").takeIf { it.startsWith("https://", true) },
                                homepageUrl = item.optString("homepage").takeIf { it.startsWith("https://", true) },
                                isHttps443 = M3uParser.isStandardHttps443(resolvedUrl)
                            )
                        )
                    }

                    if (stations.isNotEmpty()) {
                        return@withContext Result.success(stations)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        Result.failure(lastError ?: IllegalStateException("Radyo sunucularına ulaşılamadı"))
    }

    /**
     * Kanallarin mevcut agda (kurum Wi-Fi veya mobil veri) erisilebilirligini paralel kontrol eder.
     */
    suspend fun checkChannelsHealth(
        channels: List<Channel>,
        maxConcurrency: Int = 12,
        onChannelChecked: (Channel) -> Unit
    ): List<Channel> = coroutineScope {
        val semaphore = Semaphore(maxConcurrency)
        channels.map { channel ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val checked = probeSingleChannel(channel)
                    onChannelChecked(checked)
                    checked
                }
            }
        }.awaitAll()
    }

    private fun probeSingleChannel(channel: Channel): Channel {
        val start = System.currentTimeMillis()
        // 1) Hizli HEAD istegi
        val headOk = tryRequest(channel.streamUrl, useHead = true)
        if (headOk) {
            return channel.copy(
                status = LinkStatus.ONLINE,
                latencyMs = System.currentTimeMillis() - start
            )
        }
        // 2) Bazi akis sunuculari HEAD desteklemez; 1 baytlik GET deneyelim
        val getOk = tryRequest(channel.streamUrl, useHead = false)
        return channel.copy(
            status = if (getOk) LinkStatus.ONLINE else LinkStatus.OFFLINE,
            latencyMs = if (getOk) System.currentTimeMillis() - start else null
        )
    }

    private fun tryRequest(url: String, useHead: Boolean): Boolean {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)

            if (useHead) {
                builder.head()
            } else {
                builder.get().header("Range", "bytes=0-1")
            }

            probeClient.newCall(builder.build()).execute().use { response ->
                response.code in 200..299 || response.code == 206
            }
        } catch (_: Exception) {
            false
        }
    }
}
