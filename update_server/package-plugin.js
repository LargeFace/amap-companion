"use strict";

const fs = require("fs");
const path = require("path");
const yazl = require("yazl");

const sourceDir = path.resolve(process.argv[2] || "");
const outputPath = path.resolve(process.argv[3] || "");

if (!process.argv[2] || !process.argv[3]) {
  console.error("Usage: npm run plugin:pack -- <source-directory> <output.acplugin>");
  process.exit(1);
}
if (!fs.existsSync(path.join(sourceDir, "plugin.json"))) {
  console.error("Source directory must contain plugin.json.");
  process.exit(1);
}
if (path.extname(outputPath).toLowerCase() !== ".acplugin") {
  console.error("Output file must use the .acplugin extension.");
  process.exit(1);
}

const ignoredNames = new Set(["README.md", "PUT_FONT_HERE.txt", "PUT_IMAGES_HERE.txt"]);
const files = [];

function collect(directory, relativeDirectory = "") {
  for (const entry of fs.readdirSync(directory, {withFileTypes: true})) {
    if (ignoredNames.has(entry.name)) continue;
    const absolute = path.join(directory, entry.name);
    const relative = path.posix.join(relativeDirectory, entry.name);
    if (entry.isDirectory()) {
      collect(absolute, relative);
    } else if (entry.isFile()) {
      files.push({absolute, relative});
    }
  }
}

collect(sourceDir);
fs.mkdirSync(path.dirname(outputPath), {recursive: true});
const zip = new yazl.ZipFile();
for (const file of files) {
  zip.addFile(file.absolute, file.relative);
}
zip.end();

const output = fs.createWriteStream(outputPath);
zip.outputStream.pipe(output);
zip.outputStream.on("error", (error) => {
  console.error(error.message);
  process.exitCode = 1;
});
output.on("error", (error) => {
  console.error(error.message);
  process.exitCode = 1;
});
output.on("close", () => {
  console.log(`Created ${outputPath}`);
});
