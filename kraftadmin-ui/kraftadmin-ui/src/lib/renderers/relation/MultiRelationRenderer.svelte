<script lang="ts">
    import { link } from "svelte-spa-router";

    export let value: any[] = [];
    export let relatedCollection: any = null;
    export let mode: "table" | "detail" = "table";

    /**
     * Supports both relation response shapes:
     *
     * 1. Related collection:
     * {
     *     entityType: "JournalEntry",
     *     items: [...]
     * }
     *
     * 2. Direct RelatedItem array:
     * [
     *     {
     *         id: "...",
     *         entityType: "JournalEntry"
     *     }
     * ]
     */
    $: items =
        relatedCollection?.items ??
        value ??
        [];
</script>

{#if items.length === 0}
    <span class="text-text-muted italic">
        —
    </span>

{:else if mode === "table"}
    <div class="flex flex-wrap gap-2">
        {#each items.slice(0, 2) as item}
            <span class="chip">
                {item.displayField ??
                item.displayLabel ??
                item.label ??
                "—"}
            </span>
        {/each}

        {#if items.length > 2}
            <span class="text-xs text-text-muted pt-1">
                +{items.length - 2}
            </span>
        {/if}
    </div>

{:else}
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        {#each items as item}
            {@const entityType =
                item.entityType ??
                relatedCollection?.entityType}

            <div
                    class="
                    card
                    p-5
                    border-border-subtle
                    hover:border-brand-primary
                    transition-colors
                "
            >
                <div class="font-bold text-text-main">
                    {item.displayLabel ??
                    item.displayField ??
                    item.label ??
                    "Unnamed resource"}
                </div>

                {#if item.values?.description}
                    <div class="mt-2 text-sm text-text-muted">
                        {item.values.description}
                    </div>
                {/if}

                {#if entityType && item.id}
                    <div class="mt-4 flex gap-3">
                        <a
                                use:link
                                href="/resources/{entityType}/{item.id}"
                                class="
                                btn-primary
                                text-xs
                                !px-3
                                !py-1.5
                            "
                        >
                            View
                        </a>

                        <a
                                use:link
                                href="/resources/{entityType}"
                                class="
                                btn-secondary
                                text-xs
                                !px-3
                                !py-1.5
                            "
                        >
                            Manage
                        </a>
                    </div>
                {/if}
            </div>
        {/each}
    </div>

    {#if relatedCollection?.limited}
        <button
                class="
                w-full
                btn-secondary
                mt-4
                uppercase
                tracking-widest
                text-[10px]
            "
        >
            Load More
        </button>
    {/if}
{/if}