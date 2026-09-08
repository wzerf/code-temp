import { useRef, useState } from 'react';
import { Tag } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { listApiLogsApi } from '@/api/rest/api-log';
import type { ApiLogListItem, ApiLogSource } from '@/api/rest/types';
import { useProTableScrollY } from '@/hooks/useProTableScrollY';
import { formatDateTime } from '@/utils/date';
import ApiLogDetailDrawer from './modules/detail-drawer';

const METHOD_OPTIONS = {
  GET: { text: 'GET' },
  POST: { text: 'POST' },
  PUT: { text: 'PUT' },
  DELETE: { text: 'DELETE' },
  PATCH: { text: 'PATCH' },
};

function methodColor(method: string) {
  const m = (method || '').toUpperCase();
  if (m === 'GET') return 'blue';
  if (m === 'POST') return 'green';
  if (m === 'PUT' || m === 'PATCH') return 'orange';
  if (m === 'DELETE') return 'red';
  return 'default';
}

/** API 日志列表面板（供「日志审计」页 Tab 嵌入） */
export function ApiLogPanel() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const scrollY = useProTableScrollY(wrapRef);
  const [source, setSource] = useState<ApiLogSource>('hot');
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRow, setDetailRow] = useState<ApiLogListItem | null>(null);

  const openDetail = (row: ApiLogListItem) => {
    setDetailRow(row);
    setDetailOpen(true);
  };

  async function fetchRows(params: {
    current?: number;
    pageSize?: number;
    method?: string;
    module?: string;
    path?: string;
    success?: 0 | 1;
    username?: string;
    clientIp?: string;
    requestId?: string;
    createdAt?: [string, string];
  }) {
    const {
      current = 1,
      pageSize = 20,
      method,
      module,
      path,
      success,
      username,
      clientIp,
      requestId,
      createdAt,
    } = params;
    const res = await listApiLogsApi({
      page: current,
      pageSize,
      source,
      method: method || undefined,
      module: module || undefined,
      path: path || undefined,
      success,
      username: username || undefined,
      clientIp: clientIp || undefined,
      requestId: requestId || undefined,
      createdAtFrom: createdAt?.[0],
      createdAtTo: createdAt?.[1],
    });
    return { data: res.items, total: res.total, success: true };
  }

  const columns: ProColumns<ApiLogListItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    {
      title: '方法',
      dataIndex: 'method',
      width: 90,
      valueType: 'select',
      valueEnum: METHOD_OPTIONS,
      render: (_, r) => <Tag color={methodColor(r.method)}>{r.method}</Tag>,
    },
    { title: '模块', dataIndex: 'module', width: 100, ellipsis: true },
    { title: '路径', dataIndex: 'path', width: 220, ellipsis: true },
    {
      title: '状态码',
      dataIndex: 'statusCode',
      width: 90,
      search: false,
      render: (_, r) => r.statusCode ?? '-',
    },
    {
      title: '结果',
      dataIndex: 'success',
      width: 90,
      valueType: 'select',
      valueEnum: {
        1: { text: '成功', status: 'Success' },
        0: { text: '失败', status: 'Error' },
      },
      render: (_, r) => (
        <Tag color={r.success === 1 ? 'success' : 'error'}>
          {r.success === 1 ? '成功' : '失败'}
        </Tag>
      ),
    },
    {
      title: '耗时(ms)',
      dataIndex: 'costTime',
      width: 100,
      search: false,
    },
    { title: '用户', dataIndex: 'username', width: 110, ellipsis: true },
    {
      title: '客户端 IP',
      dataIndex: 'clientIp',
      width: 140,
      ellipsis: true,
    },
    {
      title: '请求 ID',
      dataIndex: 'requestId',
      width: 180,
      ellipsis: true,
      hideInTable: true,
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      valueType: 'dateTimeRange',
      search: {
        transform: (value: [string, string]) => ({
          createdAt: value,
        }),
      },
      render: (_, r) => formatDateTime(r.createdAt),
    },
    {
      title: '失败原因',
      dataIndex: 'reason',
      width: 160,
      search: false,
      ellipsis: true,
      render: (_, r) => r.reason || <span style={{ color: '#999' }}>-</span>,
    },
  ];

  return (
    <>
      <div ref={wrapRef} className="table-wrap">
        <ProTable<ApiLogListItem>
          actionRef={actionRef}
          rowKey="id"
          columns={columns}
          request={fetchRows}
          params={{ source }}
          onRow={(record) => ({
            onClick: () => openDetail(record),
            style: { cursor: 'pointer' },
          })}
          search={{ labelWidth: 'auto', defaultCollapsed: false }}
          pagination={{ defaultPageSize: 20, showSizeChanger: true }}
          options={{ density: true, reload: true, setting: true }}
          scroll={{ y: scrollY }}
          toolbar={{
            menu: {
              type: 'tab',
              activeKey: source,
              items: [
                { key: 'hot', label: '热表' },
                { key: 'archive', label: '归档' },
              ],
              onChange: (key) => {
                setSource((key as ApiLogSource) || 'hot');
                actionRef.current?.reload?.();
              },
            },
          }}
          dateFormatter="string"
          headerTitle="API 日志"
        />
      </div>
      <ApiLogDetailDrawer
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

export default ApiLogPanel;
