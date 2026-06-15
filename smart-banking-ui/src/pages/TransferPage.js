import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import toast from 'react-hot-toast';
import { v4 as uuidv4 } from 'uuid';

export default function TransferPage() {
  const { user } = useAuth();
  const [accounts, setAccounts]     = useState([]);
  const [loading, setLoading]       = useState(false);
  const [result, setResult]         = useState(null);
  const [polling, setPolling]       = useState(false);
  const pollRef = useRef(null);

  const [form, setForm] = useState({
    sourceAccountNumber: '',
    destinationAccountNumber: '',
    amount: '',
    currency: 'INR',
    description: '',
  });

  // Load user's accounts to populate source account dropdown
  useEffect(() => {
    api.get(`/api/accounts/user/${user.userUuid}`)
      .then(r => {
        const active = (r.data.data || []).filter(a => a.status === 'ACTIVE');
        setAccounts(active);
        if (active.length > 0) setForm(f => ({ ...f, sourceAccountNumber: active[0].accountNumber }));
      });
    return () => clearInterval(pollRef.current); // cleanup on unmount
  }, []);

  // Poll transaction status every 2 seconds until terminal state
  const pollStatus = (transactionRef) => {
    setPolling(true);
    pollRef.current = setInterval(async () => {
      try {
        const res = await api.get(`/api/transactions/status/${transactionRef}`);
        const status = res.data.data.currentStatus;
        setResult(prev => ({ ...prev, status }));

        // Stop polling on terminal states
        if (['COMPLETED', 'FAILED', 'REVERSED'].includes(status)) {
          clearInterval(pollRef.current);
          setPolling(false);
          if (status === 'COMPLETED') toast.success('Transfer completed successfully! 🎉');
          else toast.error('Transfer failed. No money was debited.');
        }
      } catch { clearInterval(pollRef.current); setPolling(false); }
    }, 2000);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (form.sourceAccountNumber === form.destinationAccountNumber) {
      toast.error('Source and destination cannot be the same account.');
      return;
    }
    setLoading(true);
    setResult(null);
    try {
      // Generate a fresh idempotency key for this transfer attempt
      const idempotencyKey = uuidv4();
      const res = await api.post('/api/transactions/transfer', {
        ...form, amount: parseFloat(form.amount)
      }, {
        headers: { 'X-Idempotency-Key': idempotencyKey }
      });
      const data = res.data.data;
      setResult(data);
      toast.success('Transfer initiated! Checking status...');
      pollStatus(data.transactionRef);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Transfer failed.');
    } finally { setLoading(false); }
  };

  const statusColor = (s) => ({
    COMPLETED: '#1E8449', FAILED: '#C0392B',
    PENDING: '#D35400', FRAUD_CHECKING: '#1B4F8A',
    PROCESSING: '#1B4F8A', REVERSING: '#888'
  }[s] || '#555');

  return (
    <div className="page-container" style={{ maxWidth: '600px' }}>
      <h1 className="section-title">Transfer Money</h1>

      <div className="card">
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="label">From Account</label>
            <select className="input" value={form.sourceAccountNumber}
              onChange={e => setForm({ ...form, sourceAccountNumber: e.target.value })} required>
              {accounts.map(a => (
                <option key={a.uuid} value={a.accountNumber}>
                  {a.accountNumber} — ₹{parseFloat(a.balance).toLocaleString('en-IN')} ({a.accountType})
                </option>
              ))}
            </select>
          </div>
          <div className="form-group">
            <label className="label">To Account Number</label>
            <input className="input" placeholder="SBP0000000002"
              pattern="SBP[0-9]{10}" title="Format: SBP followed by 10 digits"
              value={form.destinationAccountNumber}
              onChange={e => setForm({ ...form, destinationAccountNumber: e.target.value.toUpperCase() })}
              required />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '12px' }}>
            <div className="form-group">
              <label className="label">Amount (₹)</label>
              <input className="input" type="number" min="0.01" step="0.01" placeholder="1000.00"
                value={form.amount}
                onChange={e => setForm({ ...form, amount: e.target.value })} required />
            </div>
            <div className="form-group">
              <label className="label">Currency</label>
              <select className="input" value={form.currency}
                onChange={e => setForm({ ...form, currency: e.target.value })}>
                <option>INR</option><option>USD</option><option>EUR</option>
              </select>
            </div>
          </div>
          <div className="form-group">
            <label className="label">Description (optional)</label>
            <input className="input" placeholder="Rent payment, etc."
              value={form.description}
              onChange={e => setForm({ ...form, description: e.target.value })} />
          </div>
          <button className="btn-primary" type="submit" disabled={loading || accounts.length === 0}>
            {loading ? 'Initiating...' : '💸 Send Money'}
          </button>
        </form>
      </div>

      {/* Transfer result */}
      {result && (
        <div className="card" style={{ marginTop: '20px', borderTop: `4px solid ${statusColor(result.status)}` }}>
          <h3 style={{ marginBottom: '14px', color: statusColor(result.status) }}>
            Transfer Status: {result.status} {polling && <span className="spinner" style={{ width: '14px', height: '14px', marginLeft: '8px' }}/>}
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px', fontSize: '14px' }}>
            <div><span style={{ color: '#888' }}>Reference:</span></div>
            <div style={{ fontFamily: 'monospace', fontSize: '12px' }}>{result.transactionRef}</div>
            <div><span style={{ color: '#888' }}>Amount:</span></div>
            <div style={{ fontWeight: 700 }}>₹{parseFloat(result.amount).toLocaleString('en-IN')}</div>
            <div><span style={{ color: '#888' }}>From:</span></div>
            <div style={{ fontFamily: 'monospace', fontSize: '12px' }}>{result.sourceAccountNumber}</div>
            <div><span style={{ color: '#888' }}>To:</span></div>
            <div style={{ fontFamily: 'monospace', fontSize: '12px' }}>{result.destinationAccountNumber}</div>
          </div>
          {result.failureReason && (
            <div className="error-msg" style={{ marginTop: '12px' }}>{result.failureReason}</div>
          )}
          {polling && (
            <p style={{ color: '#888', fontSize: '13px', marginTop: '12px' }}>
              ⏳ Polling for updates every 2 seconds...
            </p>
          )}
        </div>
      )}
    </div>
  );
}