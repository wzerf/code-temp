import { createBrowserRouter, type RouteObject, type RouterProviderProps } from 'react-router-dom';
import { injectRedirects } from './utils/inject-redirect';
import { flattenLayoutAbsoluteChildren } from './utils/flatten-absolute-routes';
import { sortRoutes } from './utils/sort-routes';
import { transformRoutesWithHandle } from './utils/transform-meta-to-handle';
import type { GenerateMenuAndRoutesOptions, AppRoute, AppRouteObject } from './types';
import { generateRoutesByBackend, generateRoutesByFrontend } from '@/core/router/generators';
import type { AccessModeType } from '@/core/preferences';
import React, { createElement } from 'react';
import RouteErrorFallback from '@/layouts/components/ErrorFallback/RouteErrorFallback';

/**
 * 从路由列表中分离出：
 * - layoutRoutes: 包含 MainLayout/AuthGuard 的根路由（path='/'）
 * - staticRoutes: 不受 AuthGuard 保护的静态路由（auth/login/error 等）
 */
function separateRoutes(routes: AppRouteObject[]) {
  const layoutRoutes: AppRouteObject[] = []; // path='/' 的布局路由
  const otherRoutes: AppRouteObject[] = []; // 其他静态路由（auth/error等）

  for (const route of routes) {
    if (route.path === '/' && route.children) {
      layoutRoutes.push(route);
    } else {
      otherRoutes.push(route);
    }
  }

  return { layoutRoutes, otherRoutes };
}

export interface AccessibleRouterResult {
  router: RouterProviderProps['router'];
  /** Routes used to build the sidebar (layout children / backend tree). */
  menuRoutes: AppRouteObject[];
}

export const createAccessibleRouter = async (
  mode: AccessModeType,
  options: GenerateMenuAndRoutesOptions,
): Promise<AccessibleRouterResult> => {
  let routes: AppRouteObject[] = [...options.routes];
  let menuRoutes: AppRouteObject[];

  // 根据模式生成路由
  switch (mode) {
    case 'backend': {
      // 后端模式：从 API 获取业务路由树，挂到静态 MainLayout 下
      if (!options.fetchMenuListAsync) {
        console.warn(
          '[Router] Backend mode requires fetchMenuListAsync, falling back to frontend mode',
        );
        routes = await generateRoutesByFrontend(
          routes,
          options.permissions ?? [],
          options.forbiddenElement,
        );
        menuRoutes = routes.find((route) => route.path === '/' && route.children)?.children ?? [];
      } else {
        const { layoutRoutes, otherRoutes } = separateRoutes(routes);
        const backendRoutes = await generateRoutesByBackend({
          staticRoutes: layoutRoutes,
          mode,
          fetchMenuListAsync: options.fetchMenuListAsync,
          layoutMap: options.layoutMap,
          pageMap: options.pageMap,
        });

        const layout = layoutRoutes[0];
        if (layout) {
          // 保留：index 重定向、通配 404；再挂后端业务树
          // 若漏掉 path:'*'，未匹配子路径会在 data router 下抛 404 且无 errorElement → 默认白屏
          const staticChildren = (layout.children ?? []).filter(
            (child) =>
              child.index ||
              child.path === '/' ||
              !child.path ||
              child.path === '*' ||
              child.meta?.alwaysAvailable === true,
          );
          const splatChildren = staticChildren.filter((c) => c.path === '*');
          const nonSplatStatic = staticChildren.filter((c) => c.path !== '*');

          // backend 空树时不再回退静态 business 全量菜单（密钥错误会“假进入”）
          // 合法空权限用户：sidebar 为空；接口失败应由上层用缓存或阻断
          const businessChildren = backendRoutes;

          routes = [
            {
              ...layout,
              // 确保主布局始终有 errorElement
              errorElement: layout.errorElement,
              // splat 必须在最后
              children: [...nonSplatStatic, ...businessChildren, ...splatChildren],
            },
            ...otherRoutes,
          ];
          menuRoutes = businessChildren;
        } else {
          // No layout shell — fall back to previous behavior.
          routes = [...backendRoutes, ...otherRoutes];
          menuRoutes = backendRoutes;
        }
      }
      break;
    }
    case 'frontend':
    default: {
      // 前端模式：基于静态路由 + 权限过滤
      routes = await generateRoutesByFrontend(
        routes,
        options.permissions ?? [],
        options.forbiddenElement,
      );
      menuRoutes = routes.find((route) => route.path === '/' && route.children)?.children ?? [];
      break;
    }
  }

  if (options.autoInjectRedirect !== false)
    routes = injectRedirects(routes as unknown as AppRoute[]) as unknown as AppRouteObject[];
  if (options.autoSort !== false)
    routes = sortRoutes(routes as unknown as AppRoute[]) as unknown as AppRouteObject[];

  // RR6: absolute children like /analytics under /dashboard are illegal.
  // Menus keep the nested tree; only the router tree is flattened.
  routes = flattenLayoutAbsoluteChildren(routes);

  // 将 meta 转换为 handle，使 useMatches() 能获取路由元数据
  routes = transformRoutesWithHandle(routes);

  // Keep menuRoutes nested for sidebar grouping; only light post-process.
  if (options.autoInjectRedirect !== false) {
    menuRoutes = injectRedirects(
      menuRoutes as unknown as AppRoute[],
    ) as unknown as AppRouteObject[];
  }
  if (options.autoSort !== false) {
    menuRoutes = sortRoutes(menuRoutes as unknown as AppRoute[]) as unknown as AppRouteObject[];
  }
  menuRoutes = transformRoutesWithHandle(menuRoutes);

  // 顶层路由缺 errorElement 时补上，避免 RR 默认 “Unexpected Application Error”
  const withErrorBoundary = routes.map((route) =>
    route.errorElement
      ? route
      : {
          ...route,
          errorElement: createElement(RouteErrorFallback),
        },
  );

  return {
    router: createBrowserRouter(withErrorBoundary as RouteObject[], {
      future: {
        v7_relativeSplatPath: true,
      },
    }),
    menuRoutes,
  };
};

/**
 * 根据模式生成路由
 */
export async function generateRoutes(
  mode: AccessModeType,
  options: {
    routes: AppRouteObject[];
    permissions: string[];
    roles: string[];
    forbiddenElement?: React.ReactNode;
    fetchMenuListAsync?: () => Promise<unknown[]>;
    layoutMap?: Record<string, React.ComponentType<unknown>>;
    pageMap?: Record<string, React.ComponentType<unknown>>;
  },
): Promise<AppRouteObject[]> {
  const { routes, permissions, forbiddenElement, fetchMenuListAsync, layoutMap, pageMap } = options;

  switch (mode) {
    case 'backend': {
      // 后端模式：从接口获取菜单树，动态转换组件
      if (!fetchMenuListAsync) {
        throw new Error('Backend mode requires fetchMenuListAsync');
      }
      return generateRoutesByBackend({
        staticRoutes: routes,
        mode,
        fetchMenuListAsync,
        layoutMap,
        pageMap,
      });
    }
    case 'frontend': {
      // 前端模式：基于静态路由 + 权限过滤
      return generateRoutesByFrontend(routes, permissions, forbiddenElement);
    }
    default: {
      // 未知模式视为前端模式（fallback）
      return generateRoutesByFrontend(routes, permissions, forbiddenElement);
    }
  }
}
