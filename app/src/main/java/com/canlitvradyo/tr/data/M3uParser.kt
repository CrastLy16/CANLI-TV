package com.canlitvradyo.tr.data

import com.canlitvradyo.tr.model.Channel
import com.canlitvradyo.tr.model.ChannelType
import java.net.URI

object M3uParser {

    private val logoRegex = Regex("tvg-logo="([^"]*)"")
    private val groupRegex = Regex("group-title="([^"]*)"")
    private val idRegex = Regex("tvg-id="([^"]*)"")

    fun parse(m3uContent: String, type: ChannelType = ChannelType.TV): List<Channel> {
        val channels = mutableListOf<Channel>()
        val seenUrls = HashSet<String>()
        val lines = m3uContent.lines()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup = "Genel"
        var pendingId: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                pendingGroup = translateGroup(
                    groupRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Genel"
                )
                pendingId = idRegex.find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

                val commaIdx = line.lastIndexOf(',')
                pendingName = if (commaIdx != -1 && commaIdx < line.lastIndex) {
                    line.substring(commaIdx + 1).trim()
                } else {
                    "İsimsiz Kanal"
                }
            } else if (!line.startsWith("#")) {
                val url = line
                val name = pendingName
                if (name != null && (url.startsWith("https://", true) || url.startsWith("http://", true))) {
                    if (seenUrls.add(url)) {
                        val id = pendingId ?: "${type.name}_${channels.size}_${url.hashCode()}"
                        channels.add(
                            Channel(
                                id = id,
                                name = cleanChannelName(name),
                                streamUrl = url,
                                type = type,
                                category = pendingGroup,
                                logoUrl = pendingLogo,
                                isHttps443 = isStandardHttps443(url)
                            )
                        )
                    }
                }
                pendingName = null
                pendingLogo = null
                pendingGroup = "Genel"
                pendingId = null
            }
        }
        return channels
    }

    fun isStandardHttps443(url: String): Boolean {
        return try {
            val uri = URI(url)
            val isHttps = uri.scheme.equals("https", ignoreCase = true)
            val port = uri.port
            isHttps && (port == -1 || port == 443)
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanChannelName(raw: String): String {
        return raw
            .replace(Regex("\s*\(\d+p\)"), "")
            .replace(Regex("\s*\[Not 24/7]", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { raw }
    }

    private fun translateGroup(rawGroup: String): String {
        val primary = rawGroup.split(';').firstOrNull()?.trim().orEmpty()
        return when (primary.lowercase()) {
            "news" -> "Haber"
            "general", "undefined", "" -> "Genel"
            "sports", "sport" -> "Spor"
            "music" -> "Müzik"
            "documentary" -> "Belgesel"
            "kids", "children", "animation" -> "Çocuk"
            "movies", "cinema", "series" -> "Sinema"
            "entertainment" -> "Eğlence"
            "business" -> "Ekonomi"
            "culture", "education" -> "Kültür"
            "religious" -> "Dini"
            else -> primary.replaceFirstChar { it.uppercase() }
        }
    }
}
