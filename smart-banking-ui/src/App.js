import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './context/AuthContext';

// Pages
import LoginPage       from './pages/LoginPage';
import RegisterPage    from './pages/RegisterPage';
import DashboardPage   from './pages/DashboardPage';
import AccountsPage    from './pages/AccountsPage';
import TransferPage    from './pages/TransferPage';
import TransactionsPage from './pages/TransactionsPage';
import ProfilePage     from './pages/ProfilePage';
import Navbar          from './components/Navbar';

// ProtectedRoute — redirects to login if not authenticated
function ProtectedRoute({ children }) {
  const { isLoggedIn, loading } = useAuth();
  if (loading) return <div style={{ textAlign: 'center', marginTop: 80 }}><div className="spinner"/></div>;
  return isLoggedIn() ? children : <Navigate to="/login" replace />;
}

// PublicRoute — redirects to dashboard if already logged in
function PublicRoute({ children }) {
  const { isLoggedIn, loading } = useAuth();
  if (loading) return null;
  return isLoggedIn() ? <Navigate to="/dashboard" replace /> : children;
}

function AppRoutes() {
  const { isLoggedIn } = useAuth();
  return (
    <>
      {/* Show navbar only when logged in */}
      {isLoggedIn() && <Navbar />}
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/login"    element={<PublicRoute><LoginPage /></PublicRoute>} />
        <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
        <Route path="/dashboard"     element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
        <Route path="/accounts"      element={<ProtectedRoute><AccountsPage /></ProtectedRoute>} />
        <Route path="/transfer"      element={<ProtectedRoute><TransferPage /></ProtectedRoute>} />
        <Route path="/transactions"  element={<ProtectedRoute><TransactionsPage /></ProtectedRoute>} />
        <Route path="/profile"       element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />
      </Routes>
    </>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        {/* Toast notifications (success / error popups) */}
        <Toaster position="top-right" toastOptions={{ duration: 3000 }} />
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}