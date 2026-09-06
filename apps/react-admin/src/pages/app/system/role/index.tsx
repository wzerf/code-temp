import { useRef, useState } from 'react';
import { Button, Popconfirm, Tag} from 'antd';
import { message } from '@/core/feedback/message';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined } from '@ant-design/icons';
import { useDeleteRole } from '@/api/hooks/role';
import { listRolesApi } from '@/api/rest/role';
import type { SysRole } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import RoleFormDrawer, { type RoleFormKind } from './modules/role-form-drawer';
import RolePermissionDrawer from './modules/role-permission-drawer';

const STATUS_TAG: Record<0 | 1, { color: string; text: string }> = {
  1: { color: 'success', text: '启用' },
  0: { color: 'default', text: '禁用' },
};

const RolePage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const reload = () => actionRef.current?.reload?.();

  const deleteMut = useDeleteRole({
    onSuccess: () => {
      message.success('删除成功');
      reload();
    },
    onError: (err) => message.error(`删除失败：${(err as Error).message ?? '未知错误'}`),
  });

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerKind, setDrawerKind] = useState<RoleFormKind>('create');
  const [editing, setEditing] = useState<SysRole | null>(null);

  const [permOpen, setPermOpen] = useState(false);
  const [permRoleId, setPermRoleId] = useState<number | null>(null);

  const openCreate = () => {
    setDrawerKind('create');
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: SysRole) => {
    setDrawerKind('edit');
    setEditing(row);
    setDrawerOpen(true);
  };
  const openPermission = (row: SysRole) => {
    setPermRoleId(row.id);
    setPermOpen(true);
  };

  /* ---------- ProTable request：分页由 ProTable 接管 ---------- */
  async function fetchRoleRows(params: {
    current?: number;
    pageSize?: number;
    code?: string;
    name?: string;
    isEnabled?: 0 | 1;
  }) {
    const { current = 1, pageSize = 20, code, name, isEnabled } = params;
    const res = await listRolesApi({
      page: current,
      pageSize,
      code: code || undefined,
      name: name || undefined,
      status: isEnabled,
    });
    return { data: res.items, total: res.total, success: true };
  }

  const columns: ProColumns<SysRole>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '编码', dataIndex: 'code', width: 160, ellipsis: true },
    { title: '角色名', dataIndex: 'name', width: 160, ellipsis: true },
    {
      title: '父角色',
      dataIndex: 'parentName',
      width: 140,
      search: false,
      render: (_, r) =>
        r.parentName || <span style={{ color: '#999' }}>-</span>,
    },
    { title: '排序', dataIndex: 'sort', width: 70, search: false },
    {
      title: '用户数',
      dataIndex: 'userCount',
      width: 90,
      search: false,
      render: (_, r) => r.userCount ?? 0,
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
      title: '操作',
      valueType: 'option',
      width: 200,
      fixed: 'right',
      search: false,
      render: (_, r) => [
        <a key="edit" onClick={() => openEdit(r)}>
          编辑
        </a>,
        <a key="perm" onClick={() => openPermission(r)}>
          分配权限
        </a>,
        <Popconfirm
          key="del"
          title="确认删除"
          description={`确定删除「${r.name}」吗？`}
          onConfirm={() => deleteMut.mutate(r.id)}
        >
          <a style={{ color: '#ff4d4f' }}>删除</a>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <ContentContainer scrollable>
      <ProTable<SysRole>
        rowKey="id"
        headerTitle="角色管理"
        actionRef={actionRef}
        columns={columns}
        request={fetchRoleRows}
        search={{ labelWidth: 'auto' }}
        pagination={{
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
        }}
        scroll={{ x: 1100 }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreate}
          >
            新增角色
          </Button>,
        ]}
        tableAlertRender={false}
        dateFormatter="string"
      />
      <RoleFormDrawer
        open={drawerOpen}
        kind={drawerKind}
        row={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={reload}
      />
      <RolePermissionDrawer
        open={permOpen}
        roleId={permRoleId}
        onClose={() => setPermOpen(false)}
      />
    </ContentContainer>
  );
};

export default RolePage;