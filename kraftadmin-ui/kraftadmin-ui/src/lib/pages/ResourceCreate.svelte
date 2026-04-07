<!-- <script lang="ts">
  import { push } from 'svelte-spa-router';
  import DynamicForm from '../components/DynamicForm.svelte';
  import { notify } from '../toast';

  export let params: any = {}; 
  
  let descriptor: any = null;
  let initialData: any = {}; 
  let loading = true; 
  let isInitialized = false;
    let serverErrors: Record<string, string[]> = {};


  $: if (params.name) fetchData(params.name, params.id);

  async function fetchData(name: string, id?: string) {
    if (!isInitialized) loading = true;
    
    try {
      const resMeta = await fetch(`/admin/api/resources/descriptors`);
      const meta = await resMeta.json();
      descriptor = meta.resources.find((r: any) => r.name === name);

      if (id) {
        const resData = await fetch(`/admin/api/resources/${name}/${id}`);
        if (resData.ok) {
          const newData = await resData.json();
          console.log("Fetched data:", newData);
          // Only update if data changed to avoid breaking child internal state
          if (JSON.stringify(newData) !== JSON.stringify(initialData)) {
            initialData = newData;
          }
        }
      } else if (!isInitialized) {
        initialData = {}; 
      }
      isInitialized = true;
    } catch (e) {
      console.error("Fetch error:", e);
      notify("Error loading resource configuration");
    } finally {
      loading = false;
    }
  }

  async function handleSubmit(formData: any) {
    const isEdit = !!params.id;
    const eventType = isEdit ? 'UPDATE' : 'CREATE';
    serverErrors = {}; // Reset errors on new attempt

    try {
      const res = await fetch(`/admin/api/resources/${params.name}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ eventType, data: formData })
      });

      const result = await res.json();

      if (res.ok) {
        notify(`${params.name} ${isEdit ? 'updated' : 'created'} successfully!`);
        push(`/resources/${params.name}`);
      } else if (res.status === 422) {
        // Capture the map of field -> [error messages]
        serverErrors = result; 
        notify("Please correct the highlighted errors");
      } else {
        throw new Error(result.message || "Server error");
      }
    } catch (e: any) {
      notify(e.message || `Failed to ${isEdit ? 'update' : 'create'} record`);
    }
  }

</script> -->

<script lang="ts">
  import { push } from 'svelte-spa-router';
  import DynamicForm from '../components/DynamicForm.svelte';
  import { notify } from '../toast';
  import { onMount } from 'svelte';

  export let params: any = {};

  let descriptor: any = null;
  let initialData: any = {};
  let loading = true;
  let isInitialized = false;
  let serverErrors: Record<string, string[]> = {};

  // ✅ Track what we last fetched to avoid re-fetching on unrelated reactive updates
  let lastFetchedKey = '';

  // ✅ Replace reactive statement with a stable key-based guard
  $: {
    const key = `${params.name}__${params.id ?? 'new'}`;
    if (params.name && key !== lastFetchedKey) {
      lastFetchedKey = key;
      fetchData(params.name, params.id);
    }
  }

  async function fetchData(name: string, id?: string) {
    if (!isInitialized) loading = true;
    try {
      const resMeta = await fetch(`/admin/api/resources/descriptors`);
      const meta = await resMeta.json();
      descriptor = meta.resources.find((r: any) => r.name === name);

      if (id) {
        const resData = await fetch(`/admin/api/resources/${name}/${id}`);
        if (resData.ok) {
          const newData = await resData.json();
          console.log("Fetched data:", newData);
          // ✅ Only assign if content actually changed
          if (JSON.stringify(newData) !== JSON.stringify(initialData)) {
            initialData = newData;
          }
        }
      } else if (!isInitialized) {
        initialData = {};
      }

      isInitialized = true;
    } catch (e) {
      console.error("Fetch error:", e);
      notify("Error loading resource configuration");
    } finally {
      loading = false;
    }
  }

  async function handleSubmit(formData: any) {
    const isEdit = !!params.id;
    const eventType = isEdit ? 'UPDATE' : 'CREATE';
    serverErrors = {};

    try {
      const res = await fetch(`/admin/api/resources/${params.name}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ eventType, data: formData })
      });
      const result = await res.json();

      if (res.ok) {
        notify(`${params.name} ${isEdit ? 'updated' : 'created'} successfully!`);
        push(`/resources/${params.name}`);
      } else if (res.status === 422) {
        serverErrors = result;
        notify("Please correct the highlighted errors");
      } else {
        throw new Error(result.message || "Server error");
      }
    } catch (e: any) {
      notify(e.message || `Failed to ${isEdit ? 'update' : 'create'} record`);
    }
  }
</script>

<div class="space-y-6">
  <div class="flex items-center gap-4">
    <button on:click={() => window.history.back()} class="p-2 -ml-2 rounded-full hover:bg-bg-surface text-zinc-400 hover:text-brand-primary transition-all duration-200">
      <svg xmlns="http://www.w3.org/2000/svg" class="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
      </svg>
      <title>Back</title>
    </button>
    <div>
      <h2 class="text-2xl font-bold text-text-main capitalize">{params.id ? 'Edit' : 'New'} {params.name}</h2>
      <p class="text-xs text-zinc-500 font-medium">{params.id ? `Modifying existing ${params.name}` : `Create a new ${params.name}`}</p>
    </div>
  </div>

  {#if loading && !isInitialized}
    <div class="space-y-6 animate-pulse">
      <div class="h-64 bg-bg-surface border border-border-subtle rounded-xl shadow-sm"></div>
    </div>
  {:else if descriptor}
    <DynamicForm
      columns={descriptor.columns}
      initialData={initialData}
      onSubmit={handleSubmit}
      resourceName={params.name}
      externalErrors={serverErrors}
    />
  {:else}
    <div class="p-12 text-center bg-bg-surface border border-dashed border-border-subtle rounded-xl">
      <p class="text-zinc-500">Resource not found.</p>
    </div>
  {/if}
</div>