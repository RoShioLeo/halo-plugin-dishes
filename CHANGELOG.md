# Changelog

## 1.1.0-lemonslice.1 — 2026-11-05

- Shared per-person ordering, guest/account identity and submitted meal batches.
- Integer portions; one side edits at a time, with a synchronized source/menu draft.
- Extra portions belong to the chef; reductions consume sources in reverse order.
- Zero rows stay visible until a successful atomic commit removes them.
- One private meal aggregate saves source quantities, locking and publication with CAS.
- Chef-created dishes have distinct custom IDs and synchronized names.
- User wishes and remarks are read-only in the kitchen workspace.
- Meal tags and custom tags are separate; listing and in-stock columns both use positive boolean indicators.
- All three meal periods normalize to all day; the editor has an explicit all-day option.
- New submitted choices show one pending-confirmation badge in the kitchen workspace.
- Reserve space between category drag handles and category names.
- Removed prior orders, backup, notifications, random recommendations and adapters.
- Removed obsolete frontend components, fields and unused confetti dependencies.
- No old data/interface migration or fractional-quantity compatibility.

## 1.0.4-lemonslice.1 — 2026-11-05

Modified from bai's Halo Dishes 1.0.3, upstream commit
`ddb7a9f524323bf7efd3da90d056af369d04f071`.

- Rebrand the public page and Console menu as 柠檬私厨, with LemonSlice's lemon SVG.
- Restyle public pages for light and dark appearance, restrained gold accents,
  system sans fonts, accessible controls, mobile use and wider desktop layouts.
- Match the plugin Console surfaces and spacing to LemonSlice while preserving
  Halo's native management layout and forms.
- Remove the original site's external font stylesheet dependency.
- Preserve the `dishes` identity and existing API/data contracts.
- Pin the build environment and verify the Gradle distribution checksum.
- Version and fingerprint embedded frontend asset URLs so rebuilt or upgraded plugins refresh cached UI files.
- Bundle the original GPL-3.0 license and dated modification notices in the JAR.
- Fix Halo 2.27.2 plugin error handling by using inherited controller-local handlers;
  access denial and incorrect passwords return structured HTTP 403 with the existing
  error codes and messages, while other API success/error contracts remain unchanged.

Original functionality and source history remain available in this fork and at
https://github.com/Avrinbai/halo-plugin-dishes.
