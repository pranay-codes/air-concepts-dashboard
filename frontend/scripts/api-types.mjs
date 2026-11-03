// Generates TypeScript types from the committed OpenAPI snapshot.
// With --check, fails if the committed output differs from a fresh generation.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import openapiTS, { astToString } from "openapi-typescript";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const snapshot = resolve(frontendRoot, "..", "docs", "api", "openapi.json");
const output = resolve(frontendRoot, "src", "api", "generated", "schema.d.ts");
const checkOnly = process.argv.includes("--check");

const schema = JSON.parse(readFileSync(snapshot, "utf8"));
const generated = astToString(await openapiTS(schema));

if (checkOnly) {
  const committed = existsSync(output) ? readFileSync(output, "utf8") : null;
  if (committed !== generated) {
    console.error(
      "FAIL: src/api/generated/schema.d.ts differs from docs/api/openapi.json. " +
        "Run npm run api:generate in /frontend and commit both files together.",
    );
    process.exit(1);
  }
  console.log("OK: generated API types match docs/api/openapi.json");
} else {
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, generated);
  console.log("OK: wrote src/api/generated/schema.d.ts");
}
