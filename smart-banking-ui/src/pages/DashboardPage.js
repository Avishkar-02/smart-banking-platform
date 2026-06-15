import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

export default function DashboardPage() {
  const { user } = useAuth();
  const [accounts, setAccounts]         = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading]           = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        // Fetch accounts and recent transactions in parallel
        const [accRes, txnRes] = await Promise.all([
          api.get(`/api/accounts/user/${user.userUuid}`),
          // Get transactions for first account if any
          api.get(`/api/accounts/user/${user.userUuid}`)
            .then(r => r.data.data[0]
              ? api.get(`/api/transactions/history/${r.data.data[0].accountNumber}`)
              : { data: { data: [] } })
        ]);
        setAccounts(accRes.data.data || []);
        setTransactions((txnRes.data.data || []).slice(0, 5)); // last 5 only
      } catch { /* silently fail on dashboard */ }
      finally { setLoading(false); }
    };
    fetchData();
  }, [user.userUuid]);

  // Total balance across all accounts
  const totalBalance = accounts.reduce((sum, a) => sum + parseFloat(a.balance || 0), 0);

  const statusColor = (s) => ({
    COMPLETED: 'badge-success', FAILED: 'badge-danger',
    PENDING: 'badge-warning', FRAUD_CHECKING: 'badge-info',
    PROCESSING: 'badge-info', REVERSED: 'badge-gray',
  }[s] || 'badge-gray');

  return (
    <div className="page-container">
      {/* Greeting */}
      <div style={{ marginBottom: '28px' }}>
        <h1 style={{ fontSize: '26px', fontWeight: 800, color: '#1B4F8A' }}>
          Good day, {user.firstName}! 👋
        </h1>
        <p style={{ color: '#888', marginTop: '4px' }}>Here's your financial overview</p>
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px' }}><div className="spinner"/></div>
      ) : (
        <>
          {/* Stats row */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '16px', marginBottom: '28px' }}>
            <div className="stat-card">
              <div className="stat-value">₹{totalBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
              <div className="stat-label">Total Balance</div>
            </div>
            <div className="stat-card" style={{ borderLeftColor: '#1E8449' }}>
              <div className="stat-value" style={{ color: '#1E8449' }}>{accounts.length}</div>
              <div className="stat-label">Active Accounts</div>
            </div>
            <div className="stat-card" style={{ borderLeftColor: '#D35400' }}>
              <div className="stat-value" style={{ color: '#D35400' }}>{transactions.length}</div>
              <div className="stat-label">Recent Transactions</div>
            </div>
          </div>

          {/* Accounts summary */}
          <div className="card" style={{ marginBottom: '24px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
              <h2 style={{ fontSize: '18px', fontWeight: 700, color: '#1B4F8A' }}>Your Accounts</h2>
              <Link to="/accounts" style={{ color: '#1B4F8A', fontSize: '14px', fontWeight: 600 }}>View All →</Link>
            </div>
            {accounts.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '30px', color: '#888' }}>
                <p>No accounts yet.</p>
                <Link to="/accounts"><button className="btn-primary" style={{ width: 'auto', marginTop: '12px', padding: '10px 24px' }}>Open Account</button></Link>
              </div>
            ) : (
              accounts.map(acc => (
                <div key={acc.uuid} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '14px 16px', background: '#F8F9FA', borderRadius: '8px',
                  marginBottom: '10px'
                }}>
                  <div>
                    <div style={{ fontWeight: 700, color: '#333' }}>{acc.accountNumber}</div>
                    <div style={{ fontSize: '13px', color: '#888' }}>{acc.accountType} · {acc.currency}</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '18px', fontWeight: 800, color: '#1B4F8A' }}>
                      ₹{parseFloat(acc.balance).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </div>
                    <span className={`badge ${acc.status === 'ACTIVE' ? 'badge-success' : 'badge-danger'}`}>
                      {acc.status}
                    </span>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Quick actions */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' }}>
            <Link to="/transfer" style={{ textDecoration: 'none' }}>
              <div className="card" style={{ textAlign: 'center', cursor: 'pointer', border: '2px solid #1B4F8A' }}>
                <div style={{ fontSize: '32px' }}>💸</div>
                <div style={{ fontWeight: 700, color: '#1B4F8A', marginTop: '8px' }}>Transfer Money</div>
              </div>
            </Link>
            <Link to="/transactions" style={{ textDecoration: 'none' }}>
              <div className="card" style={{ textAlign: 'center', cursor: 'pointer', border: '2px solid #1E8449' }}>
                <div style={{ fontSize: '32px' }}>📋</div>
                <div style={{ fontWeight: 700, color: '#1E8449', marginTop: '8px' }}>View Transactions</div>
              </div>
            </Link>
          </div>

          {/* Recent transactions */}
          {transactions.length > 0 && (
            <div className="card">
              <h2 style={{ fontSize: '18px', fontWeight: 700, color: '#1B4F8A', marginBottom: '16px' }}>
                Recent Transactions
              </h2>
              <table className="table">
                <thead>
                  <tr>
                    <th>Reference</th><th>Amount</th><th>To/From</th><th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map(t => (
                    <tr key={t.transactionRef}>
                      <td style={{ fontFamily: 'monospace', fontSize: '12px' }}>{t.transactionRef}</td>
                      <td className="amount-negative">₹{parseFloat(t.amount).toLocaleString('en-IN')}</td>
                      <td style={{ fontSize: '12px', color: '#555' }}>{t.destinationAccountNumber}</td>
                      <td><span className={`badge ${statusColor(t.status)}`}>{t.status}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  );
}