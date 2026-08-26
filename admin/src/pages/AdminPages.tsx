import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { supabase } from '../App'
import { Search } from 'lucide-react'

// ── Users Page ────────────────────────────────────────────────────────────────

type AdminUser = {
  id: string
  email: string
  role: string
  is_banned: boolean
  is_suspended: boolean
  onboarding_complete: boolean
  created_at: string
}

async function searchUsers(query: string): Promise<AdminUser[]> {
  if (!query.trim()) {
    const { data } = await supabase
      .from('users')
      .select('*')
      .order('created_at', { ascending: false })
      .limit(30)
    return data ?? []
  }
  const { data } = await supabase
    .from('users')
    .select('*')
    .ilike('email', `%${query}%`)
    .limit(30)
  return data ?? []
}

export function UsersPage() {
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const qc = useQueryClient()

  const { data: users, isLoading } = useQuery({
    queryKey: ['admin-users', debouncedQuery],
    queryFn: () => searchUsers(debouncedQuery),
  })

  const suspendMutation = useMutation({
    mutationFn: async ({ userId, suspend }: { userId: string; suspend: boolean }) => {
      const { data: { user } } = await supabase.auth.getUser()
      await supabase.from('users')
        .update({ is_suspended: suspend, suspend_until: suspend ? new Date(Date.now() + 7 * 86400000).toISOString() : null })
        .eq('id', userId)
      await supabase.from('moderation_events').insert({
        target_user_id: userId,
        moderator_id: user!.id,
        action: suspend ? 'suspend' : 'restore',
        reason: suspend ? '7-day suspension via admin panel' : 'Suspension lifted',
      })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const banMutation = useMutation({
    mutationFn: async ({ userId, ban }: { userId: string; ban: boolean }) => {
      const { data: { user } } = await supabase.auth.getUser()
      await supabase.from('users')
        .update({ is_banned: ban, ban_reason: ban ? 'Banned via admin panel' : null })
        .eq('id', userId)
      await supabase.from('moderation_events').insert({
        target_user_id: userId,
        moderator_id: user!.id,
        action: ban ? 'ban' : 'restore',
        reason: ban ? 'Permanent ban via admin panel' : 'Ban lifted',
      })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
    setQuery(e.target.value)
    clearTimeout((window as any)._searchTimer)
    ;(window as any)._searchTimer = setTimeout(() => setDebouncedQuery(e.target.value), 400)
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-white mb-6">Users</h1>

      <div className="relative mb-6">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
        <input
          value={query}
          onChange={handleSearch}
          placeholder="Search by email…"
          className="w-full max-w-sm bg-gray-800 border border-gray-700 rounded-lg pl-10 pr-4 py-2.5 text-sm text-white outline-none focus:border-violet-500 transition-colors"
        />
      </div>

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-gray-400">Searching…</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800 text-gray-400">
                <th className="text-left px-5 py-3">Email</th>
                <th className="text-left px-5 py-3">Joined</th>
                <th className="text-left px-5 py-3">Status</th>
                <th className="text-left px-5 py-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users?.map(user => (
                <tr key={user.id} className="border-b border-gray-800/50 hover:bg-gray-800/20">
                  <td className="px-5 py-3">
                    <div className="text-gray-200">{user.email}</div>
                    <div className="text-xs text-gray-500 font-mono">{user.id.slice(0, 8)}…</div>
                  </td>
                  <td className="px-5 py-3 text-gray-400">
                    {new Date(user.created_at).toLocaleDateString()}
                  </td>
                  <td className="px-5 py-3">
                    {user.is_banned ? (
                      <span className="text-xs bg-red-900/40 text-red-300 px-2 py-0.5 rounded-full">Banned</span>
                    ) : user.is_suspended ? (
                      <span className="text-xs bg-amber-900/40 text-amber-300 px-2 py-0.5 rounded-full">Suspended</span>
                    ) : (
                      <span className="text-xs bg-green-900/40 text-green-300 px-2 py-0.5 rounded-full">Active</span>
                    )}
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex gap-2">
                      {!user.is_banned && (
                        <button
                          onClick={() => suspendMutation.mutate({ userId: user.id, suspend: !user.is_suspended })}
                          className="text-xs px-2.5 py-1 rounded bg-amber-900/40 text-amber-300 hover:bg-amber-900/70 transition-colors"
                        >
                          {user.is_suspended ? 'Unsuspend' : 'Suspend 7d'}
                        </button>
                      )}
                      <button
                        onClick={() => {
                          const action = user.is_banned ? 'unban' : 'ban'
                          if (confirm(`${action} ${user.email}?`)) {
                            banMutation.mutate({ userId: user.id, ban: !user.is_banned })
                          }
                        }}
                        className={`text-xs px-2.5 py-1 rounded transition-colors ${
                          user.is_banned
                            ? 'bg-green-900/40 text-green-300 hover:bg-green-900/70'
                            : 'bg-red-900/40 text-red-300 hover:bg-red-900/70'
                        }`}
                      >
                        {user.is_banned ? 'Unban' : 'Ban'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

// ── Moderation Page ───────────────────────────────────────────────────────────

type ModerationEvent = {
  id: string
  target_user_id: string
  moderator_id: string
  action: string
  reason: string
  created_at: string
}

async function fetchModerationLog(): Promise<ModerationEvent[]> {
  const { data } = await supabase
    .from('moderation_events')
    .select('*')
    .order('created_at', { ascending: false })
    .limit(100)
  return data ?? []
}

const ACTION_COLORS: Record<string, string> = {
  ban:      'bg-red-900/40 text-red-300',
  suspend:  'bg-amber-900/40 text-amber-300',
  restore:  'bg-green-900/40 text-green-300',
  warning:  'bg-blue-900/40 text-blue-300',
  restrict: 'bg-orange-900/40 text-orange-300',
  dismiss:  'bg-gray-800 text-gray-400',
}

export function ModerationPage() {
  const { data: events, isLoading } = useQuery({
    queryKey: ['moderation-log'],
    queryFn: fetchModerationLog,
  })

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-white mb-6">Moderation Log</h1>

      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        {isLoading ? (
          <div className="p-8 text-center text-gray-400">Loading…</div>
        ) : events?.length === 0 ? (
          <div className="p-8 text-center text-gray-400">No moderation events yet</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-800 text-gray-400">
                <th className="text-left px-5 py-3">Action</th>
                <th className="text-left px-5 py-3">Target User</th>
                <th className="text-left px-5 py-3">Reason</th>
                <th className="text-left px-5 py-3">When</th>
              </tr>
            </thead>
            <tbody>
              {events?.map(ev => (
                <tr key={ev.id} className="border-b border-gray-800/50 hover:bg-gray-800/20">
                  <td className="px-5 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full capitalize ${ACTION_COLORS[ev.action] ?? 'bg-gray-800 text-gray-300'}`}>
                      {ev.action}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-gray-300 font-mono text-xs">
                    {ev.target_user_id.slice(0, 12)}…
                  </td>
                  <td className="px-5 py-3 text-gray-400 max-w-xs truncate">{ev.reason}</td>
                  <td className="px-5 py-3 text-gray-500">
                    {new Date(ev.created_at).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
