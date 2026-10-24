# 星滑相册 (StarSwipe)

<p align="center">
  <img src="assets/logo.png" width="96" height="96" alt="StarSwipe Logo" />
</p>

<p align="center">
  <strong>基于 Jetpack Compose 的 Android 本地相册卡片式整理工具</strong>
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/15"><img src="https://img.shields.io/badge/Android-15%20Ready%20(API%2035)-3DDC84?logo=android&logoColor=white" alt="Android 15"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-2024.10.00-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://m3.material.io/"><img src="https://img.shields.io/badge/Material%20Design-3-green" alt="Material 3"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

---

## 项目简介

手机里的照片越攒越多，在传统网格视图里逐张勾选清理非常费眼。**星滑相册 (StarSwipe)** 采用了类似 Tinder 的卡片滑动交互，把照片筛选变成“翻牌”操作，顺手解决相似连拍清理、空文件夹清理和拍摄足迹回顾的问题。

应用**完全离线运行**，不申请网络权限，所有相似度计算、EXIF 解析与文件整理均在设备本地完成。

---

## 功能特性

### 1. 卡片式滑动整理
- **直觉化手势**：上滑移入待删除池（顶部垃圾桶实时计数），左/右/下滑保留照片，误操作可随时撤销。
- **详情与实况预览**：在卡片页可呼出半屏抽屉查看完整 EXIF 参数，支持直接播放 LivePhoto 实况照片。
- **视觉细节**：三层卡片堆叠与弹簧物理动效（Spring），背景随当前卡片动态高斯模糊并延伸至状态栏与底栏。

### 2. 相似照片聚类与清理
- **时间窗口 + 并查集聚类**：基于 180 秒时间滑动窗口与并查集（DSU）算法，除了连拍，还能识别几分钟内不同角度、不同构图的抓拍。
- **本地指纹缓存**：将计算后的 dHash 特征持久化为二进制索引（`similar_dhash_v2.bin`），聚类结果同步至本地 JSON。仅对新增照片做增量计算，避免每次打开应用重复解码 Bitmap。
- **保留建议**：综合分辨率、文件体积与长宽尺寸，在相似照片组中自动勾选画质最好的一张作为默认保留项。

### 3. 空相簿安全清理
- **限定扫描范围**：仅扫描 `DCIM/` 与 `Pictures/`，不触碰 `Download/`、`Movies/` 等目录。
- **目录过滤机制**：内置常见缓存名黑名单（`cache`、`temp`、`stickers`、`.trash` 等）及哈希/UUID/时间戳正则过滤，自动跳过包含 `.nomedia` 的文件夹，避免误删微信、小红书等第三方应用的内部资源。
- **透明化确认**：清理前弹窗列出所有待删空文件夹路径，确认清理后自动同步 `MediaStore` 刷新系统媒体库。

### 4. 拍摄足迹与 EXIF 解析
- **地点归档**：并发提取照片与视频（支持 JPEG、HEIC、MP4、MOV）中的 GPS 坐标，按省份与城市归类。
- **参数展示**：解析并展示设备型号、光圈、快门时间、ISO、焦距及拍摄详细地址。

### 5. 性能与细节优化
- **页面保活**：底部导航栏支持 Tab 状态常驻，切换页面时通过水平视差动画过渡，避免重复加载导致白屏。
- **列表流畅度优化**：缩略图加载开启 Coil `allowRgb565` 降低显存占用；核心 UI 状态模型标记 `@Immutable` 减少 Compose 无效重组；优化 Cursor 遍历逻辑以降低滚动时的 GC 频率。

---

## 技术栈

| 模块 | 采用方案 | 用途说明 |
| :--- | :--- | :--- |
| **编程语言** | Kotlin 2.0.21 | 协程异步处理、数据流管理 |
| **UI 框架** | Jetpack Compose (BOM 2024.10) | 声明式 UI、自定义手势与转场动画 |
| **设计规范** | Material Design 3 | 适配深色模式与动态取色 |
| **架构模式** | Clean Architecture + MVI/MVVM | UI -> ViewModel -> Repository 分层 |
| **图片加载** | Coil 2.7.0 (Compose & Video) | 图片加载、RGB_565 内存优化、视频抽帧 |
| **媒体接口** | MediaStore + ExifInterface | 兼容 Android 8.0 ~ 15 分区存储、读取 EXIF |
| **视频播放** | Media3 (ExoPlayer 1.4.1) | LivePhoto 实况预览与视频播放 |
| **本地存储** | Room 2.6.1 + DataStore | 整理记录持久化、已跳过照片追踪、偏好设置 |

---

## 下载安装

Releases 页面提供了按 CPU 架构（ABI）分包的 APK，建议优先下载与设备匹配的包以减小体积：

| 架构版本 | 文件名格式 | 适用设备 |
| :--- | :--- | :--- |
| **arm64-v8a** *(推荐)* | `StarSwipe_v1.0.0_release_arm64-v8a.apk` | 绝大多数主流 Android 手机（64 位 ARM） |
| **armeabi-v7a** | `StarSwipe_v1.0.0_release_armeabi-v7a.apk` | 早期 32 位旧手机或部分嵌入式设备 |
| **x86_64** | `StarSwipe_v1.0.0_release_x86_64.apk` | 64 位 Android 模拟器（MuMu、蓝叠、AVD 等） |
| **x86** | `StarSwipe_v1.0.0_release_x86.apk` | 32 位 Android 模拟器 |
| **Universal (通用包)** | `StarSwipe_v1.0.0_release_universal.apk` | 包含全部架构，不确定设备架构时可下载此包 |

---

## 本地构建

### 环境要求
- **Android Studio**：Ladybug (2024.2.1) 及以上
- **JDK**：Java 21
- **Android SDK**：Compile SDK 35，Min SDK 26

### 构建命令

```bash
# 克隆仓库
git clone [https://github.com/hoshino-sys/StarSwipe.git](https://github.com/hoshino-sys/StarSwipe.git)
cd StarSwipe

# 运行单元测试
./gradlew testDebugUnitTest

# 打包 Debug APK
./gradlew assembleDebug

# 打包 Release APK (包含各 ABI 分包与通用包)
./gradlew assembleRelease

```

生成的 APK 位于 `app/build/outputs/apk/debug/` 或 `release/` 目录下。

---

## 免责声明

1. **隐私安全**：本应用纯本地运行，无联网逻辑，不会上传或收集任何个人照片、视频及隐私数据。
2. **空相簿清理风险提示**：尽管应用已内置目录白名单、`.nomedia` 过滤及二次确认机制，但由于各厂商定制 ROM 及第三方 App 存储行为存在差异，**执行清理前请务必在弹窗中核对待删目录名称**。因未核对目录或第三方应用违规存放数据而导致的文件误删，开发者不承担相关责任。
3. **媒体文件删除**：卡片整理过程中的删除操作均通过系统标准接口由用户授权执行，请谨慎操作并做好日常数据备份。

---

## 许可证

本项目基于 [Apache License 2.0](https://www.google.com/search?q=LICENSE) 开源，欢迎提交 Issue 或 PR。