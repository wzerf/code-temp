import { Select, Space, Tag, Tooltip } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { Agent } from '@/api/rest/types';

interface Props {
  agents: Agent[];
  loading?: boolean;
  value: Agent | null;
  onChange: (agent: Agent | null) => void;
}

/** AgentPicker：跨 Agent 切换（顶部）。切换会重置该 Agent 的会话集合。 */
export default function AgentPicker({ agents, loading, value, onChange }: Props) {
  const { t } = useTranslation('agent-conversation');
  // 防御：列表可能因接口异常/热更新暂态而非数组
  const list = Array.isArray(agents) ? agents : [];

  return (
    <Space size={8}>
      <RobotOutlined style={{ fontSize: 16, color: 'rgba(128,128,128,0.9)' }} />
      <Select<number>
        allowClear
        showSearch
        optionFilterProp="label"
        style={{ minWidth: 200, maxWidth: 320 }}
        placeholder={t('agentPlaceholder')}
        loading={loading}
        disabled={list.length === 0}
        value={value?.id}
        onChange={(v) => {
          const agent = list.find((a) => a.id === v) ?? null;
          onChange(agent);
        }}
        options={list.map((a) => ({ value: a.id, label: a.name }))}
        optionRender={(option) => {
          const rel = list.find((a) => a.id === option.value);
          if (!rel) return option.label;
          return (
            <Tooltip title={rel.description}>
              <Space size={6}>
                <span>{rel.name}</span>
                {rel.isEnabled !== 1 && <Tag color="error">{t('disabled')}</Tag>}
                {rel.currentPublishedRevisionId === null && (
                  <Tag>{t('unpublished')}</Tag>
                )}
              </Space>
            </Tooltip>
          );
        }}
      />
    </Space>
  );
}
