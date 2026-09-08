import { useEffect, useRef, useState } from 'react';
import { Tag } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { useTranslation } from 'react-i18next';
import { listTaskConfigApi } from '@/api/rest/task-config';
import { listTaskExecutionApi } from '@/api/rest/task-execution';
import type { TaskExecution, TaskExecutionStatus } from '@/api/rest/types';
import { useListTaskWorkflowTypes } from '@/api/hooks/task-config';
import { useProTableScrollY } from '@/hooks/useProTableScrollY';
import { onTaskExecutionChanged } from '../modules/events';
import TaskExecutionDetailDrawer from './modules/detail-drawer';
import { formatDateTime } from '@/utils/date';
import { formatDuration, statusColor, statusLabelKey } from './modules/shared';

const STATUS_OPTIONS: TaskExecutionStatus[] = [
  'PENDING',
  'RUNNING',
  'RETRYING',
  'COMPLETED',
  'FAILED',
  'CANCELLED',
  'TERMINATED',
  'TIMED_OUT',
  'CONTINUED_AS_NEW',
];

/** 执行记录列表面板（供「任务调度」页 Tab 嵌入；无删除入口） */
export function TaskExecutionPanel() {
  const { t } = useTranslation('task');
  const actionRef = useRef<ActionType | undefined>(undefined);
  const tableWrapRef = useRef<HTMLDivElement | null>(null);
  const tableScrollY = useProTableScrollY(tableWrapRef);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRow, setDetailRow] = useState<TaskExecution | null>(null);
  const [configOptions, setConfigOptions] = useState<{ label: string; value: number }[]>([]);
  const { data: workflowTypeOptions = [], isLoading: workflowTypesLoading } =
    useListTaskWorkflowTypes();

  useEffect(() => {
    listTaskConfigApi({ page: 1, pageSize: 200 })
      .then((res) => {
        setConfigOptions(
          (res.items ?? []).map((c) => ({
            label: `${c.name}（${c.code}）`,
            value: c.id,
          })),
        );
      })
      .catch(() => undefined);
  }, []);

  // 配置 Tab 触发成功后刷新本表（双 Tab keep-alive）
  useEffect(() => onTaskExecutionChanged(() => {
    actionRef.current?.reload?.();
  }), []);

  const openDetail = (row: TaskExecution) => {
    setDetailRow(row);
    setDetailOpen(true);
  };

  async function fetchRows(params: {
    current?: number;
    pageSize?: number;
    configId?: number;
    status?: string;
    startedAt?: [string, string];
    workflowType?: string;
  }) {
    const { current = 1, pageSize = 20, configId, status, startedAt, workflowType } = params;
    const res = await listTaskExecutionApi({
      page: current,
      pageSize,
      configId: configId || undefined,
      status: status || undefined,
      startedAtFrom: startedAt?.[0],
      startedAtTo: startedAt?.[1],
      workflowType: workflowType || undefined,
    });
    return { data: res.items, total: res.total, success: true };
  }

  const statusLabel = (status: string) => {
    const key = statusLabelKey(status);
    const label = t(key);
    return label === key ? status : label;
  };

  const statusValueEnum = Object.fromEntries(
    STATUS_OPTIONS.map((s) => [s, { text: statusLabel(s) }]),
  );

  const columns: ProColumns<TaskExecution>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    {
      title: t('configName'),
      dataIndex: 'configId',
      width: 140,
      ellipsis: true,
      valueType: 'select',
      fieldProps: {
        options: configOptions,
        showSearch: true,
        allowClear: true,
        optionFilterProp: 'label',
        placeholder: t('configFilterPlaceholder'),
      },
      render: (_, r) =>
        r.configName ? (
          r.configName
        ) : (
          <span style={{ color: '#999' }}>—</span>
        ),
    },
    {
      title: t('workflowType'),
      dataIndex: 'workflowType',
      width: 160,
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
      title: t('workflowId'),
      dataIndex: 'workflowId',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: t('runId'),
      dataIndex: 'runId',
      width: 140,
      ellipsis: true,
      search: false,
    },
    {
      title: t('execStatus'),
      dataIndex: 'status',
      width: 140,
      valueType: 'select',
      valueEnum: statusValueEnum,
      render: (_, r) => (
        <Tag color={statusColor(r.status)}>{statusLabel(r.status)}</Tag>
      ),
    },
    {
      title: t('retryCount'),
      dataIndex: 'retryCount',
      width: 90,
      search: false,
      render: (_, r) => r.retryCount ?? 0,
    },
    {
      title: t('startedAt'),
      dataIndex: 'startedAt',
      width: 180,
      valueType: 'dateTimeRange',
      search: {
        transform: (value: [string, string]) => ({
          startedAt: value,
        }),
      },
      render: (_, r) => formatDateTime(r.startedAt),
    },
    {
      title: t('closedAt'),
      dataIndex: 'closedAt',
      width: 180,
      search: false,
      render: (_, r) =>
        r.closedAt ? formatDateTime(r.closedAt) : <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: t('waitDuration'),
      dataIndex: 'waitDuration',
      width: 100,
      search: false,
      render: (_, r) => formatDuration(r.pendingAt, r.startedAt),
    },
    {
      title: t('duration'),
      dataIndex: 'duration',
      width: 100,
      search: false,
      render: (_, r) => formatDuration(r.startedAt, r.closedAt),
    },
    {
      title: t('failureReason'),
      dataIndex: 'failureReason',
      width: 200,
      search: false,
      ellipsis: true,
      render: (_, r) =>
        r.failureReason || <span style={{ color: '#999' }}>-</span>,
    },
  ];

  return (
    <>
      <div ref={tableWrapRef} className="task-table-wrap">
        <ProTable<TaskExecution>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          request={fetchRows}
          // 与日志审计一致：点击行打开详情
          onRow={(record) => ({
            onClick: () => openDetail(record),
            style: { cursor: 'pointer' },
          })}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true }}
          options={{ density: true, reload: true, setting: true }}
          dateFormatter="string"
          headerTitle={t('executionListTitle')}
          scroll={{ x: 1400, y: tableScrollY }}
          // 明确不提供删除 / 批量删除
        />
      </div>
      <TaskExecutionDetailDrawer
        open={detailOpen}
        row={detailRow}
        onClose={() => {
          setDetailOpen(false);
          setDetailRow(null);
        }}
      />
    </>
  );
}

export default TaskExecutionPanel;
