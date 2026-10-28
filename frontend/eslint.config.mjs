import { defineConfig, globalIgnores } from "eslint/config";
import js from "@eslint/js";
import ts from "typescript-eslint";
import hooks from "eslint-plugin-react-hooks";

export default defineConfig([
  js.configs.recommended, ...ts.configs.recommended,
  hooks.configs.flat.recommended,
  { files: ["**/*.tsx"], languageOptions: {
      globals: { window: "readonly", document: "readonly", fetch: "readonly", process: "readonly", console: "readonly", URL: "readonly", Headers: "readonly", Response: "readonly", crypto: "readonly", FormData: "readonly" },
    } },
  { files: ["**/*.{ts,tsx,mjs}"], languageOptions: { globals: { process: "readonly", console: "readonly" } } },
  globalIgnores([".next/**", "next-env.d.ts", "src/lib/api/schema.d.ts", "playwright-report/**", "test-results/**"]),
]);
