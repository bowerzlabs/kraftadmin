<script lang="ts">
  import { onMount } from 'svelte';
  import { adminSettings } from '../stores/settings';
  
  let dashboardData: any = null;
  let loading = true;

  onMount(async () => {
    try {
      const res = await fetch('/admin/api/dashboard');
      if (res.ok) dashboardData = await res.json();
    } finally {
      loading = false; // Flips exactly when data arrives or fails
    }    
  });
</script>

<div class="p-8 space-y-10 min-h-screen bg-bg-main transition-colors duration-300">
  {#if loading}
    <div class="animate-pulse space-y-10">
      <div class="flex justify-between items-end">
        <div class="space-y-3">
          <div class="h-8 w-64 bg-zinc-200 dark:bg-zinc-800 rounded-lg"></div>
          <div class="h-4 w-96 bg-zinc-200 dark:bg-zinc-800 rounded-md"></div>
        </div>
      </div>
      
      <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        {#each Array(3) as _}
          <div class="h-32 bg-bg-surface border border-border-subtle rounded-3xl"></div>
        {/each}
      </div>

      <div class="bg-bg-surface border border-border-subtle rounded-3xl h-64">
        <div class="p-6 border-b border-border-subtle h-16"></div>
        <div class="p-6 space-y-4">
          <div class="h-4 bg-zinc-100 dark:bg-zinc-800 rounded w-full"></div>
          <div class="h-4 bg-zinc-100 dark:bg-zinc-800 rounded w-3/4"></div>
        </div>
      </div>
    </div>
  {:else if dashboardData}
    <div class="flex justify-between items-end">
      <div>
        <h1 class="text-3xl font-black text-text-main uppercase tracking-tighter">
          {dashboardData.title}
        </h1>
        <p class="text-zinc-500 text-sm">{dashboardData.welcomeMessage}</p>
      </div>
      
      <div class="flex gap-3">
        <a href="#/analytics" class="px-4 py-2 bg-bg-surface border border-border-subtle text-text-main rounded-xl text-xs font-bold hover:border-brand-primary transition-all">
          View Analytics
        </a>
        <a href="#/telemetry" class="px-4 py-2 bg-brand-primary text-white rounded-xl text-xs font-bold shadow-lg shadow-brand-primary/20 hover:opacity-90">
          Cloud Pulse
        </a>
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      {#each dashboardData.stats as stat}
        <div class="bg-bg-surface border border-border-subtle p-6 rounded-3xl shadow-sm">
          <span class="text-zinc-500 text-[10px] font-black uppercase tracking-widest">{stat.label}</span>
          <div class="flex items-center justify-between mt-2">
            <span class="text-4xl font-black text-text-main">{stat.value}</span>
            <div class="p-3 bg-brand-primary/10 rounded-2xl text-brand-primary">
              </div>
          </div>
        </div>
      {/each}
    </div>

    <div class="bg-bg-surface border border-border-subtle rounded-3xl overflow-hidden shadow-sm">
        <div class="p-6 border-b border-border-subtle flex justify-between items-center bg-zinc-50/30 dark:bg-zinc-900/30">
            <h2 class="text-xl font-bold text-text-main">Library Capabilities</h2>
            <span class="text-[10px] font-bold text-zinc-500 uppercase italic">v{$adminSettings?.version || '0.1.0'}</span>
        </div>
        <table class="w-full text-left">
            <thead class="bg-zinc-50/50 dark:bg-zinc-950/50 text-zinc-500 text-[10px] uppercase font-black">
                <tr>
                    <th class="px-6 py-4">Feature</th>
                    <th class="px-6 py-4">Status</th>
                    <th class="px-6 py-4 text-right">Requirement</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-border-subtle">
                {#each dashboardData.features as feature}
                    <tr class="hover:bg-zinc-500/5 transition-colors">
                        <td class="px-6 py-4 text-text-main font-bold">{feature.name}</td>
                        <td class="px-6 py-4">
                            <span class="px-3 py-1 rounded-full text-[10px] font-bold 
                                {feature.status === 'Active' ? 'bg-emerald-500/10 text-emerald-500' : 'bg-zinc-500/10 text-zinc-500'}">
                                {feature.status}
                            </span>
                        </td>
                        <td class="px-6 py-4 text-xs text-zinc-500 italic text-right">{feature.unlockCriteria || 'None'}</td>
                    </tr>
                {/each}
            </tbody>
        </table>
    </div>
  {/if}
</div>