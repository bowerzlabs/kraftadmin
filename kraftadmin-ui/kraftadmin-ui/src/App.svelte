<script lang="ts">
  import { onMount } from "svelte";
  import { location, replace } from "svelte-spa-router";
  import Login from "./lib/components/Login.svelte";
  import AdminLayout from "./lib/components/AdminLayout.svelte";
  import { authMode, isBridgeMode } from "./lib/stores/authMode";
  import { isAuthenticated } from "./lib/stores/auth";
  import { adminSettings } from "./lib/stores/settings";
  import { updateResources } from "./lib/stores/resources";
  import { kraftFetch } from "./api";

  let bootstrapped = false;
  let descriptor: any = null;
  let descriptorsLoaded = false;

  $: isLogin = $location === "/auth/login";

  async function loadDescriptorsAndSettings() {
    try {
      const descRes = await kraftFetch("/admin/api/resources/descriptors");

      if (descRes.ok) {
        const data = await descRes.json();
        descriptor = data;
        descriptorsLoaded = true;
        authMode.set(data.environment?.authMode ?? "unknown");
        isBridgeMode.set(data.environment?.authMode === "bridge");
        isAuthenticated.set(true);
        if (data.resources) {
          updateResources(data.resources);
        }
      } else {
        descriptorsLoaded = false;
        isAuthenticated.set(false);
        // Not authenticated — send them to the login page.
        if (!isLogin) {
          replace("/auth/login");
        }
      }

      // Only fetch settings if we actually have a session
      if (descriptorsLoaded) {
        const settingsRes = await kraftFetch("/admin/api/settings");
        if (settingsRes.ok) {
          adminSettings.set(await settingsRes.json());
        }
      }
    } catch {
      descriptorsLoaded = false;
      isAuthenticated.set(false);
      if (!isLogin) {
        replace("/auth/login");
      }
    } finally {
      bootstrapped = true;
    }
  }

  onMount(() => {
    loadDescriptorsAndSettings();
  });

  $: if (bootstrapped && $isAuthenticated && !descriptorsLoaded) {
    loadDescriptorsAndSettings();
  }

  $: faviconHref = $adminSettings?.logoUrl || '/vite.svg';
</script>

<svelte:head>
  <link rel="icon" type="image/svg+xml" href={faviconHref} />
</svelte:head>

{#if !bootstrapped}
  <div class="flex h-screen items-center justify-center bg-bg-main">
    <div class="w-12 h-12 border-4 border-brand-primary/20 border-t-brand-primary rounded-full animate-spin"></div>
  </div>
{:else if isLogin || !$isAuthenticated}
  <Login />
{:else}
  <AdminLayout {descriptor} />
{/if}