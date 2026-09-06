import { useCallback, useMemo, useRef, useState } from 'react';
import { Button, Popconfirm, Tag} from 'antd';
import { message } from '@/core/feedback/message';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  DownOutlined,
  PlusOutlined,
  UpOutlined } from '@ant-design/icons';
import { useDeleteMenu, useUpdateMenu } from '@/api/hooks/menu';
import { listMenusApi } from '@/api/rest/menu';
import type { MenuType, SysMenu } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import { useAccessRefreshStore } from '@/stores';
import MenuFormDrawer, { type MenuFormKind } from './modules/menu-form-drawer';

/** 扁平菜单按 parentId 组成树，供 ProTable 的 childrenColumnName 渲染 */
function buildTree(list: SysMenu[]): SysMenu[] {
  const byId = new Map<number, SysMenu & { children?: SysMenu[] }>();
  list.forEach((m) => byId.set(m.id, { ...m }));
  const roots: SysMenu[] = [];
  for (const node of byId.values()) {
    if (node.parentId === null || node.parentId === undefined) {
      roots.push(node);
    } else {
      const parent = byId.get(node.parentId);
      if (parent) {
        parent.children = parent.children ?? [];
        parent.children.push(node);
      } else {
        roots.push(node);
      }
    }
  }
  return roots;
}

/** 收集树中所有"可展开"节点的 id(任何有 children 的节点) */
function collectExpandableIds(nodes: SysMenu[]): number[] {
  const ids: number[] = [];
  const walk = (list: SysMenu[]) => {
    for (const n of list) {
      if (n.children && n.children.length > 0) {
        ids.push(n.id);
        walk(n.children);
      }
    }
  };
  walk(nodes);
  return ids;
}

const TYPE_TAG: Record<MenuType, { color: string; text: string }> = {
  DIR: { color: 'blue', text: 'DIR' },
  MENU: { color: 'green', text: 'MENU' },
  BUTTON: { color: 'orange', text: 'BUTTON' },
};

const STATUS_TAG: Record<0 | 1, { color: string; text: string }> = {
  1: { color: 'success', text: '启用' },
  0: { color: 'default', text: '禁用' },
};

const MenuPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const reload = () => actionRef.current?.reload?.();

  const deleteMut = useDeleteMenu({
    onSuccess: () => {
      message.success('删除成功');
      reload();
      useAccessRefreshStore.getState().refreshAccess();
    },
    onError: (err) => message.error(`删除失败：${(err as Error).message ?? '未知错误'}`),
  });

  const toggleMut = useUpdateMenu({
    onSuccess: (_data, vars) => {
      message.success(vars.data.isEnabled === 1 ? '已启用' : '已禁用');
      reload();
      useAccessRefreshStore.getState().refreshAccess();
    },
    onError: (err) => message.error(`操作失败：${(err as Error).message ?? '未知错误'}`),
  });

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerKind, setDrawerKind] = useState<MenuFormKind>('create');
  const [editing, setEditing] = useState<SysMenu | null>(null);
  const [presetParentId, setPresetParentId] = useState<number | null>(null);

  // 全部展开/折叠：受控 expandedRowKeys，与 vue 端 isExpanded 语义一致
  const [expandedKeys, setExpandedKeys] = useState<number[]>([]);

  // ProTable 内部驱动 dataSource；onDataSourceChange 把当前页的树回传出来，
  // 给工具栏「展开/折叠全部」按钮算可展开节点用。
  const [currentTree, setCurrentTree] = useState<SysMenu[]>([]);
  /** 筛选范围内的菜单条数（与分页 total=根节点数 并列展示） */
  const [itemTotal, setItemTotal] = useState(0);
  const allExpandableIds = useMemo(
    () => collectExpandableIds(currentTree),
    [currentTree],
  );
  const allExpanded =
    allExpandableIds.length > 0 &&
    allExpandableIds.every((id) => expandedKeys.includes(id));

  const openCreate = (parentId: number | null = null) => {
    setDrawerKind('create');
    setEditing(null);
    setPresetParentId(parentId);
    setDrawerOpen(true);
  };
  const openEdit = (row: SysMenu) => {
    setDrawerKind('edit');
    setEditing(row);
    setPresetParentId(null);
    setDrawerOpen(true);
  };

  /* ---------- ProTable request：后端按最外层根分页，扁平 items → 树 ---------- */
  async function fetchMenuRows(params: {
    current?: number;
    pageSize?: number;
    name?: string;
    type?: MenuType;
    permissionCode?: string;
    isEnabled?: 0 | 1;
  }) {
    const { current = 1, pageSize = 20, name, type, permissionCode, isEnabled } = params;
    // pageSize 作用于「根菜单」数量；total 为根数，itemTotal 为菜单条数
    const res = await listMenusApi({
      page: current,
      pageSize,
      name: name || undefined,
      type,
      permissionCode: permissionCode || undefined,
      status: isEnabled,
    });
    setItemTotal(res.itemTotal ?? 0);
    return { data: buildTree(res.items), total: res.total, success: true };
  }

  const columns: ProColumns<SysMenu>[] = [
    { title: 'ID', dataIndex: 'id', width: 70, search: false },
    { title: '菜单名', dataIndex: 'name', width: 200, ellipsis: true },
    {
      title: '类型',
      dataIndex: 'type',
      width: 90,
      valueType: 'select',
      valueEnum: {
        DIR: { text: 'DIR' },
        MENU: { text: 'MENU' },
        BUTTON: { text: 'BUTTON' },
      },
      render: (_, r) => {
        const t = TYPE_TAG[r.type];
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: '路由路径',
      dataIndex: 'path',
      width: 180,
      ellipsis: true,
      search: false,
      render: (_, r) => r.path || <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '权限码',
      dataIndex: 'permissionCode',
      width: 180,
      ellipsis: true,
      render: (_, r) =>
        r.permissionCode ? (
          <Tag color="default">{r.permissionCode}</Tag>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 80,
      search: false,
      render: (_, r) => r.icon || <span style={{ color: '#999' }}>-</span>,
    },
    { title: '排序', dataIndex: 'sort', width: 70, search: false },
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
      width: 250,
      fixed: 'right',
      search: false,
      render: (_, r) => {
        const canHaveChild = r.type !== 'BUTTON';
        return [
          <a key="edit" onClick={() => openEdit(r)}>
            编辑
          </a>,
          canHaveChild ? (
            <a key="add" onClick={() => openCreate(r.id)}>
              添加
            </a>
          ) : null,
          <a
            key="toggle"
            onClick={() =>
              toggleMut.mutate({
                id: r.id,
                data: { isEnabled: r.isEnabled === 1 ? 0 : 1 },
              })
            }
          >
            {r.isEnabled === 1 ? '禁用' : '启用'}
          </a>,
          <Popconfirm
            key="del"
            title="确认删除"
            description={`确定删除「${r.name}」吗？`}
            onConfirm={() => deleteMut.mutate(r.id)}
          >
            <a style={{ color: '#ff4d4f' }}>删除</a>
          </Popconfirm>,
        ];
      },
    },
  ];

  const toggleExpandAll = useCallback(() => {
    setExpandedKeys(allExpanded ? [] : allExpandableIds);
  }, [allExpanded, allExpandableIds]);

  return (
    <ContentContainer scrollable>
      <ProTable<SysMenu>
        rowKey="id"
        headerTitle="菜单管理"
        actionRef={actionRef}
        columns={columns}
        request={fetchMenuRows}
        onDataSourceChange={(ds) => setCurrentTree((ds as SysMenu[]) ?? [])}
        search={{ labelWidth: 'auto' }}
        pagination={{
          // pageSize = 每页最外层根菜单数（默认 20）；total 为根数
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个根菜单，${itemTotal} 条数据`,
        }}
        scroll={{ x: 1100 }}
        toolBarRender={() => [
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => openCreate(null)}
          >
            新增菜单
          </Button>,
          <Button
            key="toggle-expand"
            icon={allExpanded ? <UpOutlined /> : <DownOutlined />}
            onClick={toggleExpandAll}
            disabled={allExpandableIds.length === 0}
          >
            {allExpanded ? '折叠全部' : '展开全部'}
          </Button>,
        ]}
        expandable={{
          expandedRowKeys: expandedKeys,
          onExpand: (expanded, record) => {
            setExpandedKeys((prev) =>
              expanded
                ? prev.includes(record.id)
                  ? prev
                  : [...prev, record.id]
                : prev.filter((id) => id !== record.id),
            );
          },
        }}
        tableAlertRender={false}
        dateFormatter="string"
      />
      <MenuFormDrawer
        open={drawerOpen}
        kind={drawerKind}
        row={editing}
        presetParentId={presetParentId}
        onClose={() => setDrawerOpen(false)}
        onSaved={reload}
      />
    </ContentContainer>
  );
};

export default MenuPage;
