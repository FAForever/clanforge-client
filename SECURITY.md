# Security Policy

## Reporting a vulnerability

Please do **not** open a public issue for a security vulnerability. Report it privately
to the maintainers (for the FAForever deployment, through the FAForever security
contact), including:

- a description of the issue and its impact,
- steps to reproduce, and
- any suggested fix.

We will acknowledge the report, investigate, and coordinate a fix and disclosure.

## Scope

This platform authenticates through an external OpenID Connect provider and stores no
local passwords. Of particular interest are issues that could expose user data, bypass
the admin-role check, allow cross-site scripting or request forgery, or let one user
act on a clan they do not own.

A deployed build must run with the `oidc` profile and real secrets from the
environment; the `dev` profile (in-memory `admin/admin` accounts, form login) is for
local development only and must never be enabled in production.

## Supported versions

Security fixes target the `main` branch.
