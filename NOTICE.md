# Copyright, license and modification notice

This is a modified version of **Halo Dishes**, originally by **bai**.

- Original project: https://github.com/Avrinbai/halo-plugin-dishes
- Original author: bai, https://avrinbai.cn
- Upstream baseline: `ddb7a9f524323bf7efd3da90d056af369d04f071` (version 1.0.3).
- Modified and maintained by: RoShioLeo for LemonSlice Cloud, **2026-11-05 to 2026-11-08**.
- Modified version: **1.2.0**, plugin ID **lemonslice-kitchen**.
- Source repository, including build files and dependency lockfiles (release tags accompany published releases):
  https://github.com/RoShioLeo/halo-plugin-dishes

The modifications replace public and Console presentation with the LemonSlice
design, add responsive desktop and dark appearance support, replace default
branding, remove the external font dependency, and make build and distribution
notices reproducible. Existing upstream copyright and license notices are retained.
The 1.1.0 redesign adds per-person contributions to a shared date-and-meal order,
server-verified guest/account identity, multi-tag dishes, seven-day reservations,
off-menu wishes, atomic shared-batch submission with chef-only editing after submission,
additive draft batches, and explicit chef confirmation of editable menu snapshots.
The plugin's controllers provide local structured error handling on Halo 2.27.2,
with HTTP status codes for validation, access, conflicts and missing resources.

This work is distributed under the **GNU General Public License version 3**.
The complete license is included as `LICENSE` in the source and `META-INF/LICENSE`
in the plugin JAR. There is no warranty; see the license for details.

Version 1.2 uses the independent identifier `lemonslice-kitchen`. The public
`/dishes` address and the 1.1 data/API contracts remain in place. Stop the old
`dishes` plugin before enabling this plugin; never run both simultaneously.
