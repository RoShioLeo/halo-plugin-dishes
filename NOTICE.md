# Copyright, license and modification notice

This is a modified version of **Halo Dishes**, originally by **bai**.

- Original project: https://github.com/Avrinbai/halo-plugin-dishes
- Original author: bai, https://avrinbai.cn
- Upstream baseline: `ddb7a9f524323bf7efd3da90d056af369d04f071` (version 1.0.3).
- Modified by: RoShioLeo for LemonSlice Cloud, **2026-11-05**.
- Modified version: **1.0.4-lemonslice.1**.
- Corresponding source, including build files and dependency lockfiles:
  https://github.com/RoShioLeo/halo-plugin-dishes/tree/v1.0.4-lemonslice.1

The modifications replace public and Console presentation with the LemonSlice
design, add responsive desktop and dark appearance support, replace default
branding, remove the external font dependency, and make build and distribution
notices reproducible. Existing upstream copyright and license notices are retained.
The 2026-11-05 compatibility changes also make exception handlers local to the plugin
controllers on Halo 2.27.2, returning structured HTTP 403 for access/password failures
without changing the existing error codes, messages or successful API responses.

This work is distributed under the **GNU General Public License version 3**.
The complete license is included as `LICENSE` in the source and `META-INF/LICENSE`
in the plugin JAR. There is no warranty; see the license for details.

The `dishes` plugin identifier, API paths and data types remain compatible with
upstream. This fork and upstream cannot be installed simultaneously.
