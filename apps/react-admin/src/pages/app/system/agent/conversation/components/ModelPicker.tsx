import { useEffect, useMemo, useState } from 'react';
import { Select, Spin, Tag, Tooltip } from 'antd';
import { useTranslation } from 'react-i18next';
import { listModelAvailableApi } from '@/api/rest/model';
import type { ModelRelease } from '@/api/rest/types';
import { getApiErrorMessage } from '../../../blacklist/modules/error-message';

interface Props {
  sessionId: number | null;
  /** 会话当前已记住的模型 releaseId（无则 null → 回落 Revision 默认） */
  value: number | null;
  onChange: (releaseId: number | null) => Promise<void>;
  /** 当前是否可用（无会话/正在请求时禁用） */
  disabled?: boolean;
  /** 外部加载态（如切换会话读取绑定中） */
  loading?: boolean;
}

/** 解析 capabilities JSON（ModelRelease.capabilities 为 JSON 字符串） */
function parseCaps(capabilities?: string | null): string[] {
  if (!capabilities) return [];
  try {
    const parsed = JSON.parse(capabilities) as unknown;
    if (Array.isArray(parsed)) return parsed.map(String);
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed as Record<string, unknown>)
        .filter(([, v]) => v === true)
        .map(([k]) => k);
    }
    return [];
  } catch {
    return [];
  }
}

const CAP_LABEL: Record<string, string> = {
  text: '文本',
  thinking: '推理',
  tool_use: '工具',
  vision: '视觉',
  json_mode: 'JSON',
};

/**
 * ModelPicker：会话内自由选模型。
 * 候选 = 官方 PUBLISHED ∪ 本人私有；选择写入会话（model-binding），后端记住下次复用。
 */
export default function ModelPicker({ sessionId, value, onChange, disabled, loading: externalLoading }: Props) {
  const { t } = useTranslation('agent-conversation');
  const [options, setOptions] = useState<ModelRelease[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  // 会话切换时重置候选（渲染期调整，避免 effect 内同步 setState）
  const [prevSession, setPrevSession] = useState(sessionId);
  if (prevSession !== sessionId) {
    setPrevSession(sessionId);
    setOptions([]);
  }

  useEffect(() => {
    let alive = true;
    if (!sessionId) return;
    let cancelled = false;
    const run = async () => {
      setLoading(true);
      try {
        const rows = await listModelAvailableApi();
        if (alive && !cancelled) setOptions(rows ?? []);
      } catch (err) {
        console.warn(getApiErrorMessage(err, t('modelLoadFailed')));
      } finally {
        if (alive && !cancelled) setLoading(false);
      }
    };
    // 同步 setState 移入微任务，规避 effect 同步 setState 级联
    const timer = window.setTimeout(() => {
      void run();
    }, 0);
    return () => {
      alive = false;
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [sessionId, t]);

  const current = useMemo(
    () => options.find((o) => o.id === value) ?? null,
    [options, value],
  );

  const handleChange = async (releaseId: number) => {
    if (!sessionId) return;
    setSaving(true);
    try {
      await onChange(releaseId);
    } finally {
      setSaving(false);
    }
  };

  const handleClear = async () => {
    if (!sessionId) return;
    setSaving(true);
    try {
      await onChange(null);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Spin spinning={loading} size="small">
      <Select<number>
        allowClear
        showSearch
        optionFilterProp="label"
        style={{ minWidth: 180, maxWidth: 260 }}
        placeholder={t('modelPlaceholder')}
        loading={loading || saving || externalLoading}
        disabled={disabled || !sessionId}
        value={current?.id ?? undefined}
        onClear={handleClear}
        onChange={(v) => void handleChange(v)}
        options={options.map((o) => ({
          value: o.id,
          label: `${o.name}${o.scope === 'PRIVATE' ? ' (私有)' : ''}`,
        }))}
        optionRender={(option) => {
          const rel = options.find((o) => o.id === option.value);
          if (!rel) return option.label;
          const caps = parseCaps(rel.capabilities);
          return (
            <Tooltip title={rel.modelName}>
              <span>
                {option.label}
                {caps.length > 0 && (
                  <span style={{ marginLeft: 8 }}>
                    {caps.slice(0, 3).map((c) => (
                      <Tag key={c} style={{ marginInlineEnd: 4 }}>
                        {CAP_LABEL[c] ?? c}
                      </Tag>
                    ))}
                  </span>
                )}
              </span>
            </Tooltip>
          );
        }}
      />
    </Spin>
  );
}
