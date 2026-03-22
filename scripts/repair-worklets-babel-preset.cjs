const fs = require("node:fs");
const path = require("node:path");

const projectRoot = path.resolve(__dirname, "..");
const rootPresetEntry = path.join(
  projectRoot,
  "node_modules",
  "@babel",
  "preset-typescript",
  "lib",
  "index.js",
);
const nestedPresetDir = path.join(
  projectRoot,
  "node_modules",
  "react-native-worklets",
  "node_modules",
  "@babel",
  "preset-typescript",
);
const nestedPresetPackageJson = path.join(nestedPresetDir, "package.json");
const nestedPresetEntry = path.join(nestedPresetDir, "lib", "index.js");

function log(message) {
  process.stdout.write(`[repair-worklets-babel] ${message}\n`);
}

if (!fs.existsSync(nestedPresetPackageJson)) {
  log("skip: nested react-native-worklets preset is not installed");
  process.exit(0);
}

if (fs.existsSync(nestedPresetEntry)) {
  log("ok: nested preset entrypoint already exists");
  process.exit(0);
}

if (!fs.existsSync(rootPresetEntry)) {
  process.stderr.write(
    `[repair-worklets-babel] missing source preset entrypoint: ${rootPresetEntry}\n`,
  );
  process.exit(1);
}

fs.mkdirSync(path.dirname(nestedPresetEntry), { recursive: true });
fs.copyFileSync(rootPresetEntry, nestedPresetEntry);

log(`restored ${path.relative(projectRoot, nestedPresetEntry)}`);
