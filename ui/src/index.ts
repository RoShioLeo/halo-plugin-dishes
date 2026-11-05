import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiFileList3Line from '~icons/ri/file-list-3-line'
import RiRestaurant2Line from '~icons/ri/restaurant-2-line'
import RiSettings3Line from '~icons/ri/settings-3-line'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/dishes',
        name: 'DishesPluginRoot',
        component: () =>
          import(/* webpackChunkName: "DishesPluginOutlet" */ './views/DishesPluginOutlet.vue'),
        redirect: { name: 'DishesOrders' },
        meta: {
          title: '柠檬私厨',
          menu: {
            name: '柠檬私厨',
            group: 'content',
            icon: markRaw(RiRestaurant2Line),
            priority: 40,
          },
        },
        children: [
          {
            path: 'dishes',
            name: 'DishesDishes',
            component: () => import(/* webpackChunkName: "DishesView" */ './views/DishesView.vue'),
            meta: {
              title: '菜品管理',
              searchable: true,
              menu: {
                name: '菜品管理',
                icon: markRaw(RiRestaurant2Line),
                priority: 2,
              },
            },
          },
          {
            path: 'orders',
            name: 'DishesOrders',
            component: () => import(/* webpackChunkName: "OrdersView" */ './views/OrdersView.vue'),
            meta: {
              title: '厨房工作台',
              searchable: true,
              menu: {
                name: '厨房工作台',
                icon: markRaw(RiFileList3Line),
                priority: 1,
              },
            },
          },
          {
            path: 'settings',
            name: 'DishesSettings',
            component: () =>
              import(/* webpackChunkName: "SettingsView" */ './views/SettingsView.vue'),
            meta: {
              title: '插件设置',
              searchable: true,
              menu: {
                name: '插件设置',
                icon: markRaw(RiSettings3Line),
                priority: 3,
              },
            },
          },
        ],
      },
    },
  ],
  extensionPoints: {},
})
