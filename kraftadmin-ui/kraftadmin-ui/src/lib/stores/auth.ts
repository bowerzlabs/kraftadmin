// import { writable } from 'svelte/store';

// // Retrieve token if using JWT, otherwise null for Cookie mode
// const initialToken = localStorage.getItem('kraft_token') || '';

// export const token = writable(initialToken);
// export const isAuthenticated = writable(!!initialToken);

// token.subscribe(val => {
//     if (val) {
//         localStorage.setItem('kraft_token', val);
//         isAuthenticated.set(true);
//     } else {
//         localStorage.removeItem('kraft_token');
//         // Note: Don't set isAuthenticated to false here automatically, 
//         // as we might be in Cookie mode.
//     }
// });

import { writable, derived } from 'svelte/store';

// 1. Core Stores
export const token = writable(localStorage.getItem('kraft_token') || '');
export const user = writable<{ username: string; roles: string[] } | null>(null);

/**
 * isAuthenticated is now a bit smarter. 
 * It's true if:
 * - We have a JWT token (Standalone mode)
 * - OR we have a user object (Successfully fetched from a session-based endpoint)
 */
export const isAuthenticated = writable(!!localStorage.getItem('kraft_token'));

// 2. Persistence for JWT mode
token.subscribe(val => {
    if (val) {
        localStorage.setItem('kraft_token', val);
        isAuthenticated.set(true);
    } else {
        localStorage.removeItem('kraft_token');
        // We don't force isAuthenticated to false here because 
        // a session cookie might still be active.
    }
});

/**
 * Utility to clear everything on logout
 */
export function clearAuth() {
    token.set('');
    user.set(null);
    isAuthenticated.set(false);
}