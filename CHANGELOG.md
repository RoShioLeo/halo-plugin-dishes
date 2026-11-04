# Changes

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
