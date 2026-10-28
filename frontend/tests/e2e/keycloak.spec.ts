import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

test.skip(!process.env.ATO_E2E_KEYCLOAK, "Requiere el entorno local PostgreSQL + Keycloak + Spring");
test("OIDC real sin MFA obligatorio, cookie, invitación y cierre de sesión", async ({ page }) => {
  test.setTimeout(60000);
  const accounts = readFileSync(resolve("../.m0-local/accounts.txt"), "utf8");
  const password = accounts.split("\n").find((line) => line.startsWith("gerente@example.invalid: "))!.split(": ")[1];
  await page.goto("http://localhost:3000/auth/login");
  await page.locator("#username").fill("gerente");
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await expect(page).toHaveURL(/\/fundamentos$/);
  const session = await page.request.get("http://localhost:3000/api/v1/session");
  expect(session.status()).toBe(200);
  const actor = await session.json();
  expect(actor.roles).toContain("GERENTE"); expect(actor.mfa).toBe(false);
  const cookies = await page.context().cookies(); const cookie = cookies.find((item) => item.name === "ATOSESSION")!;
  expect(cookie.httpOnly).toBe(true); expect(cookie.sameSite).toBe("Lax");
  const denied = await page.request.post("http://localhost:3000/api/v1/foundation/resources", { data: { name: "Sin CSRF" } }); expect(denied.status()).toBe(403);
  const csrf = await (await page.request.get("http://localhost:3000/api/v1/session/csrf")).json();
  const headers = { [csrf.headerName]: csrf.token };
  const invitation = await page.request.post("http://localhost:3000/api/v1/invitations", { headers, data: { email: "cliente-a@example.invalid", role: "CLIENTE" } });
  expect(invitation.status()).toBe(201);
  const logout = await page.request.post("http://localhost:3000/auth/logout", { headers }); expect(logout.status()).toBe(204);
  expect((await page.request.get("http://localhost:3000/api/v1/session")).status()).toBe(401);
});
