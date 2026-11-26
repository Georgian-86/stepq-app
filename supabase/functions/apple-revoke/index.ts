// Called by the iOS app just before it deletes a member's account, when they signed in with
// Apple. Revokes StepQ's Sign in with Apple tokens, as App Review requires for account deletion
// (Guideline 5.1.1(v)). The app passes a fresh authorization code from re-authenticating.
//
// Secrets: APPLE_TEAM_ID, APPLE_BUNDLE_ID, APPLE_SIWA_KEY_ID and APPLE_SIWA_PRIVATE_KEY (the
// contents of the .p8 key with Sign in with Apple enabled). Never shipped in the app.

import { importPKCS8, SignJWT } from "npm:jose@5";
import { caller, env, json } from "../_shared/apple.ts";

async function clientSecret(): Promise<string> {
  const key = await importPKCS8(env("APPLE_SIWA_PRIVATE_KEY").replace(/\\n/g, "\n"), "ES256");
  return await new SignJWT({})
    .setProtectedHeader({ alg: "ES256", kid: env("APPLE_SIWA_KEY_ID") })
    .setIssuer(env("APPLE_TEAM_ID"))
    .setSubject(env("APPLE_BUNDLE_ID"))
    .setAudience("https://appleid.apple.com")
    .setIssuedAt()
    .setExpirationTime("5m")
    .sign(key);
}

async function appleForm(path: string, body: Record<string, string>): Promise<Response> {
  return await fetch(`https://appleid.apple.com/auth/${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams(body),
  });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  if (!(await caller(req))) return json({ error: "Log in again first." }, 401);
  let code: unknown;
  try {
    code = (await req.json())?.authorizationCode;
  } catch { /* handled below */ }
  if (typeof code !== "string" || !code || code.length > 2_000) return json({ error: "Missing authorization code" }, 400);

  try {
    const clientId = env("APPLE_BUNDLE_ID");
    const secret = await clientSecret();
    const tokenRes = await appleForm("token", {
      grant_type: "authorization_code",
      code,
      client_id: clientId,
      client_secret: secret,
    });
    const tokens = await tokenRes.json().catch(() => ({}));
    const token: string | undefined = tokens.refresh_token ?? tokens.access_token;
    if (!tokenRes.ok || !token) {
      console.error("apple-revoke token", tokenRes.status, tokens.error);
      return json({ error: "Couldn't reach Apple" }, 502);
    }
    const revokeRes = await appleForm("revoke", {
      client_id: clientId,
      client_secret: secret,
      token,
      token_type_hint: tokens.refresh_token ? "refresh_token" : "access_token",
    });
    if (!revokeRes.ok) {
      console.error("apple-revoke revoke", revokeRes.status);
      return json({ error: "Couldn't reach Apple" }, 502);
    }
    return json({ ok: true });
  } catch (e) {
    console.error("apple-revoke", e);
    return json({ error: "Couldn't reach Apple" }, 500);
  }
});
