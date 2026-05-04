const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const path = require("path");

const host = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 8788);
const publicDir = path.join(__dirname, "public");
const manifestPath = path.join(publicDir, "update.json");
const manifestTemplatePath = path.join(__dirname, "update.template.json");
const rootChangelogPath = path.join(__dirname, "..", "CHANGELOG.md");

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

function readManifest(req) {
  const sourcePath = fs.existsSync(manifestPath) ? manifestPath : manifestTemplatePath;
  const manifest = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
  const apkPath = path.join(publicDir, manifest.apkPath || "apk/amap_companion_signed.apk");
  const baseUrl = process.env.PUBLIC_BASE_URL || `http://${req.headers.host}`;
  if (fs.existsSync(apkPath)) {
    manifest.apkUrl = new URL(manifest.apkUrl || `/${path.relative(publicDir, apkPath).replace(/\\/g, "/")}`, baseUrl).toString();
    manifest.sha256 = manifest.sha256 || sha256(apkPath);
    manifest.size = fs.statSync(apkPath).size;
  }
  const changelogPath = resolveChangelogPath();
  if (fs.existsSync(changelogPath)) {
    manifest.changelogUrl = new URL("/CHANGELOG.md", baseUrl).toString();
  }
  delete manifest.apkPath;
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

const server = http.createServer((req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.pathname === "/" || url.pathname === "/health") {
      sendJson(res, 200, {ok: true, service: "amap-companion-update-server"});
      return;
    }
    if (url.pathname === "/update.json") {
      sendJson(res, 200, readManifest(req));
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
});
