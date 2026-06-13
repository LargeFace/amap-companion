"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");
const yazl = require("yazl");
const {
  MAX_PLUGIN_BYTES,
  PluginMarket,
  scanPluginPackage,
} = require("../plugin-market");

function temporaryDirectory(t) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "amap-plugin-market-"));
  t.after(() => fs.rmSync(directory, {recursive: true, force: true}));
  return directory;
}

function createManifest(overrides = {}) {
  return {
    schemaVersion: 1,
    id: "test.example.plugin",
    name: "测试插件",
    versionCode: 3,
    versionName: "1.2.0",
    developer: {name: "测试开发者", homepage: "https://example.com"},
    capabilities: ["ui"],
    entry: {ui: "ui/main.json"},
    description: "用于自动化测试",
    minAppVersionCode: 1,
    ...overrides,
  };
}

function createZip(filePath, entries) {
  return new Promise((resolve, reject) => {
    const zip = new yazl.ZipFile();
    for (const [name, content] of Object.entries(entries)) {
      zip.addBuffer(Buffer.from(content), name);
    }
    zip.end();
    const output = fs.createWriteStream(filePath);
    zip.outputStream.pipe(output);
    zip.outputStream.on("error", reject);
    output.on("error", reject);
    output.on("close", resolve);
  });
}

async function startPackageServer(t, packagePath) {
  const server = http.createServer((req, res) => {
    if (req.url === "/plugin.acplugin") {
      const stat = fs.statSync(packagePath);
      res.writeHead(200, {
        "content-type": "application/octet-stream",
        "content-length": stat.size,
      });
      fs.createReadStream(packagePath).pipe(res);
      return;
    }
    if (req.url === "/oversize.acplugin") {
      res.writeHead(200, {"content-length": MAX_PLUGIN_BYTES + 1});
      res.end();
      return;
    }
    res.writeHead(404);
    res.end();
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));
  return `http://127.0.0.1:${server.address().port}`;
}

test("scanner accepts a valid plugin and extracts market metadata", async (t) => {
  const directory = temporaryDirectory(t);
  const packagePath = path.join(directory, "valid.acplugin");
  await createZip(packagePath, {
    "plugin.json": JSON.stringify(createManifest()),
    "ui/main.json": JSON.stringify({type: "column"}),
    "README.md": "test",
  });

  const scan = await scanPluginPackage(packagePath);
  assert.equal(scan.id, "test.example.plugin");
  assert.equal(scan.versionCode, 3);
  assert.deepEqual(scan.capabilities, ["ui"]);
  assert.match(scan.sha256, /^[a-f0-9]{64}$/);
  assert.equal(scan.fileCount, 3);
});

test("scanner rejects forbidden files and missing capability entries", async (t) => {
  const directory = temporaryDirectory(t);
  const forbiddenPath = path.join(directory, "forbidden.acplugin");
  await createZip(forbiddenPath, {
    "plugin.json": JSON.stringify(createManifest()),
    "ui/main.json": "{}",
    "payload.js": "alert(1)",
  });
  await assert.rejects(scanPluginPackage(forbiddenPath), /禁止的文件类型/);

  const missingPath = path.join(directory, "missing.acplugin");
  await createZip(missingPath, {
    "plugin.json": JSON.stringify(createManifest()),
  });
  await assert.rejects(scanPluginPackage(missingPath), /入口文件不存在/);
});

test("bootstrap admin can register once with the exact environment credentials", (t) => {
  const directory = temporaryDirectory(t);
  const publicDir = path.join(directory, "public");
  const market = new PluginMarket({
    baseDir: directory,
    publicDir,
    bootstrapUser: "admin",
    bootstrapPassword: "correct-password",
  });
  assert.deepEqual(market.adminRegistrationStatus(), {registrationOpen: true});
  assert.throws(
    () => market.registerBootstrapAdmin("admin", "wrong-password"),
    /凭据不正确/,
  );
  market.registerBootstrapAdmin("admin", "correct-password");
  const users = JSON.parse(fs.readFileSync(path.join(directory, "admin_users.json"), "utf8"));
  assert.equal(users.users[0].username, "admin");
  assert.equal(users.users[0].role, "owner");
  assert.equal("password" in users.users[0], false);
  assert.notEqual(users.users[0].passwordHash, "correct-password");
  assert.equal(market.authenticate("admin", "wrong-password"), null);
  assert.ok(market.authenticate("admin", "correct-password"));
  assert.deepEqual(market.adminRegistrationStatus(), {registrationOpen: false});
  assert.throws(
    () => market.registerBootstrapAdmin("admin", "correct-password"),
    /已经完成/,
  );

  const second = new PluginMarket({
    baseDir: directory,
    publicDir,
    bootstrapUser: "replacement",
    bootstrapPassword: "replacement-password",
  });
  assert.equal(second.authenticate("replacement", "replacement-password"), null);
  assert.ok(second.authenticate("admin", "correct-password"));
});

test("administrators can create active or first-login setup accounts", (t) => {
  const directory = temporaryDirectory(t);
  const market = new PluginMarket({baseDir: directory, publicDir: path.join(directory, "public")});
  fs.writeFileSync(path.join(directory, "admin_users.json"), JSON.stringify({
    schemaVersion: 1,
    users: [],
  }));

  const active = market.createManagedAdmin({
    username: "reviewer",
    password: "reviewer-password",
  }, "owner");
  assert.equal(active.status, "active");
  assert.ok(market.authenticate("reviewer", "reviewer-password"));

  const pending = market.createManagedAdmin({username: "publisher"}, "owner");
  assert.equal(pending.status, "pending_setup");
  assert.ok(pending.setupToken);
  assert.equal(market.authenticate("publisher", "publisher-password"), null);
  assert.throws(
    () => market.setupManagedAdmin("publisher", "wrong-token", "publisher-password"),
    /设置码不正确/,
  );
  market.setupManagedAdmin("publisher", pending.setupToken, "publisher-password");
  assert.ok(market.authenticate("publisher", "publisher-password"));
  assert.equal(market.listAdminUsers().length, 2);
});

test("developers can own submissions while anonymous submitters use a tracking token", async (t) => {
  const directory = temporaryDirectory(t);
  const publicDir = path.join(directory, "public");
  const sourcePackage = path.join(directory, "source.acplugin");
  await createZip(sourcePackage, {
    "plugin.json": JSON.stringify(createManifest()),
    "ui/main.json": "{}",
  });
  const baseUrl = await startPackageServer(t, sourcePackage);
  const market = new PluginMarket({baseDir: directory, publicDir});

  market.registerDeveloper("plugin-author", "developer-password");
  assert.ok(market.authenticateDeveloper("plugin-author", "developer-password"));

  const owned = await market.submit({
    downloadUrl: `${baseUrl}/plugin.acplugin`,
    developerName: "Developer",
  }, "plugin-author");
  assert.equal(owned.ownerUsername, "plugin-author");
  assert.equal(market.developerSubmissions("plugin-author")[0].id, owned.id);
  assert.equal("trackingTokenHash" in market.developerSubmissions("plugin-author")[0], false);

  const anonymous = await market.submit({
    downloadUrl: `${baseUrl}/plugin.acplugin`,
    developerName: "Anonymous",
  });
  assert.ok(anonymous.trackingToken);
  assert.equal(
    market.trackedSubmission(anonymous.id, anonymous.trackingToken).id,
    anonymous.id,
  );
  assert.throws(
    () => market.trackedSubmission(anonymous.id, "wrong-token"),
    /追踪凭据不正确/,
  );
});

test("submission approval publishes a package and blocking removes it from market", async (t) => {
  const directory = temporaryDirectory(t);
  const publicDir = path.join(directory, "public");
  const sourcePackage = path.join(directory, "source.acplugin");
  await createZip(sourcePackage, {
    "plugin.json": JSON.stringify(createManifest()),
    "ui/main.json": "{}",
  });
  const baseUrl = await startPackageServer(t, sourcePackage);
  const market = new PluginMarket({baseDir: directory, publicDir});

  const submission = await market.submit({
    downloadUrl: `${baseUrl}/plugin.acplugin`,
    projectUrl: "https://example.com/source",
    developerName: "表单开发者",
    homepage: "https://example.com",
    notes: "测试提交",
  });
  assert.equal(submission.status, "pending");
  assert.equal(submission.scan.id, "test.example.plugin");

  const approved = await market.review(submission.id, "approve", "admin", "结构有效");
  assert.equal(approved.status, "approved");
  assert.ok(fs.existsSync(path.join(publicDir, "plugins", "test.example.plugin-3.acplugin")));
  let manifest = JSON.parse(fs.readFileSync(path.join(publicDir, "plugins.json"), "utf8"));
  assert.equal(manifest.plugins.length, 1);
  assert.equal(manifest.plugins[0].id, "test.example.plugin");

  const blocked = await market.review(submission.id, "block", "admin", "安全下架");
  assert.equal(blocked.status, "blocked");
  manifest = JSON.parse(fs.readFileSync(path.join(publicDir, "plugins.json"), "utf8"));
  assert.deepEqual(manifest.plugins, []);
  assert.equal(market.readBlocked()[0].id, "test.example.plugin");
});

test("uploaded packages use the same scan and approval pipeline", async (t) => {
  const directory = temporaryDirectory(t);
  const publicDir = path.join(directory, "public");
  const uploadedPath = path.join(directory, "browser-upload.acplugin");
  await createZip(uploadedPath, {
    "plugin.json": JSON.stringify(createManifest({
      id: "test.uploaded.plugin",
      versionCode: 4,
      versionName: "2.0.0",
    })),
    "ui/main.json": "{}",
  });
  const market = new PluginMarket({baseDir: directory, publicDir});

  const submission = await market.submitUploaded({
    projectUrl: "https://example.com/uploaded",
    developerName: "上传开发者",
    homepage: "https://example.com",
    notes: "浏览器直接上传",
  }, uploadedPath, "uploaded-plugin.acplugin");

  assert.equal(submission.status, "pending");
  assert.equal(submission.sourceType, "upload");
  assert.equal(submission.sourceName, "uploaded-plugin.acplugin");
  assert.equal(submission.scan.id, "test.uploaded.plugin");
  assert.equal(fs.existsSync(uploadedPath), false);

  await market.review(submission.id, "approve", "admin", "上传审核通过");
  const manifest = JSON.parse(fs.readFileSync(path.join(publicDir, "plugins.json"), "utf8"));
  assert.equal(manifest.plugins[0].path, "plugins/test.uploaded.plugin-4.acplugin");
  assert.ok(fs.existsSync(path.join(publicDir, manifest.plugins[0].path)));
});

test("oversized downloads are recorded as scan failures", async (t) => {
  const directory = temporaryDirectory(t);
  const placeholder = path.join(directory, "placeholder.acplugin");
  fs.writeFileSync(placeholder, "zip");
  const baseUrl = await startPackageServer(t, placeholder);
  const market = new PluginMarket({baseDir: directory, publicDir: path.join(directory, "public")});
  const submission = await market.submit({
    downloadUrl: `${baseUrl}/oversize.acplugin`,
    developerName: "测试开发者",
  });
  assert.equal(submission.status, "scan_failed");
  assert.match(submission.error, /20MB/);
});
