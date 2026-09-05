import { useEffect, useMemo, useState } from 'react';
import { Select } from 'antd';
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

/**
 * ModelPicker：会话内选模型，展示名称。
 * 候选 = 官方 PUBLISHED ∪ 本人私有；选择写入会话（model-binding），后端记住下次复用。
 */
export default function ModelPicker({ sessionId, value, onChange, disabled, loading: externalLoading }: Props) {
  const { t } = useTranslation('agent-conversation');
  const [options, setOptions] = useState<ModelRelease[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
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
    <Select<number>
      allowClear
      showSearch
      variant="borderless"
      size="small"
      className="agent-chat-model-select"
      optionFilterProp="label"
      popupMatchSelectWidth={false}
      aria-label={t('modelPlaceholder')}
      placeholder={t('modelPlaceholder')}
      loading={loading || saving || externalLoading}
      disabled={disabled || !sessionId}
      value={current?.id ?? undefined}
      onClear={handleClear}
      onChange={(v) => void handleChange(v)}
      options={options.map((o) => ({
        value: o.id,
        label: o.name,
      }))}
    />
  );
}
