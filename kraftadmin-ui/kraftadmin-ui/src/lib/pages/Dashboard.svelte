<script lang="ts">
  import { onMount } from 'svelte';
  import {
    Loader,
    Database,
    Server,
    Activity,
    Clock,
    CheckCircle,
    XCircle
  } from 'lucide-svelte';

  import MetricChart from '../components/MetricChart.svelte';

  interface DashboardStat {
    icon: string;
    label: string;
    value: string;
    trend: string | null;
  }

  interface DataSourceInfo {
    activeConnections: number | null;
    connectionString: string;
    driverOrClientName: string | null;
    extra: Record<string, string>;
    idleConnections: number | null;
    kind: string;
    maxPoolSize: number | null;
    name: string;
    poolType: string | null;
    productName: string;
    productVersion: string | null;
    reachable: boolean;
  }

  interface SystemStatus {
    appVersion: string;
    dataSources: DataSourceInfo[];
    environment: string;
    javaVersion: string;
    production: boolean;
    totalEntitiesTracked: number;
    uptimeSeconds: number;
  }

  interface Feature {
    description: string;
    name: string;
    status: string;
    unlockCriteria: string | null;
  }

  interface DashboardData {
    title: string;
    welcomeMessage: string;
    stats: DashboardStat[];
    features: Feature[];
    metrics: any[];
    systemStatus: SystemStatus;
  }

  let dashboardData: DashboardData | null = null;
  let loading = true;

  onMount(async () => {
    try {
      const res = await fetch('/admin/api/dashboard', {
        headers: {
          Accept: 'application/json'
        },
        credentials: 'same-origin'
      });

      if (res.ok) {
        dashboardData = await res.json();
      }
    } finally {
      loading = false;
    }
  });

  function formatUptime(seconds: number): string {
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    if (minutes > 0) return `${minutes}m ${secs}s`;

    return `${secs}s`;
  }

  function formatConnectionString(connectionString: string): string {
    if (
            connectionString === 'unknown' ||
            connectionString === 'unreachable'
    ) {
      return connectionString;
    }

    try {
      const url = new URL(connectionString);

      return `${url.protocol}//${url.hostname}${
              url.port ? `:${url.port}` : ''
      }${url.pathname}`;
    } catch {
      return connectionString;
    }
  }
</script>

<div
        class="min-h-screen space-y-8 bg-bg-main p-4 transition-colors duration-300 sm:p-6 lg:p-8"
>
  {#if loading}

   
    <div class="flex flex-col items-center justify-center gap-4 py-20">
      <Loader class="h-8 w-8 animate-spin text-brand-primary" />

      <p
              class="text-[10px] font-black uppercase tracking-[0.2em] text-text-muted"
      >
        Loading dashboard...
      </p>
    </div>
   

  {:else if dashboardData}

   
    <!-- HEADER -->

    <div
            class="flex flex-col gap-5 md:flex-row md:items-end md:justify-between"
    >
      <div class="min-w-0">
        <h1
                class="break-words text-2xl font-black uppercase tracking-tighter text-text-main sm:text-3xl"
        >
          {dashboardData.title}
        </h1>

        <p class="mt-1 text-sm text-text-muted">
          {dashboardData.welcomeMessage}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-3">
    <span
            class={`inline-flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-bold ${
        dashboardData.systemStatus.production
          ? 'bg-warning/10 text-warning'
          : 'bg-success/10 text-success'
      }`}
    >
      <span
              class={`h-2 w-2 rounded-full ${
          dashboardData.systemStatus.production
            ? 'bg-warning'
            : 'bg-success'
        }`}
      ></span>

      {dashboardData.systemStatus.production
              ? 'Production'
              : 'Development'}
    </span>

        <span class="font-mono text-xs text-text-muted">
      v{dashboardData.systemStatus.appVersion}
    </span>
      </div>
    </div>


    <!-- PRIMARY STATS -->

    <section class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      {#each dashboardData.stats as stat}
        <div
                class="min-w-0 rounded-3xl border border-border-subtle bg-bg-surface p-5 shadow-sm transition hover:shadow-md sm:p-6"
        >
          <div class="flex items-start justify-between gap-4">
        <span
                class="min-w-0 break-words text-[10px] font-black uppercase tracking-widest text-text-muted"
        >
          {stat.label}
        </span>

            <span
                    class="shrink-0 text-xs uppercase text-text-muted"
            >
          {stat.icon}
        </span>
          </div>

          <div class="mt-3">
        <span
                class="block truncate text-3xl font-black text-text-main sm:text-4xl"
        >
          {stat.value}
        </span>
          </div>
        </div>
      {/each}
    </section>


    <!-- METRICS -->

    {#if dashboardData.metrics?.length > 0}
      <section class="space-y-4">
        <div>
          <h2 class="text-xl font-bold text-text-main">
            Metrics
          </h2>

          <p class="text-sm text-text-muted">
            Track your application's key metrics over time.
          </p>
        </div>

        <div class="grid grid-cols-1 gap-6 2xl:grid-cols-2">
          {#each dashboardData.metrics as metric}
            <div class="min-w-0">
              <MetricChart {metric} />
            </div>
          {/each}
        </div>
      </section>
    {/if}


    <!-- SYSTEM OVERVIEW -->

    <section class="space-y-4">
      <div>
        <h2 class="text-xl font-bold text-text-main">
          System Overview
        </h2>

        <p class="text-sm text-text-muted">
          Runtime and infrastructure information for this application.
        </p>
      </div>

      <div
              class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4"
      >

        <!-- Environment -->

        <div
                class="min-w-0 rounded-3xl border border-border-subtle bg-bg-surface p-5 sm:p-6"
        >
          <div class="mb-4 flex items-center gap-3">
            <div
                    class="shrink-0 rounded-2xl bg-brand-primary/10 p-3 text-brand-primary"
            >
              <Server class="h-5 w-5" />
            </div>

            <span
                    class="text-xs font-black uppercase tracking-widest text-text-muted"
            >
          Environment
        </span>
          </div>

          <p class="truncate text-2xl font-black text-text-main">
            {dashboardData.systemStatus.environment}
          </p>
        </div>


        <!-- Java -->

        <div
                class="min-w-0 rounded-3xl border border-border-subtle bg-bg-surface p-5 sm:p-6"
        >
          <div class="mb-4 flex items-center gap-3">
            <div
                    class="shrink-0 rounded-2xl bg-orange-500/10 p-3 text-orange-500"
            >
              <Activity class="h-5 w-5" />
            </div>

            <span
                    class="text-xs font-black uppercase tracking-widest text-text-muted"
            >
          Java Runtime
        </span>
          </div>

          <p class="truncate text-2xl font-black text-text-main">
            {dashboardData.systemStatus.javaVersion}
          </p>
        </div>


        <!-- Uptime -->

        <div
                class="min-w-0 rounded-3xl border border-border-subtle bg-bg-surface p-5 sm:p-6"
        >
          <div class="mb-4 flex items-center gap-3">
            <div
                    class="shrink-0 rounded-2xl bg-success/10 p-3 text-success"
            >
              <Clock class="h-5 w-5" />
            </div>

            <span
                    class="text-xs font-black uppercase tracking-widest text-text-muted"
            >
          Uptime
        </span>
          </div>

          <p class="truncate text-2xl font-black text-text-main">
            {formatUptime(
                    dashboardData.systemStatus.uptimeSeconds
            )}
          </p>
        </div>


        <!-- Entities -->

        <div
                class="min-w-0 rounded-3xl border border-border-subtle bg-bg-surface p-5 sm:p-6"
        >
          <div class="mb-4 flex items-center gap-3">
            <div
                    class="shrink-0 rounded-2xl bg-purple-500/10 p-3 text-purple-500"
            >
              <Database class="h-5 w-5" />
            </div>

            <span
                    class="text-xs font-black uppercase tracking-widest text-text-muted"
            >
          Tracked Entities
        </span>
          </div>

          <p class="truncate text-2xl font-black text-text-main">
            {dashboardData.systemStatus.totalEntitiesTracked}
          </p>
        </div>

      </div>
    </section>


    <!-- DATA SOURCES -->

    <section class="space-y-4">
      <div
              class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between"
      >
        <div>
          <h2 class="text-xl font-bold text-text-main">
            Data Sources
          </h2>

          <p class="text-sm text-text-muted">
            Databases and storage systems detected in the application.
          </p>
        </div>

        <span
                class="text-xs font-bold uppercase text-text-muted"
        >
      {dashboardData.systemStatus.dataSources.length}
          sources
    </span>
      </div>

      <div
              class="grid grid-cols-1 gap-4 xl:grid-cols-2"
      >
        {#each dashboardData.systemStatus.dataSources as source}

          <div
                  class="min-w-0 overflow-hidden rounded-3xl border border-border-subtle bg-bg-surface p-5 sm:p-6"
          >

            <!-- SOURCE HEADER -->

            <div
                    class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between"
            >
              <div class="flex min-w-0 items-center gap-4">
                <div
                        class="shrink-0 rounded-2xl bg-brand-primary/10 p-3 text-brand-primary"
                >
                  <Database class="h-5 w-5" />
                </div>

                <div class="min-w-0">
                  <h3
                          class="truncate font-bold text-text-main"
                  >
                    {source.name}
                  </h3>

                  <p
                          class="truncate text-xs text-text-muted"
                  >
                    {source.productName}
                  </p>
                </div>
              </div>

              {#if source.reachable}

            <span
                    class="inline-flex shrink-0 items-center gap-2 text-xs font-bold text-success"
            >
              <CheckCircle class="h-4 w-4" />
              Reachable
            </span>

              {:else}

            <span
                    class="inline-flex shrink-0 items-center gap-2 text-xs font-bold text-danger"
            >
              <XCircle class="h-4 w-4" />
              Unreachable
            </span>

              {/if}
            </div>


            <!-- SOURCE DETAILS -->

            <div class="mt-6 space-y-5">

              <div class="min-w-0">
            <span
                    class="block text-[10px] font-black uppercase tracking-widest text-text-muted"
            >
              Connection
            </span>

                <code
                        class="mt-1 block break-all font-mono text-xs text-text-main"
                >
                  {formatConnectionString(
                          source.connectionString
                  )}
                </code>
              </div>


              <div
                      class="grid grid-cols-1 gap-4 sm:grid-cols-2"
              >
                <div class="min-w-0">
              <span
                      class="block text-[10px] font-black uppercase tracking-widest text-text-muted"
              >
                Driver
              </span>

                  <span
                          class="mt-1 block break-words text-sm text-text-main"
                  >
                {source.driverOrClientName || 'Unknown'}
              </span>
                </div>

                <div class="min-w-0">
              <span
                      class="block text-[10px] font-black uppercase tracking-widest text-text-muted"
              >
                Pool
              </span>

                  <span
                          class="mt-1 block break-words text-sm text-text-main"
                  >
                {source.poolType || 'N/A'}
              </span>
                </div>
              </div>


              {#if source.activeConnections !== null ||
              source.idleConnections !== null}

                <div
                        class="grid grid-cols-3 gap-3 border-t border-border-subtle pt-4"
                >
                  <div class="min-w-0">
                <span
                        class="block text-[10px] font-black uppercase tracking-widest text-text-muted"
                >
                  Active
                </span>

                    <span
                            class="mt-1 block text-lg font-black text-text-main"
                    >
                  {source.activeConnections ?? '—'}
                </span>
                  </div>

                  <div class="min-w-0">
                <span
                        class="block text-[10px] font-black uppercase tracking-widest text-text-muted"
                >
                  Idle
                </span>

                    <span
                            class="mt-1 block text-lg font-black text-text-main"
                    >
                  {source.idleConnections ?? '—'}
                </span>
                  </div>

                  <div class="min-w-0">
                <span
                        class="block text-[10px] font-black uppercase tracking-widest text-text-muted"
                >
                  Max
                </span>

                    <span
                            class="mt-1 block text-lg font-black text-text-main"
                    >
                  {source.maxPoolSize ?? '—'}
                </span>
                  </div>
                </div>

              {/if}

            </div>
          </div>

        {/each}
      </div>
    </section>


    <!-- FEATURES -->

<!--    <section class="space-y-4">-->
<!--      <div>-->
<!--        <h2 class="text-xl font-bold text-text-main">-->
<!--          Library Capabilities-->
<!--        </h2>-->

<!--        <p class="text-sm text-text-muted">-->
<!--          Features currently available in KraftAdmin.-->
<!--        </p>-->
<!--      </div>-->

<!--      <div-->
<!--              class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3"-->
<!--      >-->
<!--        {#each dashboardData.features as feature}-->

<!--          <div-->
<!--                  class="min-w-0 rounded-3xl border border-border-subtle bg-bg-surface p-5 sm:p-6"-->
<!--          >-->
<!--            <div-->
<!--                    class="flex items-start justify-between gap-4"-->
<!--            >-->
<!--              <h3-->
<!--                      class="min-w-0 break-words font-bold text-text-main"-->
<!--              >-->
<!--                {feature.name}-->
<!--              </h3>-->

<!--              <span-->
<!--                      class={`shrink-0 rounded-lg px-2 py-1 text-[10px] font-black uppercase ${-->
<!--              feature.status === 'Active'-->
<!--                ? 'bg-success/10 text-success'-->
<!--                : 'bg-warning/10 text-warning'-->
<!--            }`}-->
<!--              >-->
<!--            {feature.status}-->
<!--          </span>-->
<!--            </div>-->

<!--            <p-->
<!--                    class="mt-4 text-sm leading-relaxed text-text-muted"-->
<!--            >-->
<!--              {feature.description}-->
<!--            </p>-->

<!--            {#if feature.unlockCriteria}-->
<!--              <p-->
<!--                      class="mt-4 border-t border-border-subtle pt-4 text-xs italic text-text-muted"-->
<!--              >-->
<!--                {feature.unlockCriteria}-->
<!--              </p>-->
<!--            {/if}-->
<!--          </div>-->

<!--        {/each}-->
<!--      </div>-->
<!--    </section>-->
   

  {:else}

   
    <div
            class="flex flex-col items-center justify-center gap-4 py-20"
    >
      <p class="text-text-muted">
        Unable to load dashboard data.
      </p>
    </div>
   

  {/if}

</div>
