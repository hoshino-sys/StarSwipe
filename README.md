# 星滑相册 (StarSwipe)

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="StarSwipe Logo" />
</p>

<p align="center">
  <strong>优雅 · 灵动 · 极速 · 纯本地运行的 Android 现代相册卡片整理利器</strong>
</p>

<p align="center">
  <a href="https://developer.android.com/about/versions/15"><img src="https://img.shields.io/badge/Android-15%20Ready%20(API%2035)-3DDC84?logo=android&logoColor=white" alt="Android 15"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack%20Compose-2024.10.00-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://m3.material.io/"><img src="https://img.shields.io/badge/Material%20Design-3-green" alt="Material 3"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

---

## 🌟 项目简介 (Overview)

**星滑相册 (StarSwipe)** 是一款专为 Android 设计的高颜值、高性能现代相册整理与瘦身工具。通过经典且跟手的**卡片滑动交互（Tinder-style Cards）**，让杂乱相册的筛选整理变得如同翻牌般畅快淋漓。

应用坚持**100% 纯本地离线运行**理念，不包含任何网络上传逻辑，绝不收集用户的任何相册照片与个人隐私，所有图像相似度比对、EXIF 元数据提取与空相簿清理均在设备本地疾速完成。

---

## ✨ 核心特性 (Key Features)

### 🎴 1. 真实物理质感卡片整理流
- **三层立体叠纸动效**：底层投影羽化与立体层次感，滑动顶层时底层自然解耦静止，还原抽牌物理触感。
- **动态沉浸背景**：当前卡片放大高斯模糊，100% 贯穿系统状态栏与悬浮毛玻璃底栏，随手势滑动动态渐变清晰。
- **跟手物理手势**：
  - **上滑**：弹性移入待删除池，顶部垃圾桶动态更新红点计数，并可一键呼出超调半屏抽屉查看完整参数与实况播放；
  - **下滑/左滑/右滑**：平滑飞出并标记保留，随时支持一键撤销。
- **弹簧物理分类菜单**：分类选择按钮接入 `Spring.DampingRatioMediumBouncy` 弹簧物理曲线，展开回弹灵动饱满。

### 👥 2. 高召回率相似照片引擎
- **180 秒时间滑动窗口聚类**：突破传统连拍限制，结合并查集（DSU）算法，数十秒甚至数分钟内不同构图、多角度的相似抓拍一并识别。
- **持久化二进制指纹缓存**：建立 `similar_dhash_v2.bin` 二进制指纹索引，二次比对完全免去 Bitmap 解码与缩放计算，百张照片毫秒级完成。
- **0 秒冷启动瞬时开屏**：磁盘自动同步 `similar_groups_<year>.json`，应用冷启动及切换年份瞬时呈现，后台静默增量比对。
- **智能最佳推荐**：综合对比文件体积、分辨率与长宽尺寸，自动为相似组挑选出最清晰、最高画质的照片作为建议保留项。
- **120Hz 极速列表**：Coil `allowRgb565` 显存优化，结合列表局部滚动状态隔离，彻底杜绝上下滑动卡顿。

### 📁 3. 精准相簿识别与空相簿安全清理
- **严格限定相册根目录**：扫描范围严密锁定在 `Pictures/` 与 `DCIM/`，绝对不把 `Download/` 下载文件或 `Movies/` 视频缓存误判为相簿。
- **工业级非相簿黑名单**：全面拦截 `cache`、`temp`、`log`、`data`、`res`、`assets`、`stickers`、`.trash` 及 16 位以上 Hex 哈希、UUID、纯数字时间戳命名目录。
- **强制排除 `.nomedia` 私有目录**：严密保护微信、小红书等第三方 App 的私有表情、贴纸与内部数据。
- **二次弹窗与透明化清理**：一键清理前弹出确认框，完整列出检测到的空相簿名称；清理后自动同步 MediaStore 数据库并刷新系统媒体索引。

### 🗺️ 4. 拍摄足迹与 EXIF 深度解析
- **全相册覆盖**：深度并发解析数千张照片与视频的拍摄地理坐标，按省份与城市自动聚类归档。
- **全维度元数据展示**：设备厂商、具体型号、光圈（f/number）、曝光时间、ISO、焦距、拍摄时间与详细地址一览无余。
- **多格式支持**：支持普通 JPEG/HEIC 照片、LivePhoto 实况照片动态预览，以及 MP4/MOV 视频容器的地理坐标提取。

### ⚡ 5. 极致架构与体验打磨
- **Keep-Alive 智能页面保活**：底部双轨悬浮毛玻璃导航栏，Tab 页面首次懒加载后常驻保活，切换时带有前后方向感知的**水平弹性视差滑动转场**，0 白屏 0 重新挂载。
- **零 GC 停顿**：优化游标遍历与临时对象累加器，消除滚动卡顿。
- **全量模型稳定性强化**：核心数据结构全面标定 `@Immutable`，自动触发 Compose 智能跳过重组。

---

## 🏗️ 技术架构 (Tech Stack)

| 模块 / 层面 | 技术方案 | 说明 |
| :--- | :--- | :--- |
| **编程语言** | Kotlin 2.0.21 | 强类型、空安全、协程 Coroutines |
| **UI 框架** | Jetpack Compose (BOM 2024.10) | 全声明式响应式 UI，无传统 XML 布局 |
| **设计规范** | Material Design 3 (M3) | 深度适配明暗主题（Dark Mode）与动态色彩 |
| **架构模式** | Clean Architecture + MVI/MVVM | UI Layer -> Domain/ViewModel -> Data/Repository |
| **图片加载** | Coil 2.7.0 (Compose & Video) | 硬件位图解码、RGB_565 内存优化、视频缩略图提取 |
| **媒体引擎** | MediaStore + AndroidX ExifInterface | 分区存储兼容（API 26 ~ API 35）、EXIF GPS 元数据提取 |
| **视频播放** | Media3 (ExoPlayer 1.4.1) | 实况照片轻量视频流与视频媒体预览 |
| **本地存储** | Room 2.6.1 + DataStore Preferences | 整理记录持久化、跳过历史追踪、用户偏好配置 |

---

## 📦 安装包下载 (Downloads & Releases)

本项目已全面开启 **ABI 架构分包**，针对不同设备的处理器架构分别提供了极致优化的安装包，体积更精简、运行更流畅：

| 架构分包 | 文件名格式 | 适用场景 / 设备类型 |
| :--- | :--- | :--- |
| **arm64-v8a** *(推荐)* | `StarSwipe_v1.0.0_release_arm64-v8a.apk` | 绝大多数近五年主流 Android 手机（64 位 ARM） |
| **armeabi-v7a** | `StarSwipe_v1.0.0_release_armeabi-v7a.apk` | 早期老旧 32 位 Android 手机或特定嵌入式设备 |
| **x86_64** | `StarSwipe_v1.0.0_release_x86_64.apk` | 64 位 PC Android 模拟器（如 MuMu、夜神、蓝叠、AVD） |
| **x86** | `StarSwipe_v1.0.0_release_x86.apk` | 32 位 PC Android 模拟器 |
| **Universal (通用包)** | `StarSwipe_v1.0.0_release_universal.apk` | 聚合所有架构库，可在任何支持设备上直接安装运行 |

> 💡 **提示**：若不确定自己设备的芯片架构，请直接下载 **Universal (通用包)** 或 **arm64-v8a**。

---

## 🛠️ 本地编译与构建 (Build & Run)

### 环境要求
- **Android Studio**：Ladybug (2024.2.1) 或更高版本
- **JDK 版本**：Java 21 (Eclipse Temurin / OpenJDK 21)
- **Android SDK**：Compile SDK 35，Min SDK 26

### 命令行构建命令

```bash
# 克隆仓库
git clone https://github.com/hoshino-sys/StarSwipe.git
cd StarSwipe

# 编译 Debug 源码
./gradlew compileDebugSources

# 运行全量单元测试
./gradlew testDebugUnitTest

# 打包全架构 Release APK (包含各 ABI 独立包与 Universal 包)
./gradlew assembleRelease

# 打包全架构 Debug APK
./gradlew assembleDebug
```

编译生成的安装包位于：
- Release 包：`app/build/outputs/apk/release/`
- Debug 包：`app/build/outputs/apk/debug/`

---

## ⚖️ 免责声明 (Disclaimer)

使用本软件即代表您充分阅读并自愿接受以下免责条款：

1. **离线安全承诺**：本软件所有功能均在您的手机本地离线运行，绝不收集或上传任何照片、视频及个人隐私信息。
2. **空相簿清理责任豁免**：
   - 本软件提供“空相簿清理”功能，旨在识别并辅助清理相册中无照片、视频的空文件夹。虽然本软件已内置多级系统保护目录过滤、`.nomedia` 排除及二次确认弹窗，但鉴于 Android 系统厂商定制 ROM 的高度碎片化、特殊存储挂载策略以及部分第三方 App 违规存放配置数据的不可预测性；
   - **用户在点击“确认清理”前，必须自行核实确认相关目录名称与内容。因使用本软件清理空相簿、删除文件夹而直接或间接导致的任何非媒体文件丢失、第三方数据损坏、系统异常或其他损失，星滑相册及其开发者不承担任何形式的直接、间接、连带法律责任或赔偿责任。请务必在进行重大清理前做好重要数据备份。**
3. **媒体删除自主性**：在卡片滑动整理过程中的删除动作均由用户自主决定并授权底层系统完成，因个人误操作导致的媒体丢失风险由用户自行承担。

---

## 📄 开源许可证 (License)

本项目采用 [Apache License 2.0](LICENSE) 协议开源。欢迎提交 Issue 或 Pull Request 共同改进！
