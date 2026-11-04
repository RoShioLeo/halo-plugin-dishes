/** 与 SiteRouter / dishes.html 中 data-dishes-* 及默认文案保持一致 */

const DEFAULT_SITE_TITLE = '柠檬私厨'
const DEFAULT_BRAND_TITLE = '柠檬私厨'
const DEFAULT_BRAND_SUBTITLE = '今天，想吃点什么？'

function readRootDataset(): DOMStringMap | undefined {
  try {
    return document.getElementById('app')?.dataset
  } catch {
    return undefined
  }
}

function pick(raw: string | undefined, fallback: string): string {
  const s = raw == null ? '' : String(raw).trim()
  return s === '' ? fallback : s
}

/** 浏览器标签标题（站点级）；路由切换时在 main.ts 中与 meta.title 组合 */
export function getPublicSiteTitle(): string {
  return pick(readRootDataset()?.dishesSiteTitle, DEFAULT_SITE_TITLE)
}

export function getPublicBrandTitle(): string {
  return pick(readRootDataset()?.dishesBrandTitle, DEFAULT_BRAND_TITLE)
}

export function getPublicBrandSubtitle(): string {
  return pick(readRootDataset()?.dishesBrandSubtitle, DEFAULT_BRAND_SUBTITLE)
}
