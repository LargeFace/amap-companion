require("dotenv").config({path: require("path").join(__dirname, ".env")});

const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const {spawn} = require("child_process");
const Busboy = require("busboy");
const {PluginMarket} = require("./plugin-market");

const host = process.env.HOST || "0.0.0.0";
const port = Number(process.env.PORT || 8788);
const defaultPublicBaseUrl = "https://amap-companion.zuoqirun.top";
const autoSyncEnabled = process.env.AUTO_SYNC !== "0";
const syncIntervalMs = Math.max(60_000, Number(process.env.SYNC_INTERVAL_MS || 300_000));
const publicDir = path.join(__dirname, "public");
const manifestPath = path.join(publicDir, "update.json");
const manifestTemplatePath = path.join(__dirname, "update.template.json");
const pluginsTemplatePath = path.join(__dirname, "plugins.template.json");
const pluginsManifestPath = path.join(publicDir, "plugins.json");
const rootChangelogPath = path.join(__dirname, "..", "CHANGELOG.md");
const syncScriptPath = path.join(__dirname, "sync-build.js");
const marketDataDir = process.env.MARKET_DATA_DIR || __dirname;
const market = new PluginMarket({
  baseDir: marketDataDir,
  publicDir,
  bootstrapUser: process.env.ADMIN_BOOTSTRAP_USER,
  bootstrapPassword: process.env.ADMIN_BOOTSTRAP_PASSWORD,
});
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

function readJsonBody(req, limit = 64 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error("请求内容过大"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("error", reject);
    req.on("end", () => {
      try {
        const text = Buffer.concat(chunks).toString("utf8").trim();
        resolve(text ? JSON.parse(text) : {});
      } catch {
        reject(new Error("请求 JSON 格式无效"));
      }
    });
  });
}

function readPluginUpload(req) {
  return new Promise((resolve, reject) => {
    let parser;
    try {
      parser = Busboy({
        headers: req.headers,
        limits: {
          fields: 8,
          fieldSize: 8 * 1024,
          files: 1,
          fileSize: 20 * 1024 * 1024,
          parts: 9,
        },
      });
    } catch {
      reject(new Error("上传表单格式无效"));
      return;
    }
    const fields = {};
    const temporaryPath = path.join(
      os.tmpdir(),
      `amap-plugin-upload-${process.pid}-${crypto.randomBytes(10).toString("hex")}.acplugin`,
    );
    let fileName = "";
    let filePromise = null;
    let uploadError = null;

    parser.on("field", (name, value) => {
      if (["downloadUrl", "projectUrl", "developerName", "homepage", "notes"].includes(name)) {
        fields[name] = value;
      }
    });
    parser.on("file", (name, stream, info) => {
      if (name !== "pluginFile" || filePromise) {
        stream.resume();
        uploadError = new Error("上传表单只能包含一个插件包");
        return;
      }
      fileName = info.filename || "";
      filePromise = new Promise((fileResolve, fileReject) => {
        const output = fs.createWriteStream(temporaryPath, {flags: "wx"});
        stream.on("limit", () => {
          uploadError = new Error("插件包大小超过 20MB");
          output.destroy(uploadError);
        });
        stream.on("error", fileReject);
        output.on("error", fileReject);
        output.on("finish", fileResolve);
        stream.pipe(output);
      });
    });
    parser.on("filesLimit", () => {
      uploadError = new Error("上传表单只能包含一个插件包");
    });
    parser.on("fieldsLimit", () => {
      uploadError = new Error("上传字段过多");
    });
    parser.on("partsLimit", () => {
      uploadError = new Error("上传表单内容过多");
    });
    parser.on("error", (error) => {
      fs.rmSync(temporaryPath, {force: true});
      reject(error);
    });
    parser.on("close", async () => {
      try {
        if (filePromise) await filePromise;
        if (uploadError) throw uploadError;
        if (!filePromise || !fileName) throw new Error("请选择要上传的 .acplugin 文件");
        resolve({fields, filePath: temporaryPath, fileName});
      } catch (error) {
        fs.rmSync(temporaryPath, {force: true});
        reject(error);
      }
    });
    req.pipe(parser);
  });
}

function parseCookies(req) {
  const result = {};
  for (const pair of String(req.headers.cookie || "").split(";")) {
    const separator = pair.indexOf("=");
    if (separator < 0) continue;
    const key = pair.slice(0, separator).trim();
    const value = pair.slice(separator + 1).trim();
    if (key) result[key] = decodeURIComponent(value);
  }
  return result;
}

function adminSessionToken(req) {
  return parseCookies(req).amap_admin_session || "";
}

function developerSessionToken(req) {
  return parseCookies(req).amap_developer_session || "";
}

function requireAdmin(req, res) {
  const session = market.getSession(adminSessionToken(req));
  if (!session) {
    sendJson(res, 401, {ok: false, error: "请先登录管理员账号"});
    return null;
  }
  return session;
}

function requireDeveloper(req, res) {
  const session = market.getDeveloperSession(developerSessionToken(req));
  if (!session) {
    sendJson(res, 401, {ok: false, error: "请先登录开发者账号"});
    return null;
  }
  return session;
}

function sessionCookie(req, name, token, pathValue, maxAgeSeconds) {
  const secure = req.headers["x-forwarded-proto"] === "https" ? "; Secure" : "";
  return `${name}=${encodeURIComponent(token)}; Path=${pathValue}; HttpOnly; SameSite=Strict; Max-Age=${maxAgeSeconds}${secure}`;
}

function adminCookie(req, token, maxAgeSeconds) {
  return sessionCookie(req, "amap_admin_session", token, "/admin", maxAgeSeconds);
}

function developerCookie(req, token, maxAgeSeconds) {
  return sessionCookie(req, "amap_developer_session", token, "/", maxAgeSeconds);
}

function sha256(filePath) {
  const hash = crypto.createHash("sha256");
  hash.update(fs.readFileSync(filePath));
  return hash.digest("hex");
}

function publicBaseUrl(req) {
  const configured = process.env.PUBLIC_BASE_URL || defaultPublicBaseUrl;
  if (configured) {
    return configured.replace(/\/+$/, "");
  }
  const scheme = req.headers["x-forwarded-proto"] === "https" ? "https" : "http";
  return `${scheme}://${req.headers.host}`;
}

function readManifest(req, channel = "server") {
  const sourcePath = fs.existsSync(manifestPath) ? manifestPath : manifestTemplatePath;
  const manifest = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
  const apkPath = path.join(publicDir, manifest.apkPath || "apk/amap_companion_signed.apk");
  const baseUrl = publicBaseUrl(req);
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
  if (fs.existsSync(changelogPath) && fs.statSync(changelogPath).isFile()) {
    manifest.changelogText = fs.readFileSync(changelogPath, "utf8").trim();
  }
  delete manifest.apkPath;
  delete manifest.githubApkUrl;
  delete manifest.githubChangelogUrl;
  return manifest;
}

function readPlugins(req) {
  const sourcePath = fs.existsSync(pluginsManifestPath) ? pluginsManifestPath : pluginsTemplatePath;
  const data = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
  const baseUrl = publicBaseUrl(req);
  const publicRoot = path.resolve(publicDir);
  const plugins = Array.isArray(data.plugins) ? data.plugins : [];
  data.plugins = plugins.map((plugin) => {
    const next = {...plugin};
    const rawPath = next.path || next.pluginPath || "";
    if (!next.downloadUrl && rawPath) {
      next.downloadUrl = new URL(rawPath.replace(/\\/g, "/"), baseUrl).toString();
    } else if (next.downloadUrl) {
      next.downloadUrl = new URL(next.downloadUrl, baseUrl).toString();
    }
    const localPath = rawPath
      ? path.join(publicDir, rawPath.replace(/^\/+/, ""))
      : next.downloadUrl && next.downloadUrl.startsWith(baseUrl)
        ? path.join(publicDir, new URL(next.downloadUrl).pathname.replace(/^\/+/, ""))
        : "";
    const resolvedLocalPath = localPath ? path.resolve(localPath) : "";
    if (resolvedLocalPath
        && resolvedLocalPath.startsWith(publicRoot + path.sep)
        && fs.existsSync(resolvedLocalPath)
        && fs.statSync(resolvedLocalPath).isFile()) {
      next.size = fs.statSync(resolvedLocalPath).size;
      next.sha256 = next.sha256 || sha256(resolvedLocalPath);
    }
    delete next.path;
    delete next.pluginPath;
    return next;
  });
  return data;
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

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (req.method === "GET" && url.pathname === "/health") {
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
    if (req.method === "GET" && url.pathname === "/") {
      sendFile(res, path.join(publicDir, "index.html"), "text/html; charset=utf-8");
      return;
    }
    if (req.method === "GET" && url.pathname === "/plugins/submit") {
      sendFile(res, path.join(publicDir, "plugin-submit.html"), "text/html; charset=utf-8");
      return;
    }
    if (req.method === "GET" && url.pathname === "/admin/plugins") {
      sendFile(res, path.join(publicDir, "admin-plugins.html"), "text/html; charset=utf-8");
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/plugin-submissions") {
      const developer = market.getDeveloperSession(developerSessionToken(req));
      const ownerUsername = developer ? developer.username : "";
      const contentType = String(req.headers["content-type"] || "").toLowerCase();
      let submission;
      if (contentType.startsWith("multipart/form-data")) {
        const upload = await readPluginUpload(req);
        submission = await market.submitUploaded(upload.fields, upload.filePath, upload.fileName, ownerUsername);
      } else {
        const body = await readJsonBody(req);
        submission = await market.submit(body, ownerUsername);
      }
      sendJson(res, 201, {ok: true, submission});
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/developer/me") {
      const session = requireDeveloper(req, res);
      if (!session) return;
      sendJson(res, 200, {
        ok: true,
        user: {username: session.username},
        expiresAt: new Date(session.expiresAt).toISOString(),
      });
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/developer/register") {
      const body = await readJsonBody(req, 16 * 1024);
      market.registerDeveloper(body.username, body.password);
      const login = market.authenticateDeveloper(body.username, body.password);
      res.setHeader("set-cookie", developerCookie(req, login.token, 12 * 60 * 60));
      sendJson(res, 201, {ok: true, user: {username: login.username}, expiresAt: login.expiresAt});
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/developer/login") {
      const body = await readJsonBody(req, 16 * 1024);
      const login = market.authenticateDeveloper(body.username, body.password);
      if (!login) {
        sendJson(res, 401, {ok: false, error: "账号或密码错误"});
        return;
      }
      res.setHeader("set-cookie", developerCookie(req, login.token, 12 * 60 * 60));
      sendJson(res, 200, {ok: true, user: {username: login.username}, expiresAt: login.expiresAt});
      return;
    }
    if (req.method === "POST" && url.pathname === "/api/developer/logout") {
      market.logoutDeveloper(developerSessionToken(req));
      res.setHeader("set-cookie", developerCookie(req, "", 0));
      sendJson(res, 200, {ok: true});
      return;
    }
    if (req.method === "GET" && url.pathname === "/api/developer/submissions") {
      const session = requireDeveloper(req, res);
      if (!session) return;
      sendJson(res, 200, {ok: true, submissions: market.developerSubmissions(session.username)});
      return;
    }
    const trackingMatch = req.method === "GET"
      ? url.pathname.match(/^\/api\/plugin-submissions\/([^/]+)$/)
      : null;
    if (trackingMatch) {
      const developer = market.getDeveloperSession(developerSessionToken(req));
      const submission = market.trackedSubmission(
        decodeURIComponent(trackingMatch[1]),
        url.searchParams.get("token") || "",
        developer ? developer.username : "",
      );
      sendJson(res, 200, {ok: true, submission});
      return;
    }
    if (req.method === "GET" && url.pathname === "/admin/registration-status") {
      sendJson(res, 200, {ok: true, ...market.adminRegistrationStatus()});
      return;
    }
    if (req.method === "POST" && url.pathname === "/admin/register") {
      const body = await readJsonBody(req, 16 * 1024);
      market.registerBootstrapAdmin(body.username, body.password);
      const login = market.authenticate(body.username, body.password);
      res.setHeader("set-cookie", adminCookie(req, login.token, 12 * 60 * 60));
      sendJson(res, 201, {
        ok: true,
        user: {username: login.username, role: login.role},
        expiresAt: login.expiresAt,
      });
      return;
    }
    if (req.method === "POST" && url.pathname === "/admin/setup-password") {
      const body = await readJsonBody(req, 16 * 1024);
      market.setupManagedAdmin(body.username, body.setupToken, body.password);
      const login = market.authenticate(body.username, body.password);
      res.setHeader("set-cookie", adminCookie(req, login.token, 12 * 60 * 60));
      sendJson(res, 200, {
        ok: true,
        user: {username: login.username, role: login.role},
        expiresAt: login.expiresAt,
      });
      return;
    }
    if (req.method === "POST" && url.pathname === "/admin/login") {
      const body = await readJsonBody(req, 16 * 1024);
      const login = market.authenticate(body.username, body.password);
      if (!login) {
        sendJson(res, 401, {ok: false, error: "账号或密码错误"});
        return;
      }
      res.setHeader("set-cookie", adminCookie(req, login.token, 12 * 60 * 60));
      sendJson(res, 200, {
        ok: true,
        user: {username: login.username, role: login.role},
        expiresAt: login.expiresAt,
      });
      return;
    }
    if (req.method === "POST" && url.pathname === "/admin/logout") {
      market.logout(adminSessionToken(req));
      res.setHeader("set-cookie", adminCookie(req, "", 0));
      sendJson(res, 200, {ok: true});
      return;
    }
    if (req.method === "GET" && url.pathname === "/admin/me") {
      const session = requireAdmin(req, res);
      if (!session) return;
      sendJson(res, 200, {
        ok: true,
        user: {username: session.username, role: session.role},
        expiresAt: new Date(session.expiresAt).toISOString(),
      });
      return;
    }
    if (req.method === "GET" && url.pathname === "/admin/api/users") {
      if (!requireAdmin(req, res)) return;
      sendJson(res, 200, {ok: true, users: market.listAdminUsers()});
      return;
    }
    if (req.method === "POST" && url.pathname === "/admin/api/users") {
      const session = requireAdmin(req, res);
      if (!session) return;
      const body = await readJsonBody(req, 16 * 1024);
      const user = market.createManagedAdmin(body, session.username);
      sendJson(res, 201, {ok: true, user});
      return;
    }
    if (req.method === "GET" && url.pathname === "/admin/api/plugin-submissions") {
      if (!requireAdmin(req, res)) return;
      sendJson(res, 200, {
        ok: true,
        submissions: market.listSubmissions(),
        blockedPlugins: market.readBlocked(),
      });
      return;
    }
    const reviewMatch = req.method === "POST"
      ? url.pathname.match(/^\/admin\/api\/plugin-submissions\/([^/]+)\/(approve|reject|block)$/)
      : null;
    if (reviewMatch) {
      const session = requireAdmin(req, res);
      if (!session) return;
      const body = await readJsonBody(req, 16 * 1024);
      const submission = await market.review(
        decodeURIComponent(reviewMatch[1]),
        reviewMatch[2],
        session.username,
        body.reason,
      );
      sendJson(res, 200, {ok: true, submission});
      return;
    }
    if ((req.method === "GET" || req.method === "POST") && url.pathname === "/sync") {
      runSync("manual-http");
      sendJson(res, 202, {ok: true, syncing: true});
      return;
    }
    if (req.method === "GET" && url.pathname === "/update.json") {
      sendJson(res, 200, readManifest(req, "server"));
      return;
    }
    if (req.method === "GET" && url.pathname === "/update-github.json") {
      sendJson(res, 200, readManifest(req, "github"));
      return;
    }
    if (req.method === "GET" && url.pathname === "/plugins.json") {
      sendJson(res, 200, readPlugins(req));
      return;
    }
    if (req.method === "GET" && url.pathname === "/apk/amap_companion_signed.apk") {
      sendFile(res, path.join(publicDir, "apk", "amap_companion_signed.apk"), "application/vnd.android.package-archive");
      return;
    }
    if (req.method === "GET" && url.pathname === "/CHANGELOG.md") {
      sendFile(res, resolveChangelogPath(), "text/markdown; charset=utf-8");
      return;
    }
    // serve static files from public directory
    const staticFile = path.resolve(publicDir, `.${decodeURIComponent(url.pathname)}`);
    if (req.method === "GET"
        && staticFile.startsWith(path.resolve(publicDir) + path.sep)
        && fs.existsSync(staticFile)
        && fs.statSync(staticFile).isFile()) {
      const ext = path.extname(staticFile).toLowerCase();
      const types = {
        ".html": "text/html; charset=utf-8",
        ".css": "text/css; charset=utf-8",
        ".js": "application/javascript; charset=utf-8",
        ".json": "application/json; charset=utf-8",
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".gif": "image/gif",
        ".svg": "image/svg+xml",
        ".ico": "image/x-icon",
        ".woff2": "font/woff2",
        ".woff": "font/woff",
        ".ttf": "font/ttf",
      };
      sendFile(res, staticFile, types[ext] || "application/octet-stream");
      return;
    }
    sendText(res, 404, "not found");
  } catch (error) {
    if (res.headersSent) {
      res.destroy();
      return;
    }
    const clientError = /请填写|请选择|格式无效|必须使用|至少需要|不存在|不支持|没有可审核|上传|插件包|字段过多|内容过多|用户名|密码|凭据|注册|设置码|已存在|无须首次设置/.test(error.message);
    sendJson(res, clientError ? 400 : 500, {ok: false, error: error.message});
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
