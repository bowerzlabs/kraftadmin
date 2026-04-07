<!-- <script lang="ts">
  import { onMount } from "svelte";
  import Router from "svelte-spa-router";
  import { routes } from "./routes";
  import Sidebar from "./lib/components/Sidebar.svelte";
  import Navbar from "./lib/components/Navbar.svelte";
  import Footer from "./lib/components/Footer.svelte";
  import { isDark } from "./lib/stores/theme";
  import "./app.css";
  // import { token } from './auth';
  import Login from "./lib/components/Login.svelte";
  import { token, isAuthenticated } from "./lib/stores/auth";
  import { kraftFetch } from "./api";
  import FeedbackWidget from "./lib/components/FeedbackWidget.svelte";

  // State for the global configuration
  let descriptor: any = null;
  let resources: any[] = [];
  let loading = true;
  let authenticated = !!$token;

  async function fetchMetadata() {
    loading = true; // Ensure spinner shows while re-validating
    const path = "/admin/api/resources/descriptors";
    try {
      const response = await fetch(path, {
        headers: { Authorization: `Bearer ${$token}` },
      });

      if (response.status === 401 || response.status === 403) {
        authenticated = false;
        token.set(""); // Clear invalid token from localStorage/store
        return;
      }

      if (response.ok) {
        const data = await response.json();
        descriptor = data;
        resources = data.resources;
        authenticated = true;
      }
    } catch (error) {
      console.error("Failed to fetch descriptors:", error);
      // If the server is down or unreachable, keep authenticated false
      authenticated = false;
    } finally {
      loading = false;
    }
  }

  async function checkAuthAndLoad() {
    loading = true;
    try {
      const res = await kraftFetch("/admin/api/resources/descriptors");
      if (res.ok) {
        descriptor = await res.json();
        $isAuthenticated = true; // Unified client already verified this
      }
    } finally {
      loading = false;
    }
  }

  onMount(checkAuthAndLoad);

  // Re-fetch if token changes (e.g., after login)
  $: if ($token && !authenticated) fetchMetadata();
</script>

<svelte:head>
  <title>{descriptor?.title || 'KraftAdmin'}</title>
</svelte:head>

<div class="flex h-screen bg-bg-main text-text-main font-sans overflow-hidden transition-colors duration-200">
  {#if loading}
    <div class="spinner">...</div>
  {:else if !$isAuthenticated}
    <Login on:success={checkAuthAndLoad} />
  {:else}
    <Sidebar resources={descriptor.resources} title={descriptor?.title} />
    <div class="flex flex-1 flex-col min-w-0">
      <Navbar environment={descriptor?.environment} />
      <main class="flex-1 overflow-y-auto p-8">
        <div class="px-4">
          <Router {routes} />
        </div>
      </main>
      <FeedbackWidget/>
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

  let descriptor: any = null;
  let loading = true;

  // Unified function to fetch descriptors and verify session
  async function checkAuthAndLoad() {
    loading = true;
    try {
      const res = await kraftFetch("/admin/api/resources/descriptors");
      if (res.ok) {
        descriptor = await res.json();
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

  onMount(checkAuthAndLoad);

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
</div>