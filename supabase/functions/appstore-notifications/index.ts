// App Store Server Notifications V2: Apple calls this when a StepQ Pro subscription renews,
// expires, is refunded or revoked, so Pro starts and stops even if the app is never opened.
// Set its URL in App Store Connect (App Information > App Store Server Notifications) for both
// Production and Sandbox. Deploy with --no-verify-jwt: Apple can't send a Supabase token, so
// every request is instead checked against Apple's signature before anything changes.

import { applyTransaction, json, serviceClient, verifyWith } from "../_shared/apple.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  let signedPayload: unknown;
  try {
    signedPayload = (await req.json())?.signedPayload;
  } catch { /* handled below */ }
  if (typeof signedPayload !== "string" || signedPayload.length > 100_000) return json({ error: "Bad request" }, 400);

  try {
    const note = await verifyWith((v) => v.verifyAndDecodeNotification(signedPayload as string));
    const signedTx = note.data?.signedTransactionInfo;
    if (!signedTx) return json({ ok: true }); // e.g. TEST notifications
    const t = await verifyWith((v) => v.verifyAndDecodeTransaction(signedTx));
    let autoRenew: boolean | undefined;
    if (note.data?.signedRenewalInfo) {
      const r = await verifyWith((v) => v.verifyAndDecodeRenewalInfo(note.data!.signedRenewalInfo!));
      autoRenew = r.autoRenewStatus === 1;
    }

    // Who it belongs to: the subscription we already know, else the account token set at purchase.
    const { data: row } = await serviceClient().schema("app").from("store_subscriptions")
      .select("user_id").eq("original_transaction_id", t.originalTransactionId!).maybeSingle();
    const userId = (row?.user_id as string | undefined) ?? t.appAccountToken;
    if (!userId) return json({ ok: true, skipped: "unknown member" });

    await applyTransaction(userId, t, autoRenew);
    return json({ ok: true });
  } catch (e) {
    console.error("appstore-notifications", e);
    // A non-2xx answer makes Apple retry later, which is what we want for a temporary failure.
    return json({ error: "Not processed" }, 500);
  }
});
