import { cpSync } from "node:fs";
import { spawn } from "node:child_process";
import { resolve } from "node:path";
cpSync("public", ".next/standalone/public", { recursive: true });
cpSync(".next/static", ".next/standalone/.next/static", { recursive: true });
const server = spawn(process.execPath, [resolve(".next/standalone/server.js")], {
  stdio: "inherit", env: { ...process.env, HOSTNAME: "127.0.0.1" },
});
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => server.kill(signal));
server.on("exit", (code) => process.exit(code ?? 1));
