import { useRef, useState } from 'react';
import { Button, message, Modal, Space, Tag, Typography } from 'antd';
import { PlusOutlined, RobotOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import { deleteAgentApi, disableAgentApi, enableAgentApi, listAgentApi } from '@/api/rest/agent';
import type { Agent } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import AgentFormDrawer from './modules/agent-form-drawer';
import AgentDetailDrawer from './modules/agent-detail-drawer';
import { getApiErrorMessage } from '../blacklist/modules/error-message';

function statusOrUndefined(v: number | '' | undefined): 0 | 1 | undefined {
  if (v === '' || v === undefined) return undefined;
  return Number(v) as 0 | 1;
}

const AgentPage = () => {
  const { t } = useTranslation('agent');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Agent | null>(null);
  const [detail, setDetail] = useState<Agent | null>(null);

  const reload = () => {
    actionRef.current?.reload?.();
  };

  async function fetchRows(params: {
    current?: number;
    pageSize?: number;
    name?: string;
    isEnabled?: number | '';
  }) {
    const { current = 1, pageSize = 20, name, isEnabled } = params;
    const res = await listAgentApi({
      page: current,
      pageSize,
      name: name || undefined,
      isEnabled: statusOrUndefined(isEnabled),
    });
    return { data: res.items, total: res.total, success: true };
  }

  const runAction = async (
    label: string,
    fn: () => Promise<unknown>,
    successKey: string,
  ) => {
    try {
      await fn();
      message.success(t(successKey));
      reload();
    } catch (err) {
      message.error(`${t(label)}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handleToggle = (row: Agent) => {
    const isEnabled = row.isEnabled === 1;
    Modal.confirm({
      title: t(isEnabled ? 'confirmDisable' : 'confirmEnable'),
      okText: t('confirm'),
      cancelText: t('cancel'),
      onOk: () =>
        runAction(
          'unknownError',
          () => (isEnabled ? disableAgentApi(row.id) : enableAgentApi(row.id)),
          isEnabled ? 'disableSuccess' : 'enableSuccess',
        ),
    });
  };

  const handleDelete = (row: Agent) => {
    Modal.confirm({
      title: t('confirmDelete'),
      okText: t('delete'),
      cancelText: t('cancel'),
      okButtonProps: { danger: true },
      onOk: () => runAction('deleteFailed', () => deleteAgentApi(row.id), 'deleteSuccess'),
    });
  };

  const columns: ProColumns<Agent>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    {
      title: t('name'),
      dataIndex: 'name',
      width: 180,
      fieldProps: { placeholder: t('namePlaceholder') },
      render: (_, r) => (
        <a
          onClick={(e) => {
            e.stopPropagation();
            setDetail(r);
          }}
        >
          {r.name}
        </a>
      ),
    },
    { title: t('description'), dataIndex: 'description', ellipsis: true, search: false },
    {
      title: t('currentPublishedRevisionId'),
      dataIndex: 'currentPublishedRevisionId',
      width: 160,
      search: false,
      render: (_, r) =>
        r.currentPublishedRevisionId ? (
          <Typography.Text code>#{r.currentPublishedRevisionId}</Typography.Text>
        ) : (
          <Tag>未发布</Tag>
        ),
    },
    {
      title: t('status'),
      dataIndex: 'isEnabled',
      width: 90,
      valueType: 'select',
      valueEnum: {
        1: { text: t('enabled') },
        0: { text: t('disabled') },
      },
      render: (_, r) =>
        r.isEnabled === 1 ? (
          <Tag color="success">{t('enabled')}</Tag>
        ) : (
          <Tag color="error">{t('disabled')}</Tag>
        ),
    },
    { title: t('updatedAt'), dataIndex: 'updatedAt', width: 170, valueType: 'dateTime', search: false },
    {
      title: t('action'),
      valueType: 'option',
      key: 'option',
      width: 220,
      fixed: 'right',
      search: false,
      render: (_text, record) => [
        <a key="manage" onClick={() => setDetail(record)}>
          配置
        </a>,
        <a key="edit" onClick={() => {
 setEditing(record); setDrawerOpen(true); 
}}>
          {t('edit')}
        </a>,
        <a key="toggle" onClick={() => handleToggle(record)}>
          {record.isEnabled === 1 ? t('disable') : t('enable')}
        </a>,
        <a key="delete" style={{ color: '#ff4d4f' }} onClick={() => handleDelete(record)}>
          {t('delete')}
        </a>,
      ],
    },
  ];

  return (
    <ContentContainer scrollable>
      <ProTable<Agent>
        rowKey="id"
        headerTitle={
          <Space>
            <RobotOutlined />
            {t('pageTitle')}
          </Space>
        }
        actionRef={actionRef}
        columns={columns}
        request={fetchRows}
        search={{ labelWidth: 'auto' }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => t('total', { total }),
        }}
        scroll={{ x: 1100 }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null);
              setDrawerOpen(true);
            }}
          >
            {t('create')}
          </Button>,
        ]}
      />
      <AgentFormDrawer
        open={drawerOpen}
        row={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={reload}
      />
      <AgentDetailDrawer
        open={!!detail}
        agent={detail}
        onClose={() => setDetail(null)}
        onChanged={reload}
      />
    </ContentContainer>
  );
};

export default AgentPage;
