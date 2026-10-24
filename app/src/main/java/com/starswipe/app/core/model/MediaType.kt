package com.starswipe.app.core.model

enum class MediaType(val title: String) {
    ALL_IMAGES("全部图片"),
    ALL_VIDEOS("全部视频"),
    PHOTO("相机照片"),
    VIDEO("相机录像"),
    LIVE_PHOTO("实况/动态"),
    SCREENSHOT("截屏"),
    SCREEN_RECORDING("录屏"),
    SELFIE("自拍");

    val isVideo: Boolean
        get() = this == ALL_VIDEOS || this == VIDEO || this == SCREEN_RECORDING
}
