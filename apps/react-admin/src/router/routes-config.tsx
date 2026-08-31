import type { MenuItem } from '@/api/rest/types';
import type { AppRouteObject, ComponentRecordType } from '@/core/router';
import { errorRoutes } from './config/error-routes';
import { authRoutes } from './config/auth';
import { staticRoutes } from './config/static';

/**
 * 过滤掉在 pageMap 中无对应组件的菜单项（mock 返回的 Demos 路径在 react-admin 中不存在）
 */
export function filterMenusByPageMap(items: MenuItem[]): MenuItem[] {
  return items
    .map((m) => {
      const children = m.children ? filterMenusByPageMap(m.children) : undefined;
      const component = typeof m.component === 'string' ? m.component : '';
      const key = normalizePageMapKey(component);
      const hasOwnComponent = !component || (key !== null && key in pageMapRef);
      // 父菜单若没有 component，但有 children，则保留 children
      if (children && children.length > 0) {
        return { ...m, children };
      }
      if (hasOwnComponent) return m;
      return null;
    })
    .filter((m): m is MenuItem => m !== null);
}

export const pageMapRef: ComponentRecordType = {};
export function normalizePageMapKey(component: string): string | null {
  // mock 的 component 形如 "/dashboard/analytics/index" → 标准化为 "/dashboard/analytics"
  if (!component) return null;
  let normalized = component.replace(/^\/+/, '/');
  normalized = normalized.replace(/\/index\.(tsx?|jsx?)?$/, '');
  normalized = normalized.replace(/\.(tsx?|jsx?)$/, '');
  return normalized;
}

// 自动导入 modules 下的所有路由模块（仅包含业务功能路由）
// dashboard + system（含 user/role/dict/i18n/menu/api）+ log + task
const modulesRoutes = import.meta.glob<AppRouteObject[][]>(
  ['./modules/dashboard.tsx', './modules/system.tsx', './modules/log.tsx', './modules/task.tsx'],
  { eager: true },
);

// 提取并展平所有模块路由（这些都是相对路径的业务路由）
export const businessRoutes: AppRouteObject[] = Object.values(modulesRoutes).flatMap((module) => {
  // 模块可能导出 default 或具名导出 (如 dashboardRoutes)
  const routes = (module as { default?: AppRouteObject[] }).default ?? Object.values(module)[0];
  return Array.isArray(routes) ? routes : [];
});

// 页面组件映射：使用 Vite glob 导入所有业务页面（后端模式用）
// 后端返回的 component 路径如 "dashboard/index"，会被标准化后匹配
// 排除已裁剪的目录（保留目录 + 路由不注册），避免被打包进主 bundle
const rawPageModules = import.meta.glob(
  [
    '../pages/app/dashboard/**/*.tsx',
    '../pages/app/system/**/*.tsx',
    '../pages/app/log/**/*.tsx',
    '../pages/app/task/**/*.tsx',
    '../pages/app/agent/**/*.tsx',
  ],
  { eager: true },
);

// 将 glob 返回的模块对象转换为 ComponentType 映射
// glob eager 返回 { '../pages/app/dashboard/index.tsx': { default: Component } }
//
// 关键：pageMap 的键必须与 generate-routes-backend.ts 中 normalizeViewPath 处理后端
// component 后的结果一致。后端 component 如 "dashboard/index" → "/dashboard/index"
// 所以 pageMap 键也应该是 "/dashboard" 格式（去掉 /pages/app 前缀）
export const pageMap: ComponentRecordType = {};
for (const [globPath, module] of Object.entries(rawPageModules)) {
  const mod = module as { default?: React.ComponentType } & Record<string, unknown>;
  const Component = mod?.default || (mod as unknown as React.ComponentType);
  if (typeof Component === 'function') {
    // globPath: "../pages/app/dashboard/index.tsx"
    // 提取 app/ 之后的路径部分
    const appMatch = globPath.match(/(?:pages|views)\/app\/(.+)/);
    if (!appMatch) continue;

    const relativePath = appMatch[1] // "dashboard/index.tsx"
      .replace(/\.tsx$/, '') // "dashboard/index"
      .replace(/\/index$/, ''); // "dashboard"

    // 生成与 normalizeViewPath 一致的键
    const normalizedKey = `/${relativePath}`; // "/dashboard"
    pageMap[normalizedKey] = Component;
    pageMap[`${normalizedKey}/index`] = Component; // "/dashboard/index"
    pageMap[`${normalizedKey}.tsx`] = Component; // "/dashboard.tsx"
    pageMap[`${normalizedKey}/index.tsx`] = Component; // "/dashboard/index.tsx"
    // 同步写入 pageMapRef（供 filterMenusByPageMap 命中判断）
    pageMapRef[normalizedKey] = Component;
    pageMapRef[`${normalizedKey}/index`] = Component;
  }
}

interface BuildAllRoutesInput {
  staticRoutes: AppRouteObject[];
  business: AppRouteObject[];
  auth: AppRouteObject[];
  err: AppRouteObject[];
}

function buildAllRoutes({
  staticRoutes: staticRoutesArr,
  business,
  auth,
  err,
}: BuildAllRoutesInput): AppRouteObject[] {
  return [
    ...staticRoutesArr.map((route) => {
      // 找到主布局容器路由（path 为 '/' 且包含 children）
      if (route.path === '/' && route.children) {
        return {
          ...route,
          children: [...route.children, ...business],
        };
      }
      return route;
    }),
    ...auth, // 认证路由（独立，不受 AuthGuard 保护）
    ...err, // 错误路由放在最后
  ];
}

// 合并路由：将业务模块路由合并到主布局容器的 children 中
export const allRoutes: AppRouteObject[] = buildAllRoutes({
  staticRoutes,
  business: businessRoutes,
  auth: authRoutes,
  err: errorRoutes,
});
