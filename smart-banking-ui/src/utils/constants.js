// API base URL — everything goes through the gateway
// Change this if your gateway runs on a different port
export const API_BASE = 'http://localhost:8080';

// Local storage keys for persisting auth state
export const TOKEN_KEY    = 'sbp_access_token';
export const USER_KEY     = 'sbp_user';
export const REFRESH_KEY  = 'sbp_refresh_token';