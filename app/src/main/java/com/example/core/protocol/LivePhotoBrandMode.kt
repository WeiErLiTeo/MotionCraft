package com.example.core.protocol

enum class LivePhotoBrandMode(val id: Int, val displayName: String, val description: String) {
    FUSION(0, "通用", "全平台通用 / 抖音 / 谷歌相册"),
    OPPO(1, "OPPO / OnePlus", "ColorOS / OxygenOS 协议"),
    XIAOMI(2, "小米", "HyperOS 协议");

    companion object {
        fun fromId(id: Int): LivePhotoBrandMode = values().find { it.id == id } ?: FUSION
    }
}
