<script lang="ts">
  import { onMount } from 'svelte';
  import { adminSettings } from '../stores/settings';
  import { fade, fly } from 'svelte/transition';
  import { kraftFetch } from '../../api';
  import { snackbar } from '../stores/snackbar';
  import { Settings, Globe, Zap } from 'lucide-svelte';

  let activeTab = 'lib-config';
  let editableSettings: any = null;
  let saving = false;

  const tabs = [
    { id: 'lib-config', label: 'Library Config', icon: Settings },
    { id: 'site', label: 'Site Settings', icon: Globe },
    { id: 'integrations', label: 'Integrations', icon: Zap }
  ];

  /**
   * GET /admin/api/settings currently omits `security` and `basePath`
   * entirely (they're set once at Spring context startup and aren't part
   * of the runtime-editable Settings DTO). Backfilling defaults here keeps
   * the page from throwing on `editableSettings.security.cookieName` when
   * `security` is undefined, instead of crashing the whole settings view.
   */
  function withDefaults(raw: any) {
    return {
      basePath: raw.basePath ?? '/admin',
      title: raw.title ?? '',
      logoUrl: raw.logoUrl ?? '',
      theme: {
        primaryColor: raw.theme?.primaryColor ?? '#3b82f6',
        darkMode: raw.theme?.darkMode ?? true,
      },
      storage: {
        uploadDir: raw.storage?.uploadDir ?? 'uploads/admin',
        publicUrlPrefix: raw.storage?.publicUrlPrefix ?? '/admin/files',
      },
      pagination: {
        defaultPageSize: raw.pagination?.defaultPageSize ?? 20,
        maxPageSize: raw.pagination?.maxPageSize ?? 100,
      },
      features: {
        allowDelete: raw.features?.allowDelete ?? true,
        showTimestamps: raw.features?.showTimestamps ?? true,
        readOnly: raw.features?.readOnly ?? false,
      },
      localeConfig: {
        defaultLanguage: raw.localeConfig?.defaultLanguage ?? 'en',
        timezone: raw.localeConfig?.timezone ?? 'UTC',
      },
      telemetryConfig: {
        cloudUrl: raw.telemetryConfig?.cloudUrl ?? '',
        enabled: raw.telemetryConfig?.enabled ?? false,
      },
      // Not returned by the backend at all currently — kept null so the
      // Security Core section can render a read-only notice instead of
      // throwing. Not included in save()'s payload either way.
      security: raw.security ?? null,
    };
  }

  function syncFromStore() {
    if ($adminSettings) {
      editableSettings = withDefaults(JSON.parse(JSON.stringify($adminSettings)));
    }
  }

  onMount(syncFromStore);

  $: if ($adminSettings && !editableSettings) {
    syncFromStore();
  }

  async function save() {
    saving = true;

    // Explicitly map the UI object to the backend's expected UpdateRequest DTO
    const payload = {
      title: editableSettings.title,
      logoUrl: editableSettings.logoUrl,
      theme: {
        primaryColor: editableSettings.theme.primaryColor,
        darkMode: editableSettings.theme.darkMode
      },
      storage: {
        uploadDir: editableSettings.storage.uploadDir,
        publicUrlPrefix: editableSettings.storage.publicUrlPrefix
      },
      pagination: {
        defaultPageSize: editableSettings.pagination.defaultPageSize,
        maxPageSize: editableSettings.pagination.maxPageSize
      },
      features: {
        allowDelete: editableSettings.features.allowDelete,
        showTimestamps: editableSettings.features.showTimestamps,
        readOnly: editableSettings.features.readOnly
      },
      localeConfig: {
        defaultLanguage: editableSettings.localeConfig.defaultLanguage,
        timezone: editableSettings.localeConfig.timezone
      },
      telemetryConfig: {
        cloudUrl: editableSettings.telemetryConfig.cloudUrl,
        enabled: editableSettings.telemetryConfig.enabled
      }
      // Note: 'security' and 'basePath' are intentionally omitted here —
      // not in the backend's Update DTO, and not safely hot-reloadable
      // (cookie name / session expiry are fixed at Spring context startup).
    };

    try {
      const res = await kraftFetch('/admin/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        const updated = await res.json();
        adminSettings.set(updated);
        editableSettings = withDefaults(JSON.parse(JSON.stringify(updated)));
        snackbar.success('Settings saved.');
      } else {
        const errorData = await res.json().catch(() => null);
        snackbar.error(errorData?.message ?? 'Failed to save settings.');
      }
    } catch (e) {
      console.error('Save failed', e);
      snackbar.error('Unexpected error saving settings.');
    } finally {
      saving = false;
    }
  }
</script>

<div class="p-4 md:p-8 space-y-8 bg-bg-main min-h-screen transition-colors duration-300">
  <header class="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
    <div>
      <h1 class="text-2xl font-black text-text-main uppercase tracking-tighter">System Settings</h1>
      <p class="text-text-muted text-sm">Modify your KraftAdmin instance parameters.</p>
    </div>
    <button
            on:click={save}
            disabled={saving || !editableSettings}
            class="w-full sm:w-auto bg-brand-primary hover:opacity-90 disabled:opacity-50 text-white px-8 py-2.5 rounded-xl font-bold text-sm transition-all shadow-lg shadow-brand-primary/20">
      {saving ? 'Saving...' : 'Save Changes'}
    </button>
  </header>

  <nav class="flex items-center gap-2 p-1.5 bg-bg-surface border border-border-subtle rounded-2xl w-full sm:w-fit overflow-x-auto whitespace-nowrap">
    {#each tabs as tab}
      <button
              on:click={() => activeTab = tab.id}
              class="flex-shrink-0 flex items-center gap-2.5 px-6 py-2.5 rounded-xl text-xs font-black uppercase tracking-widest transition-all
        {activeTab === tab.id ? 'bg-bg-main text-brand-primary border border-border-subtle shadow-sm' : 'text-text-muted hover:text-text-main'}">
        <svelte:component this={tab.icon} class="w-4 h-4" strokeWidth={2} />
        {tab.label}
      </button>
    {/each}
  </nav>

  <main class="bg-bg-surface border border-border-subtle rounded-3xl p-6 md:p-10 min-h-[500px]">
    {#if editableSettings}
      {#if activeTab === 'lib-config'}
        <div in:fly={{ y: 10, duration: 300 }} class="space-y-12 pb-20">
          <section class="space-y-6">
            <div class="border-l-4 border-brand-primary pl-4">
              <h3 class="text-lg font-bold text-text-main uppercase tracking-tight">General Identity</h3>
              <p class="text-text-muted text-xs">Core identification and routing configuration.</p>
            </div>
            <div class="grid lg:grid-cols-2 gap-6 bg-bg-main p-6 rounded-2xl border border-border-subtle">
              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Site Title</label>
                <input bind:value={editableSettings.title} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main focus:border-brand-primary transition-colors" />
              </div>
              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Base Path</label>
                <input value={editableSettings.basePath} disabled class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-muted cursor-not-allowed" />
                <p class="text-[10px] text-text-muted">Fixed at startup via kraftadmin.base-path — not editable here.</p>
              </div>
              <div class="lg:col-span-2 space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Logo URL</label>
                <input bind:value={editableSettings.logoUrl} placeholder="https://..." class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main" />
              </div>
            </div>
          </section>

          <section class="space-y-6">
            <div class="border-l-4 border-border-subtle pl-4">
              <h3 class="text-lg font-bold text-text-main uppercase tracking-tight">Branding & Theme</h3>
            </div>
            <div class="bg-bg-main p-6 rounded-2xl border border-border-subtle grid gap-6">
              <div class="flex items-center justify-between">
                <div>
                  <span class="text-sm font-bold text-text-main">Accent Color</span>
                  <p class="text-[10px] text-text-muted uppercase font-mono">{editableSettings.theme.primaryColor}</p>
                </div>
                <input type="color" bind:value={editableSettings.theme.primaryColor} class="w-10 h-10 rounded-lg cursor-pointer bg-transparent border-none" />
              </div>
            </div>
          </section>

          {#if editableSettings.security}
            <section class="space-y-6">
              <div class="border-l-4 border-danger/50 pl-4">
                <h3 class="text-lg font-bold text-text-main uppercase tracking-tight text-danger">Security Core</h3>
              </div>
              <div class="bg-bg-main p-6 rounded-2xl border border-border-subtle grid lg:grid-cols-2 gap-8">
                <div class="space-y-2">
                  <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Cookie Name</label>
                  <input bind:value={editableSettings.security.cookieName} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main" />
                </div>
                <div class="space-y-2">
                  <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Session Expiry (Min)</label>
                  <input type="number" bind:value={editableSettings.security.sessionExpiryMinutes} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main" />
                </div>
              </div>
            </section>
          {:else}
            <section class="space-y-2">
              <div class="border-l-4 border-border-subtle pl-4">
                <h3 class="text-lg font-bold text-text-main uppercase tracking-tight">Security Core</h3>
                <p class="text-text-muted text-xs">Not editable from this screen — configure via kraftadmin.security.* in application.properties.</p>
              </div>
            </section>
          {/if}

          <section class="space-y-6">
            <div class="border-l-4 border-border-subtle pl-4">
              <h3 class="text-lg font-bold text-text-main uppercase tracking-tight">Artifact Storage</h3>
            </div>
            <div class="bg-bg-main p-6 rounded-2xl border border-border-subtle space-y-6">
              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Upload Directory</label>
                <input bind:value={editableSettings.storage.uploadDir} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main font-mono text-xs" />
              </div>
              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Public URL Prefix</label>
                <input bind:value={editableSettings.storage.publicUrlPrefix} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main font-mono text-xs" />
              </div>
            </div>
          </section>

          <section class="grid lg:grid-cols-2 gap-12">
            <div class="space-y-6">
              <h4 class="text-xs font-black uppercase text-text-muted tracking-widest border-b border-border-subtle pb-2">Pagination</h4>
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-2">
                  <label class="text-[9px] font-bold text-text-muted uppercase">Default Size</label>
                  <input type="number" bind:value={editableSettings.pagination.defaultPageSize} class="w-full bg-bg-main p-3 rounded-xl border border-border-subtle outline-none text-text-main" />
                </div>
                <div class="space-y-2">
                  <label class="text-[9px] font-bold text-text-muted uppercase">Max Limit</label>
                  <input type="number" bind:value={editableSettings.pagination.maxPageSize} class="w-full bg-bg-main p-3 rounded-xl border border-border-subtle outline-none text-text-main" />
                </div>
              </div>
            </div>
            <div class="space-y-6">
              <h4 class="text-xs font-black uppercase text-text-muted tracking-widest border-b border-border-subtle pb-2">Global Features</h4>
              <div class="space-y-4">
                <label class="flex items-center justify-between cursor-pointer group">
                  <span class="text-sm text-text-muted group-hover:text-text-main transition-colors">Allow Record Deletion</span>
                  <input type="checkbox" bind:checked={editableSettings.features.allowDelete} class="w-4 h-4 accent-brand-primary" />
                </label>
                <label class="flex items-center justify-between cursor-pointer group">
                  <span class="text-sm text-text-muted group-hover:text-text-main transition-colors">Show Audit Timestamps</span>
                  <input type="checkbox" bind:checked={editableSettings.features.showTimestamps} class="w-4 h-4 accent-brand-primary" />
                </label>
                <label class="flex items-center justify-between cursor-pointer group">
                  <span class="text-sm text-text-muted group-hover:text-text-main transition-colors">Instance Read-Only Mode</span>
                  <input type="checkbox" bind:checked={editableSettings.features.readOnly} class="w-4 h-4 accent-brand-primary" />
                </label>
              </div>
            </div>
          </section>

          <section class="space-y-6">
            <div class="border-l-4 border-border-subtle pl-4">
              <h3 class="text-lg font-bold text-text-main uppercase tracking-tight">Localization</h3>
            </div>
            <div class="bg-bg-main p-6 rounded-2xl border border-border-subtle grid lg:grid-cols-2 gap-6">
              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Default Language</label>
                <select bind:value={editableSettings.localeConfig.defaultLanguage} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main">
                  <option value="en">English (US)</option>
                  <option value="sw">Swahili</option>
                  <option value="fr">French</option>
                </select>
              </div>
              <div class="space-y-2">
                <label class="text-[10px] font-black uppercase text-text-muted tracking-widest">Timezone</label>
                <input bind:value={editableSettings.localeConfig.timezone} class="w-full bg-bg-surface p-3 rounded-xl border border-border-subtle outline-none text-text-main" />
              </div>
            </div>
          </section>

          <section class="space-y-6">
            <div class="border-l-4 border-brand-primary/40 pl-4">
              <h3 class="text-lg font-bold text-text-main uppercase tracking-tight">Telemetry Sink</h3>
            </div>
            <div class="bg-brand-primary/5 border border-brand-primary/20 p-8 rounded-3xl space-y-6">
              <div class="flex items-center justify-between">
                <div>
                  <h4 class="text-sm font-bold text-brand-primary uppercase tracking-widest">Enabled Cloud Telemetry</h4>
                  <p class="text-xs text-text-muted">Relay system signals to your external orchestration layer.</p>
                </div>
                <input type="checkbox" bind:checked={editableSettings.telemetryConfig.enabled} class="w-6 h-6 accent-brand-primary cursor-pointer" />
              </div>
            </div>
          </section>
        </div>
      {:else}
        <div in:fade={{ duration: 200 }} class="flex flex-col items-center justify-center h-full py-20 text-center">
          <div class="w-16 h-16 bg-bg-main rounded-full flex items-center justify-center mb-4 border border-border-subtle">
            <svg class="w-8 h-8 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"/></svg>
          </div>
          <h3 class="text-xl font-black text-text-main uppercase tracking-tighter mb-2">{tabs.find(t => t.id === activeTab)?.label}</h3>
          <p class="text-text-muted text-sm max-w-xs">This module is currently development.</p>
        </div>
      {/if}
    {:else}
      <div class="flex items-center justify-center h-[500px] text-text-muted">Loading settings...</div>
    {/if}
  </main>
</div>