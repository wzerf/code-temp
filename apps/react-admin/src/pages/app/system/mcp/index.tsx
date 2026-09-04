import { useRef, useState } from 'react';
import { Button, Input, message, Modal, Space, Table, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import {
  approveMcpDraftApi,
  deleteMcpDraftApi,
  listMcpDraftApi,
  listMcpMarketApi,
  listMcpReleaseApi,
  rejectMcpDraftApi,
  submitMcpDraftApi,
  takeDownMcpMarketApi,
  verifyMcpDraftApi,
  withdrawMcpDraftApi,
} from '@/api/rest/mcp';
import type { McpDraft, McpRelease, McpVerifyResult } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import McpFormDrawer from './modules/mcp-form-drawer';
import { getApiErrorMessage } from '../blacklist/modules/error-message';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_REVIEW: 'gold',
  REJECTED: 'red',
  CONSUMED: 'purple',
  PUBLISHED: 'green',
  DEPRECATED: 'default',
};

const McpPage = () => {
  const { t } = useTranslation('mcp');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [tab, setTab] = useState<'drafts' | 'releases' | 'market'>('drafts');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<McpDraft | null>(null);
  const [marketRows, setMarketRows] = useState<McpRelease[]>([]);
  const [marketLoading, setMarketLoading] = useState(false);
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [verifyResult, setVerifyResult] = useState<McpVerifyResult | null>(null);
  const [verifyLoading, setVerifyLoading] = useState(false);

  const reload = () => actionRef.current?.reload?.();
  const statusLabel = (s: string) => t(`statusMap.${s}`, { defaultValue: s });

  async function fetchDraftRows(params: {
    current?: number;
    pageSize?: number;
    name?: string;
    status?: string;
    visibility?: string;
  }) {
    const { current = 1, pageSize = 20, name, status, visibility } = params;
    const res = await listMcpDraftApi({
      page: current,
      pageSize,
      name: name || undefined,
      status: status || undefined,
      visibility: visibility || undefined,
    });
    return { data: res.items, total: res.total, success: true };
  }

  async function fetchReleaseRows(params: { current?: number; pageSize?: number; name?: string; status?: string }) {
    const { current = 1, pageSize = 20, name, status } = params;
    const res = await listMcpReleaseApi({
      page: current,
      pageSize,
      name: name || undefined,
      status: status || undefined,
    });
    return { data: res.items, total: res.total, success: true };
  }

  const run = async (msg: string, fn: () => Promise<unknown>, okKey: string) => {
    try {
      await fn();
      message.success(t(okKey));
      reload();
    } catch (err) {
      message.error(`${t(msg)}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const confirm = (title: string, fn: () => Promise<unknown>, okText?: string) => {
    Modal.confirm({ title, okText: okText ?? t('confirm'), cancelText: t('cancel'), onOk: fn });
  };

  const handleVerify = async (id: number) => {
    setVerifyOpen(true);
    setVerifyLoading(true);
    setVerifyResult(null);
    try {
      const res = await verifyMcpDraftApi(id);
      setVerifyResult(res);
      if (res.success) message.success(t('verifySuccess', { count: res.toolCount }));
      else message.error(res.message || t('verifyFailed'));
    } catch (err) {
      setVerifyResult(null);
      message.error(`${t('verifyFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setVerifyLoading(false);
    }
  };

  const loadMarket = async () => {
    setMarketLoading(true);
    try {
      setMarketRows(await listMcpMarketApi());
    } finally {
      setMarketLoading(false);
    }
  };

  const renderStatus = (s: string) => <Tag color={STATUS_COLOR[s]}>{statusLabel(s)}</Tag>;
  const renderVisibility = (v: string) =>
    v === 'MARKET' ? <Tag color="blue">{t('visibilityMap.MARKET')}</Tag> : <Tag>{t('visibilityMap.PRIVATE')}</Tag>;

  const draftColumns: ProColumns<McpDraft>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    {
      title: t('name'),
      dataIndex: 'name',
      width: 180,
      fieldProps: { placeholder: t('filterPlaceholder') },
      render: (_, r) => (
        <a
          onClick={() => {
            setEditing(r);
            setDrawerOpen(true);
          }}
        >
          {r.name}
        </a>
      ),
    },
    {
      title: t('visibility'),
      dataIndex: 'visibility',
      width: 100,
      valueType: 'select',
      valueEnum: { MARKET: { text: t('visibilityMap.MARKET') }, PRIVATE: { text: t('visibilityMap.PRIVATE') } },
      render: (_, r) => renderVisibility(r.visibility),
    },
    {
      title: t('transport'),
      dataIndex: 'transport',
      width: 100,
      render: (_, r) => <Tag>{t(`transportMap.${r.transport}`, { defaultValue: r.transport })}</Tag>,
    },
    { title: t('url'), dataIndex: 'url', ellipsis: true, search: false },
    {
      title: t('status'),
      dataIndex: 'status',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(['DRAFT', 'PENDING_REVIEW', 'REJECTED', 'CONSUMED'].map((s) => [s, { text: statusLabel(s) }])),
      render: (_, r) => renderStatus(r.status),
    },
    { title: t('updatedAt'), dataIndex: 'updatedAt', width: 160, valueType: 'dateTime', search: false },
    {
      title: t('action'),
      valueType: 'option',
      key: 'option',
      width: 320,
      fixed: 'right',
      search: false,
      render: (_text, r) => {
        const actions: React.ReactNode[] = [
          <a key="verify" onClick={() => handleVerify(r.id)}>
            {t('verify')}
          </a>,
        ];
        if (r.status === 'DRAFT' || r.status === 'REJECTED') {
          actions.push(
            <a key="edit" onClick={() => {
 setEditing(r); setDrawerOpen(true); 
}}>{t('edit')}</a>,
            <a key="submit" onClick={() => confirm(t('confirmSubmit'), () => run('submitFailed', () => submitMcpDraftApi(r.id), 'submitSuccess'))}>
              {t('submit')}
            </a>,
          );
        }
        if (r.status === 'PENDING_REVIEW') {
          actions.push(
            <a key="withdraw" onClick={() => confirm(t('confirmWithdraw'), () => run('submitFailed', () => withdrawMcpDraftApi(r.id), 'withdrawSuccess'))}>
              {t('withdraw')}
            </a>,
            <a key="approve" onClick={() => confirm(t('confirmApprove'), () => run('approveFailed', () => approveMcpDraftApi(r.id), 'approveSuccess'))}>
              {t('approve')}
            </a>,
            <a key="reject" style={{ color: '#ff4d4f' }} onClick={() => {
 setRejectId(r.id); setRejectReason(''); 
}}>
              {t('reject')}
            </a>,
          );
        }
        actions.push(
          <a key="delete" style={{ color: '#ff4d4f' }} onClick={() => confirm(t('confirmDelete'), () => run('deleteFailed', () => deleteMcpDraftApi(r.id), 'deleteSuccess'))}>
            {t('delete')}
          </a>,
        );
        return actions;
      },
    },
  ];

  const releaseColumns: ProColumns<McpRelease>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    { title: t('name'), dataIndex: 'name', width: 180 },
    { title: t('version'), dataIndex: 'version', width: 80, search: false },
    {
      title: t('visibility'),
      dataIndex: 'visibility',
      width: 100,
      render: (_, r) => renderVisibility(r.visibility),
    },
    { title: t('transport'), dataIndex: 'transport', width: 100, render: (_, r) => <Tag>{r.transport}</Tag> },
    { title: t('url'), dataIndex: 'url', ellipsis: true, search: false },
    {
      title: t('hasSecret'),
      dataIndex: 'hasSecret',
      width: 100,
      render: (_, r) => (r.hasSecret ? <Tag color="green">✓</Tag> : <Tag>—</Tag>),
    },
    {
      title: t('status'),
      dataIndex: 'status',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(['PUBLISHED', 'DEPRECATED'].map((s) => [s, { text: statusLabel(s) }])),
      render: (_, r) => renderStatus(r.status),
    },
    { title: t('createdAt'), dataIndex: 'createdAt', width: 160, valueType: 'dateTime', search: false },
  ];

  const marketColumns = [
    { title: t('id'), dataIndex: 'id', width: 70 },
    { title: t('name'), dataIndex: 'name', width: 200 },
    { title: t('version'), dataIndex: 'version', width: 90 },
    { title: t('url'), dataIndex: 'url', ellipsis: true },
    {
      title: t('action'),
      key: 'action',
      width: 130,
      render: (_: unknown, r: McpRelease) => (
        <a
          style={{ color: '#ff4d4f' }}
          onClick={() =>
            confirm(
              t('confirmTakeDown'),
              () =>
                run('deleteFailed', async () => {
                  await takeDownMcpMarketApi(r.id);
                  await loadMarket();
                }, 'takeDownSuccess'),
              t('takeDown'),
            )
          }
        >
          {t('takeDown')}
        </a>
      ),
    },
  ];

  const tabItems = [
    {
      key: 'drafts',
      label: t('tabDrafts'),
      children: (
        <ProTable<McpDraft>
          rowKey="id"
          headerTitle={t('tabDrafts')}
          actionRef={actionRef}
          columns={draftColumns}
          request={fetchDraftRows}
          search={{ labelWidth: 'auto' }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (v) => t('total', { total: v }) }}
          scroll={{ x: 1400 }}
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
      ),
    },
    {
      key: 'releases',
      label: t('tabReleases'),
      children: (
        <ProTable<McpRelease>
          rowKey="id"
          headerTitle={t('tabReleases')}
          columns={releaseColumns}
          request={fetchReleaseRows}
          search={{ labelWidth: 'auto' }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (v) => t('total', { total: v }) }}
          scroll={{ x: 1200 }}
        />
      ),
    },
    {
      key: 'market',
      label: t('tabMarket'),
      children: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button onClick={loadMarket} loading={marketLoading} style={{ alignSelf: 'flex-end' }}>
            刷新
          </Button>
          <Table<McpRelease> rowKey="id" columns={marketColumns as never} dataSource={marketRows} pagination={false} />
        </Space>
      ),
    },
  ];

  return (
    <ContentContainer>
      <Tabs
        activeKey={tab}
        onChange={(k) => {
          setTab(k as typeof tab);
          if (k === 'market') loadMarket();
        }}
        items={tabItems}
      />
      <McpFormDrawer open={drawerOpen} row={editing} onClose={() => setDrawerOpen(false)} onSaved={reload} />
      <Modal
        title={t('verifyResult')}
        open={verifyOpen}
        onCancel={() => setVerifyOpen(false)}
        footer={<Button onClick={() => setVerifyOpen(false)}>{t('confirm')}</Button>}
        width={680}
      >
        <Typography.Paragraph>
          {verifyResult ? t('verifySuccess', { count: verifyResult.toolCount }) : '...'}
        </Typography.Paragraph>
        <Table
          rowKey="name"
          size="small"
          loading={verifyLoading}
          dataSource={verifyResult?.tools ?? []}
          pagination={false}
          columns={[
            { title: t('toolName'), dataIndex: 'name' },
            { title: t('toolDescription'), dataIndex: 'description' },
          ]}
        />
      </Modal>
      <Modal
        title={t('reject')}
        open={rejectId !== null}
        onCancel={() => setRejectId(null)}
        onOk={async () => {
          if (rejectId === null) return;
          try {
            await rejectMcpDraftApi(rejectId, rejectReason);
            message.success(t('rejectSuccess'));
            setRejectId(null);
            reload();
          } catch (err) {
            message.error(`${t('rejectFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
          }
        }}
        okText={t('confirm')}
        cancelText={t('cancel')}
      >
        <Input.TextArea rows={3} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} placeholder={t('rejectReasonPlaceholder')} />
      </Modal>
    </ContentContainer>
  );
};

export default McpPage;
