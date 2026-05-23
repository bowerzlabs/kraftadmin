<script lang="ts">
  import { onMount } from 'svelte';
  import { fade } from 'svelte/transition';

  // Time Range & Filter Controls
  let selectedHours = 24;
  let activeInterval = 'HOURLY'; // MINUTELY, HOURLY, DAILY
  let sortByMetric = 'REQUEST_COUNT'; // REQUEST_COUNT, ERROR_RATE, SLOWEST

  // Data State
  let trafficData: any[] = [];
  let latency: any = { p50: 0, p95: 0, p99: 0, avgDurationMs: 0 };
  let topResources: any[] = [];
  let statusDistribution: Record<string, number> = {};
  let isLoading = true;

  let stats = { total: 0, peak: 0, avg: 0, errorRate: 0 };

  // Calculate dynamic intervals based on selected time window
  $: {
    if (selectedHours <= 2) activeInterval = 'MINUTELY';
    else if (selectedHours <= 48) activeInterval = 'HOURLY';
    else activeInterval = 'DAILY';
    
    // Reactively re-hydrate everything when the user switches time views
    fetchAllAnalytics();
  }

  async function fetchAllAnalytics() {
    try {
      isLoading = true;
      const [trendRes, latencyRes, topRes, statusRes] = await Promise.all([
        fetch(`/admin/api/analytics/traffic/trend?hours=${selectedHours}&interval=${activeInterval}`),
        fetch(`/admin/api/analytics/latency/report?hours=${selectedHours}`),
        fetch(`/admin/api/analytics/resources/top?limit=5&sortBy=${sortByMetric}`),
        fetch(`/admin/api/analytics/distribution/status`)
      ]);

      if (trendRes.ok) trafficData = await trendRes.json();
      if (latencyRes.ok) latency = await latencyRes.json();
      if (topRes.ok) topResources = await topRes.json();
      if (statusRes.ok) statusDistribution = await statusRes.json();

      calculateAggregatedStats();
    } catch (e) {
      console.error("Dashboard hydration failed", e);
    } finally {
      isLoading = false;
    }
  }

  function calculateAggregatedStats() {
    if (trafficData.length === 0) return;
    
    const counts = trafficData.map(d => d.totalRequests || d.count || 0);
    stats.total = counts.reduce((a, b) => a + b, 0);
    stats.peak = Math.max(...counts, 1);
    stats.avg = Math.round(stats.total / trafficData.length);

    // Calculate total error rate from the status code map distributions
    let totalCalls = 0;
    let errorCalls = 0;
    Object.entries(statusDistribution).forEach(([code, count]) => {
      const statusInt = parseInt(code);
      totalCalls += count;
      if (statusInt >= 400) errorCalls += count;
    });
    stats.errorRate = totalCalls > 0 ? (errorCalls / totalCalls) * 100 : 0.0;
  }

  function formatTimestamp(isoString: string): string {
    const date = new Date(isoString);
    if (activeInterval === 'MINUTELY') {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    if (activeInterval === 'HOURLY') {
      return `${date.toLocaleDateString([], { month: 'short', day: 'numeric' })} ${date.getHours()}:00`;
    }
    return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
  }

  onMount(fetchAllAnalytics);
</script>

<div class="min-h-screen bg-bg-main text-text-main p-6 font-sans antialiased transition-colors duration-200">
  
  <header class="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border-subtle pb-6 mb-8">
    <div>
      <div class="flex items-center gap-2 text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 mb-1">
        <span>KraftAdmin</span> <span class="text-border-subtle">/</span> <span class="text-brand-primary">Pulse Analytics</span>
      </div>
      <h1 class="text-3xl font-black tracking-tight">Intelligence Cockpit</h1>
    </div>
    
    <div class="flex flex-wrap items-center gap-3">
      <div class="bg-bg-surface border border-border-subtle p-1 rounded-xl flex items-center gap-1 shadow-sm">
        {#each [[1, '1h'], [24, '24h'], [48, '48h'], [168, '7d'], [720, '30d']] as [hours, label]}
          <button
            on:click={() => selectedHours = hours}
            class="px-3 py-1.5 rounded-lg text-xs font-bold uppercase tracking-wider transition-all {selectedHours === hours ? 'bg-brand-primary text-white shadow-md' : 'text-zinc-400 hover:text-text-main'}"
          >
            {label}
          </button>
        {/each}
      </div>

      <button 
        on:click={fetchAllAnalytics} 
        class="p-2.5 bg-bg-surface border border-border-subtle rounded-xl text-zinc-400 hover:text-text-main hover:bg-zinc-500/5 transition-colors shadow-sm"
        title="Refresh Analytics Window"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.253 8H18"/></svg>
      </button>
    </div>
  </header>

  {#if isLoading}
    <div class="h-[60vh] flex flex-col items-center justify-center gap-4">
        <div class="w-10 h-10 border-2 border-brand-primary/20 border-t-brand-primary rounded-full animate-spin"></div>
        <span class="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500">Aggregating Cloud Metrics Engine...</span>
    </div>
  {:else}
    <div class="grid grid-cols-12 gap-6" in:fade={{ duration: 250 }}>
      
      <div class="col-span-12 md:col-span-4 bg-bg-surface border border-border-subtle p-6 rounded-2xl shadow-sm relative overflow-hidden group">
        <div class="absolute inset-0 bg-gradient-to-br from-brand-primary/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none"></div>
        <span class="text-[10px] font-black uppercase tracking-widest text-zinc-500 block">System Latency (P95)</span>
        <div class="flex items-end justify-between mt-3">
          <span class="text-4xl font-black tracking-tight">
            {latency.p95?.toFixed(0) || 0}<small class="text-xs font-bold text-zinc-500 ml-1">ms</small>
          </span>
          <div class="text-right text-[10px] font-mono text-zinc-400 space-y-0.5">
            <div>P50: <span class="text-text-main font-bold">{latency.p50?.toFixed(0) || 0}ms</span></div>
            <div>P99: <span class="text-rose-500 font-bold">{latency.p99?.toFixed(0) || 0}ms</span></div>
          </div>
        </div>
      </div>

      <div class="col-span-12 md:col-span-4 bg-bg-surface border border-border-subtle p-6 rounded-2xl shadow-sm">
        <span class="text-[10px] font-black uppercase tracking-widest text-zinc-500 block">Throughput Volume</span>
        <div class="flex items-end justify-between mt-3">
          <div class="text-4xl font-black tracking-tight">
            {stats.total.toLocaleString()}
          </div>
          <span class="text-zinc-500 text-[10px] font-mono mb-1">Peak: {stats.peak.toLocaleString()}/int</span>
        </div>
      </div>

      <div class="col-span-12 md:col-span-4 bg-bg-surface border border-border-subtle p-6 rounded-2xl shadow-sm">
        <span class="text-[10px] font-black uppercase tracking-widest text-zinc-500 block">Calculated Failure Rates</span>
        <div class="flex items-end justify-between mt-3">
            <span class="text-4xl font-black tracking-tight {stats.errorRate > 2 ? 'text-rose-500' : ''}">
              {stats.errorRate.toFixed(2)}<small class="text-xs font-bold opacity-40 ml-0.5">%</small>
            </span>
            <div class="px-2.5 py-1 text-[9px] font-black rounded-lg uppercase tracking-wider {stats.errorRate > 2 ? 'bg-rose-500/10 text-rose-500 border border-rose-500/20' : 'bg-emerald-500/10 text-emerald-500 border border-emerald-500/20'}">
              {stats.errorRate > 2 ? 'Degraded' : 'Operational'}
            </div>
        </div>
      </div>

      <div class="col-span-12 lg:col-span-8 bg-bg-surface border border-border-subtle p-6 rounded-2xl shadow-sm">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h3 class="text-xs font-black uppercase tracking-widest text-zinc-400">Traffic Distribution Trend</h3>
            <p class="text-[10px] text-zinc-500 font-medium mt-0.5">Aggregated interval scale: <span class="text-text-main font-bold font-mono">{activeInterval}</span></p>
          </div>
          <span class="text-[10px] font-mono text-zinc-500">Interval Samples: {trafficData.length}</span>
        </div>
        
        <div class="h-56 flex items-end gap-1.5 px-2 relative pt-4 border-b border-border-subtle">
          {#each trafficData as point}
            {@const currentCount = point.totalRequests || point.count || 0}
            <div 
              class="flex-1 bg-brand-primary/20 dark:bg-brand-primary/30 hover:bg-brand-primary transition-all rounded-t-[3px] relative group cursor-crosshair"
              style="height: {Math.max((currentCount / stats.peak) * 100, 4)}%"
            >
                <div class="absolute -top-12 left-1/2 -translate-x-1/2 bg-bg-surface text-text-main p-2.5 rounded-xl shadow-xl opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap z-50 pointer-events-none border border-border-subtle text-[10px] font-mono font-bold">
                    <span class="text-brand-primary font-black block text-[11px] mb-0.5">{currentCount.toLocaleString()} Requests</span>
                    <span class="text-zinc-500 text-[9px] block">{formatTimestamp(point.timestamp)}</span>
                </div>
            </div>
          {/each}
        </div>
        
        <div class="flex justify-between items-center mt-3 text-[10px] font-mono text-zinc-500 px-1">
          <span>{trafficData.length > 0 ? formatTimestamp(trafficData[0].timestamp) : ''}</span>
          <span>{trafficData.length > 1 ? formatTimestamp(trafficData[trafficData.length - 1].timestamp) : ''}</span>
        </div>
      </div>

      <div class="col-span-12 lg:col-span-4 bg-bg-surface border border-border-subtle p-6 rounded-2xl shadow-sm flex flex-col justify-between">
        <div>
          <div class="flex items-center justify-between mb-6">
            <h3 class="text-xs font-black uppercase tracking-widest text-zinc-400">Bottleneck Rank</h3>
            <select 
              bind:value={sortByMetric} 
              on:change={fetchAllAnalytics}
              class="bg-bg-main border border-border-subtle text-[10px] font-bold uppercase tracking-wider rounded-lg p-1 text-text-main outline-none cursor-pointer hover:bg-zinc-500/5"
            >
              <option value="REQUEST_COUNT">Volume</option>
              <option value="ERROR_RATE">Failures</option>
              <option value="SLOWEST">Latency</option>
            </select>
          </div>

          <div class="space-y-5">
            {#each topResources as res, idx}
              <div class="space-y-1.5">
                <div class="flex items-start justify-between gap-4">
                  <div class="min-w-0">
                    <div class="text-xs font-mono font-bold truncate select-all" title={res.resource}>
                      {res.resource}
                    </div>
                    <div class="text-[10px] font-medium text-zinc-500 mt-0.5">
                      {res.totalRuns || res.requestCount || 0} calls • avg {res.averageDurationMs?.toFixed(0) || 0}ms
                    </div>
                  </div>
                  <div class="text-[10px] font-mono font-black text-right whitespace-nowrap text-zinc-400">
                    #{idx + 1}
                  </div>
                </div>
                <div class="w-full h-1.5 bg-bg-main rounded-full overflow-hidden border border-border-subtle/30">
                    <div class="h-full bg-brand-primary rounded-full" style="width: {Math.min(((res.totalRuns || res.requestCount || 0) / stats.peak) * 100, 100)}%"></div>
                </div>
              </div>
            {:else}
              <div class="text-center py-12 text-zinc-500 text-xs italic">No resource metrics recorded for this time range.</div>
            {/each}
          </div>
        </div>

        <div class="border-t border-border-subtle pt-4 mt-6 text-[10px] text-zinc-500 font-medium flex justify-between items-center">
          <span>Rank ordered by: <strong class="text-zinc-400 font-bold">{sortByMetric}</strong></span>
          <span class="font-mono">Limit: 5 Records</span>
        </div>
      </div>

    </div>
  {/if}
</div>