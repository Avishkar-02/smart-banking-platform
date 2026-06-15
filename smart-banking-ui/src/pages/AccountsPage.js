import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import toast from 'react-hot-toast';

export default function AccountsPage() {
  const { user } = useAuth();
  const [accounts, setAccounts]         = useState([]);
  const [loading, setLoading]           = useState(true);
  const [creating, setCreating]         = useState(false);
  const [showForm, setShowForm]         = useState(false);
  const [form, setForm]                 = useState({ accountType: 'SAVINGS', currency: 'INR' });

  const fetchAccounts = async () => {
    try {
      const res = await api.get(`/api/accounts/user/${user.userUuid}`);
      setAccounts(res.data.data || []);
    } catch { toast.error('Failed to load accounts'); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchAccounts(); }, []);

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreating(true);
    try {
      await api.post('/api/accounts/create', form);
      toast.success('Account created successfully!');
      setShowForm(false);
      fetchAccounts();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create account');
    } finally { setCreating(false); }
  };

  const accountTypeIcon = (type) =>
    ({ SAVINGS: '🏦', CURRENT: '💼', FIXED_DEPOSIT: '🔒' }[type] || '💳');

  return (
    <div className="page-container">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <h1 className="section-title" style={{ marginBottom: 0 }}>Your Accounts</h1>
        <button className="btn-primary" style={{ width: 'auto', padding: '10px 22px' }}
          onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Cancel' : '+ New Account'}
        </button>
      </div>

      {/* Create account form */}
      {showForm && (
        <div className="card" style={{ marginBottom: '24px', border: '2px solid #1B4F8A' }}>
          <h3 style={{ marginBottom: '18px', color: '#1B4F8A' }}>Open New Account</h3>
          <form onSubmit={handleCreate}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
              <div className="form-group">
                <label className="label">Account Type</label>
                <select className="input" value={form.accountType}
                  onChange={e => setForm({ ...form, accountType: e.target.value })}>
                  <option value="SAVINGS">Savings Account</option>
                  <option value="CURRENT">Current Account</option>
                  <option value="FIXED_DEPOSIT">Fixed Deposit</option>
                </select>
              </div>
              <div className="form-group">
                <label className="label">Currency</label>
                <select className="input" value={form.currency}
                  onChange={e => setForm({ ...form, currency: e.target.value })}>
                  <option value="INR">INR — Indian Rupee</option>
                  <option value="USD">USD — US Dollar</option>
                  <option value="EUR">EUR — Euro</option>
                </select>
              </div>
            </div>
            <button className="btn-primary" type="submit" disabled={creating} style={{ marginTop: '8px' }}>
              {creating ? 'Creating...' : 'Open Account'}
            </button>
          </form>
        </div>
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px' }}><div className="spinner"/></div>
      ) : accounts.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '60px', color: '#888' }}>
          <div style={{ fontSize: '48px' }}>🏦</div>
          <p style={{ marginTop: '16px', fontSize: '16px' }}>No accounts yet. Open your first account!</p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '18px' }}>
          {accounts.map(acc => (
            <div key={acc.uuid} className="card" style={{
              borderTop: `4px solid ${acc.status === 'ACTIVE' ? '#1B4F8A' : '#C0392B'}`
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
                <span style={{ fontSize: '28px' }}>{accountTypeIcon(acc.accountType)}</span>
                <span className={`badge ${acc.status === 'ACTIVE' ? 'badge-success' : 'badge-danger'}`}>
                  {acc.status}
                </span>
              </div>
              <div style={{ fontFamily: 'monospace', fontSize: '15px', color: '#555', marginBottom: '4px' }}>
                {acc.accountNumber}
              </div>
              <div style={{ fontSize: '13px', color: '#888', marginBottom: '16px' }}>
                {acc.accountType} · {acc.currency}
              </div>
              <div style={{ fontSize: '28px', fontWeight: 800, color: '#1B4F8A' }}>
                ₹{parseFloat(acc.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </div>
              <div style={{ fontSize: '12px', color: '#AAA', marginTop: '8px' }}>
                Opened: {new Date(acc.createdAt).toLocaleDateString('en-IN')}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}