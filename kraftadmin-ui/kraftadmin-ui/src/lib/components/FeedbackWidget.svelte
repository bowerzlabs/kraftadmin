<script lang="ts">
  import { isDark } from '../stores/theme';
  export let discussionUrl = "https://github.com/bowerzlabs/kraftadmin/discussions/new?category=ideas";
</script>

<div class="fixed bottom-20 right-8 z-50 group">
  <div class="absolute bottom-full right-0 mb-4 opacity-0 group-hover:opacity-100 transition-opacity duration-200 pointer-events-none">
    <div class="bg-zinc-900 dark:bg-zinc-100 text-zinc-100 dark:text-zinc-900 text-[10px] font-bold px-3 py-1.5 rounded-lg shadow-xl uppercase tracking-widest whitespace-nowrap">
      Share an Idea
    </div>
  </div>

  <a 
    href={discussionUrl} 
    target="_blank"
    rel="noopener noreferrer"
    class="flex items-center justify-center w-12 h-12 bg-brand-primary text-white rounded-2xl shadow-lg shadow-brand-primary/20 hover:scale-110 active:scale-95 transition-all duration-200"
  >
    <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z"/>
    </svg>
  </a>
</div>