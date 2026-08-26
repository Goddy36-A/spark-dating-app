import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { supabase } from '../App'
import { CheckCircle, XCircle, Eye } from 'lucide-react'

type Report = {
  id: string
  reporter_id: string
  reported_id: string
  category: string
  details: string
  status: string
  created_at: string
}

const STATUS_FILTERS = ['pending', 'under_review', 'resolved', 'dismissed']

async function fetchReports(status: string) {
  const { data } = await supabase
    .from('reports')
    .select('*')
    .eq('status', status)
    .order('created_at', { ascending: false })
    .limit(50)
  return (data ?? []) as Report[]
}

async function updateReportStatus(id: string, status: string, reviewerId: string) {
  await supabase
    .from('reports')
    .update({ status, reviewed_by: reviewerId, reviewed_at: new Date().toISOString() })
    .eq('id', id)
}

async function banUser(userId: string, reason: string, moderatorId: string) {
  await supabase.from('users').update({ is_banned: true, ban_reason: reason }).eq('id', userId)
  await supabase.from('moderation_events').insert({
    target_user_id: userId,
    moderator_id: moderatorId,
    action: 'ban',
    reason,
  })
}

export function ReportsPage() {
  const [statusFilter, setStatusFilter] = useState('pending')
  const [selected, setSelected] = useState<Report | null>(null)
  const qc = useQueryClient()

  const { data: reports, isLoading } = useQuery({
    queryKey: ['reports', statusFilter],
    queryFn: () => fetchReports(statusFilter),
  })

  const reviewMutation = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: string }) => {
      const { data: { user } } = await supabase.auth.getUser()
      await updateReportStatus(id, status, user!.id)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['reports'] })
      setSelected(null)
    },
  })

  const banMutation = useMutation({
    mutationFn: async (userId: string) => {
      const { data: { user } } = await supabase.auth.getUser()
      await banUser(userId, `Banned via report review`, user!.id)
      await reviewMutation.mutateAsync({ id: selected!.id, status: 'resolved' })
    },
  })

  const CATEGORY_LABELS: Record<string, string> = {
    harassment: 'Harassment',
    spam: 'Spam / Scam',
    fake_profile: 'Fake Profile',
    inappropriate_content: 'Inappropriate Content',
    impersonation: 'Impersonation',
    underage_concern: '⚠️ Underage Concern',
    other: 'Other',
  }

  return (
    <div className="p-8">
      <h1 className="text-2xl font-bold text-white mb-6">Reports</h1>

      {/* Status filter */}
      <div className="flex gap-2 mb-6">
        {STATUS_FILTERS.map(s => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors capitalize ${
              statusFilter === s
                ? 'bg-violet-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
            }`}
          >
            {s.replace('_', ' ')}
          </button>
        ))}
      </div>

      <div className="flex gap-6">
        {/* List */}
        <div className="flex-1 bg-gray-900 border border-gray-800 rounded-xl overflow-hidden">
          {isLoading ? (
            <div className="p-8 text-center text-gray-400">Loading…</div>
          ) : reports?.length === 0 ? (
            <div className="p-8 text-center text-gray-400">No {statusFilter} reports</div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-800 text-gray-400">
                  <th className="text-left px-5 py-3">Category</th>
                  <th className="text-left px-5 py-3">Date</th>
                  <th className="text-left px-5 py-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                {reports?.map(report => (
                  <tr
                    key={report.id}
                    onClick={() => setSelected(report)}
                    className={`border-b border-gray-800/50 cursor-pointer transition-colors ${
                      selected?.id === report.id ? 'bg-violet-900/20' : 'hover:bg-gray-800/30'
                    }`}
                  >
                    <td className="px-5 py-3">
                      <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                        report.category === 'underage_concern'
                          ? 'bg-red-900/50 text-red-300'
                          : 'bg-gray-800 text-gray-300'
                      }`}>
                        {CATEGORY_LABELS[report.category] ?? report.category}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-gray-400">
                      {new Date(report.created_at).toLocaleDateString()}
                    </td>
                    <td className="px-5 py-3">
                      <button className="text-violet-400 hover:text-violet-300">
                        <Eye size={16} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Detail panel */}
        {selected && (
          <div className="w-80 bg-gray-900 border border-gray-800 rounded-xl p-6 flex-shrink-0">
            <h2 className="font-semibold text-white mb-4">Report Detail</h2>

            <dl className="space-y-3 text-sm mb-6">
              <div>
                <dt className="text-gray-400">Category</dt>
                <dd className="text-white">{CATEGORY_LABELS[selected.category]}</dd>
              </div>
              <div>
                <dt className="text-gray-400">Reported user ID</dt>
                <dd className="text-white font-mono text-xs break-all">{selected.reported_id}</dd>
              </div>
              <div>
                <dt className="text-gray-400">Reporter ID</dt>
                <dd className="text-white font-mono text-xs break-all">{selected.reporter_id}</dd>
              </div>
              {selected.details && (
                <div>
                  <dt className="text-gray-400">Details</dt>
                  <dd className="text-white">{selected.details}</dd>
                </div>
              )}
            </dl>

            <div className="space-y-2">
              <button
                onClick={() => reviewMutation.mutate({ id: selected.id, status: 'under_review' })}
                className="w-full bg-blue-800 hover:bg-blue-700 text-white text-sm py-2 rounded-lg transition-colors"
              >
                Mark Under Review
              </button>
              <button
                onClick={() => reviewMutation.mutate({ id: selected.id, status: 'dismissed' })}
                className="w-full bg-gray-700 hover:bg-gray-600 text-white text-sm py-2 rounded-lg transition-colors flex items-center justify-center gap-2"
              >
                <XCircle size={15} /> Dismiss
              </button>
              <button
                onClick={() => reviewMutation.mutate({ id: selected.id, status: 'resolved' })}
                className="w-full bg-green-800 hover:bg-green-700 text-white text-sm py-2 rounded-lg transition-colors flex items-center justify-center gap-2"
              >
                <CheckCircle size={15} /> Resolve
              </button>
              <button
                onClick={() => {
                  if (confirm(`Ban user ${selected.reported_id}? This cannot be easily undone.`)) {
                    banMutation.mutate(selected.reported_id)
                  }
                }}
                className="w-full bg-red-800 hover:bg-red-700 text-white text-sm py-2 rounded-lg transition-colors"
              >
                Ban User
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
