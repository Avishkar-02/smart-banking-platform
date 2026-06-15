import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import toast from 'react-hot-toast';
import { TOKEN_KEY } from '../utils/constants';

export default function Navbar() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  // Active link highlight
  const isActive = (path) => location.pathname === path
    ? { borderBottom: '3px solid white', paddingBottom: '4px' }
    : {};

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      // Blacklist the token on the server side
      await api.post('/api/auth/logout');
      toast.success('Logged out successfully');
    } catch {
      // Even if server call fails, clear local state
    } finally {
      logout();
      navigate('/login');
      setLoggingOut(false);
    }
  };

  return (
    <nav style={{
      background: '#1B4F8A', padding: '0 24px',
      display: 'flex', alignItems: 'center',
      justifyContent: 'space-between', height: '58px',
      boxShadow: '0 2px 8px rgba(0,0,0,0.15)'
    }}>
      {/* Brand */}
      <span style={{ color: 'white', fontWeight: 800, fontSize: '18px', letterSpacing: 0.5 }}>
        💳 Smart Banking
      </span>

      {/* Navigation links */}
      <div style={{ display: 'flex', gap: '24px' }}>
        {[
          { path: '/dashboard',    label: 'Dashboard'    },
          { path: '/accounts',     label: 'Accounts'     },
          { path: '/transfer',     label: 'Transfer'     },
          { path: '/transactions', label: 'Transactions' },
          { path: '/profile',      label: 'Profile'      },
        ].map(({ path, label }) => (
          <Link key={path} to={path} style={{
            color: 'white', textDecoration: 'none',
            fontSize: '14px', fontWeight: 600, ...isActive(path)
          }}>{label}</Link>
        ))}
      </div>

      {/* User info + logout */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
        <span style={{ color: '#D6E4F0', fontSize: '14px' }}>
          Hi, {user?.firstName} 👋
        </span>
        <button
          onClick={handleLogout}
          disabled={loggingOut}
          style={{
            background: 'rgba(255,255,255,0.15)', color: 'white',
            border: '1px solid rgba(255,255,255,0.4)',
            padding: '6px 16px', borderRadius: '6px',
            cursor: 'pointer', fontSize: '13px', fontWeight: 600
          }}>
          {loggingOut ? '...' : 'Logout'}
        </button>
      </div>
    </nav>
  );
}