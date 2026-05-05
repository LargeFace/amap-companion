# 更新日志

本文档用于记录 AMap Companion 的人工整理更新日志。服务端同步 GitHub Release 后，还会生成面向客户端的更新日志：

```text
update_server/public/CHANGELOG.md
```

## 2026-05-05

- App 内新增“选择下载渠道”，支持在“服务器分发”和“GitHub 直连”之间切换，并持久化保存用户选择。
- 服务端新增 `/update-github.json` 更新入口：该入口保留服务端 manifest 能力，但 APK 和更新日志下载地址可直接指向 GitHub Release 资产。
- Release 同步脚本新增 GitHub APK 与 CHANGELOG 直链字段，树莓派同步后会同时生成服务器分发渠道和 GitHub 直连渠道。
- 服务端新增启动同步、定时同步、`/sync` 手动同步与 `/health` 状态信息，便于树莓派长期运行时自动跟随 GitHub Release。
- App 更新弹窗优先读取 manifest 中的 `changelogUrl`，可展示完整 `CHANGELOG.md` 内容。
- GitHub 直连渠道在旧 manifest 尚未补齐直链字段时会临时回退到服务器分发，避免更新入口不可用。

## 2026-05-04

- 修正默认更新地址为 `https://amap-companion.zuoqirun.top/update.json`，应用启动时自动持久化该地址，不再需要用户手动输入。
- 去除主页里的更新地址输入入口，保留自动检查更新与手动检查更新。
- 自动更新流程只保留 Android `PackageInstaller` 安装方式，移除 `pm install`、`adb install`、Shizuku、Dhizuku 和设备管理员预留模式。
- 进入应用后自动检查更新；发现新版本时先弹出版本号、APK 大小和更新日志，用户确认后再安装。
- 新增应用内悬浮窗大小预览：拖动滑条时只更新预览，不再反复重建真实悬浮窗；点击“应用当前大小到悬浮窗”后才应用到实际悬浮窗。
- 将应用内悬浮窗预览改成更接近导航截图的效果：包含深色地图背景、顶部导航指示条、蓝色路线指示卡片、红绿灯胶囊、车道信息和目的地信息。
- 悬浮窗默认大小调整为 200%，支持 80% 到 300% 范围调节。
- 车道图标仅放大图片内容，不再放大外部蓝色车道框；蓝色车道条整体缩小到约 0.66 倍。
- 红绿灯胶囊颜色调暗，降低高饱和色带来的刺眼感。
- 巡航红绿灯逻辑借鉴高德内部 `CameraLightInfo` 模型，增强对多方向红绿灯倒计时的解析和显示。
- 支持从 `CameraLightInfo` / `CameraLightInfoWrapper` 风格字符串、JSON、Bundle、数组和 List 中解析巡航红绿灯信息。
- 巡航模式下红绿灯按方向合并更新，减少前方和左转多个灯互相覆盖的问题。
- 目标应用选择仅列出 `com.autonavi.*` 包名，兼容系统应用和用户应用。
- 重新生成并替换应用图标，同时同步各 dpi 档位 launcher 图标。
- 新增树莓派 Debian arm64 更新服务器方案：树莓派只负责同步和分发 GitHub Actions 发布的 Release APK，不在本地构建 Android APK。
- 新增 GitHub Release 同步脚本，可同步 APK、`release-update.json` 和 `CHANGELOG.md`，并生成客户端 `/update.json`。
- GitHub Actions 发布流程支持自动版本号、Release 元数据和更新日志生成。
- 更新中文 README 和服务器部署指南，补充自动升级、Release 同步、systemd 部署和权限限制说明。
