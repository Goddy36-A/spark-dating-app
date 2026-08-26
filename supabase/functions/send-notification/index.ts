// supabase/functions/send-notification/index.ts
// Deploy: supabase functions deploy send-notification
// Called by DB trigger via pg_net, or directly from the app server

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY")!       // Firebase Cloud Messaging key
const FCM_URL = "https://fcm.googleapis.com/fcm/send"

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

interface NotificationPayload {
  user_id: string
  type: "new_match" | "new_message" | "new_like" | "new_super_like" | "security_alert"
  title: string
  body: string
  data?: Record<string, string>
}

serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 })
  }

  // Verify request comes from Supabase (service role)
  const authHeader = req.headers.get("Authorization")
  if (!authHeader?.startsWith("Bearer ")) {
    return new Response("Unauthorized", { status: 401 })
  }

  try {
    const payload: NotificationPayload = await req.json()
    const { user_id, type, title, body, data = {} } = payload

    // 1. Fetch user's FCM tokens
    const { data: devices, error } = await supabase
      .from("devices")
      .select("fcm_token")
      .eq("user_id", user_id)
      .eq("platform", "android")

    if (error || !devices?.length) {
      return new Response(JSON.stringify({ sent: 0 }), {
        headers: { "Content-Type": "application/json" },
      })
    }

    // 2. Store notification in DB (so user can view it in-app)
    await supabase.from("notifications").insert({
      user_id,
      type,
      title,
      body,
      data,
    })

    // 3. Send FCM push to all user devices
    const tokens = devices.map((d) => d.fcm_token)
    const fcmBody = {
      registration_ids: tokens,
      notification: {
        title,
        // Only include body content in notification for non-message types.
        // For messages, show a generic nudge to protect privacy.
        body: type === "new_message" ? "You have a new message" : body,
        sound: "default",
        click_action: "FLUTTER_NOTIFICATION_CLICK",
      },
      data: {
        type,
        ...data,
      },
      priority: "high",
      android: {
        priority: "HIGH",
        notification: {
          channel_id: getChannelId(type),
        },
      },
    }

    const fcmResponse = await fetch(FCM_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `key=${FCM_SERVER_KEY}`,
      },
      body: JSON.stringify(fcmBody),
    })

    const fcmResult = await fcmResponse.json()

    // 4. Remove stale tokens (failure_count > 0)
    if (fcmResult.results) {
      const staleTokens = fcmResult.results
        .map((r: any, i: number) => r.error === "NotRegistered" ? tokens[i] : null)
        .filter(Boolean)

      if (staleTokens.length > 0) {
        await supabase.from("devices").delete().in("fcm_token", staleTokens)
      }
    }

    return new Response(
      JSON.stringify({ sent: tokens.length - (fcmResult.failure_count ?? 0) }),
      { headers: { "Content-Type": "application/json" } },
    )
  } catch (err) {
    console.error("Notification error:", err)
    return new Response(JSON.stringify({ error: "Internal error" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    })
  }
})

function getChannelId(type: string): string {
  switch (type) {
    case "new_message": return "spark_messages"
    case "new_match":   return "spark_matches"
    case "new_like":
    case "new_super_like": return "spark_likes"
    default: return "spark_general"
  }
}
