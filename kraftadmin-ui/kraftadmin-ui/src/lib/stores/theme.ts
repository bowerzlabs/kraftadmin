import { writable } from 'svelte/store';

// Check for existing preference or system setting
const storedTheme = localStorage.getItem('theme');
const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches;

export const isDark = writable(storedTheme === 'dark' || (!storedTheme && systemDark));

// Update the DOM and LocalStorage whenever the store changes
isDark.subscribe((value) => {
  if (value) {
    document.documentElement.classList.add('dark');
    localStorage.setItem('theme', 'dark');
  } else {
    document.documentElement.classList.remove('dark');
    localStorage.setItem('theme', 'light');
  }
});