import { useMemo, useState } from 'react';
import { Button, Popconfirm, Spin, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { Agent, AgentSession } from '@/api/rest/types';
import AgentPicker from './AgentPicker';
import { parsePlatformMillis } from '@/utils/date';
import { getApiErrorMessage } from '../../../blacklist/modules/error-message';
import { message } from 'antd';

interface Props {
  agents: Agent[];
  agentsLoading?: boolean;
  activeAgent: Agent | null;
  onAgentChange: (agent: Agent | null) => void;
  conversations: AgentSession[];
  loading: boolean;
  activeId: number | null;
  creatingDisabled?: boolean;
  onSelect: (session: AgentSession | null) => void;
  onCreate: () => Promise<void>;
  onDelete: (sessionId: number) => Promise<void>;
}

function sessionStamp(session: AgentSession): string {
  return session.lastActiveAt || session.createdAt;
}

function sessionDayLabel(timestamp: string, t: (key: string) => string): string {
  const millis = parsePlatformMillis(timestamp);
  if (Number.isNaN(millis)) return t('sessionDayEarlier');
  const date = new Date(millis);
  const today = new Date();
  if (date.toDateString() === today.toDateString()) return t('sessionDayToday');
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return t('sessionDayYesterday');
  return date.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' });
}

function sessionTimeLabel(timestamp: string): string {
  const millis = parsePlatformMillis(timestamp);
  return Number.isNaN(millis)
    ? ''
    : new Date(millis).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

/** 会话栏：按最近活跃日分组，支持新建/删除。 */
export default function ChatSide({
  agents,
  agentsLoading,
  activeAgent,
  onAgentChange,
  conversations,
  loading,
  activeId,
  creatingDisabled,
  onSelect,
  onCreate,
  onDelete,
}: Props) {
  const { t } = useTranslation('agent-conversation');
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const sessionGroups = useMemo(() => {
    const groups = new Map<string, AgentSession[]>();
    [...conversations]
      .sort((left, right) => parsePlatformMillis(sessionStamp(right)) - parsePlatformMillis(sessionStamp(left)))
      .forEach((item) => {
        const label = sessionDayLabel(sessionStamp(item), t);
        groups.set(label, [...(groups.get(label) ?? []), item]);
      });
    return [...groups];
  }, [conversations, t]);

  const handleCreate = async () => {
    setCreating(true);
    try {
      await onCreate();
    } catch (err) {
      message.error(getApiErrorMessage(err, t('createFailed')));
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (sessionId: number) => {
    setDeletingId(sessionId);
    try {
      await onDelete(sessionId);
      message.success(t('deleteSuccess'));
    } catch (err) {
      message.error(getApiErrorMessage(err, t('deleteFailed')));
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <>
      <AgentPicker
        agents={agents}
        loading={agentsLoading}
        value={activeAgent}
        onChange={onAgentChange}
      />
      <Button
        className="agent-chat-new"
        icon={<PlusOutlined />}
        loading={creating}
        disabled={creatingDisabled}
        onClick={() => void handleCreate()}
      >
        {t('newChat')}
      </Button>
      <div className="agent-chat-session-list">
        <Typography.Text className="agent-chat-list-title">{t('chatSideTitle')}</Typography.Text>
        {loading ? (
          <Spin size="small" />
        ) : conversations.length === 0 ? (
          <Typography.Text className="agent-chat-session-empty" type="secondary">
            {t('noSessions')}
          </Typography.Text>
        ) : (
          sessionGroups.map(([label, items]) => (
            <div className="agent-chat-session-group" key={label}>
              <Typography.Text className="agent-chat-session-group-title">{label}</Typography.Text>
              {items.map((item) => {
                const active = item.id === activeId;
                return (
                  <div
                    className={`agent-chat-session${active ? ' agent-chat-session--active' : ''}`}
                    key={item.id}
                  >
                    <button
                      type="button"
                      className="agent-chat-session-main"
                      onClick={() => onSelect(item)}
                    >
                      <span className="agent-chat-session-title">{item.remark || `#${item.id}`}</span>
                      <span className="agent-chat-session-time">{sessionTimeLabel(sessionStamp(item))}</span>
                    </button>
                    <Popconfirm
                      title={t('deleteConfirm')}
                      okText={t('delete')}
                      cancelText={t('cancel')}
                      okButtonProps={{ danger: true }}
                      onConfirm={() => void handleDelete(item.id)}
                    >
                      <Button
                        className="agent-chat-session-delete"
                        type="text"
                        size="small"
                        danger
                        aria-label={t('delete')}
                        icon={<DeleteOutlined />}
                        loading={deletingId === item.id}
                      />
                    </Popconfirm>
                  </div>
                );
              })}
            </div>
          ))
        )}
      </div>
    </>
  );
}
