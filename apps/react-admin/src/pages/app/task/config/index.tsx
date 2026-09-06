import { useRef, useState } from 'react';
import { Button, Modal, Popconfirm, Space, Tag } from 'antd';
import { message } from '@/core/feedback/message';
import {
  DeleteOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ThunderboltOutlined } from '@ant-design/icons';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import {
  batchTaskConfigApi,
  deleteTaskConfigApi,
  listTaskConfigApi,
  triggerTaskConfigApi,
  updateTaskConfigApi } from '@/api/rest/task-config';
import type { TaskConfig, TaskConfigBatchAction } from '@/api/rest/types';
import { useDictLookups } from '@/api/hooks/dict';
import { useListTaskQueues, useListTaskWorkflowTypes } from '@/api/hooks/task-config';
import { getApiErrorMessage } from '../modules/error-message';
import { notifyTaskExecutionChanged } from '../modules/events';
import TaskConfigDrawer from './modules/config-drawer';

function statusOrUndefined(v: number | '' | undefined): 0 | 1 | undefined {
  if (v === '' || v === undefined) return undefined;
  return Number(v) as 0 | 1;
}

/** 任务配置列表面板（供「任务调度」页 Tab 嵌入） */
export function TaskConfigPanel() {
  const { t } = useTranslation('task');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [bulkLoading, setBulkLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<TaskConfig | null>(null);
  const dictLookups = useDictLookups({ typeCodes: ['sys_switch_status'] });
  const { data: workflowTypeOptions = [], isLoading: workflowTypesLoading } =
    useListTaskWorkflowTypes();
  const { data: taskQueueOptions = [], isLoading: taskQueuesLoading } = useListTaskQueues();

  async function fetchRows(params: {
    current?: number;
    pageSize?: number;
    code?: string;
    name?: string;
    isEnabled?: number | '';
    workflowType?: string;
    taskQueue?: string;
  }) {
    const {
      current = 1,
      pageSize = 20,
      code,
      name,
      isEnabled,
      workflowType,
      taskQueue,
    } = params;
    const res = await listTaskConfigApi({
      page: current,
      pageSize,
      code: code || undefined,
      name: name || undefined,
      status: statusOrUndefined(isEnabled),
      workflowType: workflowType || undefined,
      taskQueue: taskQueue || undefined,
    });
    return { data: res.items, total: res.total, success: true };
  }

  const reload = () => {
    actionRef.current?.reload?.();
  };

  const handleTrigger = async (row: TaskConfig) => {
    // 与 Vue 一致：禁用配置前端先拦截，避免仅展示英文 mock 原文
    if (row.isEnabled !== 1) {
      message.error(t('triggerDisabledHint'));
      return;
    }
    try {
      await triggerTaskConfigApi(row.id);
      message.success(t('triggerSuccess'));
      // 通知执行记录 Tab 重新拉数（keep-alive 下不会自动刷新）
      notifyTaskExecutionChanged();
    } catch (err) {
      // 禁用配置等场景：展示 mock 返回的 error 明细
      message.error(`${t('triggerFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handleToggleEnabled = async (row: TaskConfig) => {
    const next: 0 | 1 = row.isEnabled === 1 ? 0 : 1;
    try {
      await updateTaskConfigApi({ id: row.id, isEnabled: next });
      message.success(next === 1 ? t('enableSuccess') : t('disableSuccess'));
      reload();
    } catch (err) {
      message.error(`${t('updateStatusFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handleDelete = (row: TaskConfig) => {
    Modal.confirm({
      title: t('deleteConfirmTitle'),
      content: t('deleteConfirmDesc', { name: row.name }),
      okText: t('delete'),
      cancelText: t('cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteTaskConfigApi(row.id);
          message.success(t('deleteSuccess'));
          reload();
        } catch (err) {
          message.error(`${t('deleteFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
        }
      },
    });
  };

  const runBulk = async (action: TaskConfigBatchAction) => {
    if (selectedRowKeys.length === 0) {
      message.warning(t('selectFirst'));
      return;
    }
    setBulkLoading(true);
    try {
      const result = await batchTaskConfigApi({
        action,
        ids: selectedRowKeys.map((k) => Number(k)),
      });
      const successKey =
        action === 'delete'
          ? 'bulkDeleteSuccess'
          : action === 'enable'
            ? 'bulkEnableSuccess'
            : action === 'disable'
              ? 'bulkDisableSuccess'
              : 'bulkTriggerSuccess';
      message.success(
        t(successKey, {
          count: result.affected,
          skipped: result.skippedDisabled?.length ?? 0,
        }),
      );
      setSelectedRowKeys([]);
      reload();
      if (action === 'trigger') notifyTaskExecutionChanged();
    } catch (err) {
      message.error(`${t('bulkFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setBulkLoading(false);
    }
  };

  const confirmBulk = (action: TaskConfigBatchAction) => {
    if (selectedRowKeys.length === 0) {
      message.warning(t('selectFirst'));
      return;
    }
    const titleKey =
      action === 'delete'
        ? 'bulkDeleteConfirmTitle'
        : action === 'enable'
          ? 'bulkEnableConfirmTitle'
          : action === 'disable'
            ? 'bulkDisableConfirmTitle'
            : 'bulkTriggerConfirmTitle';
    Modal.confirm({
      title: t(titleKey),
      content: t('bulkConfirmDesc', { count: selectedRowKeys.length }),
      okText: t('confirm'),
      cancelText: t('cancel'),
      okButtonProps: action === 'delete' ? { danger: true } : undefined,
      onOk: () => runBulk(action),
    });
  };
   
  const renderActions = (
    _: unknown,
    record: TaskConfig,
    __: unknown,
    _action?: ActionType,
  ) => [
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
    <Popconfirm
      key="trigger"
      title={t('triggerConfirmTitle')}
      description={t('triggerConfirmDesc', { name: record.name })}
      okText={t('confirm')}
      cancelText={t('cancel')}
      onConfirm={() => handleTrigger(record)}
    >
      <a
        onClick={(e) => e.stopPropagation()}
        style={record.isEnabled === 0 ? { color: '#999' } : undefined}
      >
        {t('trigger')}
      </a>
    </Popconfirm>,
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
  ];

  const columns: ProColumns<TaskConfig>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    {
      title: t('code'),
      dataIndex: 'code',
      width: 140,
      ellipsis: true,
      fieldProps: { placeholder: t('codeFilterPlaceholder') },
    },
    {
      title: t('name'),
      dataIndex: 'name',
      width: 140,
      ellipsis: true,
      fieldProps: { placeholder: t('nameFilterPlaceholder') },
    },
    {
      title: t('workflowType'),
      dataIndex: 'workflowType',
      width: 180,
      ellipsis: true,
      valueType: 'select',
      fieldProps: {
        options: workflowTypeOptions,
        showSearch: true,
        allowClear: true,
        optionFilterProp: 'label',
        loading: workflowTypesLoading,
        placeholder: t('workflowTypeFilterPlaceholder'),
      },
    },
    {
      title: t('taskQueue'),
      dataIndex: 'taskQueue',
      width: 120,
      ellipsis: true,
      valueType: 'select',
      fieldProps: {
        options: taskQueueOptions,
        showSearch: true,
        allowClear: true,
        optionFilterProp: 'label',
        loading: taskQueuesLoading,
        placeholder: t('taskQueueFilterPlaceholder'),
      },
    },
    {
      title: t('cronExpr'),
      dataIndex: 'cronExpr',
      width: 140,
      ellipsis: true,
      search: false,
      render: (_, r) =>
        r.cronExpr ? (
          <span title={r.cronExpr}>{r.cronExpr}</span>
        ) : (
          <span style={{ color: '#999' }}>{t('manualOnly')}</span>
        ),
    },
    {
      title: t('timeoutSeconds'),
      dataIndex: 'timeoutSeconds',
      width: 100,
      search: false,
      render: (_, r) =>
        r.timeoutSeconds !== null && r.timeoutSeconds !== undefined ? (
          r.timeoutSeconds
        ) : (
          <span style={{ color: '#999' }}>—</span>
        ),
    },
    {
      title: t('status'),
      dataIndex: 'isEnabled',
      width: 90,
      valueType: 'select',
      valueEnum: dictLookups.switchValueEnum,
      render: (_, r) => {
        const tagType = dictLookups.lookupSwitchTagType(r.isEnabled);
        return (
          <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
            {dictLookups.lookupSwitchLabel(r.isEnabled)}
          </Tag>
        );
      },
    },
    {
      title: t('action'),
      valueType: 'option',
      key: 'option',
      width: 220,
      fixed: 'right',
      render: renderActions,
    },
  ];

  const toolbar = () => [
    <Button
      key="create"
      type="primary"
      icon={<PlusOutlined />}
      onClick={() => {
        setEditing(null);
        setDrawerOpen(true);
      }}
    >
      {t('createConfig')}
    </Button>,
  ];

  const renderAlert = ({
    selectedRowKeys: keys,
    onCleanSelected,
  }: {
    selectedRowKeys: React.Key[];
    onCleanSelected: () => void;
  }) => (
    <Space>
      <span>
        {t('selectedCount', { count: keys.length })}
        <a style={{ marginInlineStart: 8 }} onClick={onCleanSelected}>
          {t('clearSelection')}
        </a>
      </span>
      <Button
        size="small"
        icon={<PlayCircleOutlined />}
        loading={bulkLoading}
        onClick={() => confirmBulk('enable')}
      >
        {t('bulkEnable')}
      </Button>
      <Button
        size="small"
        icon={<PauseCircleOutlined />}
        loading={bulkLoading}
        onClick={() => confirmBulk('disable')}
      >
        {t('bulkDisable')}
      </Button>
      <Button
        size="small"
        icon={<ThunderboltOutlined />}
        loading={bulkLoading}
        onClick={() => confirmBulk('trigger')}
      >
        {t('bulkTrigger')}
      </Button>
      <Button
        size="small"
        danger
        icon={<DeleteOutlined />}
        loading={bulkLoading}
        onClick={() => confirmBulk('delete')}
      >
        {t('bulkDelete')}
      </Button>
    </Space>
  );

  return (
    <>
      <ProTable<TaskConfig>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={fetchRows}
        search={{ labelWidth: 'auto', defaultCollapsed: false }}
        pagination={{ defaultPageSize: 20, showSizeChanger: true }}
        options={{ density: true, reload: true, setting: true }}
        toolBarRender={toolbar}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
          preserveSelectedRowKeys: true,
        }}
        tableAlertRender={renderAlert}
        tableAlertOptionRender={false}
        dateFormatter="string"
        headerTitle={t('configListTitle')}
        scroll={{ x: 1200 }}
      />
      <TaskConfigDrawer
        open={drawerOpen}
        row={editing}
        onClose={() => {
          setDrawerOpen(false);
          setEditing(null);
        }}
        onSaved={reload}
      />
    </>
  );
}

export default TaskConfigPanel;
