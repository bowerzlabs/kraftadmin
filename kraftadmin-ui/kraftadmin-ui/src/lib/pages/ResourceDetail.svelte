<script lang="ts">
  import { onMount } from "svelte";
  import { link, push } from "svelte-spa-router";

  export let params: { name: string; id: string };

  let resource: any = null;
  let data: any = {}; 
  let values: any = {}; 
  let loading = true;

  onMount(async () => {
    try {
      const resMeta = await fetch("/admin/api/resources/descriptors");
      const meta = await resMeta.json();
      resource = meta.resources.find((r: any) => r.name === params.name);

      const resData = await fetch(`/admin/api/resources/${params.name}/${params.id}`);
      data = await resData.json();
      values = data.values || {};
    } catch (e) {
      console.error("Failed to load details", e);
    } finally {
      loading = false;
    }
  });

  // Type Guards & Helpers from CellComponent
  const isImageUrl = (val: any) => typeof val === 'string' && val.match(/\.(jpeg|jpg|gif|png|webp|svg)$/i);
  const isVideoUrl = (val: any) => typeof val === 'string' && val.match(/\.(mp4|webm|ogg)$/i);
  const isDateArray = (val: any) => Array.isArray(val) && val.length >= 3 && typeof val[0] === 'number';
  const isObjectResponse = (val: any) => val && typeof val === 'object' && 'displayField' in val;
  const isEmbeddedResponse = (val: any) => val && typeof val === 'object' && 'summary' in val;

  function formatSummary(embedded: any) {
    if (embedded.summary && embedded.summary.trim() !== "") return embedded.summary;
    const entries = Object.entries(embedded.data || embedded.fullMap || {});
    if (entries.length === 0) return 'Empty Object';
    return entries.slice(0, 2).map(([k, v]) => `${k}: ${v}`).join(", ");
  }

  async function handleDelete() {
    if (!confirm("Are you sure you want to delete this record?")) return;
    const res = await fetch(`/admin/api/resources/${params.name}/${params.id}`, { method: "DELETE" });
    if (res.ok) push(`/resources/${params.name}`);
    else alert("Delete failed.");
  }
</script>

<div class="p-8 space-y-8 max-w-5xl mx-auto">
  {#if loading}
    <div class="flex flex-col items-center justify-center py-20 gap-4">
      <div class="w-8 h-8 border-2 border-brand-primary/20 border-t-brand-primary rounded-full animate-spin"></div>
      <p class="text-[10px] text-zinc-500 font-black uppercase tracking-[0.2em]">Synchronizing...</p>
    </div>
  {:else if resource}
    <div class="flex flex-col md:flex-row justify-between items-start md:items-center gap-6 border-b border-zinc-800 pb-8">
      <div class="space-y-1">
        <a href="/resources/{params.name}" use:link class="text-[10px] text-zinc-500 font-bold uppercase tracking-widest hover:text-brand-primary transition-colors">
          &larr; {resource.label} List
        </a>
        <h1 class="text-4xl font-black text-white tracking-tighter">
          Record Details <span class="text-brand-primary italic opacity-50 ml-2">/</span>
          <span class="text-zinc-600 font-mono text-xl ml-2">#{params.id.slice(0, 8)}</span>
        </h1>
      </div>

      <div class="flex gap-3">
        <button on:click={handleDelete} class="px-5 py-2 bg-red-500/10 text-red-500 text-[10px] font-black uppercase tracking-widest rounded-lg border border-red-500/20 hover:bg-red-500/20 transition-all">
          Delete
        </button>
        <a href="/resources/{params.name}/edit/{params.id}" use:link class="px-5 py-2 bg-brand-primary text-white text-[10px] font-black uppercase tracking-widest rounded-lg shadow-lg shadow-brand-primary/20 hover:brightness-110 transition-all">
          Edit Record
        </a>
      </div>
    </div>

    <div class="space-y-4">
      {#each resource.columns.filter((c: any) => c.visible !== false) as col}
        <div class="bg-zinc-900/40 border border-zinc-800 rounded-xl p-5 hover:border-zinc-700 transition-colors group">
          <div class="flex flex-col gap-3">
            <div class="flex items-center gap-2">
              <div class="w-1 h-1 rounded-full bg-brand-primary group-hover:scale-150 transition-transform"></div>
              <span class="text-[10px] font-black text-zinc-500 uppercase tracking-[0.15em]">{col.label}</span>
            </div>

            <div class="text-zinc-200">
              {#if values[col.name] === null || values[col.name] === undefined}
                <span class="text-zinc-800 italic font-mono text-xs">null</span>

              {:else if isEmbeddedResponse(values[col.name]) || col.type === "OBJECT"}
                <div class="space-y-3 w-full">
                  <div class="px-3 py-1.5 bg-zinc-800/50 border border-zinc-700/50 rounded-md inline-block">
                    <span class="text-xs italic text-brand-primary font-medium">"{formatSummary(values[col.name])}"</span>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 bg-black/30 p-4 rounded-lg border border-zinc-800/50">
                    {#each Object.entries(values[col.name].data || values[col.name].fullMap || values[col.name]) as [subKey, subVal]}
                      {#if !subKey.startsWith('$')}
                        <div class="flex flex-col gap-1 border-l border-zinc-800 pl-3">
                          <span class="text-[8px] font-black text-zinc-600 uppercase tracking-tighter">{subKey}</span>
                          <span class="text-xs text-zinc-400 font-mono truncate">{subVal ?? '—'}</span>
                        </div>
                      {/if}
                    {/each}
                  </div>
                </div>

              {:else if isDateArray(values[col.name]) || col.type === "DATETIME"}
                <div class="flex items-center gap-2 font-mono text-xs text-brand-primary">
                  <span class="text-zinc-600">📅</span>
                  {#if Array.isArray(values[col.name])}
                    {values[col.name][0]}-{String(values[col.name][1]).padStart(2,'0')}-{String(values[col.name][2]).padStart(2,'0')}
                    {#if values[col.name][3] !== undefined}
                      <span class="opacity-50 ml-1">{String(values[col.name][3]).padStart(2,'0')}:{String(values[col.name][4] || 0).padStart(2,'0')}</span>
                    {/if}
                  {:else}
                    {new Date(values[col.name]).toLocaleString()}
                  {/if}
                </div>

              {:else if isObjectResponse(values[col.name]) || col.type === "RELATION"}
                <div class="inline-flex items-center gap-3 px-4 py-2 bg-brand-primary/5 border border-brand-primary/10 rounded-lg">
                  <span class="text-brand-primary font-bold text-xs">{values[col.name].displayField || values[col.name].id}</span>
                  <span class="text-[9px] text-zinc-600 font-mono tracking-tighter">REF::{values[col.name].id?.slice(0,8)}</span>
                </div>

              {:else if isImageUrl(values[col.name]) || col.type === "IMAGE"}
                <div class="relative w-full max-w-sm aspect-video bg-black rounded-lg border border-zinc-800 overflow-hidden group/img">
                  <img src={values[col.name]} alt={col.label} class="w-full h-full object-cover" />
                  <div class="absolute inset-0 bg-brand-primary/20 opacity-0 group-hover/img:opacity-100 transition-opacity"></div>
                </div>

              {:else if typeof values[col.name] === 'boolean'}
                <div class="flex items-center gap-2">
                  <div class="w-2 h-2 rounded-full {values[col.name] ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.5)]' : 'bg-zinc-700'}"></div>
                  <span class="text-[10px] font-black uppercase tracking-widest {values[col.name] ? 'text-green-500' : 'text-zinc-600'}">
                    {values[col.name] ? 'True / Active' : 'False / Inactive'}
                  </span>
                </div>

              {:else if col.type === "WYSIWYG"}
                <div class="prose prose-invert prose-xs max-w-none bg-black/40 p-5 rounded-xl border border-zinc-800">
                  {@html values[col.name]}
                </div>

              {:else}
                <p class="text-sm font-medium leading-relaxed whitespace-pre-wrap">{values[col.name]}</p>
              {/if}
            </div>
          </div>
        </div>
      {/each}
    </div>

    <div class="pt-8 flex justify-between items-center text-[9px] font-black text-zinc-700 uppercase tracking-[0.2em]">
      <span>Resource: {params.name}</span>
      <span>System ID: {params.id}</span>
    </div>
  {/if}
</div>

<style>
  :global(body) {
    background-color: #09090b;
  }
</style>