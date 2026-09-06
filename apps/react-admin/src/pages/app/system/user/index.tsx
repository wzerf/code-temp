import { useRef, useState } from 'react';
import { Button, Popconfirm, Tag} from 'antd';
import { message } from '@/core/feedback/message';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined } from '@ant-design/icons';
import {
  useDeleteUser,
  useToggleUserStatus } from '@/api/hooks/user';
import { listUsersApi } from '@/api/rest/user';
import type { UserListItem } from '@/api/rest/types';
import { formatDateTime } from '@/utils/date';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import UserFormDrawer, { type UserFormKind } from './modules/user-form-drawer';
import ResetPasswordModal from './modules/reset-password-modal';

const STATUS_TAG: Record<0 | 1, { color: string; text: string }> = {
  1: { color: 'success', text: '启用' },
  0: { color: 'default', text: '禁用' },
};

const UserPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const reload = () => actionRef.current?.reload?.();

  const deleteMut = useDeleteUser({
    onSuccess: () => {
      message.success('删除成功');
      reload();
    },
    onError: (err) => message.error(`删除失败：${(err as Error).message ?? '未知错误'}`),
  });
  const toggleMut = useToggleUserStatus({
    onSuccess: () => {
      message.success('状态已更新');
      reload();
    },
    onError: (err) => message.error(`操作失败：${(err as Error).message ?? '未知错误'}`),
  });

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerKind, setDrawerKind] = useState<UserFormKind>('create');
  const [editing, setEditing] = useState<UserListItem | null>(null);

  const [resetOpen, setResetOpen] = useState(false);
  const [resetUserId, setResetUserId] = useState<number | null>(null);

  const openCreate = () => {
    setDrawerKind('create');
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: UserListItem) => {
    setDrawerKind('edit');
    setEditing(row);
    setDrawerOpen(true);
  };
  const openReset = (row: UserListItem) => {
    setResetUserId(row.id);
    setResetOpen(true);
  };

  /* ---------- ProTable request：分页由 ProTable 接管 ---------- */
  async function fetchUserRows(params: {
    current?: number;
    pageSize?: number;
    username?: string;
    nickname?: string;
    isEnabled?: 0 | 1;
  }) {
    const { current = 1, pageSize = 20, username, nickname, isEnabled } = params;
    const res = await listUsersApi({
      page: current,
      pageSize,
      username: username || undefined,
      nickname: nickname || undefined,
      status: isEnabled,
    });
    return { data: res.items, total: res.total, success: true };
  }

  const columns: ProColumns<UserListItem>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '用户名', dataIndex: 'username', width: 140, ellipsis: true },
    { title: '昵称', dataIndex: 'nickname', width: 140, ellipsis: true },
    {
      title: '邮箱',
      dataIndex: 'email',
      width: 200,
      ellipsis: true,
      search: false,
      render: (_, r) => r.email || <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      width: 140,
      search: false,
      render: (_, r) => r.phone || <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '角色',
      dataIndex: 'roleNames',
      width: 180,
      search: false,
      render: (_, r) =>
        r.roleNames.length > 0 ? (
          r.roleNames.map((n) => <Tag key={n}>{n}</Tag>)
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '状态',
      dataIndex: 'isEnabled',
      width: 90,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '禁用' } },
      render: (_, r) => {
        const t = STATUS_TAG[r.isEnabled];
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      width: 170,
      search: false,
      render: (_, r) =>
        r.lastLoginAt ? formatDateTime(r.lastLoginAt) : <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '账号过期',
      dataIndex: 'accountExpiresAt',
      width: 170,
      search: false,
      render: (_, r) =>
        r.accountExpiresAt ? formatDateTime(r.accountExpiresAt) : <span style={{ color: '#999' }}>永不过期</span>,
    },
    {
      title: '操作',
      valueType: 'option',
      width: 240,
      fixed: 'right',
      search: false,
      render: (_, r) => [
        <a key="edit" onClick={() => openEdit(r)}>
          编辑
        </a>,
        <a
          key="toggle"
          onClick={() => toggleMut.mutate({ id: r.id, status: r.isEnabled === 1 ? 0 : 1 })}
        >
          {r.isEnabled === 1 ? '禁用' : '启用'}
        </a>,
        <a key="reset" onClick={() => openReset(r)}>
          重置密码
        </a>,
        <Popconfirm
          key="del"
          title="确认删除"
          description={`确定删除「${r.username}」吗？`}
          onConfirm={() => deleteMut.mutate(r.id)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <ContentContainer scrollable>
      <ProTable<UserListItem>
        rowKey="id"
        headerTitle="用户管理"
        actionRef={actionRef}
        columns={columns}
        request={fetchUserRows}
        search={{ labelWidth: 'auto' }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
        scroll={{ x: 1200 }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreate}
          >
            新建用户
          </Button>,
        ]}
        tableAlertRender={false}
        dateFormatter="string"
      />
      <UserFormDrawer
        open={drawerOpen}
        kind={drawerKind}
        row={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={reload}
      />
      <ResetPasswordModal
        open={resetOpen}
        userId={resetUserId}
        onClose={() => setResetOpen(false)}
      />
    </ContentContainer>
  );
};

export default UserPage;