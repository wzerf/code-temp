import { Navigate } from 'react-router-dom';

import { type AppRouteObject } from '@/core/router';
import { AuthGuard } from '@/router/guards';
import MainLayout from '@/layouts/MainLayout';
import RouteErrorFallback from '@/layouts/components/ErrorFallback/RouteErrorFallback';
import { NotFound } from '@/pages/core/error';
import McpOauthCallbackPage from '@/pages/app/system/mcp/oauth-callback';

/**
 * 静态基础路由配置
 */
export const staticRoutes: AppRouteObject[] = [
  // 主布局容器（所有需要登录的页面都在这里）
  // 直接使用 '/' 作为路径，业务路由会作为其子路由
  {
    path: '/',
    element: (
      <AuthGuard>
        <MainLayout />
      </AuthGuard>
    ),
    // 子路由未匹配 / loader 抛错时避免 RR 默认白屏（Unexpected Application Error）
    errorElement: <RouteErrorFallback />,
    meta: {
      hideInMenu: true,
      hideInTab: true,
    },
    children: [
      // 根路径重定向到分析页（对齐 Vue 概览首页）
      {
        path: '/',
        index: true,
        element: <Navigate to="/analytics" replace />,
        meta: { title: 'routes:home', hideInMenu: true, hideInTab: true },
      },
      // MCP OAuth 回调落点（redirect_uri；不在菜单中，仅登录后可达）
      {
        name: 'mcp-oauth-callback',
        path: 'system/mcp/oauth-callback',
        element: <McpOauthCallbackPage />,
        meta: { title: 'mcp:oauthCallbackProcessing', hideInMenu: true, hideInTab: true },
      },
      // 通配 404：必须保留；backend 模式拼菜单时也会被 factory 保留
      {
        path: '*',
        element: <NotFound />,
        meta: {
          title: 'routes:not-found',
          ignoreAccess: true,
          hideInMenu: true,
          hideInTab: true,
        },
      },
    ],
  },
];
