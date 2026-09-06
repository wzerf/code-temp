import { useState, useEffect, useRef } from 'react';
import { message } from '@/core/feedback/message';
import { RouterProvider } from 'react-router-dom';

import { createAccessibleRouter, type AccessibleRouterResult } from '@/core/router/factory';
import { useAuthStore, useAccessRefreshStore, useUserStore } from '@/stores';
import { useAuth } from '@/hooks/useAuth';
import { getAccessStatic } from '@/core/access';
import { fetchAllDictEntries } from '@/hooks/useDictCache';
import { usePreferencesStore } from '@/core/preferences/store';
import { getAllMenusApi } from '@/api/rest/menu';
import type { MenuItem } from '@/api/rest/types';
import {
  clearAccessMenusCache,
  loadAccessMenusCache,
  saveAccessMenusCache } from '@/utils/menu-cache';

import { Forbidden } from '@/pages/core/error';
import type { AppRouteObject, ComponentRecordType } from '@/core/router';
import MainLayout from '@/layouts/MainLayout';
import { LayoutChildrenProvider } from '@/layouts/MainLayout/context/LayoutChildrenContext';
import { AuthGuard } from '@/router/guards';
import Loading from '@/components/common/Loading';

import {
  allRoutes,
  filterMenusByPageMap,
  pageMap } from './routes-config';

// 布局组件映射（后端 component 字段 → React 组件）
// 后端模式：业务路由挂在静态 MainLayout 下；BasicLayout 仍可映射到同一壳
const AuthenticatedLayout = () => (
  <AuthGuard>
    <MainLayout />
  </AuthGuard>
);

const layoutMap: ComponentRecordType = {
  BasicLayout: AuthenticatedLayout,
};

// 静态 fallback：frontend 模式 / 未认证时使用
const staticLayoutChildren =
  allRoutes.find((route) => route.path === '/' && route.children)?.children ?? [];

export const AppRouter = () => {
  const [router, setRouter] = useState<AccessibleRouterResult['router'] | null>(null);
  const [menuRoutes, setMenuRoutes] = useState<AppRouteObject[]>(staticLayoutChildren);
  const [loading, setLoading] = useState(true);

  const accessToken = useAuthStore((s) => s.accessToken);
  const accessMode = usePreferencesStore((s) => s.preferences.app.accessMode);
  const accessVersion = useAccessRefreshStore((s) => s.version);
  // getUserPermissionCodes 内部用 useCallback 稳定，但 effect 不应依赖它
  // （auth 流程触发会调它，已在 useAuthStore 中反映）
  const { getUserPermissionCodes, fetchAccessCodes } = useAuth();
  const getUserPermissionCodesRef = useRef(getUserPermissionCodes);
  const fetchAccessCodesRef = useRef(fetchAccessCodes);
  useEffect(() => {
    getUserPermissionCodesRef.current = getUserPermissionCodes;
    fetchAccessCodesRef.current = fetchAccessCodes;
  }, [getUserPermissionCodes, fetchAccessCodes]);

  const isAuthenticated = !!accessToken;

  useEffect(() => {
    let cancelled = false;

    const initRouter = async () => {
      setLoading(true);

      try {
        // ========== 已认证时的初始化流程 ==========
        // 对齐 Vue 版 setupAccessGuard：权限码获取 + 字典预加载
        let authStillValid = isAuthenticated;
        if (isAuthenticated) {
          try {
            // 1. 获取用户权限码（角色 + 权限码）
            // accessVersion>0 时强制重拉 codes（菜单/角色变更后）
            if (accessVersion > 0) {
              const codes = await fetchAccessCodesRef.current();
              useUserStore.getState().setAccessCodes(codes);
              await getUserPermissionCodesRef.current();
            } else {
              await getUserPermissionCodesRef.current();
            }

            // 2. 预加载字典数据（部分页面依赖字典，未预加载会导致闪烁）
            await fetchAllDictEntries();

            // 初始化期间 401 会 forceLogout；以 store 为准
            authStillValid = !!useAuthStore.getState().accessToken;
          } catch (authErr) {
            // 认证失败（token 过期/无效）：forceLogout 已在拦截器中被调用
            console.warn('Auth initialization failed, will redirect to login:', authErr);
            useUserStore.getState().$reset();
            authStillValid = !!useAuthStore.getState().accessToken;

            if (cancelled) return;
          }
        }

        // await 之后，通过 useAccess 获取最新合并权限（角色码 + 权限码）
        const freshPermissions = getAccessStatic().getAllPermissions();

        // token 已失效：用 frontend 静态路由建树，AuthGuard 会送去登录，避免 backend 空菜单 + 404
        const effectiveMode = authStillValid ? accessMode : 'frontend';

        // 无论认证是否成功，都生成路由（未认证时 permissions 为空，AuthGuard 会拦截）
        const accessible = await createAccessibleRouter(effectiveMode, {
          routes: allRoutes,
          permissions: freshPermissions,
          forbiddenElement: <Forbidden />,
          fetchMenuListAsync: async () => {
            // 无 token 时不要打菜单接口（会 401 再 forceLogout）
            const token = useAuthStore.getState().accessToken;
            if (!token) {
              return [];
            }
            try {
              const items = await getAllMenusApi();
              const filtered = filterMenusByPageMap(items ?? []);
              saveAccessMenusCache(token, filtered);
              return filtered;
            } catch (menuErr) {
              // 已登录 + 本 token 有缓存：降级渲染；新登录无缓存：抛出由外层阻断
              const cached = loadAccessMenusCache<MenuItem>(token);
              // null = 从未成功写过（新登录）；数组（含空）= 本 token 曾成功拉过
              if (cached) {
                message.warning('菜单加载失败，已使用本地缓存');
                console.warn(
                  'fetchMenuListAsync failed, fallback to cached menus:',
                  menuErr,
                );
                return filterMenusByPageMap(cached);
              }
              console.warn('fetchMenuListAsync failed, no cache:', menuErr);
              throw menuErr;
            }
          },
          layoutMap,
          pageMap,
          autoInjectRedirect: true,
          autoSort: true,
        });

        if (!cancelled) {
          setRouter(accessible.router);
          // backend：仅使用 API/缓存菜单，禁止用 static 全量 business 冒充
          if (effectiveMode === 'backend') {
            setMenuRoutes(accessible.menuRoutes);
          } else {
            setMenuRoutes(
              accessible.menuRoutes.length > 0
                ? accessible.menuRoutes
                : staticLayoutChildren,
            );
          }
        }
      } catch (err) {
        console.error('Router init failed:', err);
        // 菜单加载失败且无本 token 缓存：禁止进入，清会话并回登录态路由
        if (useAuthStore.getState().accessToken) {
          message.error('菜单加载失败，请重新登录');
          clearAccessMenusCache();
          useAuthStore.getState().forceLogout();
          // forceLogout 会触发 effect 重跑（isAuthenticated=false）
          return;
        }
        if (!cancelled) {
          try {
            const guest = await createAccessibleRouter('frontend', {
              routes: allRoutes,
              permissions: [],
              forbiddenElement: <Forbidden />,
              layoutMap,
              pageMap,
              autoInjectRedirect: true,
              autoSort: true,
            });
            setRouter(guest.router);
            setMenuRoutes(staticLayoutChildren);
          } catch (guestErr) {
            console.error('Guest router init failed:', guestErr);
          }
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    initRouter();

    // cleanup：当 effect 重新触发时（isAuthenticated / accessMode / accessVersion），取消上一次
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, accessMode, accessVersion]);

  if (loading || !router)
    return <Loading fullScreen text="初始化中" subText="正在加载路由配置..." />;

  return (
    <LayoutChildrenProvider value={menuRoutes}>
      <RouterProvider
        router={router}
        future={{ v7_startTransition: true }}
      />
    </LayoutChildrenProvider>
  );
};
