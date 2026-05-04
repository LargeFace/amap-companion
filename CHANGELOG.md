# 更新日志

本文件用于记录 AMap Companion 的人工整理更新记录。

服务器自动同步 GitHub Release 时还会生成面向客户端的更新日志：

```text
update_server/public/CHANGELOG.md
```

## 2026-05-04

- 新增应用内检查更新流程：先展示新版本号、当前版本号、APK 大小和更新日志，再由用户确认更新。
- 新增多种安装模式：`pm install`、`adb install`、系统安装器，以及预留的设备管理员入口。
- `pm install` 和 `adb install` 模式统一把 APK 写入 `/data/local/tmp/amap_companion_update.apk` 后再执行安装命令。
- 新增 `ApkProvider`，系统安装器模式可通过 `content://` 安全读取已下载 APK。
- 新增树莓派 Debian arm64 更新服务器方案：树莓派只同步和分发 GitHub Actions 发布的 Release APK，不在本地构建 Android APK。
- 新增 GitHub Release 同步脚本，可同步 APK、`release-update.json` 和 `CHANGELOG.md`，并生成客户端 `/update.json`。
- GitHub Actions 发布流程新增自动版本号、Release 元数据和更新日志生成。
- 更新中文 README 和服务器部署指南，补充自动升级、Release 同步、systemd 部署和权限限制说明。
