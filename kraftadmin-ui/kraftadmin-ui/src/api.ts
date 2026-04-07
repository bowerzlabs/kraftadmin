import { token, isAuthenticated } from './lib/stores/auth';
import { get } from 'svelte/store';

/**
 * Unified fetch wrapper for KraftAdmin.
 * - Automatically sends Session Cookies (via credentials: 'include').
 * - Manually injects JWT if present in the 'token' store.
 * - Handles 401/403 globally to trigger login UI.
 */
export async function kraftFetch(path: string, options: RequestInit = {}) {
    const $token = get(token); // Get current store value without subscribing
    
    // Merge headers
    const headers = new Headers(options.headers || {});
    headers.set('Accept', 'application/json');
    if (!(options.body instanceof FormData)) {
        headers.set('Content-Type', 'application/json');
    }

    // Unified Auth: Inject JWT if we have one. 
    // If not, the browser sends the cookie automatically because of credentials: 'include'
    if ($token) {
        headers.set('Authorization', `Bearer ${$token}`);
    }

    const config: RequestInit = {
        ...options,
        headers,
        credentials: 'same-origin' // Ensures cookies are sent for relative paths
    };

    try {
        const response = await fetch(path, config);

        if (response.status === 401 || response.status === 403) {
            isAuthenticated.set(false);
            token.set(''); // Clear stale JWT
        }

        return response;
    } catch (error) {
        console.error("Network error:", error);
        throw error;
    }
}