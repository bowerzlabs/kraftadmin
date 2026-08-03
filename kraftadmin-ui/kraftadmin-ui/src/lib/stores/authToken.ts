import { writable } from 'svelte/store';

// In-memory only. Session/basic-auth users never touch this — it stays
// null for them, and kraftFetch simply skips the header in that case.
export const authToken = writable<string | null>(null);

export function setAuthToken(token: string | null) {
    authToken.set(token);
}