// Shared by the iOS Edge Functions: checking Apple's signed App Store data and applying it.
//
// Secrets (supabase secrets set ...), none of which ever ship in the app:
//   APPLE_BUNDLE_ID   in.stepq.app
//   APPLE_APP_ID      the app's numeric Apple ID from App Store Connect (needed for Production)
// SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase.

import {
  Environment,
  SignedDataVerifier,
  type JWSTransactionDecodedPayload,
} from "npm:@apple/app-store-server-library@1";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { Buffer } from "node:buffer";

export const PRODUCT_IDS = new Set(["in.stepq.app.pro.monthly", "in.stepq.app.pro.yearly"]);

// Apple Root CA - G3 signs every App Store transaction. It is downloaded from Apple and must
// match this SHA-256 fingerprint, so a swapped certificate can never be trusted.
const ROOT_URL = "https://www.apple.com/certificateauthority/AppleRootCA-G3.cer";
const ROOT_SHA256 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179";

let roots: Promise<Uint8Array[]> | null = null;

function loadRoots(): Promise<Uint8Array[]> {
  roots ??= (async () => {
    const res = await fetch(ROOT_URL);
    if (!res.ok) throw new Error(`Couldn't download Apple's root certificate (${res.status})`);
    const der = new Uint8Array(await res.arrayBuffer());
    const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", der));
    const hex = Array.from(hash, (b) => b.toString(16).padStart(2, "0")).join("");
    if (hex !== ROOT_SHA256) throw new Error("Apple's root certificate didn't match the pinned fingerprint");
    return [der];
  })().catch((e) => {
    roots = null;
    throw e;
  });
  return roots;
}

export function env(name: string): string {
  const v = Deno.env.get(name);
  if (!v) throw new Error(`Missing secret ${name}`);
  return v;
}

/** Verifiers for Production and Sandbox. TestFlight and review purchases are Sandbox. */
export async function verifiers(): Promise<SignedDataVerifier[]> {
  const certs = (await loadRoots()).map((c) => Buffer.from(c));
  const bundleId = env("APPLE_BUNDLE_ID");
  const appId = Deno.env.get("APPLE_APP_ID");
  const list: SignedDataVerifier[] = [];
  if (appId) list.push(new SignedDataVerifier(certs, true, Environment.PRODUCTION, bundleId, Number(appId)));
  list.push(new SignedDataVerifier(certs, true, Environment.SANDBOX, bundleId));
  return list;
}

/** Tries each environment in turn and returns the first that verifies, or throws. */
export async function verifyWith<T>(fn: (v: SignedDataVerifier) => Promise<T>): Promise<T> {
  let last: unknown;
  for (const v of await verifiers()) {
    try {
      return await fn(v);
    } catch (e) {
      last = e;
    }
  }
  throw last ?? new Error("Couldn't verify");
}

export function serviceClient(): SupabaseClient {
  return createClient(env("SUPABASE_URL"), env("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/** The signed-in StepQ member calling the function, from their access token, or null. */
export async function caller(req: Request): Promise<string | null> {
  const auth = req.headers.get("Authorization") ?? "";
  const token = auth.replace(/^Bearer\s+/i, "");
  if (!token) return null;
  const { data, error } = await serviceClient().auth.getUser(token);
  return error || !data.user ? null : data.user.id;
}

/**
 * Records one verified subscription transaction for [userId] and returns the plan now in
 * force. Throws with a message for people when the subscription belongs to another account.
 */
export async function applyTransaction(userId: string, t: JWSTransactionDecodedPayload, autoRenew?: boolean) {
  if (!t.originalTransactionId || !t.productId || !PRODUCT_IDS.has(t.productId)) {
    throw new UserError("That isn't a StepQ Pro subscription.");
  }
  const expires = t.expiresDate ? new Date(t.expiresDate).toISOString() : null;
  const revoked = t.revocationDate ? new Date(t.revocationDate).toISOString() : null;
  const { data, error } = await serviceClient().schema("app").rpc("apply_store_subscription", {
    p_user: userId,
    p_original_transaction_id: t.originalTransactionId,
    p_product_id: t.productId,
    p_environment: t.environment ?? "Sandbox",
    p_expires_at: expires,
    p_revoked_at: revoked,
    p_auto_renew: autoRenew ?? null,
  });
  if (error) {
    if (error.code === "42501" || error.code === "P0002") throw new UserError(error.message);
    throw new Error(error.message);
  }
  return { plan: data as string, expiresAt: expires };
}

/** An error whose message is written for the member and is safe to show them. */
export class UserError extends Error {}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
