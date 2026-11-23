package com.canlitvradyo.tr.model

enum class ChannelType {
    TV,
    RADIO
}

enum class LinkStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    UNCHECKED
}

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val type: ChannelType,
    val category: String = "Genel",
    val logoUrl: String? = null,
    val homepageUrl: String? = null,
    val isHttps443: Boolean = false,
    val status: LinkStatus = LinkStatus.UNCHECKED,
    val latencyMs: Long? = null
)
