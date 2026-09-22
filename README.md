# Better AM

为 Apple Music Android 添加液态玻璃导航栏与迷你播放器的 LSPosed 模块，基于 [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 实现。

## 功能

- 液态玻璃底部导航栏，支持折射、模糊、滑动与按压动画。
- 胶囊形迷你播放器，与 Apple Music 播放操作配合工作。
- 管理界面参考 [Shizuku Manager](https://github.com/RikkaApps/Shizuku) 布局。
- 显示框架连接、模块作用域与 Hook 版本信息，可查看、复制、清空运行日志。
- 支持简体中文、繁体中文、English、日本語及跟随系统语言。
- 浅色、深色、跟随系统、纯黑、系统动态配色。

## 使用环境

| 项目 | 要求 |
| --- | --- |
| Android | 11（API 30）及以上 |
| 框架 | 支持现代 libxposed API 102 的 LSPosed 环境 |
| 目标应用 | Apple Music（`com.apple.android.music`） |
| 适配基线 | Apple Music 6.5.3（1599） |
| 模块包名 | `moe.polariss.betteram` |

Hook 依赖 Apple Music 内部视图结构，升级目标应用后可能需要同步调整。系统动态配色需 Android 12 及以上。

## 安装与启用

1. 构建并安装 Better AM APK。
2. 在 LSPosed 中启用，作用域勾选 Apple Music。
3. 强制停止 Apple Music 并重新打开。

“已 Hook 到”表示模块曾在当前安装的 Apple Music 中执行，不代表所有玻璃效果均已安装成功，也不随日志清空而移除。更新模块后界面无变化时，先确认 LSPosed 仍指向当前模块 APK——重新安装应用可能改变 `/data/app/…` 路径，过期路径会导致注入静默失效。

## 从源码构建

需要 JDK 21 与 Android SDK Platform 37，SDK 路径经 `ANDROID_HOME` 或 `local.properties` 指定。

```sh
./gradlew :app:assembleRelease
```

macOS 可用 Android Studio 自带 JDK：

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleRelease
```

输出：`app/build/outputs/apk/release/app-release.apk`。release 启用 R8 与资源压缩，使用本机 debug key 签名；正式分发请自备固定签名密钥，仓库不含任何密钥。

安装到已连接设备：

```sh
adb install app/build/outputs/apk/release/app-release.apk
```

安装后重新确认 LSPosed 中的模块路径、启用状态与作用域，再重启 Apple Music。

## 项目结构

源码目录 `app/src/main/java/moe/polariss/betteram/`。

| 路径 | 内容 |
| --- | --- |
| `hook/` | libxposed 入口、宿主视图扫描与布局适配 |
| `ui/` | Backdrop 渲染、管理界面（`LiquidNavigationGlass.kt`、`ModuleScreen.kt` 等） |
| `ui/official/` | AndroidLiquidGlass catalog 组件适配版本 |
| `settings/`、`status/`、`log/` | 偏好存储、框架连接、跨进程日志 |
| `app/src/main/res/` | 界面资源与翻译 |
| `third_party/` | 第三方许可与来源说明 |

## 许可与致谢

原创代码采用 [MIT License](LICENSE)；第三方代码保留原许可，见 [third_party/README.md](third_party/README.md)。

- [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)：玻璃效果与 catalog 组件。
- [libxposed](https://github.com/libxposed)：现代 Xposed 模块 API。
- [Shizuku](https://github.com/RikkaApps/Shizuku)：管理界面布局参考。
- AndroidX / Jetpack Compose：管理界面与渲染基础。

本项目与 Apple Inc. 无关联，不含 Apple Music 的应用包、代码或素材。