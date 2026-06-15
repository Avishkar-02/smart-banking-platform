// Axios instance configured to talk to API Gateway
import axios from 'axios';
import { API_BASE, TOKEN_KEY } from '../utils/constants';

// Create a single axios instance used everywhere
const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
});

// Request interceptor — attaches JWT token to every request automatically
// So we never have to manually add Authorization header in each API call
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor — if token is expired (401), clear storage and redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token invalid or expired — force logout
      localStorage.clear();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default api;