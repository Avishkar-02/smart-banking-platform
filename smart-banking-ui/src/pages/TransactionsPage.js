import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import toast from 'react-hot-toast';

export default function TransactionsPage() {
  const { user }  = useAuth();
  const [accounts, setAccounts]         = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [selectedAcc, setSelectedAcc]   = useState('');
  const [loading, setLoading]           = useState(false);

  // Load accounts on mount
  useEffect(() => {
    api.get(`/api/accounts/user/${user.userUuid}`)
      .then(r => {
        const accs = r.data.data || [];
        setAccounts(accs);
        if (accs.length > 0) setSelectedAcc(accs[0].accountNumber);
      });
  }, []);

  // Fetch transactions when account selection changes
  useEffect(() => {
    if (!selectedAcc) return;
    setLoading(true);
    api.get(`/api/transactions/history/${selectedAcc}`)
      .then(r => setTransactions(r.data.data || []))
      .catch(() => toast.error('Failed to load transactions'))
      .finally(() => setLoading(false));
  }, [selectedAcc]);

  const statusBadge = (s) => ({
    COMPLETED: 'badge-success', FAILED: 'badge-danger',
    PENDING: 'badge-warning', FRAUD_CHECKING: 'badge-info',
    PROCESSING: 'badge-info', REVERSED: 'badge-gray', REVERSING: 'badge-gray',
  }[s] || 'badge-gray');

  return (
    <div className="page-container">
      <h1 className="section-title">Transaction History</h1>

      {/* Account selector */}
      <div className="card" style={{ marginBottom: '20px' }}>
        <label className="label">Select Account</label>
        <select className="input" style={{ maxWidth: '400px' }}
          value={selectedAcc} onChange={e => setSelectedAcc(e.target.value)}>
          {accounts.map(a => (
            <option key={a.uuid} value={a.accountNumber}>
              {a.accountNumber} ({a.accountType})
            </option>
          ))}
        </select>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px' }}><div className="spinner"/></div>
      ) : transactions.length === 0 ? (
        <div className="card" style={{ textAlign: 'center', padding: '60px', color: '#888' }}>
          <div style={{ fontSize: '48px' }}>📋</div>
          <p style={{ marginTop: '16px' }}>No transactions found for this account.</p>
        </div>
      ) : (
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Reference</th>
                <th>From</th>
                <th>To</th>
                <th>Amount</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map(t => (
                <tr key={t.transactionRef}>
                  <td style={{ fontSize: '12px', color: '#555' }}>
                    {new Date(t.createdAt).toLocaleString('en-IN')}
                  </td>
                  <td style={{ fontFamily: 'monospace', fontSize: '11px', color: '#888' }}>
                    {t.transactionRef}
                  </td>
                  <td style={{ fontSize: '12px', fontFamily: 'monospace' }}>
                    {t.sourceAccountNumber}
                  </td>
                  <td style={{ fontSize: '12px', fontFamily: 'monospace' }}>
                    {t.destinationAccountNumber}
                  </td>
                  <td style={{ fontWeight: 700, color: '#C0392B' }}>
                    ₹{parseFloat(t.amount).toLocaleString('en-IN')}
                  </td>
                  <td>
                    <span className={`badge ${statusBadge(t.status)}`}>{t.status}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}