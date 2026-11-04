<div align="center">
  <img alt="LemonSlice 柠檬切片" width="80" src="./web-frontend/src/assets/lemon.svg">
  <h1>柠檬私厨 · LemonSlice Dishes</h1>
  <p>为 LemonSlice Cloud 改造的 Halo 家庭点菜插件。</p>
</div>

本项目是 [bai / Halo Dishes](https://github.com/Avrinbai/halo-plugin-dishes) 的公开 fork，
基于上游 `1.0.3` / `ddb7a9f524323bf7efd3da90d056af369d04f071`，
由 RoShioLeo 于 **2026-11-05** 修改，继续使用 **GPL-3.0**。
原作者署名、许可证和 Git 历史均予以保留。

## LemonSlice 改造

- 使用本站柠檬图标、系统无衬线字体、白色留白、细分隔线和柠檬金。
- 支持浅色、深色和跟随系统，与博客共用 `lemonslice-color-mode` 外观设置。
- 前台桌面布局扩展到 1024px；手机保留便于操作的点菜、预约和提交入口。
- 后台保持 Halo 管理布局和表单，统一容器、间距、菜单名称及设置页标签。
- 移除对原站字体服务的依赖；在 JAR 内附上许可证和修改说明。
- 修复 Halo 2.27.2 中插件密码错误被显示成服务器错误的问题。

保留菜品分类、菜品管理、推荐、早餐/午餐/晚餐、预约、备注、点菜记录、
访问密码、域名来源配置、企业微信通知以及原有 ZIP 数据导入导出功能。
详细修改见 [CHANGELOG.md](./CHANGELOG.md)，原版文档见
[docs/UPSTREAM_README.md](./docs/UPSTREAM_README.md)。

## 安装

要求 **Halo >= 2.23.0**、Java 21 或对应 Halo 支持的运行环境。

1. 从[本 fork 的 Releases](https://github.com/RoShioLeo/halo-plugin-dishes/releases)
   下载 `plugin-dishes-1.0.4-lemonslice.1.jar`。
2. 在 Halo 后台「插件」上传 JAR，并启用“柠檬私厨”。
3. 在插件菜单中创建分类和菜品，按需设置访问模式、显示文案和通知。
4. 默认前台地址是站点的 `/dishes`，也可在插件设置里修改。

插件 ID 继续使用 **`dishes`**，API 路径和数据类型保持上游兼容。
**不能与原版同时安装**；原版已有用户应按 Halo 插件升级流程切换。
上游 Halo 应用市场页面提供的是原版，不是本 fork。

前台访问密码沿用上游的家庭共享密码机制；来源域名配置不代替账号鉴权。
后台管理接口使用 Halo 的管理员权限。

## 从源码构建

使用 **JDK 21、Node.js 24、pnpm 10.12.4**；Gradle 9.4.1 由仓库 wrapper
下载并校验 SHA256。两个前端的依赖锁文件都已保留。

```sh
corepack enable
corepack prepare pnpm@10.12.4 --activate
./gradlew --no-daemon clean build
```

Windows 使用 `gradlew.bat --no-daemon clean build`。
产物位于 `build/libs/plugin-dishes-1.0.4-lemonslice.1.jar`。
构建包含前台和 Console 的 TypeScript 检查、Vite 打包及现有 Java 测试。

公开前台单独开发：

```sh
cd web-frontend
pnpm install --frozen-lockfile
pnpm dev
```

开发代理默认指向 `http://127.0.0.1:8090`；插件嵌入页会由 Halo 注入前台路由
base。独立部署的配置约定保留上游方案，参见
[原作者部署文档](https://avrinbai.cn/docs/dishes/)。

## 许可与来源

原作者 **bai**：[avrinbai.cn](https://avrinbai.cn)，
上游仓库：[Avrinbai/halo-plugin-dishes](https://github.com/Avrinbai/halo-plugin-dishes)。

本修改版继续按 [GNU GPL-3.0](./LICENSE) 发布，不附带担保。
JAR 中包含 `META-INF/LICENSE`、`META-INF/NOTICE.md`、`META-INF/CHANGELOG.md`
及 [第三方依赖许可](./THIRD_PARTY_NOTICES.md)。
发布版本提供对应 Git tag 和完整源码包。版权及修改记录见 [NOTICE.md](./NOTICE.md)。
