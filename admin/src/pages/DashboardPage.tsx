import { useQuery } from '@tanstack/react-query'
import { supabase } from '../App'
import { Users, Heart, MessageSquare, Flag } from 'lucide-react'

async function fetchStats() {
  const [users, matches, messages, reports] = await Promise.all([
    supabase.from('users').select('id', { count: 'exact', head: true }),
    supabase.from('matches').select('id', { count: 'exact', head: true }),
    supabase.from('messages').select('id', { count: 'exact', head: true }),
    supabase.from('reports').select('id', { count: 'exact', head: true }).eq('status', 'pending'),
  ])
  return {
    users: users.count ?? 0,
    matches: matches.count ?? 0,
    messages: messages.count ?? 0,
    pendingReports: reports.count ?? 0,
  }
}

async function fetchRecentUsers() {
  const { data } = await supabase
    .from('users')
    .select('id, email, created_at, role, is_banned, is_suspended')
    .order('created_at', { ascending: false })
    .limit(8)
  return data ?? []
}

export function DashboardPage() {
  const { data: stats } = useQuery({ queryKey: ['admin-stats'], queryFn: fetchStats })
  const { data: recentUsers } = useQuery({ queryKey: ['recent-users'], queryFn: fetchRecentUsers })

  const STAT_CARDS = [
    { label: 'Total users',     value: stats?.users,          icon: Users,         color: 'text-violet-400' },
    { label: 'Matches made',    value: stats?.matches,         icon: Heart,         color: 'text-rose-400'   },
    { label: 'Messages sent',   value: stats?.messages,        icon: MessageSquare, color: 'text-blue-400'   },
    { label: 'Pending reports', value: stats?.pendingReports,  icon: Flag,          color: 'text-amber-400'  },
  ]

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-white mb-6">Dashboard</h1>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {STAT_CARDS.map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="bg-gray-900 border border-gray-800 rounded-xl p-5">
            <div className="flex items-center justify-between mb-3">
              <span className="text-sm text-gray-400">{label}</span>
              <Icon className={color} size={20} />
            </div>
            <div className="text-3xl font-bold text-white">
              {value?.toLocaleString() ?? '—'}
            </div>
          </div>
        ))}
      </div>

      {/* Recent signups */}
      <div className="bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-800">
          <h2 className="font-semibold text-white">Recent signups</h2>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-800 text-gray-400">
              <th className="text-left px-5 py-3">Email</th>
              <th className="text-left px-5 py-3">Joined</th>
              <th className="text-left px-5 py-3">Role</th>
              <th className="text-left px-5 py-3">Status</th>
            </tr>
          </thead>
          <tbody>
            {recentUsers?.map(user => (
              <tr key={user.id} className="border-b border-gray-800/50 hover:bg-gray-800/30 transition-colors">
                <td className="px-5 py-3 text-gray-200">{user.email}</td>
                <td className="px-5 py-3 text-gray-400">
                  {new Date(user.created_at).toLocaleDateString()}
                </td>
                <td className="px-5 py-3">
                  <span className="text-xs font-medium text-violet-300 bg-violet-900/40 px-2 py-0.5 rounded-full">
                    {user.role}
                  </span>
                </td>
                <td className="px-5 py-3">
                  {user.is_banned
                    ? <span className="text-xs text-red-400 bg-red-900/30 px-2 py-0.5 rounded-full">Banned</span>
                    : user.is_suspended
                    ? <span className="text-xs text-amber-400 bg-amber-900/30 px-2 py-0.5 rounded-full">Suspended</span>
                    : <span className="text-xs text-green-400 bg-green-900/30 px-2 py-0.5 rounded-full">Active</span>
                  }
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
