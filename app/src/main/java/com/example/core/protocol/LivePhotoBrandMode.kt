package com.example.core.protocol

enum class LivePhotoBrandMode(val id: Int, val displayName: String, val description: String) {
    FUSION(0, "通用", "全平台通用 / 抖音 / 谷歌相册"),
    OPPO(1, "OPPO / OnePlus", "ColorOS / OxygenOS 协议"),
    XIAOMI(2, "小米", "HyperOS 协议"),
    SAMSUNG(3, "三星", "One UI (SEF MotionPhoto_Data 协议)"),
    VIVO(4, "vivo / iQOO", "OriginOS (lpex + vivo uuid 协议)"),
    GOOGLE(8, "Google 原生", "Google MotionPhoto + MicroVideo 双规范");

    companion object {
        fun fromId(id: Int): LivePhotoBrandMode = entries.find { it.id == id } ?: FUSION
    }
}
