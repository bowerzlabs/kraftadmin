<script lang="ts">
  export let value: any;
  export let columnName: string;

  // 1. Precise Type Guards
  const isObjectResponse = (val: any) => val && typeof val === 'object' && 'displayField' in val;
  const isEmbeddedResponse = (val: any) => val && typeof val === 'object' && 'summary' in val;
  const isDateArray = (val: any) => Array.isArray(val) && val.length >= 3 && typeof val[0] === 'number';

  // 2. Formatting Helpers
  const isImage = (val: any) => typeof val === 'string' && val.match(/\.(jpeg|jpg|gif|png|webp)$/i);
  const isUrl = (val: any) => typeof val === 'string' && val.startsWith('http');

  function formatDisplay(val: any) {
    if (!val) return '—';
    // If it's the ID, truncate it so it doesn't break the table layout
    const str = String(val);
    return str.length > 20 ? str.substring(0, 8) + '...' : str;
  }

  function formatSummary(embedded: any) {
      if (embedded.summary && embedded.summary.trim() !== "") return embedded.summary;

      // If summary is empty, generate one from the data keys/values
      const entries = Object.entries(embedded.data || {});
      if (entries.length === 0) return 'Empty';

      return entries
        .filter(([_, v]) => v !== null && (!Array.isArray(v) || v.length > 0))
        .slice(0, 2)
        .map(([k, v]) => `${k}: ${Array.isArray(v) ? v.length : v}`)
        .join(", ");
    }

    // Formatting for numbers (like your salary: 8928787.0)
    const formatCurrency = (val: number) => new Intl.NumberFormat('en-US', {
      style: 'currency', currency: 'USD', maximumFractionDigits: 0
    }).format(val);
</script>

<div class="text-sm">
  {#if value === null || value === undefined}
    <span class="text-zinc-500 opacity-30">—</span>

 {:else if isObjectResponse(value)}
      <div class="flex items-center gap-1.5 group cursor-pointer">
       <div class="w-1.5 h-1.5 rounded-full bg-brand-primary"></div>
       <span class="text-brand-primary font-bold text-[11px] uppercase truncate max-w-[120px]">
         {value.displayField.length > 20 ? value.displayField.substring(0,8) : value.displayField}
       </span>
     </div>

   {:else if isEmbeddedResponse(value)}
     <span class="text-zinc-500 italic text-xs border-b border-zinc-200 border-dotted cursor-help"
           title={JSON.stringify(value.data, null, 2)}>
       {formatSummary(value)}
     </span>

  {:else if isDateArray(value)}
    <span class="text-zinc-600 font-mono text-xs">
      {value[0]}-{String(value[1]).padStart(2,'0')}-{String(value[2]).padStart(2,'0')}
    </span>

  {:else if isImage(value)}
    <img src={value} alt={columnName} class="w-8 h-8 rounded-lg object-cover border border-border-subtle shadow-sm" />

  {:else if Array.isArray(value)}
    <div class="flex flex-wrap gap-1">
      {#each value.slice(0, 2) as item}
        <span class="px-1.5 py-0.5 bg-zinc-100 text-zinc-600 text-[10px] font-bold rounded border border-zinc-200">
          {isObjectResponse(item) ? item.displayField : item}
        </span>
      {/each}
      {#if value.length > 2}
        <span class="text-[10px] text-zinc-400">+{value.length - 2}</span>
      {/if}
    </div>

  {:else if typeof value === 'boolean'}
    <span class="flex items-center gap-1.5">
      <span class="w-1.5 h-1.5 rounded-full {value ? 'bg-success' : 'bg-zinc-400'}"></span>
      <span class="text-[10px] font-bold uppercase {value ? 'text-text-main' : 'text-zinc-400'}">
        {value ? 'Active' : 'Off'}
      </span>
    </span>

  {:else}
    <span class="truncate block max-w-[180px] font-medium text-text-main leading-tight">
      {value}
    </span>
  {/if}
</div>