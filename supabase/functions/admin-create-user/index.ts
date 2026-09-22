import { createClient } from 'jsr:@supabase/supabase-js@2'

const ADMIN_ROLES = ['moderator', 'support', 'analyst', 'super_admin']

Deno.serve(async (req) => {
  if (req.method !== 'POST') {
    return new Response(JSON.stringify({ error: 'Method not allowed' }), { status: 405 })
  }

  const authHeader = req.headers.get('Authorization')
  if (!authHeader) {
    return new Response(JSON.stringify({ error: 'Missing authorization' }), { status: 401 })
  }

  // Client scoped to the caller's own JWT (verify_jwt=true already checked it's a valid
  // session token) — used only to look up who's calling and whether they're an admin.
  const supabaseUser = createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_ANON_KEY')!,
    { global: { headers: { Authorization: authHeader } } },
  )

  const { data: { user: caller }, error: callerErr } = await supabaseUser.auth.getUser()
  if (callerErr || !caller) {
    return new Response(JSON.stringify({ error: 'Invalid session' }), { status: 401 })
  }

  const { data: callerRow } = await supabaseUser
    .from('users')
    .select('role, is_banned')
    .eq('id', caller.id)
    .single()

  if (!callerRow || callerRow.is_banned || !ADMIN_ROLES.includes(callerRow.role)) {
    return new Response(JSON.stringify({ error: 'Forbidden: admin role required' }), { status: 403 })
  }

  let body: { email?: string; password?: string; role?: string }
  try {
    body = await req.json()
  } catch {
    return new Response(JSON.stringify({ error: 'Invalid JSON body' }), { status: 400 })
  }

  const { email, password, role } = body
  if (!email || !password) {
    return new Response(JSON.stringify({ error: 'email and password are required' }), { status: 400 })
  }
  if (password.length < 8) {
    return new Response(JSON.stringify({ error: 'password must be at least 8 characters' }), { status: 400 })
  }

  // Only a super_admin may create another admin-tier account.
  if (role && role !== 'user' && callerRow.role !== 'super_admin') {
    return new Response(JSON.stringify({ error: 'Only super_admin can assign elevated roles' }), { status: 403 })
  }
  if (role && !ADMIN_ROLES.includes(role) && role !== 'user') {
    return new Response(JSON.stringify({ error: 'Invalid role' }), { status: 400 })
  }

  // Service-role client — the powerful key stays server-side inside this function,
  // read from an Edge Function secret, and is never shipped to the browser.
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
    return new Response(JSON.stringify({ error: createErr?.message ?? 'Failed to create user' }), { status: 400 })
  }

  // auth.users trigger already inserted a public.users row with role='user' — bump it
  // if a non-default role was explicitly requested (and authorized above).
  if (role && role !== 'user') {
    await supabaseAdmin.from('users').update({ role }).eq('id', created.user.id)
  }

  // Log it in moderation_events for auditability, same as ban/suspend actions.
  await supabaseAdmin.from('moderation_events').insert({
    target_user_id: created.user.id,
    moderator_id: caller.id,
    action: 'restore',
    reason: `Account created via admin panel${role && role !== 'user' ? ` with role=${role}` : ''}`,
  })

  return new Response(
    JSON.stringify({ id: created.user.id, email: created.user.email }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  )
})
