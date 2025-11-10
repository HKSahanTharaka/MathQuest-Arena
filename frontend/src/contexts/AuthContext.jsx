import { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';
import websocket from '../services/websocket';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const token = localStorage.getItem('token');
    const userData = localStorage.getItem('user');
    
    if (token && userData) {
      const parsedUser = JSON.parse(userData);
      setUser(parsedUser);
      
      if (parsedUser.id) {
        localStorage.setItem('playerId', parsedUser.id);
      }
      
      websocket.connect(token).catch(console.error);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    if (user && user.id) {
      localStorage.setItem('playerId', user.id);
    }
  }, [user]);

  const login = async (username) => {
    try {
      setError(null);
      const response = await api.login(username);
      
      const userData = {
        id: response.playerId,
        username: response.username,
        token: response.token
      };
      
      localStorage.setItem('token', response.token);
      localStorage.setItem('playerId', response.playerId);
      localStorage.setItem('user', JSON.stringify(userData));
      setUser(userData);
      
      await websocket.connect(response.token);
      
      return { success: true };
    } catch (err) {
      setError(err.message);
      return { success: false, error: err.message };
    }
  };

  const register = async (username, password, email) => {
    try {
      setError(null);
      const response = await api.register(username, password, email);
      return { success: true, data: response };
    } catch (err) {
      setError(err.message);
      return { success: false, error: err.message };
    }
  };

  const logout = async () => {
    try {
      // Notify server that player is logging out
      await api.logout().catch(console.error);
    } catch (err) {
      // Ignore logout errors - still clear local state
      console.error('Logout error:', err);
    }
    
    localStorage.removeItem('token');
    localStorage.removeItem('playerId');
    localStorage.removeItem('user');
    setUser(null);
    websocket.disconnect();
  };

  const value = {
    user,
    loading,
    error,
    login,
    register,
    logout,
    isAuthenticated: !!user
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

