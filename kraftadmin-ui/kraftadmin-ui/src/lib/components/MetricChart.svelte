<script lang="ts">
  import { TrendingUp, TrendingDown, Minus } from 'lucide-svelte';

  interface MetricBucket {
    label: string;
    periodStart: string;
    value: number;
  }

  interface MetricGroup {
    key: string;
    label: string;
    value: number;
  }

  interface DashboardMetric {
    name: string;
    label: string;
    chartType: 'LINE' | 'BAR' | 'AREA' | 'PIE' | 'DONUT' | 'NUMBER';
    mode: 'SNAPSHOT' | 'TIME_SERIES' | 'GROUPED';
    buckets: MetricBucket[];
    groups: MetricGroup[];
    currentPeriodValue: number;
    previousPeriodValue: number | null;
    growthPercent: number | null;
  }

  export let metric: DashboardMetric;

  $: series = metric.mode === 'GROUPED'
          ? metric.groups.map((g) => ({ label: g.label, value: g.value }))
          : metric.buckets.map((b) => ({ label: b.label, value: b.value }));

  $: maxValue = Math.max(...series.map((s) => s.value), 1);
  $: totalValue = series.reduce((sum, s) => sum + s.value, 0);
  $: hasData = series.some((s) => s.value > 0);
  $: formattedCurrentValue = formatValue(metric.currentPeriodValue);

  function formatValue(value: number): string {
    if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`;
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
    if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`;
    return new Intl.NumberFormat().format(value);
  }

  function getGrowthClass(growth: number | null): string {
    if (growth === null) return 'text-text-muted';
    if (growth > 0) return 'text-success';
    if (growth < 0) return 'text-danger';
    return 'text-text-muted';
  }

  function getX(index: number): number {
    return (index / Math.max(series.length - 1, 1)) * 1000;
  }

  function getY(value: number): number {
    return 300 - (value / maxValue) * 260;
  }

  $: chartPoints = series.map((s, index) => `${getX(index)},${getY(s.value)}`).join(' ');
  $: areaPoints = `0,300 ${chartPoints} 1000,300`;

  // Fixed, distinguishable palette for pie/donut slices — cycles if there
  // are more slices than colors, which is fine since groupByLimit caps
  // cardinality anyway (default 10).
  const SLICE_COLORS = [
    'var(--color-brand-primary, #3b82f6)', '#22c55e', '#f59e0b', '#ef4444',
    '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6',
  ];

  function sliceColor(index: number): string {
    return SLICE_COLORS[index % SLICE_COLORS.length];
  }

  // Converts { label, value }[] into SVG arc path data for a pie/donut,
  // as fractions of a full circle centered at (100,100) with radius 90.
  function buildSlices(items: { label: string; value: number }[], innerRadiusRatio = 0) {
    const total = items.reduce((sum, s) => sum + s.value, 0);
    if (total === 0) return [];

    const cx = 100, cy = 100, r = 90;
    const innerR = r * innerRadiusRatio;
    let cumulativeAngle = -Math.PI / 2; // start at 12 o'clock

    return items.map((item, i) => {
      const fraction = item.value / total;
      const angle = fraction * 2 * Math.PI;
      const startAngle = cumulativeAngle;
      const endAngle = cumulativeAngle + angle;
      cumulativeAngle = endAngle;

      const x1 = cx + r * Math.cos(startAngle);
      const y1 = cy + r * Math.sin(startAngle);
      const x2 = cx + r * Math.cos(endAngle);
      const y2 = cy + r * Math.sin(endAngle);
      const largeArc = angle > Math.PI ? 1 : 0;

      let path: string;
      if (innerR > 0) {
        const ix1 = cx + innerR * Math.cos(endAngle);
        const iy1 = cy + innerR * Math.sin(endAngle);
        const ix2 = cx + innerR * Math.cos(startAngle);
        const iy2 = cy + innerR * Math.sin(startAngle);
        path = `M ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 1 ${x2} ${y2} L ${ix1} ${iy1} A ${innerR} ${innerR} 0 ${largeArc} 0 ${ix2} ${iy2} Z`;
      } else {
        path = `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${largeArc} 1 ${x2} ${y2} Z`;
      }

      return { path, color: sliceColor(i), label: item.label, value: item.value, percent: (fraction * 100).toFixed(1) };
    });
  }

  $: slices = (metric.chartType === 'PIE' || metric.chartType === 'DONUT')
          ? buildSlices(series, metric.chartType === 'DONUT' ? 0.6 : 0)
          : [];
</script>

<div class="bg-bg-surface border border-border-subtle rounded-3xl p-6 shadow-sm">

  <div class="flex items-start justify-between gap-4 mb-6">
    <div>
      <p class="text-[10px] font-black uppercase tracking-widest text-text-muted">
        {metric.label}
      </p>

      <div class="flex items-baseline gap-3 mt-2">
        <span class="text-3xl font-black text-text-main">
          {formattedCurrentValue}
        </span>

        {#if metric.growthPercent !== null}
          <span class={`flex items-center gap-1 text-xs font-bold ${getGrowthClass(metric.growthPercent)}`}>
            {#if metric.growthPercent > 0}
              <TrendingUp size={14} />
            {:else if metric.growthPercent < 0}
              <TrendingDown size={14} />
            {:else}
              <Minus size={14} />
            {/if}
            {metric.growthPercent > 0 ? '+' : ''}{metric.growthPercent.toFixed(1)}%
          </span>
        {/if}
      </div>

      <p class="text-xs text-text-muted mt-1">
        {#if metric.mode === 'GROUPED'}
          Top {series.length} by value
        {:else if metric.mode === 'TIME_SERIES'}
          Compared to previous period
        {:else}
          Overall total
        {/if}
      </p>
    </div>

    {#if metric.chartType !== 'NUMBER'}
      <span class="text-[10px] uppercase font-black tracking-widest text-text-muted">
        {metric.chartType}
      </span>
    {/if}
  </div>

  {#if metric.chartType === 'NUMBER'}
    <!-- SNAPSHOT metrics need no chart body at all — the big value above IS the metric. -->

  {:else if hasData}

    {#if metric.chartType === 'BAR'}
      <div class="h-48 overflow-x-auto overflow-y-hidden">
        <div class="flex items-end gap-2 h-full min-w-full" style={`width: ${Math.max(series.length * 56, 100)}px`}>
          {#each series as item}
            <div class="flex-1 min-w-[48px] h-full flex flex-col justify-end gap-2">
              <div
                      class="w-full bg-brand-primary rounded-t-lg transition-all duration-300 hover:opacity-80"
                      style={`height: ${(item.value / maxValue) * 100}%`}
                      title={`${item.label}: ${formatValue(item.value)}`}
              ></div>
              <span class="text-[9px] text-text-muted text-center truncate">{item.label}</span>
            </div>
          {/each}
        </div>
      </div>

    {:else if metric.chartType === 'PIE' || metric.chartType === 'DONUT'}
      <div class="flex flex-col sm:flex-row items-center gap-6">
        <svg viewBox="0 0 200 200" class="w-40 h-40 flex-shrink-0">
          {#each slices as slice}
            <path d={slice.path} fill={slice.color} class="transition-opacity hover:opacity-80">
              <title>{slice.label}: {formatValue(slice.value)} ({slice.percent}%)</title>
            </path>
          {/each}
        </svg>
        <div class="flex-1 w-full space-y-2 min-w-0">
          {#each slices as slice}
            <div class="flex items-center justify-between gap-2 text-xs">
              <div class="flex items-center gap-2 min-w-0">
                <span class="w-2.5 h-2.5 rounded-full flex-shrink-0" style={`background: ${slice.color}`}></span>
                <span class="text-text-main truncate">{slice.label}</span>
              </div>
              <span class="text-text-muted font-mono flex-shrink-0">{formatValue(slice.value)} · {slice.percent}%</span>
            </div>
          {/each}
        </div>
      </div>

    {:else}
      <!-- LINE / AREA -->
      <div class="relative h-48">
        <div class="absolute inset-0 flex flex-col justify-between pointer-events-none">
          {#each Array(4) as _}
            <div class="border-t border-border-subtle"></div>
          {/each}
        </div>

        <svg viewBox="0 0 1000 300" preserveAspectRatio="none" class="absolute inset-0 w-full h-full overflow-visible">
          {#if metric.chartType === 'AREA'}
            <polygon points={areaPoints} class="fill-brand-primary opacity-10" />
          {/if}

          <polyline
                  points={chartPoints}
                  fill="none"
                  stroke="currentColor"
                  stroke-width="4"
                  vector-effect="non-scaling-stroke"
                  class="text-brand-primary"
          />

          {#each series as item, index}
            <circle cx={getX(index)} cy={getY(item.value)} r="5" class="fill-brand-primary">
              <title>{item.label}: {formatValue(item.value)}</title>
            </circle>
          {/each}
        </svg>
      </div>

      <div class="flex justify-between mt-3">
        {#each series as item}
          <span class="text-[9px] text-text-muted truncate max-w-16">{item.label}</span>
        {/each}
      </div>
    {/if}

  {:else}
    <div class="h-48 flex items-center justify-center">
      <p class="text-xs text-text-muted">No data available for this period</p>
    </div>
  {/if}

</div>