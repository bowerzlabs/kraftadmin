import { writable } from 'svelte/store';
export const toast = writable<string | null>(null);

export function notify(msg: string) {
    toast.set(msg);
    setTimeout(() => toast.set(null), 3000);
}