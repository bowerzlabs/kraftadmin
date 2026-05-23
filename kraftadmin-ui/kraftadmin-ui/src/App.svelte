<!-- <script lang="ts">
  import { onMount } from "svelte";
  import Router from "svelte-spa-router";
  import { routes } from "./routes";
  import Sidebar from "./lib/components/Sidebar.svelte";
  import Navbar from "./lib/components/Navbar.svelte";
  import Footer from "./lib/components/Footer.svelte";
  import FeedbackWidget from "./lib/components/FeedbackWidget.svelte";
  import Login from "./lib/components/Login.svelte";
  
  import { isDark } from "./lib/stores/theme";
  import { token, isAuthenticated } from "./lib/stores/auth";
  import { kraftFetch } from "./api";
  import "./app.css";
  import { adminSettings } from "./lib/stores/settings";

  let descriptor: any = null;
  let loading = true;

  // Unified function to fetch descriptors and verify session
  async function checkAuthAndLoad() {
    loading = true;
    try {
      const res = await kraftFetch("/admin/api/resources/descriptors");
      if (res.ok) {
        descriptor = await res.json();
        adminSettings.set({version:})
        $isAuthenticated = true; 
      } else {
        // If 401/403, kraftFetch already set $isAuthenticated = false
        // We just ensure descriptor is cleared
        descriptor = null;
      }
    } catch (error) {
      console.error("Connectivity error:", error);
      $isAuthenticated = false;
    } finally {
      loading = false;
    }
  }

  onMount(
    checkAuthAndLoad
  );

  // REACTIVE SYNC: If token is set (login) but we aren't auth'd yet, load.
  // If $isAuthenticated becomes false (401 from api.ts), Svelte will 
  // automatically re-evaluate the {#if} block in the markup.
  $: if ($token && !$isAuthenticated && !loading) {
    checkAuthAndLoad();
  }
</script>

<svelte:head>
  <title>{descriptor?.title || 'KraftAdmin'}</title>
</svelte:head>

<div class="flex h-screen bg-bg-main text-text-main font-sans overflow-hidden transition-colors duration-200 {$isDark ? 'dark' : ''}">
  {#if loading}
    <div class="flex flex-1 items-center justify-center bg-bg-main">
       <div class="flex flex-col items-center gap-4">
         <div class="w-12 h-12 border-4 border-brand-primary/20 border-t-brand-primary rounded-full animate-spin"></div>
         <div class="animate-pulse text-brand-primary font-black tracking-widest text-[10px] uppercase">
            Syncing Kraft Environment...
         </div>
       </div>
    </div>
  {:else if !$isAuthenticated}
    <Login on:success={checkAuthAndLoad} />
  {:else}
    <Sidebar resources={descriptor?.resources || []} title={descriptor?.title} />
    <div class="flex flex-1 flex-col min-w-0 relative">
      <Navbar environment={descriptor?.environment} />
      <main class="flex-1 overflow-y-auto p-8">
        <div class="px-4">
          <Router {routes} />
        </div>
      </main>
      <FeedbackWidget />
      <Footer />
    </div>
  {/if}
</div> -->

<script lang="ts">
  import { onMount } from "svelte";
  import Router from "svelte-spa-router";
  import { routes } from "./routes";
  import Sidebar from "./lib/components/Sidebar.svelte";
  import Navbar from "./lib/components/Navbar.svelte";
  import Footer from "./lib/components/Footer.svelte";
  import FeedbackWidget from "./lib/components/FeedbackWidget.svelte";
  import Login from "./lib/components/Login.svelte";
  
  import { isDark } from "./lib/stores/theme";
  import { token, isAuthenticated } from "./lib/stores/auth";
  import { kraftFetch } from "./api";
  import "./app.css";
  import { adminSettings } from "./lib/stores/settings";

  let descriptor: any = null;
  let loading = true;

  // Unified function to fetch descriptors, settings, and verify session
  async function checkAuthAndLoad() {
    loading = true;
    try {
      // Parallel fetch for speed: load resources and global settings together
      const [descRes, settingsRes] = await Promise.all([
        kraftFetch("/admin/api/resources/descriptors"),
        kraftFetch("/admin/api/settings")
      ]);

      if (descRes.ok) {
        descriptor = await descRes.json();
        $isAuthenticated = true; 
      } else {
        descriptor = null;
      }

      if (settingsRes.ok) {
        const settingsData = await settingsRes.json();
        console.log(settingsData)
        adminSettings.set(settingsData);
        
        // Apply dynamic theme coloring if provided by the backend
        if (settingsData.theme?.primaryColor) {
           document.documentElement.style.setProperty('--brand-primary', settingsData.theme.primaryColor);
        }
      }
      
    } catch (error) {
      console.error("Connectivity error:", error);
      $isAuthenticated = false;
    } finally {
      loading = false;
    }
  }

  onMount(checkAuthAndLoad);

  // REACTIVE SYNC: If token is set (login) but we aren't auth'd yet, load.
  $: if ($token && !$isAuthenticated && !loading) {
    checkAuthAndLoad();
  }
</script>

<svelte:head>
  <title>{descriptor?.title || 'KraftAdmin'}</title>
</svelte:head>

<div class="flex h-screen bg-bg-main text-text-main font-sans overflow-hidden transition-colors duration-200 {$isDark ? 'dark' : ''}">
  {#if loading}
    <div class="flex flex-1 items-center justify-center bg-bg-main">
       <div class="flex flex-col items-center gap-4">
         <div class="w-12 h-12 border-4 border-brand-primary/20 border-t-brand-primary rounded-full animate-spin"></div>
         <div class="animate-pulse text-brand-primary font-black tracking-widest text-[10px] uppercase">
            Syncing Kraft Environment...
         </div>
       </div>
    </div>
  {:else if !$isAuthenticated}
    <Login on:success={checkAuthAndLoad} />
  {:else}
    <Sidebar resources={descriptor?.resources || []} title={descriptor?.title} />
    <div class="flex flex-1 flex-col min-w-0 relative">
      <Navbar environment={descriptor?.environment} />
      <main class="flex-1 overflow-y-auto p-8">
        <div class="px-4">
          <Router {routes} />
        </div>
      </main>
      <FeedbackWidget />
      <Footer />
    </div>
  {/if}
</div>