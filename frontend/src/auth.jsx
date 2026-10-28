import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, session, setUnauthorizedHandler } from './api.js';

const AuthContext = createContext(null);
export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(session.user);
  const [ready, setReady] = useState(!session.token);

  const logout = useCallback(() => { session.clear(); setUser(null); }, []);
  const signIn = useCallback((resp) => { session.set(resp.token, resp.user); setUser(resp.user); }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    if (session.token) {
      api.me().then((u) => { session.setUser(u); setUser(u); }).catch(() => {}).finally(() => setReady(true));
    }
  }, [logout]);

  if (!ready) return <p style={{ padding: 24 }}>Загрузка…</p>;
  return <AuthContext.Provider value={{ user, signIn, logout, isAdmin: user?.role === 'ADMIN' }}>{children}</AuthContext.Provider>;
}
