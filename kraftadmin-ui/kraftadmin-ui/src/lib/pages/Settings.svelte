
<script lang="ts">
  import { onMount } from 'svelte';
  import { adminSettings } from '../stores/settings';
  import { fade, fly } from 'svelte/transition';

  // State management
  let activeTab = 'lib-config';
  let editableSettings: any = null;
  let loading = true;

  // Tabs Definition
  const tabs = [
    { id: 'lib-config', label: 'Library Config', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z' },
    { id: 'site', label: 'Site Settings', icon: 'M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9' },
    { id: 'integrations', label: 'Integrations', icon: 'M13 10V3L4 14h7v7l9-11h-7z' }
  ];

  onMount(async () => {
    loading = true;
    const res = await fetch('/admin/api/settings');
    if (res.ok) {
        const data = await res.json();
        adminSettings.set(data);
        editableSettings = JSON.parse(JSON.stringify(data));
    }
    loading = false;
  });

  // Keep editableSettings reactive to store fallback
  $: if (!editableSettings && $adminSettings) {
      editableSettings = JSON.parse(JSON.stringify($adminSettings));
  }

  async function save() {
    const res = await fetch('/admin/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editableSettings)
    });
    
    if (res.ok) {
      const updated = await res.json();
      adminSettings.set(updated);
      alert("Configuration Synced."); 
    }
  }
</script>

<div>
  <header class="flex justify-between items-center">
    <div>
      <h1 class="text-2xl font-black text-white uppercase tracking-tighter">System Settings</h1>
      <p class="text-zinc-500 text-sm">Orchestrate your KraftAdmin instance parameters.</p>
    </div>
    <button 
      on:click={save} 
      class="bg-brand-primary hover:bg-brand-primary/90 text-white px-8 py-2.5 rounded-xl font-bold text-sm transition-all shadow-lg shadow-brand-primary/20">
      Save Changes
    </button>
  </header>

  <nav class="flex items-center gap-2 p-1.5 bg-zinc-900 border border-zinc-800 rounded-2xl w-fit">
    {#each tabs as tab}
      <button 
        on:click={() => activeTab = tab.id}
        class="flex items-center gap-2.5 px-6 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all
        {activeTab === tab.id ? 'bg-zinc-800 text-brand-primary border border-zinc-700 shadow-xl' : 'text-zinc-500 hover:text-zinc-300'}">
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={tab.icon}/></svg>
        {tab.label}
      </button>
    {/each}
  </nav>

  <main class="bg-zinc-900/50 border border-zinc-800 rounded-3xl p-10 min-h-[500px]">
    
  
    {#if activeTab === 'lib-config'}
  <div in:fly={{ y: 10, duration: 300 }} class="space-y-12 pb-20">
    
    <section class="space-y-6">
      <div class="border-l-4 border-brand-primary pl-4">
        <h3 class="text-lg font-bold text-white uppercase tracking-tight">General Identity</h3>
        <p class="text-zinc-500 text-xs">Core identification and routing configuration.</p>
      </div>
      <div class="grid lg:grid-cols-2 gap-6 bg-zinc-950 p-6 rounded-2xl border border-zinc-800">
        <div class="space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Site Title</label>
          <input bind:value={editableSettings.title} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200 focus:border-brand-primary transition-colors" />
        </div>
        <div class="space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Base Path</label>
          <input bind:value={editableSettings.basePath} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
        </div>
        <div class="lg:col-span-2 space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Logo URL</label>
          <input bind:value={editableSettings.logoUrl} placeholder="https://..." class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
        </div>
      </div>
    </section>

    <section class="space-y-6">
      <div class="border-l-4 border-zinc-700 pl-4">
        <h3 class="text-lg font-bold text-white uppercase tracking-tight">Branding & Theme</h3>
      </div>
      <div class="bg-zinc-950 p-6 rounded-2xl border border-zinc-800 grid gap-6">
         <div class="flex items-center justify-between">
            <div class="space-y-1">
              <span class="text-sm font-bold text-zinc-300">Accent Color</span>
              <p class="text-[10px] text-zinc-500 uppercase font-mono">{editableSettings.theme.primaryColor}</p>
            </div>
            <input type="color" bind:value={editableSettings.theme.primaryColor} class="w-10 h-10 rounded-lg cursor-pointer bg-transparent border-none" />
         </div>
         <div class="flex items-center justify-between">
            <span class="text-sm font-bold text-zinc-300">System Dark Mode</span>
            <button 
              on:click={() => editableSettings.theme.darkMode = !editableSettings.theme.darkMode}
              class="w-12 h-6 rounded-full transition-all relative {editableSettings.theme.darkMode ? 'bg-brand-primary' : 'bg-zinc-800'}">
              <div class="absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-all {editableSettings.theme.darkMode ? 'translate-x-6' : ''}"></div>
            </button>
         </div>
      </div>
    </section>

    <section class="space-y-6">
      <div class="border-l-4 border-red-900/50 pl-4">
        <h3 class="text-lg font-bold text-white uppercase tracking-tight text-red-500/80">Security Core</h3>
      </div>
      <div class="bg-zinc-950 p-6 rounded-2xl border border-zinc-800 grid lg:grid-cols-2 gap-8">
        <div class="space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Cookie Name</label>
          <input bind:value={editableSettings.security.cookieName} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
        </div>
        <div class="space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Session Expiry (Min)</label>
          <input type="number" bind:value={editableSettings.security.sessionExpiryMinutes} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
        </div>
      </div>
    </section>

    <section class="space-y-6">
      <div class="border-l-4 border-zinc-700 pl-4">
        <h3 class="text-lg font-bold text-white uppercase tracking-tight">Artifact Storage</h3>
      </div>
      <div class="bg-zinc-950 p-6 rounded-2xl border border-zinc-800 space-y-6">
        <div class="space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Upload Directory</label>
          <input bind:value={editableSettings.storage.uploadDir} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200 font-mono text-xs" />
        </div>
        <div class="space-y-2">
          <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Public URL Prefix</label>
          <input bind:value={editableSettings.storage.publicUrlPrefix} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200 font-mono text-xs" />
        </div>
      </div>
    </section>

    <section class="grid lg:grid-cols-2 gap-12">
      <div class="space-y-6">
        <h4 class="text-xs font-black uppercase text-zinc-500 tracking-widest border-b border-zinc-800 pb-2">Pagination</h4>
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="text-[9px] font-bold text-zinc-600 uppercase">Default Size</label>
            <input type="number" bind:value={editableSettings.pagination.defaultPageSize} class="w-full bg-zinc-950 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
          </div>
          <div class="space-y-2">
            <label class="text-[9px] font-bold text-zinc-600 uppercase">Max Limit</label>
            <input type="number" bind:value={editableSettings.pagination.maxPageSize} class="w-full bg-zinc-950 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
          </div>
        </div>
      </div>

      <div class="space-y-6">
        <h4 class="text-xs font-black uppercase text-zinc-500 tracking-widest border-b border-zinc-800 pb-2">Global Features</h4>
        <div class="space-y-4">
          <label class="flex items-center justify-between cursor-pointer group">
            <span class="text-sm text-zinc-400 group-hover:text-zinc-200 transition-colors">Allow Record Deletion</span>
            <input type="checkbox" bind:checked={editableSettings.features.allowDelete} class="w-4 h-4 accent-brand-primary" />
          </label>
          <label class="flex items-center justify-between cursor-pointer group">
            <span class="text-sm text-zinc-400 group-hover:text-zinc-200 transition-colors">Show Audit Timestamps</span>
            <input type="checkbox" bind:checked={editableSettings.features.showTimestamps} class="w-4 h-4 accent-brand-primary" />
          </label>
          <label class="flex items-center justify-between cursor-pointer group">
            <span class="text-sm text-zinc-400 group-hover:text-zinc-200 transition-colors">Instance Read-Only Mode</span>
            <input type="checkbox" bind:checked={editableSettings.features.readOnly} class="w-4 h-4 accent-brand-primary" />
          </label>
        </div>
      </div>
    </section>

    <section class="space-y-6">
       <div class="border-l-4 border-zinc-700 pl-4">
        <h3 class="text-lg font-bold text-white uppercase tracking-tight">Localization</h3>
      </div>
      <div class="bg-zinc-950 p-6 rounded-2xl border border-zinc-800 grid lg:grid-cols-2 gap-6">
         <div class="space-y-2">
            <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Default Language</label>
            <select bind:value={editableSettings.localeConfig.defaultLanguage} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200">
               <option value="en">English (US)</option>
               <option value="sw">Swahili</option>
               <option value="fr">French</option>
            </select>
         </div>
         <div class="space-y-2">
            <label class="text-[10px] font-black uppercase text-zinc-500 tracking-widest">Timezone</label>
            <input bind:value={editableSettings.localeConfig.timezone} class="w-full bg-zinc-900 p-3 rounded-xl border border-zinc-800 outline-none text-zinc-200" />
         </div>
      </div>
    </section>

    <section class="space-y-6">
      <div class="border-l-4 border-brand-primary/40 pl-4">
        <h3 class="text-lg font-bold text-white uppercase tracking-tight">Telemetry Sink</h3>
      </div>
      <div class="bg-brand-primary/5 border border-brand-primary/10 p-8 rounded-3xl space-y-6">
        <div class="flex items-center justify-between">
          <div>
            <h4 class="text-sm font-bold text-brand-primary uppercase tracking-widest">Enabled Cloud Telemetry</h4>
            <p class="text-xs text-zinc-500">Relay system signals to your external orchestration layer.</p>
          </div>
          <input type="checkbox" bind:checked={editableSettings.telemetryConfig.enabled} class="w-6 h-6 accent-brand-primary" />
        </div>
        
        {#if editableSettings.telemetryConfig.enabled}
          <div class="space-y-2">
            <label class="text-[10px] font-black uppercase text-brand-primary/60 tracking-widest">Telemetry Sink URL</label>
            <input bind:value={editableSettings.telemetryConfig.cloudUrl} placeholder="http://localhost:8090" class="w-full bg-zinc-950 p-4 rounded-xl border border-brand-primary/20 focus:ring-1 ring-brand-primary outline-none text-zinc-200 font-mono" />
          </div>
        {/if}
      </div>
    </section>

  </div>

    {:else}
      <div in:fade={{ duration: 200 }} class="flex flex-col items-center justify-center h-full py-20 text-center">
        <div class="w-16 h-16 bg-zinc-800 rounded-full flex items-center justify-center mb-4 border border-zinc-700">
           <svg class="w-8 h-8 text-zinc-600" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"/></svg>
        </div>
        <h3 class="text-xl font-black text-white uppercase tracking-tighter mb-2">{tabs.find(t => t.id === activeTab)?.label}</h3>
        <p class="text-zinc-500 text-sm max-w-xs">This module is currently in the KraftAdmin v0.1.0 roadmap.</p>
      </div>
    {/if}

  </main>
</div>
