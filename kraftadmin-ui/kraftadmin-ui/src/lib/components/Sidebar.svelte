<script lang="ts">
  import { link, location } from 'svelte-spa-router'
  import {adminSettings} from '../stores/settings';
  
  export let title: string = "KraftAdmin";
  export let resources: any[] = [];

  $: appResources = resources.filter(r => !r.isSystem);
</script>

<aside class="w-60 h-screen bg-bg-surface text-text-main flex flex-col border-r border-border-subtle transition-colors duration-200 select-none">
  <div class="p-5 mb-2">
    <div class="flex items-center gap-3">
      <div class="w-7 h-7 bg-blue-600 rounded flex items-center justify-center text-white font-bold text-xs">
        K
      </div>
      <div>
        <h1 class="text-sm font-bold tracking-tight leading-none">{$adminSettings.title}</h1>
        <span class="text-[9px] text-zinc-500 font-medium uppercase tracking-tighter">Admin Console</span>
      </div>
    </div>
  </div>

  <nav class="flex-1 px-3 space-y-6 overflow-y-auto">

    <div>
      <a href="/" use:link
        class="flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors
        {$location === '/' ? 'bg-blue-50 text-blue-700' : 'text-zinc-500 hover:bg-zinc-100'}">
        <span class="w-1 h-1 rounded-full bg-current"></span>
        <span>Dashboard</span>
      </a>
    </div>

    <div class="space-y-1">
      <div class="px-3 text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2">Resources</div>
      {#if appResources.length === 0}
        <div class="px-3 py-2 text-xs text-zinc-400 italic">No resources</div>
      {/if}
      {#each appResources as resource}
        <a
          href="/resources/{resource.name}"
          use:link
          class="flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors
          {$location === `/resources/${resource.name}` ? 'bg-blue-50 text-blue-700' : 'text-zinc-500 hover:bg-zinc-100'}"
        >
          <span class="w-1 h-1 rounded-full bg-current opacity-40"></span>
          <span class="truncate">{resource.label}</span>
           {#if resource.totalCount > 0}
        <span class="bg-zinc-100 text-zinc-500 text-[10px] px-2 py-0.5 rounded-full font-bold group-hover:bg-brand-primary group-hover:text-white transition-colors">
          {resource.totalCount}
        </span>
      {/if}
        </a>
      {/each}

    </div>

    <div class="space-y-1">
      <div class="px-3 text-[10px] font-bold text-zinc-400 uppercase tracking-widest mb-2">System</div>

      <a href="/logs" use:link
        class="flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors
        {$location === '/logs' ? 'bg-blue-50 text-blue-700' : 'text-zinc-500 hover:bg-zinc-100'}">
        <span class="w-1 h-1 rounded-full bg-current"></span>
        <span>Logs</span>
      </a>

      <a href="/settings" use:link
        class="flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors
        {$location === '/settings' ? 'bg-blue-50 text-blue-700' : 'text-zinc-500 hover:bg-zinc-100'}">
        <span class="w-1 h-1 rounded-full bg-current"></span>
        <span>Settings</span>
      </a>
    </div>
  </nav>

  <div class="p-4 border-t border-zinc-100 bg-bg-surface text-text-main">
    <div class="flex items-center justify-between px-2">
      <span class="text-[10px] font-bold text-zinc-400 uppercase">v{$adminSettings.version}</span>
      <div class="flex items-center gap-1.5">
        <span class="w-1.5 h-1.5 rounded-full bg-green-500"></span>
        <span class="text-[10px] text-zinc-500 font-medium">Online</span>
      </div>
    </div>
  </div>
</aside>