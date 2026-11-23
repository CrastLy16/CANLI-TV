package com.canlitvradyo.tr.data

import android.content.Context
import com.canlitvradyo.tr.model.Channel
import com.canlitvradyo.tr.model.ChannelType
import com.canlitvradyo.tr.model.LinkStatus
import org.json.JSONArray
import org.json.JSONObject

class ChannelCache(context: Context) {

    private val prefs = context.getSharedPreferences("canli_tv_radyo_cache", Context.MODE_PRIVATE)

    fun saveChannels(type: ChannelType, channels: List<Channel>) {
        val array = JSONArray()
        for (ch in channels) {
            val obj = JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("streamUrl", ch.streamUrl)
                put("type", ch.type.name)
                put("category", ch.category)
                put("logoUrl", ch.logoUrl ?: "")
                put("homepageUrl", ch.homepageUrl ?: "")
                put("isHttps443", ch.isHttps443)
                put("status", ch.status.name)
            }
            array.put(obj)
        }
        prefs.edit()
            .putString(keyFor(type), array.toString())
            .putLong("updated_${type.name}", System.currentTimeMillis())
            .apply()
    }

    fun loadChannels(type: ChannelType): List<Channel> {
        val raw = prefs.getString(keyFor(type), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<Channel>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Channel(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        streamUrl = obj.optString("streamUrl"),
                        type = ChannelType.valueOf(obj.optString("type", type.name)),
                        category = obj.optString("category", "Genel"),
                        logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
                        homepageUrl = obj.optString("homepageUrl").takeIf { it.isNotBlank() },
                        isHttps443 = obj.optBoolean("isHttps443", false),
                        status = runCatching {
                            LinkStatus.valueOf(obj.optString("status", LinkStatus.UNCHECKED.name))
                        }.getOrDefault(LinkStatus.UNCHECKED)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLastUpdated(type: ChannelType): Long = prefs.getLong("updated_${type.name}", 0L)

    fun loadFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun saveFavorites(ids: Set<String>) {
        prefs.edit().putStringSet("favorites", ids.toSet()).apply()
    }

    fun isHttpsOnlyMode(): Boolean = prefs.getBoolean("https_only_mode", true)

    fun setHttpsOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean("https_only_mode", enabled).apply()
    }

    private fun keyFor(type: ChannelType): String = "channels_${type.name}"
}
