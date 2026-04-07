<script lang="ts">
import { onMount, onDestroy } from 'svelte';
import { fade, slide } from 'svelte/transition';

  let logs: any[] = [];
  let activeFilter = 'ALL';
  let autoRefresh = true;
  let interval: any;

  async function fetchLogs() {
    try {
      const res = await fetch('/admin/api/system/logs');
      if (res.ok) {
        const data = await res.json();
        // Keep the latest logs at the top
        logs = data.sort((a: { timestamp: number }, b: { timestamp: number }) => b.timestamp - a.timestamp);
        console.log("Fetched Logs:", logs);
      }
    } catch (e) {
      console.error("Log Stream Interrupted", e);
    }
  }

  onMount(() => {
    fetchLogs();
    interval = setInterval(() => {
      if (autoRefresh) fetchLogs();
    }, 3000);
  });

  onDestroy(() => clearInterval(interval));

  $: filteredLogs = activeFilter === 'ALL' 
    ? logs 
    : logs.filter(l => l.level === activeFilter);

  const getLevelStyles = (level: string) => {
    switch (level) {
      case 'ERROR': return 'text-red-500 bg-red-500/10 border-red-500/20';
      case 'AUDIT': return 'text-emerald-500 bg-emerald-500/10 border-emerald-500/20';
      case 'WARN':  return 'text-amber-500 bg-amber-500/10 border-amber-500/20';
      default:      return 'text-zinc-400 bg-zinc-500/10 border-zinc-500/20';
    }
  };
</script>

<div class="flex flex-col h-full bg-bg-main border border-border-subtle rounded-3xl overflow-hidden shadow-2xl shadow-black/50">
  
  <div class="flex items-center justify-between p-4 border-b border-border-subtle bg-bg-surface/50 backdrop-blur-md">
    <div class="flex items-center gap-6">
      <div class="flex items-center gap-2">
        <div class="w-2 h-2 rounded-full bg-emerald-500 {autoRefresh ? 'animate-ping' : ''}"></div>
        <h2 class="text-xs font-black uppercase tracking-widest text-text-main">System Pulse</h2>
      </div>

      <nav class="flex p-1 bg-bg-main rounded-xl border border-border-subtle">
        {#each ['ALL', 'AUDIT', 'ERROR', 'INFO'] as filter}
          <button 
            on:click={() => activeFilter = filter}
            class="px-4 py-1.5 rounded-lg text-[10px] font-bold transition-all uppercase tracking-tight
            {activeFilter === filter ? 'bg-bg-surface text-brand-primary shadow-sm' : 'text-zinc-500 hover:text-zinc-300'}"
          >
            {filter}
          </button>
        {/each}
      </nav>
    </div>

    <button 
      on:click={() => autoRefresh = !autoRefresh}
      class="text-[10px] font-black uppercase tracking-widest {autoRefresh ? 'text-brand-primary' : 'text-zinc-500 hover:text-white'}"
    >
      {autoRefresh ? 'Live Stream' : 'Paused'}
    </button>
  </div>

  <div class="flex-1 overflow-y-auto font-mono text-[11px] leading-relaxed custom-scrollbar">
    <table class="w-full border-collapse">
      <thead class="sticky top-0 bg-bg-surface text-zinc-500 border-b border-border-subtle z-10 text-left">
        <tr>
          <th class="px-6 py-3 font-black uppercase tracking-tighter w-24">Timestamp</th>
          <th class="px-6 py-3 font-black uppercase tracking-tighter w-20">Level</th>
          <th class="px-6 py-3 font-black uppercase tracking-tighter">Payload / Activity</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-zinc-900/50">
        {#each filteredLogs as log (log.id || log.timestamp)}
          <tr transition:fade={{ duration: 200 }} class="hover:bg-white/5 transition-colors group">
            <td class="px-6 py-3 text-zinc-500 tabular-nums whitespace-nowrap">
              {new Date(log.timestamp).toLocaleTimeString('en-GB', { hour12: false })}
              <span class="opacity-0 group-hover:opacity-100 text-[9px] ml-1 transition-opacity">
                .{new Date(log.timestamp).getMilliseconds()}
              </span>
            </td>

            <td class="px-6 py-3">
              <span class="px-2 py-0.5 rounded border {getLevelStyles(log.level)} font-black text-[9px]">
                {log.level}
              </span>
            </td>

            <td class="px-6 py-3">
              {#if log.level === 'AUDIT'}
                <div class="flex items-center gap-2">
                  <span class="text-emerald-400 font-bold">{log.actor.username}</span>
                  <span class="text-zinc-500">→</span>
                  <span class="text-zinc-300 uppercase tracking-tight">{log.action}</span>
                  <span class="text-zinc-500">on</span>
                  <span class="px-2 py-0.5 bg-zinc-800 text-zinc-100 rounded-md border border-zinc-700">
                    {log.resource} <small class="text-zinc-500 ml-1">#{log.resourceId}</small>
                  </span>
                </div>
              {:else}
                <span class="{log.level === 'ERROR' ? 'text-red-400' : 'text-zinc-300'} font-medium">
                  {log.message}
                </span>
                {#if log.stackTrace}
                   <pre class="mt-2 p-3 bg-red-950/20 border border-red-500/10 rounded-xl text-red-500/70 overflow-x-auto">
                     {log.stackTrace}
                   </pre>
                {/if}
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
    
    {#if filteredLogs.length === 0}
      <div class="p-20 text-center text-zinc-600 uppercase tracking-widest font-black animate-pulse">
        No active signals in this buffer
      </div>
    {/if}
  </div>
</div>

<style>
  /* Minimal dark scrollbar for the "Command Center" feel */
  .custom-scrollbar::-webkit-scrollbar { width: 4px; }
  .custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
  .custom-scrollbar::-webkit-scrollbar-thumb { background: #27272a; border-radius: 10px; }
  .custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #3f3f46; }
</style>