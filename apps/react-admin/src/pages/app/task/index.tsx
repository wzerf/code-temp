/* eslint-disable react-hooks/set-state-in-effect --
 * 与 log 审计双 Tab 页一致：visitedTabs / panelReady 在 activeKey 变化时同步，
 * 用于 keep-alive 与切换过渡，属 UI 编排而非外部系统副作用。
 */
import { useEffect, useMemo, useRef, useState, useTransition } from 'react';
import { Spin, Tabs } from 'antd';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { useAccess } from '@/core/access';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import Forbidden from '@/pages/core/error/403';
import { TaskConfigPanel } from './config';
import { TaskExecutionPanel } from './execution';
import './task.css';

/** Tab 与 URL query、权限码映射 */
const TASK_TAB_DEFS = [
  {
    key: 'config',
    labelKey: 'tabConfig',
    permissionCode: 'task:config:list',
  },
  {
    key: 'execution',
    labelKey: 'tabExecution',
    permissionCode: 'task:execution:list',
  },
] as const;

type TaskTabKey = (typeof TASK_TAB_DEFS)[number]['key'];

function isTaskTabKey(value: string | null): value is TaskTabKey {
  return TASK_TAB_DEFS.some((t) => t.key === value);
}

function renderTaskTabPanel(key: TaskTabKey) {
  if (key === 'config') return <TaskConfigPanel />;
  return <TaskExecutionPanel />;
}

/**
 * 任务调度统一页：侧栏一级菜单进入，页顶 Tab 切换任务配置 / 执行记录。
 * URL：/task?tab=config|execution
 *
 * 体验：已访问 Tab 缓存不销毁；切换时用 Spin 过渡。
 */
const TaskSchedulePage = () => {
  const { t } = useTranslation('task');
  const [searchParams, setSearchParams] = useSearchParams();
  const { hasAccessByCodes } = useAccess();
  const [isPending, startTransition] = useTransition();
  const [panelReady, setPanelReady] = useState(true);
  const readyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [visitedTabs, setVisitedTabs] = useState<Set<TaskTabKey>>(() => new Set());

  const allowedTabs = useMemo(
    () => TASK_TAB_DEFS.filter((tab) => hasAccessByCodes([tab.permissionCode])),
    [hasAccessByCodes],
  );

  const allowedKeys = useMemo(() => allowedTabs.map((tab) => tab.key), [allowedTabs]);

  const requestedTab = searchParams.get('tab');
  const activeKey: TaskTabKey | undefined = useMemo(() => {
    if (allowedKeys.length === 0) return undefined;
    if (isTaskTabKey(requestedTab) && allowedKeys.includes(requestedTab)) {
      return requestedTab;
    }
    if (allowedKeys.includes('config')) return 'config';
    return allowedKeys[0];
  }, [allowedKeys, requestedTab]);

  const effectiveVisited = useMemo(() => {
    const next = new Set(visitedTabs);
    if (activeKey) next.add(activeKey);
    return next;
  }, [visitedTabs, activeKey]);

  useEffect(() => {
    if (!activeKey) return;
    setVisitedTabs((prev) => {
      if (prev.has(activeKey)) return prev;
      const next = new Set(prev);
      next.add(activeKey);
      return next;
    });
  }, [activeKey]);

  // 校正非法 / 无权限的 tab 参数
  useEffect(() => {
    if (!activeKey) return;
    if (requestedTab === activeKey) return;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set('tab', activeKey);
        return next;
      },
      { replace: true },
    );
  }, [activeKey, requestedTab, setSearchParams]);

  // 切换 Tab 时短暂 loading
  useEffect(() => {
    if (!activeKey) return;
    if (readyTimerRef.current) {
      clearTimeout(readyTimerRef.current);
      readyTimerRef.current = null;
    }
    setPanelReady(false);
    readyTimerRef.current = setTimeout(() => {
      setPanelReady(true);
      readyTimerRef.current = null;
    }, 120);
    return () => {
      if (readyTimerRef.current) {
        clearTimeout(readyTimerRef.current);
        readyTimerRef.current = null;
      }
    };
  }, [activeKey]);

  const showLoading = isPending || !panelReady;

  const switchTab = (key: string) => {
    if (!isTaskTabKey(key) || key === activeKey) return;
    setPanelReady(false);
    setVisitedTabs((prev) => {
      if (prev.has(key)) return prev;
      const next = new Set(prev);
      next.add(key);
      return next;
    });
    startTransition(() => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('tab', key);
          return next;
        },
        { replace: true },
      );
    });
  };

  if (allowedTabs.length === 0 || !activeKey) {
    return <Forbidden />;
  }

  return (
    <ContentContainer className="task-page">
      <Tabs
        activeKey={activeKey}
        onChange={switchTab}
        items={allowedTabs.map((tab) => {
          const mounted = effectiveVisited.has(tab.key);
          return {
            key: tab.key,
            label: t(tab.labelKey),
            forceRender: mounted,
            children: mounted ? (
              <div className="task-tab-pane">
                {activeKey === tab.key && showLoading ? (
                  <div
                    style={{
                      position: 'absolute',
                      inset: 0,
                      zIndex: 2,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      background: 'var(--ant-color-bg-container, rgba(255,255,255,0.55))',
                    }}
                  >
                    <Spin tip={t('loading')} />
                  </div>
                ) : null}
                <div
                  style={{
                    visibility: activeKey === tab.key && showLoading ? 'hidden' : 'visible',
                    flex: 1,
                    minHeight: 0,
                    overflow: 'hidden',
                    display: 'flex',
                    flexDirection: 'column',
                    position: 'relative',
                  }}
                >
                  {renderTaskTabPanel(tab.key)}
                </div>
              </div>
            ) : null,
          };
        })}
      />
    </ContentContainer>
  );
};

export default TaskSchedulePage;
