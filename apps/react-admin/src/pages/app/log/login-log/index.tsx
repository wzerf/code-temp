import { useRef, useState } from 'react';
import { Tag } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { listLoginLogsApi } from '@/api/rest/login-log';
import type { LoginLogListItem, LoginLogSource } from '@/api/rest/types';
import { useProTableScrollY } from '@/hooks/useProTableScrollY';
import { formatDateTime } from '@/utils/date';
import LoginLogDetailDrawer from './modules/detail-drawer';

const LOGIN_METHOD_OPTIONS = {
  PASSWORD: { text: 'PASSWORD' },
  SSO: { text: 'SSO' },
  OAUTH: { text: 'OAUTH' },
  SMS: { text: 'SMS' },
};

/** 登录日志列表面板（供「日志审计」页 Tab 嵌入） */
export function LoginLogPanel() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const scrollY = useProTableScrollY(wrapRef);
  const [source, setSource] = useState<LoginLogSource>('hot');
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailRow, setDetailRow] = useState<LoginLogListItem | null>(null);

  const openDetail = (row: LoginLogListItem) => {
    setDetailRow(row);
    setDetailOpen(true);
  };

  async function fetchRows(params: {
    current?: number;
    pageSize?: number;
    username?: string;
    success?: 0 | 1;
    loginMethod?: string;
    loginIp?: string;
    loginTime?: [string, string];
  }) {
    const {
      current = 1,
      pageSize = 20,
      username,
      success,
      loginMethod,
      loginIp,
      loginTime,
    } = params;
    const res = await listLoginLogsApi({
      page: current,
      pageSize,
      source,
      username: username || undefined,
      success,
      loginMethod: loginMethod || undefined,
      loginIp: loginIp || undefined,
      loginTimeFrom: loginTime?.[0],
      loginTimeTo: loginTime?.[1],
    });
    return { data: res.items, total: res.total, success: true };
  }

  const columns: ProColumns<LoginLogListItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '用户名', dataIndex: 'username', width: 120, ellipsis: true },
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
      title: '登录方式',
      dataIndex: 'loginMethod',
      width: 110,
      valueType: 'select',
      valueEnum: LOGIN_METHOD_OPTIONS,
    },
    { title: '登录 IP', dataIndex: 'loginIp', width: 140, ellipsis: true },
    {
      title: '浏览器',
      dataIndex: 'browserName',
      width: 140,
      search: false,
      ellipsis: true,
      render: (_, r) =>
        [r.browserName, r.browserVersion].filter(Boolean).join(' ') || (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '操作系统',
      dataIndex: 'osName',
      width: 140,
      search: false,
      ellipsis: true,
      render: (_, r) =>
        [r.osName, r.osVersion].filter(Boolean).join(' ') || (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      width: 180,
      valueType: 'dateTimeRange',
      search: {
        transform: (value: [string, string]) => ({
          loginTime: value,
        }),
      },
      render: (_, r) => formatDateTime(r.loginTime),
    },
    {
      title: '地理位置',
      dataIndex: 'location',
      width: 120,
      search: false,
      ellipsis: true,
      render: (_, r) => r.location || <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '失败原因',
      dataIndex: 'reason',
      width: 180,
      search: false,
      ellipsis: true,
      render: (_, r) => r.reason || <span style={{ color: '#999' }}>-</span>,
    },
  ];

  return (
    <>
      <div ref={wrapRef} className="table-wrap">
        <ProTable<LoginLogListItem>
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
                setSource((key as LoginLogSource) || 'hot');
                actionRef.current?.reload?.();
              },
            },
          }}
          dateFormatter="string"
          headerTitle="登录日志"
        />
      </div>
      <LoginLogDetailDrawer
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

export default LoginLogPanel;
