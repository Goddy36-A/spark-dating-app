import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { supabase } from '../App'
import {
  LayoutDashboard, Users, Flag, Shield, LogOut, Sparkles
} from 'lucide-react'

const NAV_ITEMS = [
  { to: '/',           label: 'Dashboard',  icon: LayoutDashboard, exact: true },
  { to: '/users',      label: 'Users',      icon: Users },
  { to: '/reports',    label: 'Reports',    icon: Flag },
  { to: '/moderation', label: 'Moderation', icon: Shield },
]

export function AdminLayout() {
  const navigate = useNavigate()

  const handleLogout = async () => {
    await supabase.auth.signOut()
    navigate('/login')
  }

  return (
    <div className="min-h-screen flex bg-gray-950 text-gray-100">
      {/* Sidebar */}
      <aside className="w-56 flex-shrink-0 bg-gray-900 border-r border-gray-800 flex flex-col">
        {/* Logo */}
        <div className="h-16 flex items-center px-5 border-b border-gray-800">
          <Sparkles className="text-rose-400 mr-2" size={20} />
          <span className="font-semibold text-white text-lg tracking-tight">Spark Admin</span>
        </div>

        {/* Nav */}
        <nav className="flex-1 py-4 space-y-1 px-2">
          {NAV_ITEMS.map(({ to, label, icon: Icon, exact }) => (
            <NavLink
              key={to}
              to={to}
              end={exact}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-violet-900/60 text-violet-300'
                    : 'text-gray-400 hover:bg-gray-800 hover:text-gray-100'
                }`
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>

        {/* Logout */}
        <div className="p-3 border-t border-gray-800">
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 px-3 py-2.5 w-full rounded-lg text-sm text-gray-400 hover:bg-gray-800 hover:text-gray-100 transition-colors"
          >
            <LogOut size={18} />
            Log out
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
