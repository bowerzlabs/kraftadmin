<script lang="ts">
  import { link } from "svelte-spa-router";
  import Render from "../renderers/Render.svelte";
  import {
    Loader,
    ChevronDown,
    Download,
    FileJson,
    FileSpreadsheet,
    FileCode2,
    Trash2,
    X
  } from "@lucide/svelte";
  import { onMount } from "svelte";
  import { flash } from "../stores/flash";
  import { snackbar } from "../stores/snackbar";
  import { confirmDialog } from "../stores/dialog";
  import type {
    KraftAdminResource,
    KraftAdminColumn,
    ResourceRow,
    KraftOperationResponse
  } from "../types/resources";

  export let params: { name?: string } = {};

  let resource: KraftAdminResource | null = null;
  let items: ResourceRow[] = [];
  let columns: KraftAdminColumn[] = [];
  let currentPage = 1;
  let searchQuery = "";
  let sortField: string | null = null;
  let sortDirection: "ASC" | "DESC" | null = null;
  let debounceTimer: number;
  let pagination = {
    total: 0,
    pageSize: 20,
    totalPages: 0
  };
  let loading = true;

  // ---- Bulk selection state ----
  let selectedIds = new Set<string>();
  let bulkActionInProgress = false;
  let exportMenuOpen = false;

  const EXPORT_FORMATS: { value: "JSON" | "CSV" | "XML"; label: string; icon: typeof FileJson }[] = [
    { value: "JSON", label: "JSON", icon: FileJson },
    { value: "CSV", label: "CSV", icon: FileSpreadsheet },
    { value: "XML", label: "XML", icon: FileCode2 }
  ];

  $: allOnPageSelected =
          items.length > 0 && items.every((row) => selectedIds.has(row.id));
  $: someOnPageSelected =
          items.some((row) => selectedIds.has(row.id)) && !allOnPageSelected;

  function toggleSelectAllOnPage() {
    const next = new Set(selectedIds);
    if (allOnPageSelected) {
      items.forEach((row) => next.delete(row.id));
    } else {
      items.forEach((row) => next.add(row.id));
    }
    selectedIds = next;
  }

  function toggleRowSelection(id: string) {
    const next = new Set(selectedIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    selectedIds = next;
  }

  function clearSelection() {
    selectedIds = new Set();
  }

  // Show flash message from Create/Edit page

  onMount(() => {
    const msg = flash.consume();
    if (msg) {
      snackbar[msg.type](msg.message);
    }
  });


  $: if (params.name) {
    currentPage = 1;
    searchQuery = "";
    sortField = null;
    sortDirection = null;
    clearSelection();
    loadData(params.name, 1, "");
  }

  $: placeholder =
          resource?.searchableFields?.length ?? 0 > 0
                  ? `Search by ${resource!.searchableFields.join(", ")}...`
                  : "Search records...";

  async function handlePageChange(newPage: number) {
    if (
            newPage >= 1 &&
            newPage <= pagination.totalPages &&
            newPage !== currentPage
    ) {
      currentPage = newPage;
      clearSelection();

      await loadData(
              params.name!,
              newPage,
              searchQuery,
              sortField ?? undefined,
              sortDirection ?? undefined
      );
    }
  }

  function handleSearch(event: Event) {
    clearTimeout(debounceTimer);

    const target = event.target as HTMLInputElement;
    searchQuery = target.value;

    debounceTimer = window.setTimeout(() => {
      currentPage = 1;
      clearSelection();

      loadData(
              params.name!,
              currentPage,
              searchQuery,
              sortField ?? undefined,
              sortDirection ?? undefined
      );
    }, 500);
  }

  function handleSort(columnName: string) {
    if (!resource?.sortableFields?.includes(columnName)) return;

    if (sortField === columnName) {
      sortDirection = sortDirection === "ASC" ? "DESC" : "ASC";
    } else {
      sortField = columnName;
      sortDirection = "DESC";
    }

    loadData(
            params.name!,
            currentPage,
            searchQuery,
            sortField,
            sortDirection ?? undefined
    );
  }

  // The list endpoint returns { resource: KraftAdminResource }, unwrapped —
  // not a KraftOperationResponse<T> envelope like save/delete/detail. Model
  // that explicitly rather than relying on `any`.
  interface ListResourceResponse {
    resource: KraftAdminResource;
  }

  // Response shape for BulkAction.DELETE, mirrors BulkDeleteResult on the backend.
  interface BulkDeleteResult {
    requested: number;
    deleted: number;
    failed: Record<string, string>;
  }

  async function loadData(
          resourceName: string,
          page: number,
          query: string,
          sField?: string,
          sDir?: string
  ) {
    loading = true;

    try {
      let url = `/admin/api/resources/${resourceName}?page=${page}&size=${pagination.pageSize}`;

      if (query) url += `&q=${encodeURIComponent(query)}`;

      if (sField) url += `&sortField=${sField}&sortDirection=${sDir || "DESC"}`;

      const response = await fetch(url);

      // The error-path body still comes back as { success:false, message }
      // (KraftOperationResponse shape) even though the success path is a
      // bare { resource }. Read as unknown first, then narrow.
      const result: ListResourceResponse | KraftOperationResponse<unknown> =
              await response.json();

      if (!response.ok || (result as KraftOperationResponse<unknown>).success === false) {
        const message = (result as KraftOperationResponse<unknown>).message;
        snackbar.error(message || "Unable to load resource data.");
        return;
      }

      const success = result as ListResourceResponse;
      resource = success.resource;
      columns = resource.columns.filter((c) => c.showInTable);
      items = resource.data.items;

      pagination = {
        total: resource.data.total,
        pageSize: resource.data.pageSize,
        totalPages: resource.data.totalPages
      };

      // Drop any selected ids that no longer exist on the current page's
      // result set (e.g. after a search narrows the visible rows).
      const visibleIds = new Set(items.map((row) => row.id));
      selectedIds = new Set([...selectedIds].filter((id) => visibleIds.has(id)));
    } catch (e: any) {
      console.error(e);
      snackbar.error(e.message || "Unable to connect to the server.");
    } finally {
      loading = false;
    }
  }

  async function handleDelete(id: string) {
    const confirmed = await confirmDialog.open({
      title: "Delete Record",
      message: "Are you sure you want to delete this record?",
      variant: "danger"
    });

    if (!confirmed) return;

    try {
      const res = await fetch(`/admin/api/resources/${params.name}/${id}`, { method: "DELETE" });
      const result: KraftOperationResponse<unknown> = await res.json();

      if (res.ok && result.success) {
        snackbar.success(result.message || "Record deleted successfully.");

        await loadData(
                params.name!,
                currentPage,
                searchQuery,
                sortField ?? undefined,
                sortDirection ?? undefined
        );
      } else {
        snackbar.error(result.message || "Delete failed.");
      }
    } catch (e: any) {
      snackbar.error(e.message || "Delete failed.");
    }
  }

  // ids omitted (or empty) means "act on the whole resource" for EXPORT —
  // handled server-side. DELETE never allows this (enforced server-side too).
  function buildBulkActionUrl(actionName: string, ids: Set<string>, format?: string): string {
    const searchParams = new URLSearchParams();
    searchParams.set("actionName", actionName);
    if (format) searchParams.set("format", format);
    ids.forEach((id) => searchParams.append("selectedIds", id));
    return `/admin/api/resources/${params.name}/bulk-action?${searchParams.toString()}`;
  }

  async function handleBulkDelete() {
    if (selectedIds.size === 0) return;

    const confirmed = await confirmDialog.open({
      title: "Delete Records",
      message: `Are you sure you want to delete ${selectedIds.size} selected record(s)? This cannot be undone.`,
      variant: "danger"
    });

    if (!confirmed) return;

    bulkActionInProgress = true;

    try {
      const res = await fetch(buildBulkActionUrl("DELETE", selectedIds));
      const result: KraftOperationResponse<BulkDeleteResult> = await res.json();

      if (res.ok && result.data) {
        const { deleted, requested, failed } = result.data;

        if (Object.keys(failed).length > 0) {
          snackbar.error(
                  `Deleted ${deleted}/${requested} record(s). ${Object.keys(failed).length} failed — see console for details.`
          );
          console.warn("Bulk delete failures:", failed);
        } else {
          snackbar.success(result.message || `Deleted ${deleted}/${requested} record(s).`);
        }

        clearSelection();

        await loadData(
                params.name!,
                currentPage,
                searchQuery,
                sortField ?? undefined,
                sortDirection ?? undefined
        );
      } else {
        snackbar.error(result.message || "Bulk delete failed.");
      }
    } catch (e: any) {
      snackbar.error(e.message || "Bulk delete failed.");
    } finally {
      bulkActionInProgress = false;
    }
  }

  function extractFilename(response: Response, fallback: string): string {
    const disposition = response.headers.get("Content-Disposition");
    if (!disposition) return fallback;
    const match = /filename="?([^"]+)"?/i.exec(disposition);
    return match?.[1] ?? fallback;
  }

  async function downloadBlobResponse(response: Response, fallbackFilename: string) {
    const blob = await response.blob();
    const filename = extractFilename(response, fallbackFilename);
    const objectUrl = URL.createObjectURL(blob);

    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();

    URL.revokeObjectURL(objectUrl);
  }

  // Exports the current selection, or the entire resource when nothing is
  // selected — the empty Set produces a URL with no selectedIds param,
  // which the backend treats as "export everything."
  async function handleBulkExport(format: "JSON" | "CSV" | "XML") {
    exportMenuOpen = false;
    bulkActionInProgress = true;

    const isExportAll = selectedIds.size === 0;
    const suffix = isExportAll ? "all" : "selected";

    try {
      const res = await fetch(buildBulkActionUrl("EXPORT", selectedIds, format));

      if (!res.ok) {
        // Error responses come back as JSON (KraftOperationResponse-shaped),
        // unlike the success path which streams file bytes.
        const errorBody = await res.json().catch(() => null);
        snackbar.error(errorBody?.message || "Export failed.");
        return;
      }

      await downloadBlobResponse(res, `${params.name}-export-${suffix}.${format.toLowerCase()}`);
      snackbar.success(
              isExportAll
                      ? `Exported all ${params.name} records as ${format}.`
                      : `Exported ${selectedIds.size} record(s) as ${format}.`
      );
    } catch (e: any) {
      snackbar.error(e.message || "Export failed.");
    } finally {
      bulkActionInProgress = false;
    }
  }
</script>

<svelte:head>
  <title>{resource?.label ? `${resource.label} | ${resource?.name}` : 'Loading...'}</title>
</svelte:head>

<div class="space-y-6">
  <div class="flex flex-col md:flex-row md:justify-between md:items-end gap-4">
    <div>
      <h2 class="text-2xl font-bold text-text-main capitalize tracking-tight">{resource?.label || params.name}</h2>
      <p class="text-xs text-text-muted mt-1 font-medium">Manage and monitor your {params.name} resource data</p>
    </div>

    <div class="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
      <input
              type="text"
              placeholder={placeholder}
              value={searchQuery}
              on:input={handleSearch}
              class="px-4 py-2.5 bg-bg-surface border border-border-subtle rounded-xl text-xs focus:outline-none focus:ring-2 focus:ring-brand-primary/20 text-text-main"
      />

      <!-- Export is always available, regardless of selection — exports
           "all" when nothing is checked, "selected" otherwise. -->
      <div class="relative">
        <button
                on:click={() => (exportMenuOpen = !exportMenuOpen)}
                disabled={bulkActionInProgress}
                class="btn-secondary text-xs px-4 py-2.5 flex items-center gap-1.5 disabled:opacity-40">
          <Download class="w-3.5 h-3.5" />
          {selectedIds.size > 0 ? `Export Selected (${selectedIds.size})` : "Export All"}
          <ChevronDown class="w-3.5 h-3.5" />
        </button>

        {#if exportMenuOpen}
          <div class="absolute right-0 mt-1 w-36 bg-bg-surface border border-border-subtle rounded-lg shadow-lg z-10 overflow-hidden">
            {#each EXPORT_FORMATS as fmt}
              <button
                      on:click={() => handleBulkExport(fmt.value)}
                      class="w-full text-left px-3 py-2 text-[11px] font-semibold text-text-main hover:bg-bg-main/60 transition-colors flex items-center gap-2">
                <svelte:component this={fmt.icon} class="w-3.5 h-3.5 text-text-muted" />
                {fmt.label}
              </button>
            {/each}
          </div>
        {/if}
      </div>

      <a href="/resources/{params.name}/create" use:link
         class="px-5 py-2.5 bg-brand-primary text-white text-xs font-bold rounded-xl shadow-lg shadow-brand-primary/20 hover:opacity-90 transition-all text-center">
        + New
      </a>
    </div>
  </div>

  {#if selectedIds.size > 0}
    <div class="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 px-4 py-3 bg-brand-primary/5 border border-brand-primary/20 rounded-xl">
      <div class="text-xs font-bold text-text-main">
        {selectedIds.size} record{selectedIds.size === 1 ? "" : "s"} selected
      </div>

      <div class="flex items-center gap-2">
        <button
                on:click={clearSelection}
                disabled={bulkActionInProgress}
                class="text-[11px] font-bold text-text-muted hover:text-text-main px-3 py-1.5 disabled:opacity-40 flex items-center gap-1">
          <X class="w-3.5 h-3.5" />
          Clear
        </button>

        <button
                on:click={handleBulkDelete}
                disabled={bulkActionInProgress}
                class="btn-danger text-[11px] px-3 py-1.5 disabled:opacity-40 flex items-center gap-1">
          <Trash2 class="w-3.5 h-3.5" />
          {bulkActionInProgress ? "Working..." : "Delete Selected"}
        </button>
      </div>
    </div>
  {/if}

  <div class="bg-bg-surface border border-border-subtle rounded-2xl shadow-sm overflow-hidden flex flex-col">
    {#if loading}
      <div class="flex flex-col items-center justify-center py-20 gap-4">
        <Loader class="w-8 h-8 text-brand-primary animate-spin" />
        <p class="text-[10px] text-text-muted font-black uppercase tracking-[0.2em]">Synchronizing...</p>
      </div>
    {:else if items.length === 0}
      <div class="p-24 text-center text-text-muted font-medium">No records found.</div>
    {:else}
      <div class="overflow-x-auto w-full">
        <table class="w-full text-left border-collapse min-w-[600px]">
          <thead class="bg-bg-main/50 border-b border-border-subtle">
          <tr>
            <th class="px-6 py-4 w-10">
              <input
                      type="checkbox"
                      checked={allOnPageSelected}
                      indeterminate={someOnPageSelected}
                      on:change={toggleSelectAllOnPage}
                      aria-label="Select all rows on this page"
                      class="w-4 h-4 rounded border-border-subtle text-brand-primary focus:ring-brand-primary/30"
              />
            </th>
            {#each columns as col}
              <th
                      class="px-6 py-4 text-[10px] font-extrabold text-text-muted uppercase tracking-widest cursor-pointer hover:text-brand-primary transition-colors select-none"
                      on:click={() => handleSort(col.name)}
              >
                <div class="flex items-center gap-1.5">
                  {col.label}
                  <span class={sortField === col.name ? "text-brand-primary font-bold" : "text-border-subtle"}>
                      {#if sortField === col.name}
                        {sortDirection === 'ASC' ? '↑' : '↓'}
                      {:else if resource?.sortableFields?.includes(col.name)}
                        ↕
                      {/if}
                    </span>
                </div>
              </th>
            {/each}
            <th class="px-6 py-4 text-right text-[10px] font-extrabold text-text-muted uppercase tracking-widest">Action</th>
          </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle">
          {#each items as row}
            <tr class="hover:bg-bg-main/40 transition-colors group" class:bg-brand-primary={selectedIds.has(row.id)}>
            <td class="px-6 py-4">
              <input
                      type="checkbox"
                      checked={selectedIds.has(row.id)}
                      on:change={() => toggleRowSelection(row.id)}
                      aria-label="Select row"
                      class="w-4 h-4 rounded border-border-subtle text-brand-primary focus:ring-brand-primary/30"
              />
            </td>
            {#each columns as col}
              <td class="px-6 py-4 whitespace-nowrap text-text-main">
                <Render
                        type={col.type}
                        value={row.values[col.name]}
                        elementCollection={col.elementCollection}
                        label={col.label}
                        mode="table"
                />
              </td>
            {/each}
            <td class="px-6 py-4 text-right">
              <div class="flex justify-end gap-2">
                <a href="/resources/{params.name}/{row.id}" use:link class="btn-secondary text-[11px] px-3 py-1.5">View</a>
                {#if row.metadata?.canDelete}
                  <button on:click={() => handleDelete(row.id)} class="btn-danger text-[11px] px-3 py-1.5">Delete</button>
                {/if}
              </div>
            </td>
            </tr>
          {/each}
          </tbody>
        </table>
      </div>

      <div class="px-4 md:px-6 py-4 bg-bg-main/30 border-t border-border-subtle flex flex-col sm:flex-row items-center justify-between gap-4">
        <div class="text-[10px] text-text-muted font-bold uppercase tracking-wider">
          Showing {(currentPage - 1) * pagination.pageSize + 1} - {Math.min(currentPage * pagination.pageSize, pagination.total)} of {pagination.total}
        </div>

        <div class="flex items-center gap-2">
          <button
                  on:click={() => handlePageChange(currentPage - 1)}
                  disabled={currentPage === 1}
                  class="p-2 rounded-lg border border-border-subtle bg-bg-surface text-text-main disabled:opacity-30 hover:border-brand-primary transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" /></svg>
          </button>

          <span class="text-xs font-bold text-text-main px-2">
            {currentPage} <span class="text-text-muted">/</span> {pagination.totalPages}
          </span>

          <button
                  on:click={() => handlePageChange(currentPage + 1)}
                  disabled={currentPage === pagination.totalPages}
                  class="p-2 rounded-lg border border-border-subtle bg-bg-surface text-text-main disabled:opacity-30 hover:border-brand-primary transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" /></svg>
          </button>
        </div>
      </div>
    {/if}
  </div>
</div>