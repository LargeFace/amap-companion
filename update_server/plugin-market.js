"use strict";

const crypto = require("crypto");
const dns = require("dns");
const fs = require("fs");
const http = require("http");
const https = require("https");
const net = require("net");
const path = require("path");
const yauzl = require("yauzl");

const MAX_PLUGIN_BYTES = 20 * 1024 * 1024;
const MAX_UNCOMPRESSED_BYTES = 50 * 1024 * 1024;
const MAX_ZIP_ENTRIES = 2048;
const MAX_MANIFEST_BYTES = 256 * 1024;
const ALLOWED_CAPABILITIES = new Set(["font", "icons", "ui", "overlayStyle"]);
const FORBIDDEN_EXTENSIONS = new Set([
  ".apk", ".dex", ".so", ".js", ".sh", ".bat", ".cmd", ".exe", ".dll",
]);
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;
const PBKDF2_ITERATIONS = 210000;
const PBKDF2_KEY_LENGTH = 64;
const PBKDF2_DIGEST = "sha512";

function nowIso() {
  return new Date().toISOString();
}

function ensureDirectory(directory) {
  fs.mkdirSync(directory, {recursive: true});
}

function readJson(filePath, fallback) {
  if (!fs.existsSync(filePath)) {
    return fallback;
  }
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function writeJsonAtomic(filePath, value) {
  ensureDirectory(path.dirname(filePath));
  const temporary = `${filePath}.${process.pid}.${crypto.randomBytes(6).toString("hex")}.tmp`;
  fs.writeFileSync(temporary, `${JSON.stringify(value, null, 2)}\n`, "utf8");
  fs.renameSync(temporary, filePath);
}

function hashPassword(password, salt) {
  return crypto.pbkdf2Sync(
    password,
    salt,
    PBKDF2_ITERATIONS,
    PBKDF2_KEY_LENGTH,
    PBKDF2_DIGEST,
  ).toString("hex");
}

function createPasswordUser(username, password, extra = {}) {
  const salt = crypto.randomBytes(24).toString("hex");
  return {
    username,
    passwordHash: hashPassword(password, salt),
    salt,
    createdAt: nowIso(),
    ...extra,
  };
}

function createAdminUser(username, password) {
  return createPasswordUser(username, password, {role: "admin", status: "active"});
}

function verifyPassword(password, user) {
  if (!user || !user.passwordHash || !user.salt) {
    return false;
  }
  const actual = Buffer.from(hashPassword(password, user.salt), "hex");
  const expected = Buffer.from(user.passwordHash, "hex");
  return actual.length === expected.length && crypto.timingSafeEqual(actual, expected);
}

function isPrivateIp(address) {
  if (net.isIPv4(address)) {
    const parts = address.split(".").map(Number);
    return parts[0] === 10
      || parts[0] === 127
      || (parts[0] === 169 && parts[1] === 254)
      || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31)
      || (parts[0] === 192 && parts[1] === 168)
      || parts[0] === 0;
  }
  if (net.isIPv6(address)) {
    const normalized = address.toLowerCase();
    return normalized === "::1"
      || normalized === "::"
      || normalized.startsWith("fc")
      || normalized.startsWith("fd")
      || normalized.startsWith("fe8")
      || normalized.startsWith("fe9")
      || normalized.startsWith("fea")
      || normalized.startsWith("feb");
  }
  return false;
}

async function validateDownloadUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL(rawUrl);
  } catch {
    throw new Error("插件下载地址格式无效");
  }
  if (parsed.username || parsed.password) {
    throw new Error("插件下载地址不能包含账号信息");
  }
  const localHttp = parsed.protocol === "http:"
    && (parsed.hostname === "127.0.0.1" || parsed.hostname === "localhost");
  if (parsed.protocol !== "https:" && !localHttp) {
    throw new Error("插件下载地址必须使用 HTTPS；本地测试仅允许 localhost 或 127.0.0.1");
  }
  if (!localHttp) {
    const addresses = await dns.promises.lookup(parsed.hostname, {all: true});
    if (!addresses.length || addresses.some((item) => isPrivateIp(item.address))) {
      throw new Error("插件下载地址不能指向本地或私有网络");
    }
  }
  return parsed;
}

async function downloadPackage(rawUrl, destinationPath, options = {}) {
  const maxBytes = options.maxBytes || MAX_PLUGIN_BYTES;
  const timeoutMs = options.timeoutMs || 20000;
  const maxRedirects = options.maxRedirects == null ? 5 : options.maxRedirects;
  ensureDirectory(path.dirname(destinationPath));
  const temporaryPath = `${destinationPath}.part`;
  fs.rmSync(temporaryPath, {force: true});

  async function request(currentUrl, redirectsLeft) {
    const parsed = await validateDownloadUrl(currentUrl);
    const transport = parsed.protocol === "https:" ? https : http;
    return new Promise((resolve, reject) => {
      const req = transport.get(parsed, {
        headers: {
          "accept": "application/octet-stream, application/zip;q=0.9, */*;q=0.1",
          "user-agent": "AMap-Companion-Plugin-Scanner/1.0",
        },
      }, async (response) => {
        const status = response.statusCode || 0;
        if ([301, 302, 303, 307, 308].includes(status) && response.headers.location) {
          response.resume();
          if (redirectsLeft <= 0) {
            reject(new Error("插件下载重定向次数过多"));
            return;
          }
          try {
            const nextUrl = new URL(response.headers.location, parsed).toString();
            resolve(await request(nextUrl, redirectsLeft - 1));
          } catch (error) {
            reject(error);
          }
          return;
        }
        if (status < 200 || status >= 300) {
          response.resume();
          reject(new Error(`插件下载失败：HTTP ${status}`));
          return;
        }
        const contentLength = Number(response.headers["content-length"] || 0);
        if (contentLength > maxBytes) {
          response.resume();
          reject(new Error("插件包大小超过 20MB"));
          return;
        }
        const output = fs.createWriteStream(temporaryPath, {flags: "w"});
        let total = 0;
        let settled = false;
        const fail = (error) => {
          if (settled) return;
          settled = true;
          response.destroy();
          output.destroy();
          fs.rmSync(temporaryPath, {force: true});
          reject(error);
        };
        response.on("data", (chunk) => {
          total += chunk.length;
          if (total > maxBytes) {
            fail(new Error("插件包大小超过 20MB"));
          }
        });
        response.on("error", fail);
        output.on("error", fail);
        output.on("finish", () => {
          if (settled) return;
          settled = true;
          if (total <= 0) {
            fs.rmSync(temporaryPath, {force: true});
            reject(new Error("插件包为空"));
            return;
          }
          fs.renameSync(temporaryPath, destinationPath);
          resolve({size: total, finalUrl: parsed.toString()});
        });
        response.pipe(output);
      });
      req.setTimeout(timeoutMs, () => req.destroy(new Error("插件下载超时")));
      req.on("error", (error) => {
        fs.rmSync(temporaryPath, {force: true});
        reject(error);
      });
    });
  }

  return request(rawUrl, maxRedirects);
}

function invalidZipPath(name) {
  if (!name || name.includes("\\") || name.startsWith("/") || /^[A-Za-z]:/.test(name)) {
    return true;
  }
  return name.split("/").some((segment) => segment === ".." || segment === ".");
}

function openZip(filePath) {
  return new Promise((resolve, reject) => {
    yauzl.open(filePath, {
      lazyEntries: true,
      autoClose: true,
      decodeStrings: true,
      strictFileNames: true,
      validateEntrySizes: true,
    }, (error, zipFile) => error ? reject(error) : resolve(zipFile));
  });
}

function readZipEntry(zipFile, entry, maxBytes) {
  return new Promise((resolve, reject) => {
    zipFile.openReadStream(entry, (error, stream) => {
      if (error) {
        reject(error);
        return;
      }
      const chunks = [];
      let total = 0;
      stream.on("data", (chunk) => {
        total += chunk.length;
        if (total > maxBytes) {
          stream.destroy(new Error("plugin.json 大小异常"));
          return;
        }
        chunks.push(chunk);
      });
      stream.on("error", reject);
      stream.on("end", () => resolve(Buffer.concat(chunks)));
    });
  });
}

function validateManifest(manifest, fileNames) {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error("plugin.json 必须是 JSON 对象");
  }
  if (manifest.schemaVersion !== 1) {
    throw new Error(`不支持的 schemaVersion：${manifest.schemaVersion}`);
  }
  const id = String(manifest.id || "").trim();
  if (!/^[A-Za-z0-9._-]{3,80}$/.test(id)) {
    throw new Error("plugin.json 包含非法插件 id");
  }
  const versionCode = Number(manifest.versionCode);
  if (!Number.isInteger(versionCode) || versionCode <= 0) {
    throw new Error("versionCode 必须是大于 0 的整数");
  }
  if (!Array.isArray(manifest.capabilities) || manifest.capabilities.length === 0) {
    throw new Error("插件至少需要声明一个 capability");
  }
  const capabilities = [...new Set(manifest.capabilities.map((item) => String(item).trim()))];
  const unknownCapability = capabilities.find((item) => !ALLOWED_CAPABILITIES.has(item));
  if (unknownCapability) {
    throw new Error(`不允许的 capability：${unknownCapability}`);
  }
  if (!manifest.entry || typeof manifest.entry !== "object" || Array.isArray(manifest.entry)) {
    throw new Error("plugin.json 缺少 entry 对象");
  }
  for (const capability of capabilities) {
    const entryPath = String(manifest.entry[capability] || "").trim();
    if (invalidZipPath(entryPath) || entryPath.endsWith("/")) {
      throw new Error(`capability ${capability} 的入口路径无效`);
    }
    if (!fileNames.has(entryPath)) {
      throw new Error(`capability ${capability} 的入口文件不存在：${entryPath}`);
    }
  }
  const developer = manifest.developer && typeof manifest.developer === "object"
    ? manifest.developer
    : {};
  return {
    schemaVersion: 1,
    id,
    name: String(manifest.name || id).trim() || id,
    versionCode,
    versionName: String(manifest.versionName || versionCode).trim() || String(versionCode),
    developer: {
      name: String(developer.name || "").trim(),
      homepage: String(developer.homepage || "").trim(),
    },
    capabilities,
    description: String(manifest.description || "").trim(),
    minAppVersionCode: Math.max(0, Number(manifest.minAppVersionCode) || 0),
  };
}

async function scanPluginPackage(filePath) {
  const stat = fs.statSync(filePath);
  if (!stat.isFile() || stat.size <= 0) {
    throw new Error("插件包为空");
  }
  if (stat.size > MAX_PLUGIN_BYTES) {
    throw new Error("插件包大小超过 20MB");
  }
  const zipFile = await openZip(filePath);
  const fileNames = new Set();
  let manifestBuffer = null;
  let entryCount = 0;
  let uncompressedBytes = 0;

  await new Promise((resolve, reject) => {
    let done = false;
    const fail = (error) => {
      if (done) return;
      done = true;
      zipFile.close();
      reject(error);
    };
    zipFile.on("error", fail);
    zipFile.on("end", () => {
      if (done) return;
      done = true;
      resolve();
    });
    zipFile.on("entry", async (entry) => {
      try {
        entryCount += 1;
        if (entryCount > MAX_ZIP_ENTRIES) {
          throw new Error("ZIP 文件条目过多");
        }
        const name = entry.fileName;
        if (invalidZipPath(name)) {
          throw new Error(`ZIP 包含非法路径：${name}`);
        }
        const isDirectory = name.endsWith("/");
        if (!isDirectory) {
          const unixFileType = (entry.externalFileAttributes >>> 16) & 0xf000;
          if (unixFileType && unixFileType !== 0x8000) {
            throw new Error(`ZIP 包含不允许的特殊文件：${name}`);
          }
          if (fileNames.has(name)) {
            throw new Error(`ZIP 包含重复文件：${name}`);
          }
          const extension = path.posix.extname(name).toLowerCase();
          if (FORBIDDEN_EXTENSIONS.has(extension)) {
            throw new Error(`ZIP 包含禁止的文件类型：${name}`);
          }
          fileNames.add(name);
          uncompressedBytes += entry.uncompressedSize;
          if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
            throw new Error("ZIP 解压后内容过大");
          }
          if (name === "plugin.json") {
            if (manifestBuffer) {
              throw new Error("ZIP 不能包含多个 plugin.json");
            }
            manifestBuffer = await readZipEntry(zipFile, entry, MAX_MANIFEST_BYTES);
          }
        }
        zipFile.readEntry();
      } catch (error) {
        fail(error);
      }
    });
    zipFile.readEntry();
  });

  if (!manifestBuffer) {
    throw new Error("插件包缺少根目录 plugin.json");
  }
  let manifest;
  try {
    manifest = JSON.parse(manifestBuffer.toString("utf8"));
  } catch {
    throw new Error("plugin.json 不是合法 JSON");
  }
  const metadata = validateManifest(manifest, fileNames);
  return {
    ...metadata,
    sha256: crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex"),
    size: stat.size,
    fileCount: fileNames.size,
    uncompressedSize: uncompressedBytes,
    scannedAt: nowIso(),
  };
}

class PluginMarket {
  constructor(options = {}) {
    this.baseDir = options.baseDir || __dirname;
    this.publicDir = options.publicDir || path.join(this.baseDir, "public");
    this.submissionDir = path.join(this.baseDir, "plugin_submissions");
    this.packageDir = path.join(this.submissionDir, "packages");
    this.submissionsPath = path.join(this.submissionDir, "submissions.json");
    this.blockedPath = path.join(this.submissionDir, "blocked-plugins.json");
    this.adminUsersPath = path.join(this.baseDir, "admin_users.json");
    this.developerUsersPath = path.join(this.baseDir, "developer_users.json");
    this.publicPluginsDir = path.join(this.publicDir, "plugins");
    this.pluginsManifestPath = path.join(this.publicDir, "plugins.json");
    this.sessions = new Map();
    this.developerSessions = new Map();
    this.bootstrapUser = String(options.bootstrapUser || "").trim();
    this.bootstrapPassword = String(options.bootstrapPassword || "");
    this.writeQueue = Promise.resolve();
    ensureDirectory(this.packageDir);
    ensureDirectory(this.publicPluginsDir);
    if (!fs.existsSync(this.submissionsPath)) {
      writeJsonAtomic(this.submissionsPath, {schemaVersion: 1, submissions: []});
    }
    if (!fs.existsSync(this.blockedPath)) {
      writeJsonAtomic(this.blockedPath, {schemaVersion: 1, plugins: []});
    }
    if (!fs.existsSync(this.developerUsersPath)) {
      writeJsonAtomic(this.developerUsersPath, {schemaVersion: 1, users: []});
    }
  }

  enqueue(operation) {
    const run = this.writeQueue.then(operation, operation);
    this.writeQueue = run.catch(() => {});
    return run;
  }

  readSubmissions() {
    const data = readJson(this.submissionsPath, {schemaVersion: 1, submissions: []});
    return Array.isArray(data.submissions) ? data.submissions : [];
  }

  readBlocked() {
    const data = readJson(this.blockedPath, {schemaVersion: 1, plugins: []});
    return Array.isArray(data.plugins) ? data.plugins : [];
  }

  listSubmissions() {
    return this.readSubmissions().sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)));
  }

  safeSubmission(submission) {
    const safe = {...submission};
    delete safe.trackingTokenHash;
    return safe;
  }

  validateUsername(username) {
    const clean = String(username || "").trim();
    if (!/^[A-Za-z0-9._-]{3,64}$/.test(clean)) {
      throw new Error("用户名需为 3-64 位字母、数字、点、下划线或连字符");
    }
    return clean;
  }

  validatePassword(password) {
    const clean = String(password || "");
    if (clean.length < 10) throw new Error("密码至少需要 10 个字符");
    return clean;
  }

  readAdminUsers() {
    const data = readJson(this.adminUsersPath, {schemaVersion: 1, users: []});
    return Array.isArray(data.users) ? data.users : [];
  }

  readDeveloperUsers() {
    const data = readJson(this.developerUsersPath, {schemaVersion: 1, users: []});
    return Array.isArray(data.users) ? data.users : [];
  }

  adminRegistrationStatus() {
    return {
      registrationOpen: this.readAdminUsers().length === 0
        && Boolean(this.bootstrapUser && this.bootstrapPassword),
    };
  }

  registerBootstrapAdmin(username, password) {
    if (!this.adminRegistrationStatus().registrationOpen) {
      throw new Error("初始管理员注册未开放或已经完成");
    }
    const cleanUsername = this.validateUsername(username);
    if (cleanUsername !== this.bootstrapUser || String(password || "") !== this.bootstrapPassword) {
      throw new Error("初始管理员凭据不正确");
    }
    writeJsonAtomic(this.adminUsersPath, {
      schemaVersion: 1,
      users: [createPasswordUser(cleanUsername, String(password), {
        role: "owner",
        status: "active",
      })],
    });
    return {username: cleanUsername, role: "owner"};
  }

  listAdminUsers() {
    return this.readAdminUsers().map((user) => ({
      username: user.username,
      role: user.role || "admin",
      status: user.status || (user.passwordHash ? "active" : "pending_setup"),
      createdAt: user.createdAt,
      createdBy: user.createdBy || "",
    }));
  }

  createManagedAdmin(input, creator) {
    const username = this.validateUsername(input.username);
    const password = String(input.password || "");
    const users = this.readAdminUsers();
    if (users.some((user) => user.username === username)) throw new Error("管理员用户名已存在");
    if (password) {
      users.push(createPasswordUser(username, this.validatePassword(password), {
        role: "admin",
        status: "active",
        createdBy: creator,
      }));
      writeJsonAtomic(this.adminUsersPath, {schemaVersion: 1, users});
      return {username, status: "active"};
    }
    const setupToken = crypto.randomBytes(18).toString("base64url");
    users.push({
      username,
      role: "admin",
      status: "pending_setup",
      setupTokenHash: crypto.createHash("sha256").update(setupToken).digest("hex"),
      createdAt: nowIso(),
      createdBy: creator,
    });
    writeJsonAtomic(this.adminUsersPath, {schemaVersion: 1, users});
    return {username, status: "pending_setup", setupToken};
  }

  setupManagedAdmin(username, setupToken, password) {
    const cleanUsername = this.validateUsername(username);
    const users = this.readAdminUsers();
    const index = users.findIndex((user) => user.username === cleanUsername);
    const user = users[index];
    if (!user || user.status !== "pending_setup" || !user.setupTokenHash) {
      throw new Error("该管理员账号无需首次设置或不存在");
    }
    const actual = crypto.createHash("sha256").update(String(setupToken || "")).digest("hex");
    if (actual !== user.setupTokenHash) throw new Error("首次设置码不正确");
    users[index] = createPasswordUser(cleanUsername, this.validatePassword(password), {
      role: user.role || "admin",
      status: "active",
      createdAt: user.createdAt || nowIso(),
      createdBy: user.createdBy || "",
      activatedAt: nowIso(),
    });
    writeJsonAtomic(this.adminUsersPath, {schemaVersion: 1, users});
    return {username: cleanUsername};
  }

  authenticate(username, password) {
    const users = this.readAdminUsers();
    const user = users.find((item) => item.username === String(username || "").trim());
    if (!user || (user.status && user.status !== "active") || !verifyPassword(String(password || ""), user)) {
      return null;
    }
    const token = crypto.randomBytes(32).toString("base64url");
    const session = {
      username: user.username,
      role: user.role || "admin",
      createdAt: Date.now(),
      expiresAt: Date.now() + SESSION_TTL_MS,
    };
    this.sessions.set(token, session);
    return {token, username: user.username, role: session.role, expiresAt: new Date(session.expiresAt).toISOString()};
  }

  getSession(token) {
    if (!token) return null;
    const session = this.sessions.get(token);
    if (!session) return null;
    if (session.expiresAt <= Date.now()) {
      this.sessions.delete(token);
      return null;
    }
    return session;
  }

  logout(token) {
    if (token) this.sessions.delete(token);
  }

  registerDeveloper(username, password) {
    const cleanUsername = this.validateUsername(username);
    const cleanPassword = this.validatePassword(password);
    const users = this.readDeveloperUsers();
    if (users.some((user) => user.username === cleanUsername)) throw new Error("开发者用户名已存在");
    users.push(createPasswordUser(cleanUsername, cleanPassword, {status: "active"}));
    writeJsonAtomic(this.developerUsersPath, {schemaVersion: 1, users});
    return {username: cleanUsername};
  }

  authenticateDeveloper(username, password) {
    const users = this.readDeveloperUsers();
    const user = users.find((item) => item.username === String(username || "").trim());
    if (!user || !verifyPassword(String(password || ""), user)) return null;
    const token = crypto.randomBytes(32).toString("base64url");
    const session = {username: user.username, expiresAt: Date.now() + SESSION_TTL_MS};
    this.developerSessions.set(token, session);
    return {token, username: user.username, expiresAt: new Date(session.expiresAt).toISOString()};
  }

  getDeveloperSession(token) {
    const session = token ? this.developerSessions.get(token) : null;
    if (!session) return null;
    if (session.expiresAt <= Date.now()) {
      this.developerSessions.delete(token);
      return null;
    }
    return session;
  }

  logoutDeveloper(token) {
    if (token) this.developerSessions.delete(token);
  }

  developerSubmissions(username) {
    return this.listSubmissions()
      .filter((submission) => submission.ownerUsername === username)
      .map((submission) => this.safeSubmission(submission));
  }

  trackedSubmission(id, trackingToken, username = "") {
    const submission = this.readSubmissions().find((item) => item.id === id);
    if (!submission) throw new Error("提交记录不存在");
    const owned = username && submission.ownerUsername === username;
    const tokenHash = crypto.createHash("sha256").update(String(trackingToken || "")).digest("hex");
    if (!owned && (!submission.trackingTokenHash || tokenHash !== submission.trackingTokenHash)) {
      throw new Error("提交追踪凭据不正确");
    }
    return this.safeSubmission(submission);
  }

  validateSubmissionInput(input, requireDownloadUrl) {
    const downloadUrl = String(input.downloadUrl || "").trim();
    const projectUrl = String(input.projectUrl || "").trim();
    const developerName = String(input.developerName || "").trim();
    const homepage = String(input.homepage || "").trim();
    const notes = String(input.notes || "").trim();
    if (requireDownloadUrl && !downloadUrl) throw new Error("请填写插件下载地址或上传插件包");
    if (!developerName) throw new Error("请填写开发者名称");
    for (const [label, value] of [["项目地址", projectUrl], ["主页", homepage]]) {
      if (value) {
        let parsed;
        try {
          parsed = new URL(value);
        } catch {
          throw new Error(`${label}格式无效`);
        }
        if (!["http:", "https:"].includes(parsed.protocol)) {
          throw new Error(`${label}必须使用 HTTP 或 HTTPS`);
        }
      }
    }
    return {downloadUrl, projectUrl, developerName, homepage, notes};
  }

  async createSubmission(input, source, ownerUsername = "") {
    const values = this.validateSubmissionInput(input, source.type === "url");
    const id = `${Date.now().toString(36)}-${crypto.randomBytes(6).toString("hex")}`;
    const packagePath = path.join(this.packageDir, `${id}.acplugin`);
    const trackingToken = crypto.randomBytes(18).toString("base64url");
    const record = {
      id,
      status: "scanning",
      sourceType: source.type,
      sourceName: source.name || "",
      downloadUrl: values.downloadUrl,
      projectUrl: values.projectUrl,
      developerName: values.developerName,
      homepage: values.homepage,
      notes: values.notes,
      ownerUsername: String(ownerUsername || ""),
      trackingTokenHash: crypto.createHash("sha256").update(trackingToken).digest("hex"),
      createdAt: nowIso(),
      updatedAt: nowIso(),
      scan: null,
      error: "",
      review: null,
    };
    await this.enqueue(() => {
      const submissions = this.readSubmissions();
      submissions.push(record);
      writeJsonAtomic(this.submissionsPath, {schemaVersion: 1, submissions});
    });
    try {
      const sourceResult = await source.prepare(packagePath);
      const scan = await scanPluginPackage(packagePath);
      record.status = "pending";
      record.scan = {...scan, ...sourceResult};
    } catch (error) {
      record.status = "scan_failed";
      record.error = error.message;
      fs.rmSync(packagePath, {force: true});
    } finally {
      if (source.cleanup) source.cleanup();
    }
    record.updatedAt = nowIso();
    await this.enqueue(() => {
      const submissions = this.readSubmissions();
      const index = submissions.findIndex((item) => item.id === id);
      submissions[index] = record;
      writeJsonAtomic(this.submissionsPath, {schemaVersion: 1, submissions});
    });
    const response = {...record, trackingToken};
    delete response.trackingTokenHash;
    return response;
  }

  async submit(input, ownerUsername = "") {
    return this.createSubmission(input, {
      type: "url",
      prepare: async (packagePath) => {
        const download = await downloadPackage(String(input.downloadUrl || "").trim(), packagePath);
        return {finalUrl: download.finalUrl};
      },
    }, ownerUsername);
  }

  async submitUploaded(input, uploadedPath, originalName, ownerUsername = "") {
    const cleanName = path.basename(String(originalName || ""));
    if (path.extname(cleanName).toLowerCase() !== ".acplugin") {
      fs.rmSync(uploadedPath, {force: true});
      throw new Error("上传文件必须使用 .acplugin 扩展名");
    }
    try {
      return await this.createSubmission(input, {
        type: "upload",
        name: cleanName,
        prepare: async (packagePath) => {
          const stat = fs.statSync(uploadedPath);
          if (!stat.isFile() || stat.size <= 0) throw new Error("上传的插件包为空");
          if (stat.size > MAX_PLUGIN_BYTES) throw new Error("插件包大小超过 20MB");
          fs.copyFileSync(uploadedPath, packagePath);
          fs.rmSync(uploadedPath, {force: true});
          return {uploadedFileName: cleanName};
        },
        cleanup: () => fs.rmSync(uploadedPath, {force: true}),
      }, ownerUsername);
    } catch (error) {
      fs.rmSync(uploadedPath, {force: true});
      throw error;
    }
  }

  async review(id, action, reviewer, reason = "") {
    return this.enqueue(() => {
      const submissions = this.readSubmissions();
      const index = submissions.findIndex((item) => item.id === id);
      if (index < 0) throw new Error("提交记录不存在");
      const record = submissions[index];
      if (!record.scan || !record.scan.id) {
        throw new Error("该提交没有可审核的扫描结果");
      }
      const reviewedAt = nowIso();
      if (action === "approve") {
        const source = path.join(this.packageDir, `${id}.acplugin`);
        if (!fs.existsSync(source)) throw new Error("待审插件包不存在");
        const fileName = `${record.scan.id}-${record.scan.versionCode}.acplugin`;
        fs.copyFileSync(source, path.join(this.publicPluginsDir, fileName));
        record.status = "approved";
        record.publicPath = `plugins/${fileName}`;
        this.removeBlockedPlugin(record.scan.id);
      } else if (action === "reject") {
        record.status = "rejected";
      } else if (action === "block") {
        record.status = "blocked";
        this.addBlockedPlugin(record.scan.id, reviewer, reason);
      } else {
        throw new Error("不支持的审核操作");
      }
      record.review = {action, reviewer, reason: String(reason || "").trim(), reviewedAt};
      record.updatedAt = reviewedAt;
      submissions[index] = record;
      writeJsonAtomic(this.submissionsPath, {schemaVersion: 1, submissions});
      this.rebuildPluginsManifest(submissions);
      return record;
    });
  }

  addBlockedPlugin(pluginId, reviewer, reason) {
    const plugins = this.readBlocked().filter((item) => item.id !== pluginId);
    plugins.push({id: pluginId, reviewer, reason: String(reason || "").trim(), blockedAt: nowIso()});
    writeJsonAtomic(this.blockedPath, {schemaVersion: 1, plugins});
  }

  removeBlockedPlugin(pluginId) {
    const plugins = this.readBlocked().filter((item) => item.id !== pluginId);
    writeJsonAtomic(this.blockedPath, {schemaVersion: 1, plugins});
  }

  rebuildPluginsManifest(submissions = this.readSubmissions()) {
    const blocked = new Set(this.readBlocked().map((item) => item.id));
    const latest = new Map();
    for (const record of submissions) {
      if (record.status !== "approved" || !record.scan || blocked.has(record.scan.id) || !record.publicPath) {
        continue;
      }
      const packagePath = path.join(this.publicDir, record.publicPath);
      if (!fs.existsSync(packagePath)) continue;
      const previous = latest.get(record.scan.id);
      if (!previous || record.scan.versionCode > previous.scan.versionCode) {
        latest.set(record.scan.id, record);
      }
    }
    const plugins = [...latest.values()]
      .sort((a, b) => a.scan.name.localeCompare(b.scan.name, "zh-CN"))
      .map((record) => ({
        schemaVersion: record.scan.schemaVersion,
        id: record.scan.id,
        name: record.scan.name,
        versionCode: record.scan.versionCode,
        versionName: record.scan.versionName,
        developer: record.scan.developer,
        capabilities: record.scan.capabilities,
        description: record.scan.description,
        minAppVersionCode: record.scan.minAppVersionCode,
        path: record.publicPath,
        sha256: record.scan.sha256,
        size: record.scan.size,
      }));
    const manifest = {schemaVersion: 1, plugins};
    writeJsonAtomic(this.pluginsManifestPath, manifest);
    return manifest;
  }
}

module.exports = {
  ALLOWED_CAPABILITIES,
  MAX_PLUGIN_BYTES,
  PluginMarket,
  createAdminUser,
  downloadPackage,
  hashPassword,
  scanPluginPackage,
  validateDownloadUrl,
  verifyPassword,
  writeJsonAtomic,
};
