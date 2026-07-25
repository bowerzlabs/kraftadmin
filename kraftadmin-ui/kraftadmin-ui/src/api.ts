import { replace } from "svelte-spa-router";
import { isAuthenticated } from "./lib/stores/auth";
import { snackbar } from "./lib/stores/snackbar";
import { get } from "svelte/store";
import { isBridgeMode } from "./lib/stores/authMode";

export async function kraftFetch(path: string, options: RequestInit = {}) {
  const headers = new Headers(options.headers || {});

  headers.set("Accept", "application/json");

  if (!(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: "same-origin"
  });

  if (response.status === 401) {
    const bridged = get(isBridgeMode);

    if (bridged) {
      // In bridge mode, KraftAdmin does not own authentication — the
      // parent application does. A 401 here means the PARENT app's
      // session is invalid/expired. KraftAdmin has no login page to
      // send the user to that would fix this; redirecting into our own
      // /auth/login would show a login form that can't actually
      // authenticate them against the parent app's session store.
      // Surface it as an error and let the parent app's own session
      // handling (e.g. a top-level redirect it owns, a refresh, an
      // iframe postMessage, etc.) take over.
      snackbar.error("Your session has expired. Please sign in again.");
      isAuthenticated.set(false);
      return response;
    }

    // Standalone mode — KraftAdmin owns its own session, so its own
    // login page is the correct place to send the user.
    isAuthenticated.set(false);
    replace("/auth/login");
    return response;
  }

  if (response.status === 403) {
    try {
      const body = await response.clone().json();
      snackbar.error(body?.message ?? "You do not have permission to perform this action.");
    } catch {
      snackbar.error("You do not have permission to perform this action.");
    }
    return response;
  }

  return response;
}