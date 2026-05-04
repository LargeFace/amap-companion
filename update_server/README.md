# AMap Companion 更新服务器部署指南

本目录用于在服务器上分发 AMap Companion 更新包。推荐部署方式是：

1. GitHub Actions 负责构建、签名 APK，并发布 GitHub Release。
2. 树莓派 Debian arm64 只负责定时同步 GitHub Release 里的 APK、`release-update.json` 和 `CHANGELOG.md`。
3. App 访问树莓派的 `/update.json`，查看更新详情并选择安装方式。

这样树莓派不需要安装 Android SDK，也不会遇到 arm64 无法运行 Android build-tools 的问题。

## 1. 前置条件

- 一台树莓派或 Debian arm64 服务器。
- 服务器能访问 GitHub。
- 仓库已启用 `.github/workflows/build-release.yml`。
- 每次推送到 `master` 后，GitHub Actions 会生成 Release，并附带：
  - `amap-companion-xxxxxxx.apk`
  - `release-update.json`
  - `CHANGELOG.md`

如果仓库是私有仓库，需要准备一个 GitHub Token，至少需要能读取 Release 资源。

## 2. 安装依赖

```bash
sudo apt update
sudo apt install -y git nodejs npm
node -v
```

Node.js 需要 18 或更高版本。如果系统源版本过旧，请使用你常用的 Node.js 18+ 安装方式。

## 3. 克隆项目

下面路径可按需修改，指南中统一使用 `/opt/amap-companion`。

```bash
sudo mkdir -p /opt/amap-companion
sudo chown -R "$USER":"$USER" /opt/amap-companion
git clone https://github.com/你的用户名/你的仓库.git /opt/amap-companion
cd /opt/amap-companion/update_server
npm install
```

## 4. 配置同步参数

公开仓库最少只需要设置仓库名：

```bash
export GITHUB_REPO=你的用户名/你的仓库
```

私有仓库还需要：

```bash
export GITHUB_TOKEN=你的GitHubToken
```

常用环境变量：

- `GITHUB_REPO`: GitHub 仓库，格式 `owner/repo`。
- `GITHUB_TOKEN`: 私有仓库或避免 GitHub API 限流时使用。
- `RELEASE_TAG`: 要同步的 Release，默认 `latest`。
- `ASSET_PATTERN`: APK 资源名匹配正则，默认 `\.apk$`。
- `MANIFEST_ASSET`: Release 里的更新描述文件名，默认 `release-update.json`。
- `CHANGELOG_ASSET`: Release 里的更新日志文件名，默认 `CHANGELOG.md`。
- `PORT`: HTTP 服务端口，默认 `8787`。
- `HOST`: HTTP 监听地址，默认 `0.0.0.0`。
- `PUBLIC_BASE_URL`: 对外访问根地址，例如 `https://example.com`。

## 5. 手动同步一次 Release

```bash
cd /opt/amap-companion/update_server
GITHUB_REPO=你的用户名/你的仓库 npm run sync:force
```

成功后会生成：

```text
update_server/public/update.json
update_server/public/CHANGELOG.md
update_server/public/apk/amap_companion_signed.apk
```

检查生成结果：

```bash
cat public/update.json
ls -lh public/apk/
```

## 6. 启动更新服务器

```bash
cd /opt/amap-companion/update_server
HOST=0.0.0.0 PORT=8787 npm start
```

浏览器或命令行检查：

```bash
curl http://127.0.0.1:8787/health
curl http://127.0.0.1:8787/update.json
curl http://127.0.0.1:8787/CHANGELOG.md
```

App 内填写的更新地址为：

```text
http://树莓派IP:8787/update.json
```

如果你通过 Nginx、域名或 HTTPS 反代，请设置：

```bash
PUBLIC_BASE_URL=https://你的域名
```

这样 `/update.json` 里的 `apkUrl` 和 `changelogUrl` 会使用对外地址。

## 7. 配置 systemd 自动运行

复制 unit 文件：

```bash
sudo cp /opt/amap-companion/update_server/deploy/*.service /etc/systemd/system/
sudo cp /opt/amap-companion/update_server/deploy/*.timer /etc/systemd/system/
```

编辑同步服务：

```bash
sudo nano /etc/systemd/system/amap-companion-sync.service
```

至少确认这些内容：

```ini
WorkingDirectory=/opt/amap-companion/update_server
Environment=GITHUB_REPO=你的用户名/你的仓库
# 私有仓库才需要：
# Environment=GITHUB_TOKEN=你的GitHubToken
User=pi
```

编辑 HTTP 服务：

```bash
sudo nano /etc/systemd/system/amap-companion-update.service
```

确认：

```ini
WorkingDirectory=/opt/amap-companion/update_server
Environment=HOST=0.0.0.0
Environment=PORT=8787
# 如果走域名或反代：
# Environment=PUBLIC_BASE_URL=https://你的域名
User=pi
```

启用服务和定时器：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now amap-companion-update.service
sudo systemctl enable --now amap-companion-sync.timer
```

默认同步频率是开机 2 分钟后执行一次，之后每 5 分钟执行一次。

查看状态：

```bash
systemctl status amap-companion-update.service
systemctl status amap-companion-sync.timer
```

查看日志：

```bash
journalctl -u amap-companion-update.service -f
journalctl -u amap-companion-sync.service -f
```

手动触发同步：

```bash
sudo systemctl start amap-companion-sync.service
```

## 8. App 更新流程

App 点击“检查更新”后会：

1. 请求服务器 `/update.json`。
2. 比较本地 `versionCode` 和服务器 `versionCode`。
3. 如果有新版本，先弹出更新详情，包括版本号和更新日志。
4. 点击“更新”后选择安装方式。

目前安装方式：

- `pm install`: 下载 APK 后尝试写入 `/data/local/tmp/`，再执行 `pm install -r -d <路径>`。
- `adb install`: 下载 APK 后尝试写入 `/data/local/tmp/`，再执行 `adb install -r -d <路径>`。
- `系统安装器`: 调用系统安装界面，适合允许用户确认安装的设备。
- `设备管理员`: 入口已预留，授权和静默安装代码暂未实现。

注意：普通第三方应用不一定有权限写入 `/data/local/tmp/`，也不一定能执行 `pm install` / `adb install`。这取决于车机 ROM、root、系统签名、白名单或设备管理策略。

## 9. 返回格式

`/update.json` 示例：

```json
{
  "packageName": "com.autonavi.companion",
  "versionCode": 1760000000,
  "versionName": "20260504-abcdef0",
  "apkUrl": "http://server:8787/apk/amap_companion_signed.apk",
  "sha256": "...",
  "size": 123456,
  "force": false,
  "changelog": ["abcdef0 修复红绿灯倒计时显示"],
  "changelogUrl": "http://server:8787/CHANGELOG.md"
}
```

客户端只会在服务器 `versionCode` 大于本地版本时提示更新。
