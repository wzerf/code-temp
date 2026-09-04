import { useRef, useState } from 'react';
import { Button, message, Modal, Space, Table, Tabs, Tag, Input } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import {
  approveSkillDraftApi,
  deleteSkillDraftApi,
  listSkillDraftApi,
  listSkillMarketApi,
  listSkillReleaseApi,
  rejectSkillDraftApi,
  submitSkillDraftApi,
  takeDownSkillMarketApi,
  withdrawSkillDraftApi,
} from '@/api/rest/skill';
import type { SkillDraft, SkillRelease } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import SkillFormDrawer from './modules/skill-form-drawer';
import SkillReleaseDetailDrawer from './modules/skill-release-detail-drawer';
import GitSourcePanel from './modules/git-source-panel';
import { getApiErrorMessage } from '../blacklist/modules/error-message';

const { TextArea } = Input;

const SKILL_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_REVIEW: 'gold',
  REJECTED: 'red',
  CONSUMED: 'purple',
  PUBLISHED: 'green',
  DEPRECATED: 'default',
};

const SkillPage = () => {
  const { t } = useTranslation('skill');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const marketRef = useRef<ActionType | undefined>(undefined);
  const [tab, setTab] = useState<'drafts' | 'releases' | 'market' | 'git'>('drafts');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<SkillDraft | null>(null);
  const [marketRows, setMarketRows] = useState<SkillRelease[]>([]);
  const [rejectId, setRejectId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [marketLoading, setMarketLoading] = useState(false);
  const [viewRelease, setViewRelease] = useState<SkillRelease | null>(null);

  const reload = () => {
    actionRef.current?.reload?.();
  };

  const statusLabel = (s: string) => t(`statusMap.${s}`, { defaultValue: s });

  async function fetchDraftRows(params: {
    current?: number;
    pageSize?: number;
    name?: string;
    status?: string;
    visibility?: string;
  }) {
    const { current = 1, pageSize = 20, name, status, visibility } = params;
    const res = await listSkillDraftApi({
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
    const res = await listSkillReleaseApi({
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
    Modal.confirm({
      title,
      okText: okText ?? t('confirm'),
      cancelText: t('cancel'),
      onOk: fn,
    });
  };

  const loadMarket = async () => {
    setMarketLoading(true);
    try {
      setMarketRows(await listSkillMarketApi());
    } finally {
      setMarketLoading(false);
    }
  };

  const renderStatus = (s: string) => <Tag color={SKILL_STATUS_COLOR[s]}>{statusLabel(s)}</Tag>;
  const renderVisibility = (v: string) =>
    v === 'MARKET' ? <Tag color="blue">{t('visibilityMap.MARKET')}</Tag> : <Tag>{t('visibilityMap.PRIVATE')}</Tag>;

  const actionColumn: ProColumns<SkillDraft> = {
    title: t('action'),
    valueType: 'option',
    key: 'option',
    width: 300,
    fixed: 'right',
    search: false,
    render: (_text, r) => {
      const actions: React.ReactNode[] = [];
      if (r.status === 'DRAFT' || r.status === 'REJECTED') {
        actions.push(
          <a key="edit" onClick={() => {
 setEditing(r); setDrawerOpen(true); 
}}>{t('edit')}</a>,
          <a key="submit" onClick={() => confirm(t('confirmSubmit'), () => run('submitFailed', () => submitSkillDraftApi(r.id), 'submitSuccess'))}>
            {t('submit')}
          </a>,
        );
      }
      if (r.status === 'PENDING_REVIEW') {
        actions.push(
          <a key="withdraw" onClick={() => confirm(t('confirmWithdraw'), () => run('submitFailed', () => withdrawSkillDraftApi(r.id), 'withdrawSuccess'))}>
            {t('withdraw')}
          </a>,
          <a key="approve" onClick={() => confirm(t('confirmApprove'), () => run('approveFailed', () => approveSkillDraftApi(r.id), 'approveSuccess'))}>
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
        <a key="delete" style={{ color: '#ff4d4f' }} onClick={() => confirm(t('confirmDelete'), () => run('deleteFailed', () => deleteSkillDraftApi(r.id), 'deleteSuccess'))}>
          {t('delete')}
        </a>,
      );
      return actions;
    },
  };

  const draftColumns: ProColumns<SkillDraft>[] = [
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
      width: 110,
      valueType: 'select',
      valueEnum: {
        MARKET: { text: t('visibilityMap.MARKET') },
        PRIVATE: { text: t('visibilityMap.PRIVATE') },
      },
      render: (_, r) => renderVisibility(r.visibility),
    },
    {
      title: t('status'),
      dataIndex: 'status',
      width: 130,
      valueType: 'select',
      valueEnum: Object.fromEntries(['DRAFT', 'PENDING_REVIEW', 'REJECTED', 'CONSUMED'].map((s) => [s, { text: statusLabel(s) }])),
      render: (_, r) => renderStatus(r.status),
    },
    { title: t('description'), dataIndex: 'description', ellipsis: true, search: false },
    { title: t('contentHash'), dataIndex: 'contentHash', width: 140, ellipsis: true, search: false },
    {
      title: '资源数',
      dataIndex: 'resourceCount',
      width: 90,
      search: false,
      render: (_, r) => <Tag>{r.resourceCount ?? 0} 个文件</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'groupKey',
      width: 160,
      search: false,
      render: (_, r) =>
        r.groupKey ? (
          <Tag color="geekblue">{r.groupKey}</Tag>
        ) : (
          <span style={{ color: '#999' }}>手动</span>
        ),
    },
    { title: t('updatedAt'), dataIndex: 'updatedAt', width: 170, valueType: 'dateTime', search: false },
    actionColumn,
  ];

  const releaseColumns: ProColumns<SkillRelease>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    {
      title: t('name'),
      dataIndex: 'name',
      width: 180,
      render: (_, r) => (
        <a onClick={() => setViewRelease(r)}>{r.name}</a>
      ),
    },
    { title: t('version'), dataIndex: 'version', width: 80, search: false },
    {
      title: '资源数',
      dataIndex: 'resourceCount',
      width: 90,
      search: false,
      render: (_, r) => <Tag>{r.resourceCount ?? 0} 个文件</Tag>,
    },
    {
      title: t('visibility'),
      dataIndex: 'visibility',
      width: 100,
      render: (_, r) => renderVisibility(r.visibility),
    },
    {
      title: t('status'),
      dataIndex: 'status',
      width: 120,
      valueType: 'select',
      valueEnum: Object.fromEntries(['PUBLISHED', 'DEPRECATED'].map((s) => [s, { text: statusLabel(s) }])),
      render: (_, r) => renderStatus(r.status),
    },
    { title: t('description'), dataIndex: 'description', ellipsis: true, search: false },
    { title: t('createdAt'), dataIndex: 'createdAt', width: 170, valueType: 'dateTime', search: false },
  ];

  const marketColumns = [
    { title: t('id'), dataIndex: 'id', width: 70 },
    {
      title: t('name'),
      dataIndex: 'name',
      width: 200,
      render: (_: unknown, r: SkillRelease) => <a onClick={() => setViewRelease(r)}>{r.name}</a>,
    },
    { title: t('version'), dataIndex: 'version', width: 90 },
    {
      title: '资源数',
      dataIndex: 'resourceCount',
      width: 100,
      render: (_: unknown, r: SkillRelease) => <Tag>{r.resourceCount ?? 0} 个文件</Tag>,
    },
    { title: t('description'), dataIndex: 'description', ellipsis: true },
    {
      title: t('action'),
      key: 'action',
      width: 130,
      render: (_: unknown, r: SkillRelease) => (
        <a
          style={{ color: '#ff4d4f' }}
          onClick={() =>
            confirm(
              t('confirmTakeDown'),
              () =>
                run('deleteFailed', async () => {
                  await takeDownSkillMarketApi(r.id);
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
        <ProTable<SkillDraft>
          rowKey="id"
          headerTitle={t('tabDrafts')}
          actionRef={actionRef}
          columns={draftColumns}
          request={fetchDraftRows}
          search={{ labelWidth: 'auto' }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (v) => t('total', { total: v }) }}
          scroll={{ x: 1300 }}
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
        <ProTable<SkillRelease>
          rowKey="id"
          headerTitle={t('tabReleases')}
          actionRef={marketRef}
          columns={releaseColumns}
          request={fetchReleaseRows}
          search={{ labelWidth: 'auto' }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true, showTotal: (v) => t('total', { total: v }) }}
          scroll={{ x: 1000 }}
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
          <Table<SkillRelease> rowKey="id" columns={marketColumns as never} dataSource={marketRows} pagination={false} />
        </Space>
      ),
    },
    {
      key: 'git',
      label: t('tabGit'),
      children: <GitSourcePanel />,
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
      <SkillFormDrawer open={drawerOpen} row={editing} onClose={() => setDrawerOpen(false)} onSaved={reload} />
      <SkillReleaseDetailDrawer open={!!viewRelease} release={viewRelease} onClose={() => setViewRelease(null)} />
      <Modal
        title={t('reject')}
        open={rejectId !== null}
        onCancel={() => setRejectId(null)}
        onOk={async () => {
          if (rejectId === null) return;
          try {
            await rejectSkillDraftApi(rejectId, rejectReason);
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
        <TextArea
          rows={3}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder={t('rejectReasonPlaceholder')}
        />
      </Modal>
    </ContentContainer>
  );
};

export default SkillPage;
