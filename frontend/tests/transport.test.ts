import { test } from "node:test";
import assert from "node:assert/strict";
import { apiRequest, ApiError, validateMoney } from "../src/lib/api/transport.ts";

test("money conserva precisión y rechaza números y formato local", () => {
  assert.equal(validateMoney({ amount: "999999999999999999.12345", currency: "CRC" }).amount, "999999999999999999.12345");
  assert.throws(() => validateMoney({ amount: "1.000,00", currency: "CRC" }));
  assert.throws(() => validateMoney({ amount: 2 as unknown as string, currency: "CRC" }));
});
test("mutación usa cookie y token CSRF nuevo; conserva la idempotency key", async () => {
  const calls: { path: string; options?: RequestInit }[] = [];
  const fake = (async (input: string, options?: RequestInit) => {
    calls.push({ path: input, options });
    return new Response(JSON.stringify(input.endsWith("/csrf") ? { headerName: "X-CSRF-TOKEN", token: "synthetic" } : { id: "example" }), { status: 200, headers: { "Content-Type": "application/json" } });
  }) as typeof fetch;
  await apiRequest("/api/v1/foundation/resources", { method: "POST", headers: { "Idempotency-Key": "same-intention" }, body: "{}" }, fake);
  assert.equal(calls[0].path, "/api/v1/session/csrf");
  assert.equal(new Headers(calls[1].options?.headers).get("X-CSRF-TOKEN"), "synthetic");
  assert.equal(new Headers(calls[1].options?.headers).get("Idempotency-Key"), "same-intention");
  assert.equal(calls[1].options?.credentials, "same-origin");
});
test("Problem Details y respuesta no JSON producen un error utilizable", async () => {
  const fake = (async () => new Response(JSON.stringify({ detail: "Recurso cambió", code: "VERSION_MISMATCH" }), { status: 412, headers: { "Content-Type": "application/problem+json" } })) as typeof fetch;
  await assert.rejects(apiRequest("/api/v1/example", {}, fake), (error: unknown) => error instanceof ApiError && error.status === 412 && error.problem.code === "VERSION_MISMATCH");
  const gateway = (async () => new Response("<html>Bad gateway</html>", { status: 502 })) as typeof fetch;
  await assert.rejects(apiRequest("/api/v1/example", {}, gateway), (error: unknown) => error instanceof ApiError && error.status === 502);
});
