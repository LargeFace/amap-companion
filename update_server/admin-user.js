"use strict";

require("dotenv").config({path: require("path").join(__dirname, ".env")});

const fs = require("fs");
const path = require("path");
const {createAdminUser, writeJsonAtomic} = require("./plugin-market");

const username = String(process.argv[2] || "").trim();
const password = String(process.env.ADMIN_PASSWORD || "");
const renameFrom = String(process.env.ADMIN_RENAME_FROM || "").trim();
const dataDir = process.env.MARKET_DATA_DIR || __dirname;
const usersPath = path.join(dataDir, "admin_users.json");

if (!/^[A-Za-z0-9._-]{3,64}$/.test(username)) {
  console.error("Usage: set ADMIN_PASSWORD, then run: npm run admin:user -- <username>");
  console.error("Username must be 3-64 characters: letters, numbers, dot, underscore, or hyphen.");
  process.exit(1);
}
if (password.length < 10) {
  console.error("ADMIN_PASSWORD must contain at least 10 characters.");
  process.exit(1);
}

let data = {schemaVersion: 1, users: []};
if (fs.existsSync(usersPath)) {
  data = JSON.parse(fs.readFileSync(usersPath, "utf8"));
}
const users = Array.isArray(data.users) ? data.users : [];
const nextUser = createAdminUser(username, password);
if (renameFrom && renameFrom !== username) {
  const oldIndex = users.findIndex((user) => user.username === renameFrom);
  if (oldIndex >= 0) {
    users.splice(oldIndex, 1);
  }
}
const index = users.findIndex((user) => user.username === username);
if (index >= 0) {
  users[index] = nextUser;
} else {
  users.push(nextUser);
}
writeJsonAtomic(usersPath, {schemaVersion: 1, users});
console.log(`${index >= 0 ? "Updated" : "Created"} administrator: ${username}`);
