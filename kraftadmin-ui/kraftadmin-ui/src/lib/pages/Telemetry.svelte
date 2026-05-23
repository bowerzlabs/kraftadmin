<script lang="ts">
  import { onMount } from 'svelte';
  import { fade } from 'svelte/transition';

  // State management
  let activeTab = 'overview'; // overview, exceptions, tasks, outbound
  let dashboardData: any[] = [];
  let pagedData : any[] = [];
  let selectedTrace : any = null;
  let traceDetails : any = null;
  let loading = false;
  let limit = 50;
  let offset = 0;

  // Fetch standard overview data
  async function fetchDashboard() {
    loading = true;
    try {
      const res = await fetch(`/admin/api/monitoring/dashboard?limit=${limit}`);
      dashboardData = await res.json();
    } catch (err) {
      console.error("Failed to load dashboard metrics", err);
    } finally {
      loading = false;
    }
  }

  // Fetch dedicated table page details
  async function fetchTabCategory(tabName: string, resetPager = true) {
    if (resetPager) { offset = 0; pagedData = []; }
    if (tabName === 'overview') return fetchDashboard();
    
    loading = true;
    const endpointMap = {
      exceptions: 'exceptions',
      tasks: 'tasks',
      outbound: 'outbound-http'
    };

    try {
      const res = await fetch(`/admin/api/monitoring/${endpointMap[tabName]}?limit=20&offset=${offset}`);
      const data = await res.json();
      pagedData = resetPager ? data : [...pagedData, ...data];
    } catch (err) {
      console.error(`Failed to load ${tabName} data`, err);
    } finally {
      loading = false;
    }
  }

  // Fetch full stacked relation map details for the deep dive modal/panel
  async function inspectTrace(traceId: string) {
    selectedTrace = traceId;
    traceDetails = null;
    try {
      const res = await fetch(`/admin/api/monitoring/traces/${traceId}`);
      traceDetails = await res.json();
    } catch (err) {
      console.error("Trace aggregation failure", err);
    }
  }

  function handleTabChange(targetTab: string) {
    activeTab = targetTab;
    fetchTabCategory(targetTab);
  }

  function loadMore() {
    offset += 20;
    fetchTabCategory(activeTab, false);
  }

  // Helper utility to parse timestamps cleanly into UI sub-labels
  function formatFullDateTime(timestamp: number | string | undefined): string {
    if (!timestamp) return 'No Time Data';
    const date = new Date(Number(timestamp));
    return `${date.toLocaleDateString([], { month: 'short', day: 'numeric' })} at ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}`;
  }

  onMount(() => {
    fetchDashboard();
    const poll = setInterval(() => {
      if (activeTab === 'overview' && !selectedTrace) fetchDashboard();
    }, 4000);
    return () => clearInterval(poll);
  });
</script>

<div class="p-6 bg-bg-main min-h-screen text-text-main font-sans antialiased">
  
  <div class="flex items-center justify-between border-b border-border-subtle pb-6 mb-6">
    <div class="flex items-center gap-4">
      <div class="p-3 bg-bg-surface border border-border-subtle rounded-2xl text-brand-primary shadow-sm">
        <svg class="w-6 h-6 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/></svg>
      </div>
      <div>
        <h1 class="text-2xl font-black uppercase tracking-tighter">Monitoring Dashboard</h1>
        <p class="text-xs font-medium text-zinc-500">Monitor your application runtime events in real-time</p>
      </div>
    </div>
    
    <div class="flex items-center gap-2 px-4 py-1.5 bg-emerald-500/5 rounded-full border border-emerald-500/20">
      <div class="w-2 h-2 bg-emerald-500 rounded-full animate-ping"></div>
      <span class="text-[10px] font-black text-emerald-600 uppercase tracking-widest">Live Engine Connected</span>
    </div>
  </div>

  <div class="flex items-center gap-2 border-b border-border-subtle pb-px mb-6">
    {#each [['overview', 'Overview Feed'], ['exceptions', 'Exceptions / Errors'], ['tasks', 'Background Tasks'], ['outbound', 'Outbound Requests']] as [id, label]}
      <button 
        on:click={() => handleTabChange(id)}
        class="px-4 py-2 text-xs font-bold uppercase tracking-wider border-b-2 transition-all duration-200 {activeTab === id ? 'border-brand-primary text-brand-primary bg-brand-primary/5 rounded-t-xl' : 'border-transparent text-zinc-400 hover:text-text-main'}"
      >
        {label}
      </button>
    {/each}
  </div>

  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
    
    <div class="lg:col-span-2 space-y-4">
      {#if loading && offset === 0}
        <div class="p-12 text-center text-zinc-400 text-sm font-medium uppercase tracking-widest animate-pulse">Querying local outbox storage database...</div>
      {:else}
        
        {#if activeTab === 'overview'}
          <div class="bg-bg-surface border border-border-subtle rounded-3xl overflow-hidden shadow-sm">
            <table class="w-full text-left border-collapse">
              <thead>
                <tr class="bg-zinc-500/5 text-zinc-400 text-[10px] font-black uppercase tracking-wider border-b border-border-subtle">
                  <th class="p-4">Method / Resource</th>
                  <th class="p-4">Timestamp</th>
                  <th class="p-4">Duration</th>
                  <th class="p-4">Queries</th>
                  <th class="p-4">Status</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle text-xs font-medium">
                {#each dashboardData as entry}
                  <tr 
                    on:click={() => inspectTrace(entry.event.traceId)}
                    class="hover:bg-zinc-500/5 cursor-pointer transition-colors {selectedTrace === entry.event.traceId ? 'bg-brand-primary/5' : ''}"
                  >
                    <td class="p-4">
                      <span class="px-2 py-0.5 rounded text-[10px] font-black mr-2 uppercase {entry.event.action === 'GET' ? 'bg-blue-500/10 text-blue-500' : 'bg-amber-500/10 text-amber-500'}">{entry.event.action}</span>
                      <span class="text-text-main font-mono">{entry.event.resource}</span>
                    </td>
                    <td class="p-4 font-mono text-zinc-400 text-[11px]">
                      {formatFullDateTime(entry.event.timestamp || entry.event.createdAt)}
                    </td>
                    <td class="p-4 font-mono text-zinc-500">{entry.event.durationMs}ms</td>
                    <td class="p-4">
                      <span class="px-2 py-0.5 rounded-full bg-zinc-500/10 font-bold text-zinc-500">{entry.queries.length} SQL</span>
                    </td>
                    <td class="p-4">
                      <span class="font-bold {entry.event.status >= 400 ? 'text-rose-500' : 'text-emerald-500'}">{entry.event.status}</span>
                    </td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}

        {#if activeTab === 'exceptions'}
          <div class="space-y-3">
            {#each pagedData as exc}
              <div on:click={() => inspectTrace(exc.traceId)} class="p-4 bg-bg-surface border border-border-subtle rounded-2xl hover:border-rose-500/30 cursor-pointer transition-all">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs font-mono font-black text-rose-500 bg-rose-500/10 px-2 py-0.5 rounded">{exc.exceptionClass.split('.').pop()}</span>
                  <span class="text-[11px] font-mono font-bold text-zinc-400">{formatFullDateTime(exc.timestamp || exc.createdAt)}</span>
                </div>
                <h3 class="text-sm font-bold text-text-main mb-1">{exc.message}</h3>
                <p class="text-xs text-zinc-500 font-mono truncate">{exc.method} {exc.path}</p>
              </div>
            {/each}
            <button on:click={loadMore} class="w-full py-3 bg-zinc-500/5 hover:bg-zinc-500/10 border border-border-subtle rounded-xl text-xs font-black uppercase tracking-wider">Load Older Failures</button>
          </div>
        {/if}

        {#if activeTab === 'tasks'}
          <div class="bg-bg-surface border border-border-subtle rounded-3xl overflow-hidden shadow-sm">
            <table class="w-full text-left border-collapse">
              <thead>
                <tr class="bg-zinc-500/5 text-zinc-400 text-[10px] font-black uppercase tracking-wider border-b border-border-subtle">
                  <th class="p-4">Task Name</th>
                  <th class="p-4">Execution Time</th>
                  <th class="p-4">Type</th>
                  <th class="p-4">Status</th>
                  <th class="p-4">Duration</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle text-xs font-medium">
                {#each pagedData as task}
                  <tr on:click={() => inspectTrace(task.traceId)} class="hover:bg-zinc-500/5 cursor-pointer">
                    <td class="p-4 font-mono text-text-main">{task.name}</td>
                    <td class="p-4 font-mono text-zinc-400 text-[11px]">
                      {formatFullDateTime(task.createdAt || task.timestamp)}
                    </td>
                    <td class="p-4 text-zinc-400 text-[10px] uppercase font-bold">{task.type}</td>
                    <td class="p-4">
                      <span class="px-2 py-0.5 rounded text-[10px] font-black uppercase {task.status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-500' : task.status === 'START' ? 'bg-blue-500/10 text-blue-500' : 'bg-rose-500/10 text-rose-500'}">{task.status}</span>
                    </td>
                    <td class="p-4 font-mono text-zinc-500">{task.durationMs}ms</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}

        {#if activeTab === 'outbound'}
          <div class="bg-bg-surface border border-border-subtle rounded-3xl overflow-hidden shadow-sm">
            <table class="w-full text-left border-collapse">
              <thead>
                <tr class="bg-zinc-500/5 text-zinc-400 text-[10px] font-black uppercase tracking-wider border-b border-border-subtle">
                  <th class="p-4">Outbound URL</th>
                  <th class="p-4">Fired At</th>
                  <th class="p-4">Method</th>
                  <th class="p-4">Status</th>
                  <th class="p-4">Duration</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle text-xs font-medium">
                {#each pagedData as http}
                  <tr on:click={() => inspectTrace(http.traceId)} class="hover:bg-zinc-500/5 cursor-pointer">
                    <td class="p-4 font-mono text-text-main truncate max-w-xs">{http.url}</td>
                    <td class="p-4 font-mono text-zinc-400 text-[11px]">
                      {formatFullDateTime(http.createdAt || http.timestamp)}
                    </td>
                    <td class="p-4"><span class="px-2 py-0.5 rounded text-[10px] font-black bg-purple-500/10 text-purple-500 uppercase">{http.method}</span></td>
                    <td class="p-4 font-bold {http.statusCode >= 400 ? 'text-rose-500' : 'text-emerald-500'}">{http.statusCode}</td>
                    <td class="p-4 font-mono text-zinc-500">{http.durationMs}ms</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}

      {/if}
    </div>

    <div class="bg-bg-surface border border-border-subtle rounded-3xl p-6 shadow-sm sticky top-6">
      {#if !selectedTrace}
        <div class="text-center py-12 text-zinc-400 space-y-2">
          <svg class="w-8 h-8 mx-auto opacity-40" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
          <p class="text-xs font-bold uppercase tracking-wider">Select an execution row</p>
          <p class="text-[11px] text-zinc-500 max-w-[200px] mx-auto leading-relaxed">Inspect queries and outbound dependencies under that trace.</p>
        </div>
      {:else if !traceDetails}
        <div class="text-center py-12 text-zinc-400 text-xs font-mono uppercase tracking-widest animate-pulse">Assembling trace timelines...</div>
      {:else}
        
        <div class="space-y-6" in:fade={{ duration: 150 }}>
          <div class="flex items-center justify-between border-b border-border-subtle pb-4">
            <div>
              <h2 class="text-sm font-black uppercase tracking-tight">Trace Deep Dive</h2>
              <p class="text-[10px] font-mono text-zinc-400">{traceDetails.traceId}</p>
            </div>
            <button on:click={() => selectedTrace = null} class="text-zinc-400 hover:text-text-main text-xs font-bold uppercase">Close</button>
          </div>

          {#if traceDetails.request}
            <div class="p-3 bg-zinc-500/5 rounded-xl border border-border-subtle">
              <div class="flex justify-between items-start mb-1">
                <span class="text-[9px] font-black uppercase text-zinc-400 tracking-wider">Inbound Execution Endpoint</span>
                <span class="text-[9px] font-mono text-zinc-500">{formatFullDateTime(traceDetails.request.timestamp || traceDetails.request.createdAt)}</span>
              </div>
              <p class="text-xs font-mono font-bold text-text-main">{traceDetails.request.action} {traceDetails.request.resource}</p>
              <p class="text-xs font-mono text-zinc-500 mt-1">Total Frame Window Latency: <span class="text-brand-primary font-bold">{traceDetails.request.durationMs}ms</span></p>
            </div>
          {/if}

          <div>
            <span class="text-[10px] font-black uppercase text-zinc-400 tracking-widest block mb-2">Database Layer ({traceDetails.queries.length})</span>
            {#if traceDetails.queries.length === 0}
              <p class="text-[11px] text-zinc-500 font-medium italic pl-2">No database statements triggered in this runtime trace scope.</p>
            {:else}
              <div class="space-y-2 max-h-40 overflow-y-auto pr-1">
                {#each traceDetails.queries as query}
                  <div class="p-2.5 bg-zinc-500/5 rounded-lg text-[11px] font-mono border border-border-subtle">
                    <p class="text-text-main line-clamp-2 select-all">{query.sql}</p>
                    <div class="flex items-center justify-between text-zinc-500 text-[10px] mt-1.5 font-sans font-medium">
                      <div class="flex items-center gap-2">
                        <span>Latency: <strong class="text-text-main font-mono">{query.durationMs}ms</strong></span>
                        {#if query.isSlowQuery}<span class="text-amber-500 font-bold uppercase text-[9px]">Slow Query</span>{/if}
                        {#if query.isPotentialNPlusOne}<span class="text-rose-500 font-bold uppercase text-[9px]">N+1 Trap</span>{/if}
                      </div>
                      <span class="text-[9px] opacity-60">{formatFullDateTime(query.startedAt || query.createdAt)}</span>
                    </div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          <div>
            <span class="text-[10px] font-black uppercase text-zinc-400 tracking-widest block mb-2">Outbound HTTP Client Calls ({traceDetails.outboundHttp.length})</span>
            {#if traceDetails.outboundHttp.length === 0}
              <p class="text-[11px] text-zinc-500 font-medium italic pl-2">No external HTTP calls made during this execution context frame.</p>
            {:else}
              <div class="space-y-2">
                {#each traceDetails.outboundHttp as outbound}
                  <div class="p-2.5 bg-zinc-500/5 rounded-lg text-[11px] font-mono border border-border-subtle">
                    <p class="text-text-main break-all font-bold"><span class="text-purple-500 mr-1">[{outbound.method}]</span> {outbound.url}</p>
                    <div class="flex justify-between items-center text-zinc-500 text-[10px] mt-1.5 font-sans font-medium">
                      <span>Status: <span class="font-bold text-text-main">{outbound.statusCode}</span> | Duration: <span class="text-text-main font-mono font-bold">{outbound.durationMs}ms</span></span>
                      <span class="text-[9px] opacity-60">{formatFullDateTime(outbound.createdAt || outbound.timestamp)}</span>
                    </div>
                  </div>
                {/each}
              </div>
            {/if}
          </div>

          {#if traceDetails.exception}
            <div class="p-3 bg-rose-500/5 border border-rose-500/20 rounded-xl">
              <div class="flex justify-between items-start mb-1">
                <span class="text-[9px] font-black uppercase text-rose-500 tracking-wider">Unhandled Exception Crashed Frame</span>
                <span class="text-[9px] font-mono text-rose-400/70">{formatFullDateTime(traceDetails.exception.timestamp || traceDetails.exception.createdAt)}</span>
              </div>
              <p class="text-xs font-mono font-bold text-rose-600 truncate">{traceDetails.exception.exceptionClass}</p>
              <p class="text-xs text-text-main font-medium mt-1">{traceDetails.exception.message}</p>
            </div>
          {/if}

        </div>
      {/if}
    </div>

  </div>
</div>