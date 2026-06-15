import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import toast from 'react-hot-toast';

export default function RegisterPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '', password: '', phoneNumber: ''
  });
  const [error, setError]   = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (form.password.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    setLoading(true);
    try {
      const res = await api.post('/api/auth/register', form);
      const data = res.data.data;
      login({
        userUuid: data.userUuid, email: data.email,
        firstName: data.firstName, lastName: data.lastName, role: data.role,
      }, data.accessToken, data.refreshToken);
      toast.success('Account created! Welcome aboard 🎉');
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh', display: 'flex',
      alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #1B4F8A 0%, #2980B9 100%)'
    }}>
      <div className="card" style={{ width: '420px' }}>
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <div style={{ fontSize: '36px' }}>💳</div>
          <h2 style={{ color: '#1B4F8A' }}>Create Account</h2>
        </div>

        {error && <div className="error-msg">{error}</div>}

        <form onSubmit={handleSubmit}>
          {/* First & Last name side by side */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="label">First Name</label>
              <input className="input" placeholder="Avishkar"
                value={form.firstName}
                onChange={e => setForm({ ...form, firstName: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Last Name</label>
              <input className="input" placeholder="Suryawanshi"
                value={form.lastName}
                onChange={e => setForm({ ...form, lastName: e.target.value })} required />
            </div>
          </div>
          <div className="form-group">
            <label className="label">Email</label>
            <input className="input" type="email" placeholder="you@example.com"
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })} required />
          </div>
          <div className="form-group">
            <label className="label">Password (min 8 chars)</label>
            <input className="input" type="password" placeholder="••••••••"
              value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })} required />
          </div>
          <div className="form-group">
            <label className="label">Phone (optional)</label>
            <input className="input" placeholder="9876543210"
              value={form.phoneNumber}
              onChange={e => setForm({ ...form, phoneNumber: e.target.value })} />
          </div>
          <button className="btn-primary" type="submit" disabled={loading}>
            {loading ? 'Creating...' : 'Create Account'}
          </button>
        </form>

        <p style={{ textAlign: 'center', marginTop: '18px', fontSize: '14px', color: '#555' }}>
          Already have an account?{' '}
          <Link to="/login" style={{ color: '#1B4F8A', fontWeight: 700 }}>Sign In</Link>
        </p>
      </div>
    </div>
  );
}