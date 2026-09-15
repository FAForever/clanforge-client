/**
 * Controllers and page routes. HTTP in, model and view name out.
 *
 * Thin on purpose. A controller validates and shapes the request, calls one
 * service and returns a view name; it holds no rule of its own. Templates live
 * in src/main/resources/templates, static assets in src/main/resources/static.
 *
 * PageController currently serves the migrated design pages, which still carry
 * hardcoded sample content. Replacing that content with real data is a matter
 * of adding a service call here, not of adding queries here.
 */
package com.faforever.clan_management.web;
