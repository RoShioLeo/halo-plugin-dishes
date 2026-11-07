# Third-party notices

This fork retains its GPL-3.0 license in `LICENSE`. The following notices cover third-party code and icon data used by the web interfaces. Their original license terms are retained separately; this document does not replace or amend those terms.

The full license texts are distributed in `third-party-licenses/` in this source repository and copied into the plugin JAR by the build. They are retained here even when frontend minification removes license comments.

## Bundled frontend dependencies

| Component | Version | Copyright / attribution | License text | Source |
| --- | --- | --- | --- | --- |
| Vue, `@vue/runtime-dom`, `@vue/runtime-core`, `@vue/reactivity`, `@vue/shared` | 3.5.32 | Copyright (c) 2018-present, Yuxi (Evan) You | [MIT](third-party-licenses/vue-MIT.txt) | <https://github.com/vuejs/core/tree/v3.5.32> |
| Vue Router | 4.6.4 | Copyright (c) 2019-present Eduardo San Martin Morote | [MIT](third-party-licenses/vue-router-MIT.txt) | <https://github.com/vuejs/router/tree/v4.6.4> |
| `@vue/devtools-api` (Vue Router dependency) | 6.6.4 | Copyright (c) 2014-present Evan You | [MIT](third-party-licenses/vue-devtools-api-MIT.txt) | <https://github.com/vuejs/vue-devtools/tree/v6.6.4> |
| Tailwind CSS (generated public-page CSS) | 4.2.2 | Copyright (c) Tailwind Labs, Inc. | [MIT](third-party-licenses/tailwindcss-MIT.txt) | <https://github.com/tailwindlabs/tailwindcss/tree/v4.2.2> |
| vue-draggable-plus | 0.5.6 | Copyright (c) 2023 丶远方 | [MIT](third-party-licenses/vue-draggable-plus-MIT.txt) | <https://github.com/Alfred-Skyblue/vue-draggable-plus/tree/v0.5.6> |
| SortableJS (embedded in vue-draggable-plus) | 1.15.2 | Copyright (c) 2019 All contributors to Sortable; embedded header credits RubaXa and owenm | [MIT](third-party-licenses/sortablejs-MIT.txt) | <https://github.com/SortableJS/Sortable/tree/1.15.2> |
| Remix Icon SVG paths, from `@iconify-json/ri` | Icons 4.8.0 / package 1.2.10 | Remix Design | [Apache-2.0](third-party-licenses/remixicon-Apache-2.0.txt), [attribution](third-party-licenses/remixicon-NOTICE.txt) | <https://github.com/Remix-Design/RemixIcon/tree/v4.8.0> |

The installed Vue runtime packages listed above carry identical complete license texts. One unchanged copy is supplied for all of them. The vue-draggable-plus distribution embeds SortableJS 1.15.2; it imports Vue from the host in the Console build. No separate VueUse runtime package is included by this plugin's Console bundle.

The Remix Icon paths used here are `file-list-3-line`, `restaurant-2-line`,
`settings-3-line`, `refresh-line`, `share-line`, `close-line`, `search-line`,
`edit-line`, `add-line`, `subtract-line`, `arrow-left-s-line`,
`arrow-right-s-line`, `group-line`, `send-plane-line`, `menu-line`,
`check-line`, and `delete-bin-line`. The build and local
Vue icon component wrap the original static path data without changing it. The
pinned icon set declares Apache-2.0 in its installed `info.json` and `package.json`;
the full text is copied from the matching upstream v4.8.0 `License` file.

## Host-provided dependencies

The Console bundle references `HaloUiShared`, `Vue`, `HaloComponents`, `axios`, and `VueRouter` as host globals. It does not redistribute the implementations of Halo UI Shared, Halo Components, or Axios. Halo supplies those components and their notices. The separately bundled public frontend's Vue and Vue Router notices are included above.

Build-only tooling, type declarations, tests, and package-manager dependencies are not included in this runtime notice inventory. Their source packages retain their own licenses in their respective distributions. On dependency upgrades, update this inventory and the corresponding full license texts before distributing a new plugin build.

## License-text provenance

The Vue, Vue Router, Tailwind CSS, and vue-draggable-plus texts were copied without modification from the installed packages' `LICENSE` files. The remaining texts were copied without modification from these version-pinned upstream sources:

- SortableJS: <https://raw.githubusercontent.com/SortableJS/Sortable/1.15.2/LICENSE>.
- Remix Icon: <https://raw.githubusercontent.com/Remix-Design/RemixIcon/v4.8.0/License>.
- Vue Devtools API: <https://raw.githubusercontent.com/vuejs/vue-devtools/v6.6.4/LICENSE>.

Inventory prepared on 2026-11-05 for the LemonSlice fork.
