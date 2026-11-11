import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync, symlinkSync } from "node:fs";
if (!existsSync(".env")) {
  const template = readFileSync(".env.example", "utf8").replaceAll(
    "replace-with-random-secret",
    () => randomBytes(32).toString("hex"),
  );
  writeFileSync(".env", template, { mode: 0o600 });
  console.log("Created local .env with independent random secrets.");
} else console.log("Preserving existing .env.");
if (!existsSync("infra/.env")) symlinkSync("../.env", "infra/.env");
