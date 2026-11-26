// Called by the iOS app after a StepQ Pro purchase, renewal or restore. Checks the transaction
// Apple signed, that it belongs to this app and to the signed-in member, then updates their plan.
// The app only unlocks Pro once this answers, so Pro can never be granted from the phone alone.

import { applyTransaction, caller, json, UserError, verifyWith } from "../_shared/apple.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const userId = await caller(req);
  if (!userId) return json({ error: "Log in again first." }, 401);

  let signed: string | undefined;
  try {
    signed = (await req.json())?.signedTransaction;
  } catch { /* handled below */ }
  if (typeof signed !== "string" || signed.length > 20_000) {
    return json({ error: "That purchase couldn't be read. Try Restore purchases." }, 400);
  }

  try {
    const t = await verifyWith((v) => v.verifyAndDecodeTransaction(signed!));
    // The app sets appAccountToken to the member's id when buying. A purchase made for another
    // StepQ account (or with none, e.g. bought before signing in) can't be claimed here.
    if (t.appAccountToken && t.appAccountToken.toLowerCase() !== userId.toLowerCase()) {
      return json({ error: "This App Store subscription belongs to another StepQ account." }, 403);
    }
    return json(await applyTransaction(userId, t));
  } catch (e) {
    if (e instanceof UserError) return json({ error: e.message }, 403);
    console.error("appstore-verify", e);
    return json({ error: "We couldn't confirm the purchase with the App Store. Try Restore purchases in a minute." }, 400);
  }
});
