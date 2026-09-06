import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Input, Modal } from 'antd';
import type { InputRef } from 'antd';
import {
  CloseOutlined,
  EnterOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

import type { AppMenu } from '@/core/router/types';
import { useI18n } from '@/core/i18n';
import { usePreferencesStore } from '@/core/preferences/store';
import { getIconFromName } from '@/layouts/MainLayout/utils/iconResolver';
import { isHttpUrl } from '@/utils/inference';

const SEARCH_HISTORY_KEY = `__search-history-${typeof location !== 'undefined' ? location.hostname : 'app'}__`;
const MAX_HISTORY = 10;

/** 菜单搜索条目（扁平化后的可导航项） */
export interface SearchMenuItem {
  path: string;
  name: string;
  icon?: string;
}

/** 侧栏菜单节点（与 transformRoutesToMenu 输出对齐，字段可选） */
export type SearchableMenu = Partial<AppMenu> & {
  path?: string;
  name?: string;
  label?: string;
  icon?: string;
  children?: SearchableMenu[];
};

interface GlobalSearchProps {
  menus: SearchableMenu[];
  isDark?: boolean;
}

/** 正则特殊字符转义 */
const REGEX_SPECIAL = new Set(['$', '(', ')', '*', '+', '.', '?', '[', '\\', ']', '^', '{', '|', '}']);

function escapeRegexChar(c: string): string {
  return REGEX_SPECIAL.has(c) ? `\\${c}` : c;
}

/** 对齐 Vue SearchPanel：字符级模糊匹配 a.*b.*c */
function createSearchReg(key: string): RegExp {
  const keys = [...key].map(escapeRegexChar).join('.*');
  return new RegExp(`.*${keys}.*`, 'i');
}

function loadHistory(): SearchMenuItem[] {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as SearchMenuItem[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveHistory(items: SearchMenuItem[]) {
  try {
    localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(items.slice(0, MAX_HISTORY)));
  } catch {
    // ignore quota / private mode
  }
}

function uniqueByPath(items: SearchMenuItem[]): SearchMenuItem[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (!item.path || seen.has(item.path)) return false;
    seen.add(item.path);
    return true;
  });
}

/**
 * 全局菜单搜索：按关键字过滤侧栏菜单并跳转。
 * 对齐 Vue GlobalSearch + SearchPanel 行为。
 */
export function GlobalSearch({ menus, isDark = false }: GlobalSearchProps) {
  const { t } = useI18n('common');
  const { t: tRoutes } = useTranslation('routes');
  const navigate = useNavigate();
  const inputRef = useRef<InputRef>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const shortcutKeys = usePreferencesStore((s) => s.preferences.shortcutKeys);
  const enableShortcut =
    (shortcutKeys?.enable ?? true) && (shortcutKeys?.globalSearch ?? true);

  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const [history, setHistory] = useState<SearchMenuItem[]>(() => loadHistory());
  const isNavigatingRef = useRef(false);

  const translateLabel = useCallback(
    (label: string | undefined): string => {
      if (!label) return '';
      if (label.startsWith('menu:') || label.startsWith('routes:')) {
        const keyName = label.substring(label.indexOf(':') + 1);
        return tRoutes(keyName, { defaultValue: label });
      }
      if (label.includes('.')) {
        const translated = tRoutes(label, { defaultValue: '' });
        if (translated) return translated;
      }
      return tRoutes(label, { defaultValue: label });
    },
    [tRoutes],
  );

  /** 扁平化菜单树为可搜索项（只保留有 path 的节点） */
  const searchItems = useMemo(() => {
    const items: SearchMenuItem[] = [];
    const walk = (nodes: SearchableMenu[]) => {
      for (const item of nodes) {
        const rawName = item.name || item.label;
        if (item.path && rawName) {
          items.push({
            path: item.path,
            name: translateLabel(rawName),
            icon: typeof item.icon === 'string' ? item.icon : undefined,
          });
        }
        if (item.children?.length) {
          walk(item.children);
        }
      }
    };
    walk(menus);
    return uniqueByPath(items);
  }, [menus, translateLabel]);

  const results = useMemo(() => {
    const key = keyword.trim();
    if (!key) {
      return uniqueByPath(history);
    }
    const reg = createSearchReg(key.toLowerCase());
    return searchItems.filter((item) => reg.test(item.name.toLowerCase()));
  }, [keyword, history, searchItems]);

  const close = useCallback(() => {
    setOpen(false);
    setKeyword('');
    setActiveIndex(0);
  }, []);

  const openModal = useCallback(() => {
    setOpen(true);
    setKeyword('');
    setActiveIndex(0);
    setHistory(loadHistory());
  }, []);

  const onKeywordChange = useCallback((value: string) => {
    setKeyword(value);
    setActiveIndex(0);
  }, []);

  const pushHistory = useCallback((item: SearchMenuItem) => {
    setHistory((prev) => {
      const next = uniqueByPath([item, ...prev]).slice(0, MAX_HISTORY);
      saveHistory(next);
      return next;
    });
  }, []);

  const handleSelect = useCallback(
    async (item: SearchMenuItem | undefined) => {
      if (!item?.path) return;
      pushHistory(item);
      close();
      // 等弹层关闭后再跳转，避免焦点/滚动异常
      await Promise.resolve();
      if (isHttpUrl(item.path)) {
        window.open(item.path, '_blank');
      } else {
        navigate(item.path, { replace: true });
      }
    },
    [close, navigate, pushHistory],
  );

  /** 仅删除「最近搜索」历史项 */
  const removeHistoryItem = useCallback((index: number) => {
    setHistory((prev) => {
      const next = prev.filter((_, i) => i !== index);
      saveHistory(next);
      return next;
    });
    setActiveIndex((i) => Math.max(0, i - 1));
  }, []);

  const scrollActiveIntoView = useCallback(() => {
    const el = listRef.current?.querySelector(`[data-search-item="${activeIndex}"]`);
    el?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex]);

  // 打开后聚焦输入框
  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => {
      inputRef.current?.focus();
    }, 50);
    return () => window.clearTimeout(timer);
  }, [open]);

  // 快捷键 ⌘/Ctrl+K
  useEffect(() => {
    if (!enableShortcut) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key?.toLowerCase() === 'k' && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setOpen((prev) => {
          if (prev) {
            setKeyword('');
            setActiveIndex(0);
            return false;
          }
          setHistory(loadHistory());
          return true;
        });
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enableShortcut]);

  // 弹层内键盘导航
  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close();
        return;
      }
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        isNavigatingRef.current = true;
        setActiveIndex((i) => {
          if (results.length === 0) return 0;
          return i >= results.length - 1 ? 0 : i + 1;
        });
        return;
      }
      if (event.key === 'ArrowUp') {
        event.preventDefault();
        isNavigatingRef.current = true;
        setActiveIndex((i) => {
          if (results.length === 0) return 0;
          return i <= 0 ? results.length - 1 : i - 1;
        });
        return;
      }
      if (event.key === 'Enter') {
        event.preventDefault();
        void handleSelect(results[activeIndex]);
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, close, results, activeIndex, handleSelect]);

  useEffect(() => {
    if (open) scrollActiveIntoView();
  }, [activeIndex, open, scrollActiveIntoView]);

  useEffect(() => {
    const onMouseMove = () => {
      isNavigatingRef.current = false;
    };
    window.addEventListener('mousemove', onMouseMove);
    return () => window.removeEventListener('mousemove', onMouseMove);
  }, []);

  const isWin = typeof navigator !== 'undefined' && /Win/i.test(navigator.platform);
  const shortcutLabel = isWin ? 'Ctrl K' : '⌘ K';

  const accentBg = isDark ? '#1f1f1f' : '#f5f5f5';
  const activeBg = isDark ? '#1668dc' : '#1677ff';
  const borderColor = isDark ? '#404040' : '#d9d9d9';
  const muted = isDark ? '#a6a6a6' : '#8c8c8c';
  const textColor = isDark ? '#ffffff' : '#262626';

  return (
    <>
      <div
        className="search-trigger-btn"
        role="button"
        tabIndex={0}
        onClick={openModal}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openModal();
          }
        }}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '6px 12px',
          borderRadius: 20,
          backgroundColor: isDark ? '#2a2a2a' : '#f5f5f5',
          border: `1px solid ${borderColor}`,
          cursor: 'pointer',
          transition: 'all 0.2s',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.backgroundColor = isDark ? '#363636' : '#e8e8e8';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.backgroundColor = isDark ? '#2a2a2a' : '#f5f5f5';
        }}
      >
        <SearchOutlined style={{ color: muted, fontSize: 14 }} />
        <span style={{ color: muted, fontSize: 13 }}>{t('header.search')}</span>
        {enableShortcut && (
          <kbd
            style={{
              display: 'inline-block',
              padding: '2px 6px',
              fontSize: 11,
              fontFamily: 'monospace',
              lineHeight: 1.4,
              color: isDark ? '#8c8c8c' : '#595959',
              backgroundColor: isDark ? '#1f1f1f' : '#e8e8e8',
              border: `1px solid ${borderColor}`,
              borderRadius: 3,
            }}
          >
            {t('header.searchShortcut', { defaultValue: shortcutLabel })}
          </kbd>
        )}
      </div>

      <Modal
        open={open}
        onCancel={close}
        footer={
          <div
            style={{
              display: 'flex',
              gap: 16,
              fontSize: 12,
              color: muted,
              justifyContent: 'flex-start',
            }}
          >
            <span>
              <EnterOutlined style={{ marginRight: 4 }} />
              {t('header.searchSelect')}
            </span>
            <span>↑↓ {t('header.searchNavigate')}</span>
            <span>Esc {t('header.searchClose')}</span>
          </div>
        }
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <SearchOutlined style={{ color: muted }} />
            <Input
              ref={inputRef}
              variant="borderless"
              value={keyword}
              onChange={(e) => onKeywordChange(e.target.value)}
              placeholder={t('header.searchPlaceholder')}
              autoFocus
              style={{ padding: 0, fontSize: 15 }}
            />
          </div>
        }
        width={560}
        destroyOnHidden
        styles={{
          body: { paddingTop: 8, maxHeight: 420, overflow: 'auto' },
        }}
      >
        {/* 有关键字但无结果 */}
        {keyword.trim() && results.length === 0 && (
          <div style={{ textAlign: 'center', color: muted, padding: '32px 0' }}>
            <SearchOutlined style={{ fontSize: 36, marginBottom: 12 }} />
            <p style={{ margin: 0, fontSize: 13 }}>
              {t('header.searchNoResults')}{' '}
              <span style={{ color: textColor, fontWeight: 500 }}>&quot;{keyword.trim()}&quot;</span>
            </p>
          </div>
        )}

        {/* 无关键字且无历史 */}
        {!keyword.trim() && results.length === 0 && (
          <div style={{ textAlign: 'center', color: muted, padding: '40px 0' }}>
            <p style={{ margin: 0, fontSize: 13 }}>{t('header.searchNoRecent')}</p>
          </div>
        )}

        {results.length > 0 && (
          <ul ref={listRef} style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {!keyword.trim() && history.length > 0 && (
              <li style={{ fontSize: 12, color: muted, marginBottom: 8 }}>{t('header.searchRecent')}</li>
            )}
            {results.map((item, index) => {
              const active = index === activeIndex;
              return (
                <li
                  key={item.path}
                  data-search-item={index}
                  data-index={index}
                  onClick={() => void handleSelect(item)}
                  onMouseEnter={(e) => {
                    if (isNavigatingRef.current) return;
                    const idx = Number((e.currentTarget as HTMLElement).dataset.index);
                    if (!Number.isNaN(idx)) setActiveIndex(idx);
                  }}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    padding: '12px 14px',
                    marginBottom: 8,
                    borderRadius: 8,
                    cursor: 'pointer',
                    backgroundColor: active ? activeBg : accentBg,
                    color: active ? '#fff' : textColor,
                    transition: 'background-color 0.15s',
                  }}
                >
                  <span style={{ display: 'inline-flex', fontSize: 16, flexShrink: 0 }}>
                    {getIconFromName(item.icon) ?? <SearchOutlined />}
                  </span>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {item.name}
                  </span>
                  {!keyword.trim() && (
                    <CloseOutlined
                      onClick={(e) => {
                        e.stopPropagation();
                        removeHistoryItem(index);
                      }}
                      style={{ fontSize: 12, opacity: 0.7 }}
                    />
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Modal>
    </>
  );
}

export default GlobalSearch;
