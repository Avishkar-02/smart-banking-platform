// AuthContext provides the logged-in user and login/logout functions
// to the entire app. Any component can call useAuth() to get this.
import React, { createContext, useContext, useState, useEffect } from 'react';
import { TOKEN_KEY, USER_KEY, REFRESH_KEY } from '../utils/constants';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // user holds: { userUuid, email, firstName, lastName, role }
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // On app load, check if user was already logged in (token in localStorage)
  useEffect(() => {
    const savedUser = localStorage.getItem(USER_KEY);
    if (savedUser) {
      setUser(JSON.parse(savedUser));
    }
    setLoading(false);
  }, []);

  // Called after successful login or register
  const login = (userData, accessToken, refreshToken) => {
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(userData));
    setUser(userData);
  };

  // Called on logout button click
  const logout = () => {
    localStorage.clear();
    setUser(null);
  };

  const isLoggedIn = () => !!user;

  return (
    <AuthContext.Provider value={{ user, login, logout, isLoggedIn, loading }}>
      {children}
    </AuthContext.Provider>
  );
}

// Custom hook — components call: const { user, login, logout } = useAuth();
export function useAuth() {
  return useContext(AuthContext);
}