import { useRef, useState, type ReactNode } from 'react';
import { Button, Input, message, Modal, Space, Table, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import {
  approveModelDraftApi,
  deleteModelDraftApi,
  deprecateModelReleaseApi,
  getModelDraftApi,
  listModelAvailableApi,
  listModelDraftApi,
  listModelReleaseApi,
  rejectModelDraftApi,
  submitModelDraftApi,
  verifyModelDraftApi,
  withdrawModelDraftApi,
} from '@/api/rest/model';
import type { ModelDraft, ModelRelease, ModelVerifyResult } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import ModelFormDrawer from './modules/model-form-drawer';
import { getApiErrorMessage } from '../blacklist/modules/error-message';

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_REVIEW: 'gold',
  REJECTED: 'red',
  CONSUMED: 'purple',
  PUBLISHED: 'green',
  DEPRECATED: 'default',
};

const ModelPage = () => {
  const { t } = useTranslation('model');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [tab, setTab] = useState<'drafts' | 'releases' | 'available'>('drafts');
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit' | null>(null);
  const [editing, setEditing] = useState<ModelDraft | null>(null);
  const [availableRows, setAvailableRows] = useState<ModelRelease[]>([]);
  const [availableLoading, setAvailableLoading] = useState(false);
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [verifyOpen, setVerifyOpen] = useState(false);
  const [verifyResult, setVerifyResult] = useState<ModelVerifyResult | null>(null);
  const [verifyLoading, setVerifyLoading] = useState(false);

  const reload = () => actionRef.current?.reload?.();
  const statusLabel = (s: string) => t(`statusMap.${s}`, { defaultValue: s });

  async function fetchDraftRows(params: {
    current?: number;
    pageSize?: number;
    name?: string;
    status?: string;
    scope?: string;
  }) {
    const { current = 1, pageSize = 20, name, status, scope } = params;
    const res = await listModelDraftApi({
      page: current,
      pageSize,
      name: name || undefined,
      status: status || undefined,
      scope: scope || undefined,
    });
    return { data: res.items, total: res.total, success: true };
  }

  async function fetchReleaseRows(params: { current?: number; pageSize?: number; name?: string; status?: string; scope?: string }) {
    const { current = 1, pageSize = 20, name, status, scope } = params;
    const res = await listModelReleaseApi({
      page: current,
      pageSize,
      name: name || undefined,
      status: status || undefined,
      scope: scope || undefined,
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
      const res = await verifyModelDraftApi(id);
      setVerifyResult(res);
      if (res.success) message.success(res.message);
      else message.error(res.message || t('verifyFailed'));
    } catch (err) {
      setVerifyResult(null);
      message.error(`${t('verifyFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setVerifyLoading(false);
    }
  };

  const loadAvailable = async () => {
    setAvailableLoading(true);
    try {
      setAvailableRows(await listModelAvailableApi());
    } finally {
      setAvailableLoading(false);
    }
  };

  const openEdit = async (draft: ModelDraft) => {
    try {
      const detail = await getModelDraftApi(draft.id);
      setEditing(detail);
      setDrawerMode('edit');
    } catch (err) {
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const renderStatus = (s: string) => <Tag color={STATUS_COLOR[s]}>{statusLabel(s)}</Tag>;
  const renderScope = (v: string) =>
    v === 'OFFICIAL' ? <Tag color="blue">{t('scopeMap.OFFICIAL')}</Tag> : <Tag>{t('scopeMap.PRIVATE')}</Tag>;

  const draftColumns: ProColumns<ModelDraft>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    {
      title: t('name'),
      dataIndex: 'name',
      width: 180,
      fieldProps: { placeholder: t('filterPlaceholder') },
      render: (_, r) => (
        <a
          onClick={() => openEdit(r)}
        >
          {r.name}
        </a>
      ),
    },
    {
      title: t('scope'),
      dataIndex: 'scope',
      width: 100,
      valueType: 'select',
      valueEnum: { OFFICIAL: { text: t('scopeMap.OFFICIAL') }, PRIVATE: { text: t('scopeMap.PRIVATE') } },
      render: (_, r) => renderScope(r.scope),
    },
    {
      title: t('provider'),
      dataIndex: 'provider',
      width: 160,
      search: false,
      render: (_, r) => <Tag>{t(`providerMap.${r.provider}`, { defaultValue: r.provider })}</Tag>,
    },
    { title: t('modelName'), dataIndex: 'modelName', width: 160, search: false },
    { title: '上下文', dataIndex: 'contextLength', width: 110, search: false },
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
      width: 360,
      fixed: 'right',
      search: false,
      render: (_text, r) => {
        const actions: ReactNode[] = [
          <a key="verify" onClick={() => handleVerify(r.id)}>
            {t('verify')}
          </a>,
        ];
        if (r.status === 'DRAFT' || r.status === 'REJECTED') {
          actions.push(
            <a
              key="edit"
              onClick={() => openEdit(r)}
            >
              {t('edit')}
            </a>,
          );
          if (r.scope === 'OFFICIAL') {
            actions.push(
              <a
                key="submit"
                onClick={() =>
                  confirm(t('confirmSubmit'), () => run('submitFailed', () => submitModelDraftApi(r.id), 'submitSuccess'))
                }
              >
                {t('submit')}
              </a>,
            );
          } else {
            actions.push(
              <a
                key="publish"
                onClick={() =>
                  confirm(t('confirmPublish'), () => run('approveFailed', () => approveModelDraftApi(r.id), 'approveSuccess'))
                }
              >
                {t('publish')}
              </a>,
            );
          }
        }
        if (r.status === 'PENDING_REVIEW') {
          actions.push(
            <a
              key="withdraw"
              onClick={() =>
                confirm(t('confirmWithdraw'), () => run('submitFailed', () => withdrawModelDraftApi(r.id), 'withdrawSuccess'))
              }
            >
              {t('withdraw')}
            </a>,
            <a
              key="approve"
              onClick={() =>
                confirm(t('confirmApprove'), () => run('approveFailed', () => approveModelDraftApi(r.id), 'approveSuccess'))
              }
            >
              {t('approve')}
            </a>,
            <a
              key="reject"
              style={{ color: '#ff4d4f' }}
              onClick={() => {
                setRejectId(r.id);
                setRejectReason('');
              }}
            >
              {t('reject')}
            </a>,
          );
        }
        actions.push(
          <a
            key="delete"
            style={{ color: '#ff4d4f' }}
            onClick={() =>
              confirm(t('confirmDelete'), () => run('deleteFailed', () => deleteModelDraftApi(r.id), 'deleteSuccess'))
            }
          >
            {t('delete')}
          </a>,
        );
        return actions;
      },
    },
  ];

  const releaseColumns: ProColumns<ModelRelease>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    { title: t('name'), dataIndex: 'name', width: 180 },
    { title: t('version'), dataIndex: 'version', width: 80, search: false },
    {
      title: t('scope'),
      dataIndex: 'scope',
      width: 100,
      valueType: 'select',
      valueEnum: { OFFICIAL: { text: t('scopeMap.OFFICIAL') }, PRIVATE: { text: t('scopeMap.PRIVATE') } },
      render: (_, r) => renderScope(r.scope),
    },
    { title: t('provider'), dataIndex: 'provider', width: 160, search: false },
    { title: t('modelName'), dataIndex: 'modelName', width: 160, search: false },
    { title: '上下文', dataIndex: 'contextLength', width: 110, search: false },
    {
      title: t('hasSecret'),
      dataIndex: 'hasSecret',
      width: 100,
      search: false,
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
    {
      title: t('action'),
      valueType: 'option',
      key: 'option',
      width: 100,
      search: false,
      render: (_text, r) =>
        r.status === 'PUBLISHED' ? (
          <a
            style={{ color: '#ff4d4f' }}
            onClick={() =>
              confirm(t('confirmDeprecate'), () => run('deleteFailed', () => deprecateModelReleaseApi(r.id), 'deprecateSuccess'))
            }
          >
            {t('deprecate')}
          </a>
        ) : null,
    },
  ];

  const availableColumns = [
    { title: t('id'), dataIndex: 'id', width: 70 },
    { title: t('name'), dataIndex: 'name', width: 180 },
    { title: t('version'), dataIndex: 'version', width: 80 },
    {
      title: t('scope'),
      dataIndex: 'scope',
      width: 100,
      render: (_: unknown, r: ModelRelease) => renderScope(r.scope),
    },
    { title: t('provider'), dataIndex: 'provider', width: 160 },
    { title: t('modelName'), dataIndex: 'modelName', width: 160 },
    { title: '上下文', dataIndex: 'contextLength', width: 110 },
  ];

  const tabItems = [
    {
      key: 'drafts',
      label: t('tabDrafts'),
      children: (
        <ProTable<ModelDraft>
          rowKey="id"
          headerTitle={t('tabDrafts')}
          actionRef={actionRef}
          columns={draftColumns}
          request={fetchDraftRows}
          search={{ labelWidth: 'auto' }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (v) => t('total', { total: v }) }}
          scroll={{ x: 1500 }}
          toolBarRender={() => [
            <Button
              key="create"
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null);
                setDrawerMode('create');
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
        <ProTable<ModelRelease>
          rowKey="id"
          headerTitle={t('tabReleases')}
          columns={releaseColumns}
          request={fetchReleaseRows}
          search={{ labelWidth: 'auto' }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (v) => t('total', { total: v }) }}
          scroll={{ x: 1400 }}
        />
      ),
    },
    {
      key: 'available',
      label: t('tabAvailable'),
      children: (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button onClick={loadAvailable} loading={availableLoading} style={{ alignSelf: 'flex-end' }}>
            {t('refresh')}
          </Button>
          <Table<ModelRelease>
            rowKey="id"
            columns={availableColumns as never}
            dataSource={availableRows}
            pagination={false}
          />
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
          if (k === 'available') loadAvailable();
        }}
        items={tabItems}
      />
      {drawerMode && (
        <ModelFormDrawer
          open
          row={drawerMode === 'edit' ? editing : null}
          onClose={() => {
            setDrawerMode(null);
            setEditing(null);
          }}
          onSaved={reload}
        />
      )}
      <Modal
        title={t('verifyResult')}
        open={verifyOpen}
        onCancel={() => setVerifyOpen(false)}
        footer={<Button onClick={() => setVerifyOpen(false)}>{t('confirm')}</Button>}
        width={680}
      >
        <Typography.Paragraph>
          {verifyResult ? verifyResult.message : '...'}
        </Typography.Paragraph>
        <Table
          rowKey="id"
          size="small"
          loading={verifyLoading}
          dataSource={(verifyResult?.remoteModelIds ?? []).map((id) => ({ id }))}
          pagination={false}
          columns={[{ title: t('remoteModelId'), dataIndex: 'id' }]}
        />
      </Modal>
      <Modal
        title={t('reject')}
        open={rejectId !== null}
        onCancel={() => setRejectId(null)}
        onOk={async () => {
          if (rejectId === null) return;
          try {
            await rejectModelDraftApi(rejectId, rejectReason);
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
        <Input.TextArea
          rows={3}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder={t('rejectReasonPlaceholder')}
        />
      </Modal>
    </ContentContainer>
  );
};

export default ModelPage;
