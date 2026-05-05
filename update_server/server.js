const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const path = require("path");
const {spawn} = require("child_process");

const host = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 8788);
const autoSyncEnabled = process.env.AUTO_SYNC !== "0";
const syncIntervalMs = Math.max(60_000, Number(process.env.SYNC_INTERVAL_MS || 300_000));
const publicDir = path.join(__dirname, "public");
const manifestPath = path.join(publicDir, "update.json");
const manifestTemplatePath = path.join(__dirname, "update.template.json");
const rootChangelogPath = path.join(__dirname, "..", "CHANGELOG.md");
const syncScriptPath = path.join(__dirname, "sync-build.js");
let syncing = false;
let lastSync = null;

function sendJson(res, statusCode, body) {
  const text = JSON.stringify(body, null, 2);
  res.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": "*",
  });
  res.end(text);
}

function sendText(res, statusCode, text) {
  res.writeHead(statusCode, {"content-type": "text/plain; charset=utf-8"});
  res.end(text);
}

function sha256(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(filePath));
  return hash.digest("hex");
}

function readManifest(req, channel = "server") {
  const sourcePath = fs.existsSync(manifestPath) ? manifestPath : manifestTemplatePath;
  const manifest = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
  const apkPath = path.join(publicDir, manifest.apkPath || "apk/amap_companion_signed.apk");
  const scheme = req.headers["x-forwarded-proto"] === "https" ? "https" : "http";
  const baseUrl = process.env.PUBLIC_BASE_URL || `${scheme}://${req.headers.host}`;
  const githubApkUrl = manifest.githubApkUrl || "";
  const githubChangelogUrl = manifest.githubChangelogUrl || "";
  if (channel === "github" && githubApkUrl) {
    manifest.apkUrl = githubApkUrl;
    manifest.downloadChannel = "github";
  } else if (fs.existsSync(apkPath)) {
    manifest.apkUrl = new URL(manifest.apkUrl || `/${path.relative(publicDir, apkPath).replace(/\\/g, "/")}`, baseUrl).toString();
    manifest.downloadChannel = channel === "github" ? "server-fallback" : "server";
    manifest.sha256 = manifest.sha256 || sha256(apkPath);
    manifest.size = fs.statSync(apkPath).size;
  }
  const changelogPath = resolveChangelogPath();
  if (channel === "github" && githubChangelogUrl) {
    manifest.changelogUrl = githubChangelogUrl;
  } else if (fs.existsSync(changelogPath)) {
    manifest.changelogUrl = new URL("/CHANGELOG.md", baseUrl).toString();
  }
  delete manifest.apkPath;
  delete manifest.githubApkUrl;
  delete manifest.githubChangelogUrl;
  return manifest;
}

function resolveChangelogPath() {
  const generatedPath = path.join(publicDir, "CHANGELOG.md");
  if (fs.existsSync(generatedPath)) {
    return generatedPath;
  }
  return rootChangelogPath;
}

function sendFile(res, filePath, contentType) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    sendText(res, 404, "not found");
    return;
  }
  res.writeHead(200, {
    "content-type": contentType,
    "content-length": fs.statSync(filePath).size,
    "cache-control": "no-store",
  });
  fs.createReadStream(filePath).pipe(res);
}

function runSync(reason = "timer") {
  if (!autoSyncEnabled) {
    console.log("[release-sync] auto sync disabled");
    return;
  }
  if (syncing) {
    console.log(`[release-sync] skip ${reason}, sync already running`);
    return;
  }
  if (!fs.existsSync(syncScriptPath)) {
    console.log(`[release-sync] sync script not found: ${syncScriptPath}`);
    return;
  }
  syncing = true;
  const startedAt = new Date();
  console.log(`[release-sync] start ${reason}`);
  const child = spawn(process.execPath, [syncScriptPath], {
    cwd: __dirname,
    env: process.env,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.on("data", (chunk) => process.stdout.write(chunk));
  child.stderr.on("data", (chunk) => process.stderr.write(chunk));
  child.on("close", (code) => {
    syncing = false;
    lastSync = {
      reason,
      code,
      startedAt: startedAt.toISOString(),
      finishedAt: new Date().toISOString(),
    };
    console.log(`[release-sync] finish ${reason}, exit=${code}`);
  });
  child.on("error", (error) => {
    syncing = false;
    lastSync = {
      reason,
      code: -1,
      error: error.message,
      startedAt: startedAt.toISOString(),
      finishedAt: new Date().toISOString(),
    };
    console.error(`[release-sync] failed ${reason}: ${error.stack || error.message}`);
  });
}

const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.pathname === "/" || url.pathname === "/health") {
      sendJson(res, 200, {
        ok: true,
        service: "amap-companion-update-server",
        autoSyncEnabled,
        syncIntervalMs,
        syncing,
        lastSync,
      });
      return;
    }
    if (url.pathname === "/sync") {
      runSync("manual-http");
      sendJson(res, 202, {ok: true, syncing: true});
      return;
    }
    if (url.pathname === "/update.json") {
      sendJson(res, 200, readManifest(req, "server"));
      return;
    }
    if (url.pathname === "/update-github.json") {
      sendJson(res, 200, readManifest(req, "github"));
      return;
    }
    if (url.pathname === "/apk/amap_companion_signed.apk") {
      sendFile(res, path.join(publicDir, "apk", "amap_companion_signed.apk"), "application/vnd.android.package-archive");
      return;
    }
    if (url.pathname === "/CHANGELOG.md") {
      sendFile(res, resolveChangelogPath(), "text/markdown; charset=utf-8");
      return;
    }
    sendText(res, 404, "not found");
  } catch (error) {
    sendJson(res, 500, {ok: false, error: error.message});
  }
});

server.listen(port, host, () => {
  console.log(`AMap Companion update server listening on http://${host}:${port}`);
  console.log(`Update manifest: http://${host}:${port}/update.json`);
  console.log(`GitHub direct manifest: http://${host}:${port}/update-github.json`);
  if (autoSyncEnabled) {
    console.log(`Release sync enabled, interval=${syncIntervalMs}ms`);
    runSync("startup");
    setInterval(() => runSync("timer"), syncIntervalMs).unref();
  } else {
    console.log("Release sync disabled by AUTO_SYNC=0");
  }
});
