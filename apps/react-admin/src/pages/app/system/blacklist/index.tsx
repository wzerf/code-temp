import { useRef, useState } from 'react';
import { Button, Modal, Space, Tag, Typography } from 'antd';
import { message } from '@/core/feedback/message';
import {
  DeleteOutlined,
  PlusOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import {
  batchBlacklistApi,
  deleteBlacklistApi,
  listBlacklistApi,
  updateBlacklistApi } from '@/api/rest/blacklist';
import type {
  Blacklist,
  BlacklistBatchAction,
  BlacklistScope,
  BlacklistTargetType } from '@/api/rest/types';
import { useDictLookups } from '@/api/hooks/dict';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import BlacklistFormDrawer from './modules/blacklist-form-drawer';
import { getApiErrorMessage } from './modules/error-message';

function statusOrUndefined(v: number | '' | undefined): 0 | 1 | undefined {
  if (v === '' || v === undefined) return undefined;
  return Number(v) as 0 | 1;
}

const BlacklistPage = () => {
  const { t } = useTranslation('blacklist');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [bulkLoading, setBulkLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Blacklist | null>(null);
  const dictLookups = useDictLookups({ typeCodes: ['sys_switch_status'] });

  const reload = () => {
    actionRef.current?.reload?.();
  };

  async function fetchRows(params: {
    current?: number;
    pageSize?: number;
    targetType?: string;
    targetValue?: string;
    scope?: string;
    isEnabled?: number | '';
  }) {
    const {
      current = 1,
      pageSize = 20,
      targetType,
      targetValue,
      scope,
      isEnabled,
    } = params;
    const res = await listBlacklistApi({
      page: current,
      pageSize,
      targetType: targetType || undefined,
      targetValue: targetValue || undefined,
      scope: scope || undefined,
      status: statusOrUndefined(isEnabled),
    });
    return { data: res.items, total: res.total, success: true };
  }

  const handleToggleEnabled = async (row: Blacklist) => {
    const next: 0 | 1 = row.isEnabled === 1 ? 0 : 1;
    try {
      await updateBlacklistApi({ id: row.id, isEnabled: next });
      message.success(next === 1 ? t('enableSuccess') : t('disableSuccess'));
      reload();
    } catch (err) {
      message.error(
        `${t('updateStatusFailed')}：${getApiErrorMessage(err, t('unknownError'))}`,
      );
    }
  };

  const handleDelete = (row: Blacklist) => {
    Modal.confirm({
      title: t('deleteConfirmTitle'),
      content: t('deleteConfirmDesc', { value: row.targetValue }),
      okText: t('delete'),
      cancelText: t('cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteBlacklistApi(row.id);
          message.success(t('deleteSuccess'));
          reload();
        } catch (err) {
          message.error(
            `${t('deleteFailed')}：${getApiErrorMessage(err, t('unknownError'))}`,
          );
        }
      },
    });
  };

  const runBulk = async (action: BlacklistBatchAction) => {
    if (selectedRowKeys.length === 0) {
      message.warning(t('selectFirst'));
      return;
    }
    setBulkLoading(true);
    try {
      const result = await batchBlacklistApi({
        action,
        ids: selectedRowKeys.map((k) => Number(k)),
      });
      const successKey =
        action === 'delete'
          ? 'bulkDeleteSuccess'
          : action === 'enable'
            ? 'bulkEnableSuccess'
            : 'bulkDisableSuccess';
      message.success(t(successKey, { count: result.affected }));
      setSelectedRowKeys([]);
      reload();
    } catch (err) {
      message.error(
        `${t('bulkFailed')}：${getApiErrorMessage(err, t('unknownError'))}`,
      );
    } finally {
      setBulkLoading(false);
    }
  };

  const confirmBulk = (action: BlacklistBatchAction) => {
    if (selectedRowKeys.length === 0) {
      message.warning(t('selectFirst'));
      return;
    }
    const titleKey =
      action === 'delete'
        ? 'bulkDeleteConfirmTitle'
        : action === 'enable'
          ? 'bulkEnableConfirmTitle'
          : 'bulkDisableConfirmTitle';
    Modal.confirm({
      title: t(titleKey),
      content: t('bulkConfirmDesc', { count: selectedRowKeys.length }),
      okText: t('confirm'),
      cancelText: t('cancel'),
      okButtonProps: action === 'delete' ? { danger: true } : undefined,
      onOk: () => runBulk(action),
    });
  };

  const targetTypeValueEnum: Record<BlacklistTargetType, { text: string }> = {
    IP: { text: t('targetTypeMap.IP') },
    SYS_USER: { text: t('targetTypeMap.SYS_USER') },
    DEVICE: { text: t('targetTypeMap.DEVICE') },
  };
  const scopeValueEnum: Record<BlacklistScope, { text: string }> = {
    LOGIN: { text: t('scopeMap.LOGIN') },
    API: { text: t('scopeMap.API') },
    ALL: { text: t('scopeMap.ALL') },
  };

  const renderStatus = (_: unknown, r: Blacklist) => {
    const tagType = dictLookups.lookupSwitchTagType(r.isEnabled);
    return (
      <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
        {dictLookups.lookupSwitchLabel(r.isEnabled)}
      </Tag>
    );
  };

  const columns: ProColumns<Blacklist>[] = [
    { title: t('id'), dataIndex: 'id', width: 70, search: false },
    {
      title: t('targetType'),
      dataIndex: 'targetType',
      width: 100,
      valueType: 'select',
      valueEnum: targetTypeValueEnum,
      fieldProps: {
        allowClear: true,
        placeholder: t('targetTypeFilterPlaceholder'),
      },
      render: (_, r) => {
        const key = r.targetType as BlacklistTargetType;
        return targetTypeValueEnum[key]?.text ?? r.targetType;
      },
    },
    {
      title: t('targetValue'),
      dataIndex: 'targetValue',
      width: 160,
      ellipsis: true,
      fieldProps: { placeholder: t('targetValueFilterPlaceholder') },
    },
    {
      title: t('scope'),
      dataIndex: 'scope',
      width: 110,
      valueType: 'select',
      valueEnum: scopeValueEnum,
      fieldProps: {
        allowClear: true,
        placeholder: t('scopeFilterPlaceholder'),
      },
      render: (_, r) => {
        const key = r.scope as BlacklistScope;
        return scopeValueEnum[key]?.text ?? r.scope;
      },
    },
    {
      title: t('startsAt'),
      dataIndex: 'startsAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: t('expiresAt'),
      dataIndex: 'expiresAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, r) =>
        r.expiresAt ? (
          r.expiresAt
        ) : (
          <span style={{ color: '#999' }}>{t('neverExpires')}</span>
        ),
    },
    {
      title: t('reason'),
      dataIndex: 'reason',
      ellipsis: true,
      search: false,
      render: (_, r) =>
        r.reason ? (
          <span title={r.reason}>{r.reason}</span>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: t('remark'),
      dataIndex: 'remark',
      ellipsis: true,
      search: false,
      render: (_, r) =>
        r.remark ? (
          <span title={r.remark}>{r.remark}</span>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: t('status'),
      dataIndex: 'isEnabled',
      width: 90,
      valueType: 'select',
      valueEnum: dictLookups.switchValueEnum,
      render: renderStatus,
    },
    {
      title: t('updatedAt'),
      dataIndex: 'updatedAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: t('action'),
      valueType: 'option',
      key: 'option',
      width: 160,
      fixed: 'right',
      search: false,
       
      render: (_text, record) => [
        <a
          key="edit"
          onClick={(e) => {
            e.stopPropagation();
            setEditing(record);
            setDrawerOpen(true);
          }}
        >
          {t('edit')}
        </a>,
        <a
          key="toggle"
          onClick={(e) => {
            e.stopPropagation();
            void handleToggleEnabled(record);
          }}
        >
          {record.isEnabled === 1 ? t('disable') : t('enable')}
        </a>,
        <a
          key="delete"
          style={{ color: '#ff4d4f' }}
          onClick={(e) => {
            e.stopPropagation();
            handleDelete(record);
          }}
        >
          {t('delete')}
        </a>,
      ],
    },
  ];

  const renderAlert = ({
    selectedRowKeys: keys,
    onCleanSelected,
  }: {
    selectedRowKeys: React.Key[];
    onCleanSelected: () => void;
  }) => {
    const count = keys.length;
    return (
      <Space size={8}>
        <Typography.Text>
          {t('selectedCount', { count })}
        </Typography.Text>
        <Button
          size="small"
          loading={bulkLoading}
          disabled={count === 0}
          onClick={() => confirmBulk('enable')}
        >
          {t('bulkEnable')}
        </Button>
        <Button
          size="small"
          loading={bulkLoading}
          disabled={count === 0}
          onClick={() => confirmBulk('disable')}
        >
          {t('bulkDisable')}
        </Button>
        <Button
          size="small"
          danger
          ghost
          icon={<DeleteOutlined />}
          loading={bulkLoading}
          disabled={count === 0}
          onClick={() => confirmBulk('delete')}
        >
          {t('bulkDelete')}
        </Button>
        <Button size="small" type="text" onClick={onCleanSelected}>
          {t('clearSelection')}
        </Button>
      </Space>
    );
  };

  return (
    <ContentContainer scrollable>
      <ProTable<Blacklist>
        rowKey="id"
        headerTitle={t('pageTitle')}
        actionRef={actionRef}
        columns={columns}
        request={fetchRows}
        search={{ labelWidth: 'auto' }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (total) => t('total', { total }),
        }}
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
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys),
          preserveSelectedRowKeys: true,
        }}
        tableAlertRender={renderAlert}
        tableAlertOptionRender={false}
        dateFormatter="string"
      />
      <BlacklistFormDrawer
        open={drawerOpen}
        row={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={reload}
      />
    </ContentContainer>
  );
};

export default BlacklistPage;
