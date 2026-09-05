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
  /** 当前是否可用（无 Agent / 正在请求时禁用） */
  disabled?: boolean;
  /** 外部加载态（如切换会话读取绑定中） */
  loading?: boolean;
}

/**
 * ModelPicker：会话内选模型，展示名称。
 * 候选 = 官方 PUBLISHED ∪ 本人私有。
 * 草稿态也拉取候选并允许预选；真实会话的选择写入 model-binding。
 */
export default function ModelPicker({ sessionId, value, onChange, disabled, loading: externalLoading }: Props) {
  const { t } = useTranslation('agent-conversation');
  const [options, setOptions] = useState<ModelRelease[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    const run = async () => {
      setLoading(true);
      try {
        const rows = await listModelAvailableApi();
        if (alive) setOptions(rows ?? []);
      } catch (err) {
        console.warn(getApiErrorMessage(err, t('modelLoadFailed')));
      } finally {
        if (alive) setLoading(false);
      }
    };
    void run();
    return () => {
      alive = false;
    };
  }, [t]);

  // 草稿且尚未选模型：自动预选第一个可用模型，避免一直停在「默认模型」占位
  useEffect(() => {
    if (sessionId !== null || value !== null || options.length === 0) return;
    const firstId = options[0]?.id;
    if (firstId === null || firstId === undefined) return;
    void onChange(firstId);
  }, [sessionId, value, options, onChange]);

  const current = useMemo(
    () => options.find((o) => o.id === value) ?? null,
    [options, value],
  );

  const handleChange = async (releaseId: number) => {
    setSaving(true);
    try {
      await onChange(releaseId);
    } finally {
      setSaving(false);
    }
  };

  const handleClear = async () => {
    setSaving(true);
    try {
      await onChange(null);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Select<number>
      allowClear={sessionId !== null}
      showSearch
      variant="borderless"
      size="small"
      className="agent-chat-model-select"
      optionFilterProp="label"
      popupMatchSelectWidth={false}
      aria-label={t('modelPlaceholder')}
      placeholder={t('modelPlaceholder')}
      loading={loading || saving || externalLoading}
      disabled={disabled}
      value={current?.id ?? (sessionId === null ? options[0]?.id : undefined)}
      onClear={handleClear}
      onChange={(v) => void handleChange(v)}
      options={options.map((o) => ({
        value: o.id,
        label: o.name,
      }))}
    />
  );
}
