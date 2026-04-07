<script lang="ts">
  export let subColumns: any[] = [];
  export let value: any = {};
  export let label = "";

  // ✅ Keyed fields — only recomputes when subColumns changes
  let fields: any[] = [];
  let lastJson = '';
  $: {
    const json = JSON.stringify(subColumns);
    if (json !== lastJson) {
      lastJson = json;
      fields = subColumns.filter(c => c.visible && !['id', 'createdAt', 'updatedAt'].includes(c.name));
    }
  }

  // ✅ Keep local formData in sync with parent value
  // Use a local copy to avoid mutating the parent object directly on every keystroke
  let formData: any = {};
  $: {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      formData = { ...value };
    }
  }

  // ✅ Propagate local changes back up to parent
  function update(fieldName: string, newVal: any) {
    formData = { ...formData, [fieldName]: newVal };
    value = formData;
  }
</script>

<div class="embedded-container">
  <header class="embedded-header">
    <span class="label-text">{label}</span>
    <div class="header-line"></div>
  </header>

  <div class="form-grid">
    {#each fields as col (col.name)}
      <div class="field-group {['TEXTAREA', 'JSON', 'WYSIWYG', 'ARRAY', 'VIDEO'].includes(col.type) ? 'span-2' : ''}">
        <label for={col.name} class="field-label">
          {col.label}
          {#if col.required}<span class="req-star">*</span>{/if}
        </label>

        {#if col.type === 'CHECKBOX'}
          <div class="toggle-wrapper">
            <button type="button"
              on:click={() => update(col.name, !formData[col.name])}
              class="toggle-btn" class:active={formData[col.name]}>
              <div class="toggle-dot" class:dot-active={formData[col.name]}></div>
            </button>
          </div>

        {:else if col.type === 'RADIO'}
          <div class="flex-row gap-4 py-1">
            {#each col.selectOptions || [] as opt}
              <label class="radio-label">
                <input type="radio"
                  checked={formData[col.name] === opt.value}
                  on:change={() => update(col.name, opt.value)} />
                <span>{opt.label}</span>
              </label>
            {/each}
          </div>

        {:else if col.type === 'SELECT'}
          <select
            class="input-base"
            value={formData[col.name] ?? null}
            on:change={(e) => update(col.name, e.currentTarget.value)}>
            <option value={null}>{col.placeholder || `Select ${col.label}...`}</option>
            {#each col.selectOptions || [] as opt}
              <option value={opt.value}>{opt.label}</option>
            {/each}
          </select>

        {:else if col.type === 'DATE'}
          <input type="date"
            class="input-base"
            value={formData[col.name] ?? ''}
            on:change={(e) => update(col.name, e.currentTarget.value)} />

        {:else if col.type === 'DATETIME'}
          <input type="datetime-local"
            class="input-base"
            value={formData[col.name] ?? ''}
            on:change={(e) => update(col.name, e.currentTarget.value)} />

        {:else if col.type === 'TIME'}
          <input type="time"
            class="input-base"
            value={formData[col.name] ?? ''}
            on:change={(e) => update(col.name, e.currentTarget.value)} />

        {:else if col.type === 'NUMBER' || col.type === 'RANGE'}
          <div class="flex flex-col gap-2">
            <input
              type={col.type === 'RANGE' ? 'range' : 'number'}
              step="any"
              class={col.type === 'RANGE' ? 'range-input' : 'input-base'}
              value={formData[col.name] ?? 0}
              on:input={(e) => update(col.name, parseFloat(e.currentTarget.value))} />
            {#if col.type === 'RANGE'}
              <span class="text-xs text-brand-primary font-mono">{formData[col.name] || 0}</span>
            {/if}
          </div>

        {:else if col.type === 'COLOR'}
          <div class="color-input-group">
            <input type="color"
              class="color-swatch"
              value={formData[col.name] ?? '#000000'}
              on:input={(e) => update(col.name, e.currentTarget.value)} />
            <input type="text"
              class="input-base mono"
              placeholder="#000000"
              value={formData[col.name] ?? ''}
              on:input={(e) => update(col.name, e.currentTarget.value)} />
          </div>

        {:else if col.type === 'ARRAY'}
          <input type="text"
            class="input-base"
            placeholder="e.g. Kotlin, Java"
            value={Array.isArray(formData[col.name]) ? formData[col.name].join(', ') : (formData[col.name] ?? '')}
            on:input={(e) => update(col.name, e.currentTarget.value.split(',').map((s: string) => s.trim()).filter(Boolean))} />

        {:else if col.type === 'JSON' || col.type === 'TEXTAREA' || col.type === 'WYSIWYG'}
          <textarea
            class="input-base mono"
            rows={4}
            placeholder={col.type === 'JSON' ? '{"key": "value"}' : '...'}
            value={formData[col.name] ?? ''}
            on:input={(e) => update(col.name, e.currentTarget.value)}></textarea>

        {:else if col.type === 'IMAGE'}
          <div class="image-field">
            {#if formData[col.name]}
              <div class="preview-box">
                <img src={formData[col.name]} alt="Preview" />
              </div>
            {/if}
            <input type="text"
              class="input-base"
              placeholder="Image URL..."
              value={formData[col.name] ?? ''}
              on:input={(e) => update(col.name, e.currentTarget.value)} />
          </div>

        {:else}
          <!-- TEXT, EMAIL, URL, TEL, PASSWORD — all safe string types -->
          <input
            type={['EMAIL', 'URL', 'NUMBER', 'TEL', 'PASSWORD'].includes(col.type) ? col.type.toLowerCase() : 'text'}
            placeholder={col.placeholder || ''}
            class="input-base"
            value={typeof formData[col.name] === 'string' ? formData[col.name] : (formData[col.name] ?? '')}
            on:input={(e) => update(col.name, e.currentTarget.value)} />
        {/if}
      </div>
    {/each}
  </div>
</div>

<style>
  .embedded-container { width: 100%; }

  .embedded-header {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    margin-bottom: 1rem;
  }
  .label-text {
    font-size: 0.7rem;
    font-weight: 800;
    color: #3b82f6;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    white-space: nowrap;
  }
  .header-line {
    flex: 1;
    height: 1px;
    background: #27272a;
  }

  .form-grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: 1rem;
    padding: 1rem;
    background: #0c0c0e;
    border: 1px dashed #27272a;
    border-radius: 0.5rem;
  }
  @media (min-width: 768px) {
    .form-grid { grid-template-columns: repeat(2, 1fr); }
    .span-2 { grid-column: span 2; }
  }

  .field-group { display: flex; flex-direction: column; gap: 0.375rem; }
  .field-label { font-size: 0.7rem; font-weight: 700; color: #52525b; text-transform: uppercase; letter-spacing: 0.05em; }
  .req-star { color: #ef4444; }

  .input-base { width: 100%; padding: 0.625rem 0.75rem; border-radius: 0.375rem; border: 1px solid #27272a; background-color: #09090b; color: #fafafa; outline: none; font-size: 0.875rem; transition: border-color 0.2s; }
  .input-base:focus { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15); }
  .mono { font-family: ui-monospace, monospace; font-size: 0.8rem; }

  .toggle-btn { width: 2.75rem; height: 1.5rem; border-radius: 99px; position: relative; background-color: #3f3f46; border: none; cursor: pointer; transition: background 0.3s; }
  .toggle-btn.active { background-color: #3b82f6; }
  .toggle-dot { position: absolute; top: 0.25rem; left: 0.25rem; background-color: white; width: 1rem; height: 1rem; border-radius: 50%; transition: transform 0.2s; }
  .dot-active { transform: translateX(1.25rem); }

  .color-input-group { display: flex; gap: 0.5rem; align-items: center; }
  .color-swatch { width: 3rem; height: 2.75rem; border: 1px solid #27272a; border-radius: 0.375rem; background: none; cursor: pointer; padding: 0; }

  .range-input { width: 100%; accent-color: #3b82f6; cursor: pointer; }

  .image-field { display: flex; flex-direction: column; gap: 0.5rem; }
  .preview-box img { width: 100%; height: 8rem; object-fit: cover; border-radius: 0.5rem; border: 1px solid #27272a; }

  .radio-label { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-size: 0.875rem; color: #d4d4d8; }
  .flex-row { display: flex; flex-wrap: wrap; gap: 1rem; padding: 0.25rem 0; }

  .text-brand-primary { color: #3b82f6; }
  .font-mono { font-family: ui-monospace, monospace; }
  .flex { display: flex; }
  .flex-col { flex-direction: column; }
  .gap-2 { gap: 0.5rem; }
  .gap-4 { gap: 1rem; }
  .text-xs { font-size: 0.75rem; }

  @keyframes fadeIn { from { opacity: 0; transform: translateY(-2px); } to { opacity: 1; transform: translateY(0); } }
</style>