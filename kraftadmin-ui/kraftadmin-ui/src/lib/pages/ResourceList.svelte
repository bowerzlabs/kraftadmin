<script lang="ts">
  import { link } from 'svelte-spa-router';
  import Cell from '../../lib/components/Cell.svelte';

  export let params: { name?: string } = {};

  let resource: any = null;
  let items: any[] = [];
  let columns: any[] = [];
  let currentPage = 1; // Use a primitive to avoid object-mutation loops
  let pagination = { total: 0, pageSize: 20, totalPages: 0 };
  let loading = true;

  // 1. Trigger ONLY when the resource name changes (Reset page to 1)
  $: if (params.name) {
      currentPage = 1; 
      loadData(params.name, 1);
  }

  // 2. Trigger when the page explicitly changes
  async function handlePageChange(newPage: number) {
    if (newPage >= 1 && newPage <= pagination.totalPages && newPage !== currentPage) {
      currentPage = newPage;
      await loadData(params.name!, newPage);
    }
  }

  async function loadData(resourceName: string, page: number) {
    loading = true;
    try {
      const response = await fetch(`/admin/api/resources/${resourceName}?page=${page}&size=${pagination.pageSize}`);
      const result = await response.json();

      resource = result.resource;
      columns = resource.columns.filter((c: any) => c.visible);
      items = resource.data.items;
      
      // Update metadata without triggering a re-run
      pagination = {
        total: resource.data.total,
        pageSize: resource.data.pageSize,
        totalPages: resource.data.totalPages
      };
    } catch (e) {
      console.error("Error loading resource data:", e);
    } finally {
      loading = false;
    }
  }

  async function handleDelete(id: any) {
    if (!confirm(`Are you sure you want to delete?`)) return;
    const res = await fetch(`/admin/api/resources/${params.name}/${id}`, { method: 'DELETE' });
    if (res.ok) loadData(params.name!, currentPage);
  }
</script>

<svelte:head>
  <title>
    {resource?.label ? `${resource.label} | ${resource?.name}` : 'Loading...'}
  </title>
</svelte:head>

<div class="space-y-6">
  <div class="flex justify-between items-end">
    <div>
      <h2 class="text-2xl font-bold text-text-main capitalize tracking-tight">{resource?.label || params.name}</h2>
      <p class="text-xs text-zinc-500 mt-1 font-medium">Manage and monitor your {params.name} resource data</p>
    </div>
    <a href="/resources/{params.name}/create" use:link class="px-5 py-2.5 bg-brand-primary text-white text-xs font-bold rounded-xl shadow-lg shadow-brand-primary/20 hover:opacity-90 transition-all">+ New</a>
  </div>

  <div class="bg-bg-surface border border-border-subtle rounded-2xl shadow-sm overflow-hidden flex flex-col">
    {#if loading}
      <div class="p-24 text-center">
         <div class="inline-block w-8 h-8 border-4 border-brand-primary/20 border-t-brand-primary rounded-full animate-spin mb-4"></div>
         <div class="text-zinc-500 text-sm font-bold uppercase tracking-widest">Fetching Data...</div>
      </div>
    {:else if items.length === 0}
      <div class="p-24 text-center text-zinc-500 font-medium">No records found.</div>
    {:else}
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse">
          <thead>
            <tr class="bg-bg-main/50 border-b border-border-subtle">
              {#each columns as col}
                <th class="px-6 py-4 text-[10px] font-extrabold text-zinc-500 uppercase tracking-widest">{col.label}</th>
              {/each}
              <th class="px-6 py-4 text-right text-[10px] font-extrabold text-zinc-500 uppercase tracking-widest">Action</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle">
           {#each items as row}
             <tr class="hover:bg-bg-main/40 transition-colors group">
               {#each columns as col}
                 <td class="px-6 py-4 whitespace-nowrap">
                   <Cell value={row.values[col.name]} columnName={col.name} />
                 </td>
               {/each}
               <td class="px-6 py-4 text-right">
                  <div class="flex justify-end gap-2">
                     <a href="/resources/{params.name}/{row.id}" use:link class="px-3 py-1.5 bg-bg-main text-brand-primary text-[11px] font-bold rounded-lg border border-border-subtle">View</a>
                     {#if row.metadata?.canDelete}
                       <button on:click={() => handleDelete(row.id)} class="px-3 py-1.5 bg-bg-main text-danger text-[11px] font-bold rounded-lg border border-border-subtle">Delete</button>
                     {/if}
                  </div>
               </td>
             </tr>
           {/each}
          </tbody>
        </table>
      </div>

      <div class="px-6 py-4 bg-bg-main/30 border-t border-border-subtle flex items-center justify-between">
        <div class="text-xs text-zinc-500 font-medium">
          Showing <span class="text-text-main font-bold">{(currentPage - 1) * pagination.pageSize + 1}</span> 
          to <span class="text-text-main font-bold">{Math.min(currentPage * pagination.pageSize, pagination.total)}</span> 
          of <span class="text-text-main font-bold">{pagination.total}</span> entries
        </div>

        <div class="flex items-center gap-2">
  <button 
    on:click={() => handlePageChange(currentPage - 1)} 
    disabled={currentPage === 1}
    class="p-2 rounded-lg border border-border-subtle bg-bg-surface disabled:opacity-30 hover:bg-bg-main transition-colors">
    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" /></svg>
    <title>Previous Page</title>
  </button>
  
  <span class="text-xs font-bold px-4">Page {currentPage} of {pagination.totalPages}</span>

  <button 
    on:click={() => handlePageChange(currentPage + 1)} 
    disabled={currentPage === pagination.totalPages}
    class="p-2 rounded-lg border border-border-subtle bg-bg-surface disabled:opacity-30 hover:bg-bg-main transition-colors">
    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
    <title>Next Page</title>
  </button>
</div>
      </div>
    {/if}
  </div>
</div>