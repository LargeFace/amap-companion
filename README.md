# AMap Companion

AMap Companion 是一个用于高德地图车机版广播数据的 Android 悬浮窗辅助应用。
它会监听导航和巡航相关广播，并在可拖动悬浮窗中显示转向提示、红绿灯倒计时、车道信息、目的地、预计到达信息、道路提醒、电子眼提示、速度和部分协议详情。

> 本项目由 AI 编写和维护，代码与文档仍建议结合真实车机环境继续验证。

## 巡航红绿灯定制地图

巡航模式下的多方向红绿灯（例如同一路口同时存在左转和直行倒计时）需要高德地图车机版额外广播 `CameraLightInfoWrapper` 的完整列表。原版高德通常只暴露单个方向字段，AMap Companion 无法稳定获得完整巡航红绿灯序列。

- 定制地图 skill 仓库：<https://github.com/zuo-qirun/amap-cruise-wrapper-skill>
- 定制地图 skill ZIP 镜像：<https://gh-proxy.com/https://github.com/zuo-qirun/amap-cruise-wrapper-skill/archive/refs/heads/master.zip>
- 已改好的高德地图：<https://github.com/zuo-qirun/amap-cruise-wrapper-skill/releases/download/v20260523-cruise-wrapper/amap-auto-cruise-wrapper-20260523.apk>
- 已改好的高德地图镜像：<https://gh.llkk.cc/https://github.com/zuo-qirun/amap-cruise-wrapper-skill/releases/download/v20260523-cruise-wrapper/amap-auto-cruise-wrapper-20260523.apk>

该定制地图会额外发送 `lightsData`、`lightsCount` 和 `clearLights` 广播字段，用于显示和及时清除巡航红绿灯倒计时。

## 主要功能

- 可拖动悬浮窗，点击悬浮窗可打开主界面。
- 支持用户自主选择目标应用包名，当前目标应用列表会过滤 `com.autonavi.*`。
- 支持导航模式和巡航模式状态展示。
- 支持显示高德 AMapAuto 广播协议中的车道信息。
- 使用高德风格车道图标资源渲染复杂车道，避免简单箭头复用导致重叠和难以辨认。
- 红绿灯倒计时使用醒目的胶囊样式，并支持方向相关红绿灯信息；巡航多方向红绿灯需要配合定制地图。
- 支持显示目的地名称、剩余时间、剩余距离、当前道路、速度、道路类型、电子眼和道路提醒等信息。
- 支持进入应用后自动检查更新，发现新版本时展示更新详情，并通过 Android `PackageInstaller` 安装。
- 支持通过滑动条实时调整悬浮窗大小，悬浮窗和内容会同步缩放。

## 构建方式

本项目保持轻量化，没有使用 Gradle，直接通过 Android SDK build-tools 构建。
在 Windows 本机执行：

```powershell
.\build.ps1
```

构建完成后会生成：

```text
amap_companion_signed.apk
```

服务端自动构建时可以通过环境变量覆盖版本号：

```bash
APP_VERSION_CODE=1760000000 APP_VERSION_NAME=20260504-abcdef0 pwsh -NoProfile -ExecutionPolicy Bypass -File ./build.ps1
```

## 自动构建与发布

仓库已配置 GitHub Actions。
每次向 `master` 分支推送代码后，GitHub Actions 会自动：

- 安装 Android SDK 和 build-tools。
- 构建并签名 APK。
- 上传 APK artifact。
- 创建 GitHub Release，并附带可安装的 APK 文件。

也可以在 GitHub Actions 页面手动触发构建。

## 检查更新与自动升级

应用内支持设置更新服务器地址，例如：

```text
http://你的服务器IP:8787/update.json
```

设置后，应用进入主界面会自动检查更新；也可以手动点击“检查更新”。如果服务器返回的 `versionCode` 大于当前应用版本，应用会先展示版本号和更新日志。点击“更新”后会下载 APK、校验 SHA-256，并通过 Android `PackageInstaller` 提交安装。

如果系统要求用户确认安装，应用会自动打开系统确认界面。部分车机 ROM 可能限制普通应用安装未知来源 APK，失败时应用会显示 `PackageInstaller` 返回的状态信息，便于排查权限问题。

服务器端代码位于：

```text
update_server/
```

树莓派 Debian arm64 推荐只负责分发 GitHub Actions 构建出的 Release APK，不在树莓派本地构建。详细部署方式见：

```text
update_server/README.md
```

## 插件市场与本地 DIY 插件

应用支持声明式 `.acplugin` 插件包，用于扩展字体、图标资源、UI 模板和悬浮窗样式。插件包本质是 ZIP，不执行第三方 Java/JS 代码。

最小 `plugin.json`：

```json
{
  "schemaVersion": 1,
  "id": "example.clean.dashboard",
  "name": "简洁仪表风格",
  "versionCode": 1,
  "versionName": "1.0.0",
  "developer": {
    "name": "开发者名称",
    "homepage": "https://example.com"
  },
  "capabilities": ["font", "icons", "ui", "overlayStyle"],
  "entry": {
    "font": "fonts/main.ttf",
    "icons": "icons/icons.json",
    "ui": "ui/dashboard.json",
    "overlayStyle": "ui/dashboard.json"
  },
  "minAppVersionCode": 1,
  "description": "插件简介"
}
```

插件优先安装到公共目录 `/sdcard/amap_companion/plugins/{pluginId}/`；设备未授予存储权限或公共目录不可写时，自动改用应用私有目录 `files/plugins/{pluginId}/`。应用会同时读取两处目录。字体、图标和 UI 模板三类能力可分别混搭启用；同类只会启用一个插件。`overlayStyle` 会作为“悬浮窗样式”列表中的一个新样式出现，选中后按该插件模板渲染。旧的 `/sdcard/amap_companion/diy/` 字体和巡航箭头仍保留为低优先级兼容层。

UI 模板支持固定组件：`column`、`row`、`text`、`image`、`badge`、`turnIcon`、`laneBar`、`trafficLights`、`edog`、`spacer`。文本可绑定 `mode`、`roadName`、`heading`、`turnText`、`turnDistance`、`turnRoad`、`turnIcon`、`eta`、`alert`、`detail`、`limitSpeed`、`currentSpeed`、`cameraType` 和 `raw.keyType`。

示例插件源码见：

```text
plugin_examples/clean_dashboard/
```

## 更新日志

项目更新日志见：

```text
CHANGELOG.md
```

服务器每次自动构建后还会生成面向客户端的：

```text
update_server/public/CHANGELOG.md
```

## 签名说明

当前 APK 使用仓库中的 `debug.keystore` 签名，以便后续构建可以覆盖安装到同一设备上的旧版本。
签名信息说明见：

```text
SIGNING.md
```

请不要随意替换 `debug.keystore`，否则已安装版本可能无法直接升级覆盖。

## 适用场景

本应用主要用于配合高德地图车机版广播协议进行悬浮窗显示和调试。
如果使用虚拟定位、修改版高德车机版或不同系统 ROM，广播字段和权限行为可能存在差异，需要结合实际环境测试。
