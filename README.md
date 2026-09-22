# Better AM

为 Apple Music Android 添加液态玻璃底部导航栏与迷你播放器的 LSPosed 模块，基于 [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 实现。

## 功能

- 液态玻璃底部导航栏，支持折射、模糊、滑动与按压动画。
- 胶囊形迷你播放器，与 Apple Music 原有页面和播放操作配合工作。
- 管理界面参考 [Shizuku Manager](https://github.com/RikkaApps/Shizuku) 的布局。
- 显示框架连接、模块作用域与 Hook 版本信息，支持查看、复制和清空运行日志。
- 支持简体中文、繁体中文、English、日本語及跟随系统语言。
- 支持浅色、深色、跟随系统、纯黑背景和系统动态配色。
- 管理界面适配系统手势导航，设置页与日志页支持预测性返回。

## 使用环境

| 项目 | 要求 |
| --- | --- |
| Android | Android 11（API 30）及以上 |
| 框架 | 支持现代 libxposed API 102 的 LSPosed 环境 |
| 目标应用 | Apple Music，包名 `com.apple.android.music` |
| 当前适配基线 | Apple Music 6.5.3（1599） |
| 模块包名 | `moe.polariss.betteram` |

Hook 依赖 Apple Music 的内部视图结构。升级目标应用后，可能需要同步调整模块。系统动态配色需要 Android 12 及以上；预测性返回动画需要支持该功能的 Android 版本及手势导航设置。

## 安装与启用

1. 构建并安装 Better AM APK。
2. 在 LSPosed 中启用 Better AM。
3. 在模块作用域中勾选 Apple Music。
4. 强制停止 Apple Music，然后重新打开。
5. 打开 Better AM 查看连接状态和运行日志。

“已 Hook 到”来自目标进程单独上报的版本记录，表示模块曾在当前安装的 Apple Music 中执行，并不表示所有玻璃效果均已安装成功。该记录独立于日志保存，清空日志不会移除；目标应用更新或重新安装后需要重新上报。

如果更新模块后界面没有变化，先确认 LSPosed 是否仍指向当前安装的模块 APK。Android 重新安装应用时可能改变 `/data/app/…` 路径；过期的模块路径会导致注入失效。排查时应先确认模块已经加载，再检查渲染逻辑。

## 从源码构建

需要 JDK 21、Android SDK Platform 37 和可用的 Android SDK 路径。通过 `ANDROID_HOME` 或本地 `local.properties` 配置 SDK；Gradle 使用仓库内的 Wrapper。

```sh
./gradlew :app:assembleRelease
```

macOS 可使用 Android Studio 自带的 JDK：

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleRelease
```

APK 输出位置：

```text
app/build/outputs/apk/release/app-release.apk
```

当前 release 配置启用 R8 和资源压缩，并使用本机 Android debug key 签名。正式分发时应使用自己保管的固定签名密钥；仓库不包含签名密钥。不同机器生成的 debug 签名通常不能直接覆盖彼此安装的版本。

安装到已连接的开发设备：

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

安装后重新确认 LSPosed 中的模块路径、启用状态与作用域，再重启 Apple Music。

## 项目结构

源码目录为 `app/src/main/java/dev/polaris/betteram/`，Kotlin 包名为 `moe.polariss.betteram`。

| 路径 | 内容 |
| --- | --- |
| `hook/` | libxposed 入口、宿主视图扫描与布局适配 |
| `ui/LiquidNavigationGlass.kt` | 宿主画面采样和 Backdrop 渲染桥接 |
| `ui/official/` | AndroidLiquidGlass catalog 组件的适配版本 |
| `ui/ModuleScreen.kt`、`ui/SettingsPage.kt` | 管理首页、日志与设置界面 |
| `settings/` | 管理界面语言和外观偏好 |
| `status/` | 框架连接和作用域读取 |
| `log/` | 跨进程运行日志 |
| `app/src/main/res/` | 界面资源和翻译 |
| `third_party/` | 第三方许可与来源说明 |

## 开发与反馈

开发约定和设备验证步骤见 [AGENTS.md](AGENTS.md)。目前没有自动化测试套件，Hook 与视觉效果需要在配置了 LSPosed 的真实设备上验证。

反馈问题时请提供 Android 版本、设备型号、Apple Music 版本、框架版本、复现步骤及相关日志。公开日志或截图前，请先检查其中是否包含个人信息。请勿提交 Apple Music APK、反编译文件或签名密钥。

## 许可与致谢

Better AM 原创代码采用 [MIT License](LICENSE)。第三方代码保留原有许可，详见 [第三方说明](third_party/README.md)。

- [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)：玻璃效果和 catalog 组件。
- [libxposed](https://github.com/libxposed)：现代 Xposed 模块 API 与服务接口。
- [Shizuku](https://github.com/RikkaApps/Shizuku)：管理界面的布局参考。
- [AndroidX / Jetpack Compose](https://developer.android.com/jetpack/compose)：管理界面与渲染基础。

本项目与 Apple Inc. 无关联，未包含 Apple Music 的应用包、代码或素材。Apple Music 名称归其权利人所有。
