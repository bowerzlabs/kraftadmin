<script lang="ts">
  import { location } from 'svelte-spa-router';
  import { isDark } from '../stores/theme';
  import { token, isAuthenticated, user } from '../stores/auth';
  import { kraftFetch } from '../../api';

  // Structured environment object from backend KraftAdminDescriptor
  export let environment: {
    name: string;
    authMode: string;
    showLogout: boolean;
    version: string;
  } = {
    name: 'Production',
    authMode: 'bridge',
    showLogout: false,
    version: '1.0.0'
  };

  let loggingOut = false;

  // Compute initials for the avatar (e.g., "admin" -> "AD")
  $: initials = $user?.username?.substring(0, 2).toUpperCase() || 'AD';
  $: breadcrumb = $location.split('/').pop() || 'Dashboard';

  async function handleLogout() {
    if (loggingOut) return;
    loggingOut = true;

    try {
      await kraftFetch('/admin/api/auth/logout', { method: 'POST' });
    } catch (error) {
      console.error("Logout request failed", error);
    } finally {
      $token = '';
      $isAuthenticated = false;
      loggingOut = false;
      window.location.hash = '/auth/login';
    }
  }
</script>

<header class="h-16 bg-bg-surface border-b border-border-subtle flex items-center justify-between px-8 z-10 transition-colors duration-200">
  <div class="flex items-center gap-3 text-sm">
    <span class="text-zinc-400 font-medium">Home</span>
    <span class="text-zinc-300">/</span>
    <span class="font-bold text-text-main capitalize">{breadcrumb}</span>
  </div>

  <div class="flex items-center gap-4">
    <span class="px-2 py-1 rounded text-[10px] font-bold uppercase tracking-wider bg-zinc-800/50 text-zinc-400 border border-zinc-700/50">
      {environment.name}
    </span>

    <button
      on:click={() => isDark.update(v => !v)}
      class="p-2 rounded-lg bg-bg-main text-zinc-500 hover:text-brand-primary transition-colors"
      aria-label="Toggle Theme"
    >
      {#if $isDark}
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="1" x2="3" y1="12" y2="12"/><line x1="21" x2="23" y1="12" y2="12"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/><line x1="5.64" y1="18.36" x2="4.22" y2="19.78"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/></svg>
      {:else}
        <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
      {/if}
    </button>

    <div class="h-6 w-[1px] bg-border-subtle mx-2"></div>

    {#if environment.showLogout}
      <button 
        on:click={handleLogout}
        disabled={loggingOut}
        class="flex items-center gap-2 px-3 py-1.5 rounded-lg text-zinc-500 hover:text-red-500 hover:bg-red-500/5 transition-all group"
      >
        <div class="h-8 w-8 rounded-full bg-brand-primary/10 border border-brand-primary/20 flex items-center justify-center text-[10px] font-bold text-brand-primary group-hover:border-red-500/20 group-hover:bg-red-500/10 group-hover:text-red-500">
          {initials}
        </div>
        <span class="text-xs font-semibold">{loggingOut ? 'Signing out...' : 'Sign Out'}</span>
      </button>
    {:else}
      <div class="flex items-center gap-4">
        <a href="/" class="text-xs font-medium text-zinc-500 hover:text-brand-primary transition-colors flex items-center gap-1">
          <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
          Back to Website
        </a>
        <div title="Logged in via parent application" class="h-8 w-8 rounded-full bg-zinc-800 border border-zinc-700 flex items-center justify-center text-[10px] font-bold text-zinc-400">
          {initials}
        </div>
      </div>
    {/if}
  </div>
</header>