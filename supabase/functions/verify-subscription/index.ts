// supabase/functions/verify-subscription/index.ts
// Deploy: supabase functions deploy verify-subscription
// NEVER trust client-side subscription status — always verify with Google Play here.

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const GOOGLE_PLAY_SERVICE_ACCOUNT = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")!

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

// Product ID → tier mapping
const PRODUCT_TIERS: Record<string, string> = {
  "spark_plus_monthly":    "plus",
  "spark_gold_monthly":    "gold",
  "spark_platinum_monthly": "platinum",
  "spark_plus_yearly":     "plus",
  "spark_gold_yearly":     "gold",
}

serve(async (req: Request) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 })

  // Authenticate the caller — must be a valid Supabase user
  const authHeader = req.headers.get("Authorization")
  if (!authHeader) return new Response("Unauthorized", { status: 401 })

  const token = authHeader.replace("Bearer ", "")
  const { data: { user }, error: authError } = await supabase.auth.getUser(token)
  if (authError || !user) return new Response("Unauthorized", { status: 401 })

  try {
    const { purchase_token, product_id, package_name } = await req.json()

    if (!purchase_token || !product_id) {
      return new Response(JSON.stringify({ error: "Missing purchase_token or product_id" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      })
    }

    // 1. Get an OAuth2 access token for Google Play API
    const accessToken = await getGoogleAccessToken()

    // 2. Verify with Google Play Developer API
    const verifyUrl = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${
      package_name ?? "com.spark.dating"
    }/purchases/subscriptions/${product_id}/tokens/${purchase_token}`

    const verifyResponse = await fetch(verifyUrl, {
      headers: { Authorization: `Bearer ${accessToken}` },
    })

    if (!verifyResponse.ok) {
      console.error("Play API error:", await verifyResponse.text())
      return new Response(JSON.stringify({ error: "Purchase verification failed" }), {
        status: 400,
        headers: { "Content-Type": "application/json" },
      })
    }

    const purchaseData = await verifyResponse.json()

    // paymentState: 0=pending, 1=received, 2=free trial, 3=pending deferred
    const isActive = purchaseData.paymentState === 1 || purchaseData.paymentState === 2
    const expiresAt = purchaseData.expiryTimeMillis
      ? new Date(parseInt(purchaseData.expiryTimeMillis)).toISOString()
      : null

    // 3. Upsert subscription record
    const tier = PRODUCT_TIERS[product_id] ?? "free"
    await supabase.from("subscriptions").upsert({
      user_id: user.id,
      tier,
      status: isActive ? "active" : "expired",
      product_id,
      purchase_token,
      expires_at: expiresAt,
    }, { onConflict: "user_id" })

    // 4. Log to audit
    await supabase.from("audit_logs").insert({
      user_id: user.id,
      action: "subscription_verified",
      new_data: { product_id, tier, status: isActive ? "active" : "expired" },
    })

    return new Response(
      JSON.stringify({ success: true, tier, active: isActive, expires_at: expiresAt }),
      { headers: { "Content-Type": "application/json" } },
    )
  } catch (err) {
    console.error("Subscription verification error:", err)
    return new Response(JSON.stringify({ error: "Internal error" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    })
  }
})

async function getGoogleAccessToken(): Promise<string> {
  // Use service account JWT to get an OAuth2 access token
  const serviceAccount = JSON.parse(GOOGLE_PLAY_SERVICE_ACCOUNT)
  const now = Math.floor(Date.now() / 1000)

  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }))
  const payload = btoa(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }))

  // NOTE: In production, use a proper JWT signing library.
  // This is a placeholder — Deno's crypto.subtle supports RSA-PKCSv1.5.
  const tokenRequest = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${payload}.SIGNATURE_PLACEHOLDER`,
    }),
  })

  const { access_token } = await tokenRequest.json()
  return access_token
}
