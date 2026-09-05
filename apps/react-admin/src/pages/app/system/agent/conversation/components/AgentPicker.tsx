import { Select, Tag } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { Agent } from '@/api/rest/types';

interface Props {
  agents: Agent[];
  loading?: boolean;
  value: Agent | null;
  onChange: (agent: Agent | null) => void;
}

function AgentMark({ name }: { name?: string }) {
  const letter = name?.trim().slice(0, 1);
  return (
    <span className="agent-chat-agent-avatar" aria-hidden>
      {letter || <RobotOutlined />}
    </span>
  );
}

/** AgentPicker：侧栏内切换 Agent，视觉对齐新建会话按钮。 */
export default function AgentPicker({ agents, loading, value, onChange }: Props) {
  const { t } = useTranslation('agent-conversation');
  const list = Array.isArray(agents) ? agents : [];

  return (
    <div className="agent-chat-agent-picker">
      <AgentMark name={value?.name} />
      <Select<number>
        allowClear
        showSearch
        variant="borderless"
        optionFilterProp="label"
        className="agent-chat-agent-select"
        classNames={{ popup: { root: 'agent-chat-agent-dropdown' } }}
        popupMatchSelectWidth
        placeholder={t('agentPlaceholder')}
        loading={loading}
        disabled={list.length === 0}
        value={value?.id}
        aria-label={t('agentPlaceholder')}
        onChange={(v) => {
          const agent = list.find((a) => a.id === v) ?? null;
          onChange(agent);
        }}
        options={list.map((a) => ({ value: a.id, label: a.name }))}
        optionRender={(option) => {
          const rel = list.find((a) => a.id === option.value);
          if (!rel) return option.label;
          return (
            <span className="agent-chat-agent-option" title={rel.description || rel.name}>
              <AgentMark name={rel.name} />
              <span className="agent-chat-agent-option-name">{rel.name}</span>
              {rel.isEnabled !== 1 && (
                <Tag color="error" bordered={false}>
                  {t('disabled')}
                </Tag>
              )}
              {rel.currentPublishedRevisionId === null && (
                <Tag bordered={false}>{t('unpublished')}</Tag>
              )}
            </span>
          );
        }}
      />
    </div>
  );
}
