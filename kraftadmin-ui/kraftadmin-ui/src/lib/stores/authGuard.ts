import { isBridgeMode } from "./authMode";
import { isAuthenticated } from "./auth";
import { get } from "svelte/store";

export async function authGuard(): Promise<boolean> {
  // In bridge mode, KraftAdmin never gates routes itself — the parent
  // app's own middleware/filter (AdminSecurityFilter, per the Kotlin side
  // of this system) already enforced access before this page ever loaded.
  // If the backend session is genuinely invalid, individual API calls via
  // kraftFetch will surface that as a 401/403, not the router.
  if (get(isBridgeMode)) {
    return true;
  }

  return get(isAuthenticated);
}