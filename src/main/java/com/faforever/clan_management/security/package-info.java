/**
 * Authentication, authorisation and the security response headers.
 *
 * SecurityConfig is the single filter chain. Every route is denied by default;
 * anything public is named explicitly in the permitAll list, so opening a route
 * up is a visible line in a diff rather than an omission.
 *
 * The form login and the in-memory dev account are placeholders. The Scope of
 * Work commits to OpenID Connect through ORY Hydra, which replaces both. The
 * dev account is behind @Profile("dev") so it cannot exist in a deployed build.
 *
 * The Content-Security-Policy here allows no inline script and no inline style.
 * That is deliberate and predates the templates: keep new markup free of
 * style="..." attributes and <script> blocks rather than relaxing the policy.
 */
package com.faforever.clan_management.security;
