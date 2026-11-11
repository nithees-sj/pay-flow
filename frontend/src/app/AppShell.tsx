import { useState } from "react";
import { NavLink, Outlet, Link } from "react-router-dom";
import {
  LayoutDashboard,
  ArrowLeftRight,
  Send,
  Bell,
  LogOut,
  Moon,
  Sun,
  Menu,
  X,
  ShieldCheck,
  ChevronDown,
} from "lucide-react";
import { Logo } from "../components/Logo";
import { useAuth } from "../features/auth/AuthProvider";
import { useInbox } from "../api/queries";
export function AppShell() {
  const { profile, logout } = useAuth();
  const inbox = useInbox(0, false, 1);
  const [open, setOpen] = useState(false);
  const [dark, setDark] = useState(() =>
    document.documentElement.classList.contains("dark"),
  );
  function theme() {
    document.documentElement.classList.toggle("dark", !dark);
    localStorage.setItem("payflow-theme", !dark ? "dark" : "light");
    setDark(!dark);
  }
  const links = [
    { to: "/", label: "Overview", icon: LayoutDashboard },
    { to: "/transactions", label: "Transactions", icon: ArrowLeftRight },
    { to: "/send", label: "Send money", icon: Send },
    { to: "/notifications", label: "Notifications", icon: Bell },
  ];
  return (
    <div className="app-shell">
      {open && (
        <button
          className="sidebar-backdrop"
          onClick={() => setOpen(false)}
          aria-label="Close navigation"
        />
      )}
      <aside className={`sidebar ${open ? "sidebar-open" : ""}`}>
        <Link className="brand-link" to="/" onClick={() => setOpen(false)}>
          <Logo />
        </Link>
        <button
          className="icon-button close-nav"
          onClick={() => setOpen(false)}
          aria-label="Close navigation"
        >
          <X />
        </button>
        <div className="workspace-label">PERSONAL WORKSPACE</div>
        <nav className="main-nav" aria-label="Main navigation">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === "/"}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              <Icon size={19} />
              <span>{label}</span>
              {to === "/notifications" && !!inbox.data?.unreadCount && (
                <span className="nav-count">{inbox.data.unreadCount}</span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="demo-note">
            <ShieldCheck size={23} />
            <strong>A little room to explore.</strong>
            <p>This is your demo wallet. Every rupee here is test money.</p>
          </div>
          <button className="logout" onClick={logout}>
            <LogOut size={17} />
            Sign out
          </button>
          <div className="sidebar-footer">
            PayFlow <span>v1.0 · INR</span>
          </div>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <button
            className="icon-button mobile-menu"
            onClick={() => setOpen(true)}
            aria-label="Open navigation"
          >
            <Menu />
          </button>
          <span className="topbar-title">Your personal wallet</span>
          <div className="topbar-actions">
            <span className="environment-tag">
              <span />
              Demo workspace
            </span>
            <button
              className="icon-button"
              onClick={theme}
              aria-label={dark ? "Switch to light mode" : "Switch to dark mode"}
            >
              {dark ? <Sun size={19} /> : <Moon size={19} />}
            </button>
            <Link
              to="/notifications"
              className="icon-button notification-button"
              aria-label={`Notifications, ${inbox.data?.unreadCount ?? 0} unread`}
            >
              <Bell size={20} />
              {!!inbox.data?.unreadCount && (
                <span className="notification-dot" />
              )}
            </Link>
            <span className="topbar-divider" />
            <div className="user-chip">
              <span className="avatar">
                {profile?.user.name.slice(0, 2).toUpperCase()}
              </span>
              <span>
                <strong>{profile?.user.name}</strong>
                <small>Personal account</small>
              </span>
              <ChevronDown size={15} />
            </div>
          </div>
        </header>
        <main id="main-content" className="page-content">
          <Outlet />
        </main>
        <footer className="workspace-footer">
          <span>Built for clarity. Designed for peace of mind.</span>
          <span>
            <ShieldCheck size={14} /> Simulated funds only
          </span>
        </footer>
      </div>
    </div>
  );
}
