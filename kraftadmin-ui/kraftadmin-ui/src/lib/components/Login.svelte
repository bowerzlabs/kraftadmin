<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { token } from '../stores/auth';

  const dispatch = createEventDispatcher();
  let username = '';
  let password = '';
  let error = '';
  let submittng = false;

  // async function handleLogin() {
  //   submittng = true;
  //   error = '';
  //   try {
  //     const res = await fetch('/admin/api/auth/login', {
  //       method: 'POST',
  //       body: JSON.stringify({ username, password }),
  //       headers: { 'Content-Type': 'application/json' }
  //     });

  //     console.log("res " + res.json);
      
  //     if (res.ok) {
  //       const data = await res.json();
  //       console.log("data " + data);
  //       $token = data.token; // Save to store & localStorage
  //       dispatch('success');
  //     } else {
  //       error = 'Invalid credentials';
  //     }
  //   } catch (e) {
  //     error = 'Server connection failed';
  //   } finally {
  //     submittng = false;
  //   }
  // }

  // async function handleLogin() {
  //   submittng = true;
  //   error = '';
  //   try {
  //     const res = await fetch('/admin/api/auth/login', {
  //       method: 'POST',
  //       body: JSON.stringify({ username, password }),
  //       headers: { 'Content-Type': 'application/json' }
  //     });

  //     // Log only the status first
  //     console.log("Response status:", res.status);
      
  //     const data = await res.json(); // Read the stream ONCE here

  //     if (res.ok) {
  //       // Check if your backend returns 'token' or 'message'
  //       // Your controller currently returns mapOf("message" to "Login successful")
  //       // But your Svelte app expects data.token
  //       if (data.token) {
  //           $token = data.token;
  //           dispatch('success');
  //       } else {
  //           // If using cookies (as your controller shows), you might not need a token in the store
  //           // but your App.svelte logic depends on $token. 
  //           // Better to return the token in the JSON body too.
  //           dispatch('success'); 
  //       }
  //     } else {
  //       error = data.error || 'Invalid credentials';
  //     }
  //   } catch (e) {
  //     console.error(e);
  //     error = 'Server connection failed';
  //   } finally {
  //     submittng = false;
  //   }
  // }

  async function handleLogin() {
  submittng = true;
  error = '';
  try {
    const res = await fetch('/admin/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
      headers: { 'Content-Type': 'application/json' }
    });

    const data = await res.json();

    console.log("Response status:", res.status);
    console.log("Response data:", data);

    if (res.ok) {
      // 1. Support JWT: If server returns a token, store it
      if (data.token) {
        $token = data.token;
      }
      
      // 2. Support Session/Cookie: 
      // Even if no token is returned, the cookie is now set.
      // We signal success to App.svelte to trigger fetchMetadata
      dispatch('success');
    } else {
      error = data.error || 'Invalid credentials';
    }
  } catch (e: any) {
    console.log(e.message);
    error = 'Server connection failed';
  } finally {
    submittng = false;
  }
}
</script>

<div class="flex-1 flex items-center justify-center bg-black">
  <div class="w-full max-w-sm p-8 rounded-2xl bg-zinc-900 border border-zinc-800 shadow-2xl">
    <div class="mb-8">
      <h1 class="text-2xl font-bold text-white">KraftAdmin</h1>
      <p class="text-zinc-500 text-sm">Sign in to access intelligence dashboard</p>
    </div>

    {#if error}
      <div class="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded text-red-500 text-xs">
        {error}
      </div>
    {/if}

    <div class="space-y-4">
      <div class="flex flex-col gap-1.5">
        <!-- <label class="text-[10px] text-zinc-400 font-bold uppercase">Username</label> -->
        <input bind:value={username} type="text" class="input-base" placeholder="admin" />
      </div>

      <div class="flex flex-col gap-1.5">
        <!-- <label class="text-[10px] text-zinc-400 font-bold uppercase" contr>Password</label> -->
        <input bind:value={password} type="password" class="input-base" placeholder="••••••••" />
      </div>

      <button 
        on:click={handleLogin}
        disabled={submittng}
        class="w-full py-3 mt-4 bg-white text-black font-bold rounded-lg hover:bg-zinc-200 transition-all active:scale-[0.98] disabled:opacity-50">
        {submittng ? 'Authenticating...' : 'Sign In'}
      </button>
    </div>
  </div>
</div>