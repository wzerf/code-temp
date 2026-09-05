import { useState } from 'react';
import { Button, List, Popconfirm, Space, Spin, Tooltip, Typography, Empty } from 'antd';
import {
  DeleteOutlined,
  MessageOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { AgentSession } from '@/api/rest/types';
import { getApiErrorMessage } from '../../../blacklist/modules/error-message';
import { message } from 'antd';

interface Props {
  agentName: string;
  conversations: AgentSession[];
  loading: boolean;
  activeId: number | null;
  onSelect: (session: AgentSession | null) => void;
  onCreate: () => Promise<void>;
  onDelete: (sessionId: number) => Promise<void>;
}

/** 会话栏：列出某 Agent 的会话（标题=remark），支持新建/删除。 */
export default function ChatSide({
  agentName,
  conversations,
  loading,
  activeId,
  onSelect,
  onCreate,
  onDelete,
}: Props) {
  const { t } = useTranslation('agent-conversation');
  const [creating, setCreating] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

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
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div style={{ padding: '12px 12px 8px', display: 'flex', alignItems: 'center', gap: 8 }}>
        <Typography.Text strong ellipsis style={{ flex: 1 }}>
          {agentName || t('chatSideTitle')}
        </Typography.Text>
        <Tooltip title={t('newChat')}>
          <Button
            size="small"
            type="primary"
            ghost
            icon={<PlusOutlined />}
            loading={creating}
            onClick={() => void handleCreate()}
          />
        </Tooltip>
      </div>

      <Spin spinning={loading}>
        <div style={{ flex: 1, overflowY: 'auto', paddingInline: 8 }}>
          {conversations.length === 0 && !loading ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noSessions')} />
          ) : (
            <List
              size="small"
              dataSource={conversations}
              renderItem={(s) => {
                const active = s.id === activeId;
                return (
                  <List.Item
                    onClick={() => onSelect(active ? null : s)}
                    style={{
                      cursor: 'pointer',
                      borderRadius: 8,
                      padding: '6px 10px',
                      background: active ? 'rgba(22,119,255,0.1)' : undefined,
                      border: active ? '1px solid rgba(22,119,255,0.4)' : '1px solid transparent',
                    }}
                    actions={[
                      <Popconfirm
                        key="del"
                        title={t('deleteConfirm')}
                        okText={t('delete')}
                        cancelText={t('cancel')}
                        okButtonProps={{ danger: true }}
                        onConfirm={() => handleDelete(s.id)}
                      >
                        <Button
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          loading={deletingId === s.id}
                          onClick={(e) => e.stopPropagation()}
                        />
                      </Popconfirm>,
                    ]}
                  >
                    <Space size={6} style={{ minWidth: 0, flex: 1 }}>
                      <MessageOutlined style={{ color: 'rgba(128,128,128,0.7)' }} />
                      <Typography.Text
                        ellipsis={{ tooltip: s.remark || `#${s.id}` }}
                        style={{ flex: 1, fontSize: 13 }}
                      >
                        {s.remark || `#${s.id}`}
                      </Typography.Text>
                    </Space>
                  </List.Item>
                );
              }}
            />
          )}
        </div>
      </Spin>
    </div>
  );
}
