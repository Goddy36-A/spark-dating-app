import { createClient } from 'jsr:@supabase/supabase-js@2'

const ADMIN_ROLES = ['moderator', 'support', 'analyst', 'super_admin']

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, 'Content-Type': 'application/json' },
  })
}

Deno.serve(async (req) => {
  // Browsers send a CORS preflight OPTIONS request before the real POST when
  // custom headers (Authorization, apikey) are involved — without this, the
  // preflight gets no CORS headers, the browser blocks the real request, and
  // supabase-js surfaces it as a generic "Failed to send a request" error.
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  if (req.method !== 'POST') {
    return json({ error: 'Method not allowed' }, 405)
  }

  const authHeader = req.headers.get('Authorization')
  if (!authHeader) {
    return json({ error: 'Missing authorization' }, 401)
  }

  const supabaseUser = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_ANON_KEY')!,
    { global: { headers: { Authorization: authHeader } } },
  )

  const { data: { user: caller }, error: callerErr } = await supabaseUser.auth.getUser()
  if (callerErr || !caller) {
    return json({ error: 'Invalid session' }, 401)
  }

  const { data: callerRow } = await supabaseUser
    .from('users')
    .select('role, is_banned')
    .eq('id', caller.id)
    .single()

  if (!callerRow || callerRow.is_banned || !ADMIN_ROLES.includes(callerRow.role)) {
    return json({ error: 'Forbidden: admin role required' }, 403)
  }

  let body: { email?: string; password?: string; role?: string }
  try {
    body = await req.json()
  } catch {
    return json({ error: 'Invalid JSON body' }, 400)
  }

  const { email, password, role } = body
  if (!email || !password) {
    return json({ error: 'email and password are required' }, 400)
  }
  if (password.length < 8) {
    return json({ error: 'password must be at least 8 characters' }, 400)
  }

  if (role && role !== 'user' && callerRow.role !== 'super_admin') {
    return json({ error: 'Only super_admin can assign elevated roles' }, 403)
  }
  if (role && !ADMIN_ROLES.includes(role) && role !== 'user') {
    return json({ error: 'Invalid role' }, 400)
  }

  const supabaseAdmin = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
  )

  const { data: created, error: createErr } = await supabaseAdmin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
  })

  if (createErr || !created.user) {
    return json({ error: createErr?.message ?? 'Failed to create user' }, 400)
  }

  if (role && role !== 'user') {
    await supabaseAdmin.from('users').update({ role }).eq('id', created.user.id)
  }

  await supabaseAdmin.from('moderation_events').insert({
    target_user_id: created.user.id,
    moderator_id: caller.id,
    action: 'restore',
    reason: `Account created via admin panel${role && role !== 'user' ? ` with role=${role}` : ''}`,
  })

  return json({ id: created.user.id, email: created.user.email }, 200)
})
