import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

export default function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.get('/api/auth/users/profile')
      .then(r => setProfile(r.data.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div style={{ textAlign: 'center', padding: '80px' }}><div className="spinner"/></div>;

  const Row = ({ label, value }) => (
    <div style={{
      display: 'flex', justifyContent: 'space-between',
      padding: '14px 0', borderBottom: '1px solid #EAEDED'
    }}>
      <span style={{ color: '#888', fontSize: '14px' }}>{label}</span>
      <span style={{ fontWeight: 600, fontSize: '14px', color: '#333' }}>{value || '—'}</span>
    </div>
  );

  return (
    <div className="page-container" style={{ maxWidth: '600px' }}>
      <h1 className="section-title">My Profile</h1>

      <div className="card">
        {/* Avatar */}
        <div style={{ textAlign: 'center', marginBottom: '28px' }}>
          <div style={{
            width: '80px', height: '80px', borderRadius: '50%',
            background: '#1B4F8A', color: 'white',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '32px', fontWeight: 800, margin: '0 auto 12px'
          }}>
            {user.firstName?.[0]}{user.lastName?.[0]}
          </div>
          <h2 style={{ color: '#1B4F8A' }}>{user.firstName} {user.lastName}</h2>
          <span className={`badge ${user.role === 'ADMIN' ? 'badge-warning' : 'badge-info'}`}>
            {user.role}
          </span>
        </div>

        {profile && (
          <>
            <Row label="Full Name" value={`${profile.firstName} ${profile.lastName}`} />
            <Row label="Email" value={profile.email} />
            <Row label="Phone" value={profile.phoneNumber} />
            <Row label="Account Status" value={profile.status} />
            <Row label="KYC Approved" value={profile.kycApproved ? '✅ Yes' : '❌ Not yet'} />
            <Row label="Member Since" value={new Date(profile.createdAt).toLocaleDateString('en-IN', { year: 'numeric', month: 'long', day: 'numeric' })} />
            <Row label="User UUID" value={<span style={{ fontFamily: 'monospace', fontSize: '11px' }}>{profile.uuid}</span>} />
          </>
        )}
      </div>
    </div>
  );
}