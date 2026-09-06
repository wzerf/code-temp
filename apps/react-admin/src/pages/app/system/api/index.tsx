import { useCallback, useMemo, useRef, useState } from 'react';
import { Button, Popconfirm, Space, Tag} from 'antd';
import { message } from '@/core/feedback/message';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import {
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  PlusOutlined,
  SyncOutlined,
  UpOutlined } from '@ant-design/icons';
import {
  useApiGroups,
  useDeleteApi,
  useSyncApisApi } from '@/api/hooks/api';
import { listApisApi } from '@/api/rest/api';
import type { HttpMethod, SysApi } from '@/api/rest/types';
import { formatDateTime } from '@/utils/date';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import ApiFormDrawer from './modules/api-form-drawer';

const METHOD_COLOR: Record<HttpMethod, string> = {
  GET: 'blue',
  POST: 'green',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
  OPTIONS: 'default',
  HEAD: 'default',
};

const STATUS_TAG: Record<0 | 1, { color: string; text: string }> = {
  1: { color: 'success', text: '启用' },
  0: { color: 'default', text: '禁用' },
};

/** 分组树节点：父行为合成分组，子行为真实接口 */
type ApiTreeNode = SysApi & {
  isGroup?: boolean;
  children?: ApiTreeNode[];
  /** 稳定 rowKey：分组用 group:xxx，叶子用数字 id 字符串 */
  rowKey: string;
};

/**
 * 按 apiGroup 合成分组树；空分组名归为「未分组」。
 * 列表接口按「分组」分页：list 为当前页各组的完整接口集合。
 */
function buildApiGroupTree(list: SysApi[]): ApiTreeNode[] {
  const groups = new Map<string, SysApi[]>();
  list.forEach((a) => {
    const g = a.apiGroup?.trim() || '未分组';
    const arr = groups.get(g) ?? [];
    arr.push(a);
    groups.set(g, arr);
  });

  // 分组父节点使用负 id，避免与真实接口 id 冲突
  let groupSeq = -1;

  return [...groups.entries()]
    .sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([groupName, apis]) => {
      const children: ApiTreeNode[] = [...apis]
        .sort((a, b) => a.id - b.id)
        .map((a) => ({
          ...a,
          rowKey: String(a.id),
        }));

      const parentId = groupSeq--;
      return {
        id: parentId,
        name: `${groupName}（${children.length}）`,
        method: 'GET' as HttpMethod,
        path: '',
        permissionCode: '',
        apiGroup: groupName,
        remark: '',
        isEnabled: 1 as const,
        deletedAt: 0,
        createdAt: '',
        updatedAt: '',
        isGroup: true,
        rowKey: `group:${groupName}`,
        children,
      };
    });
}

/** 收集树中所有可展开节点的 rowKey */
function collectExpandableKeys(nodes: ApiTreeNode[]): string[] {
  const keys: string[] = [];
  const walk = (list: ApiTreeNode[]) => {
    for (const n of list) {
      if (n.children && n.children.length > 0) {
        keys.push(n.rowKey);
        walk(n.children);
      }
    }
  };
  walk(nodes);
  return keys;
}

const ApiPage = () => {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const reload = () => actionRef.current?.reload?.();

  // 接口列表由 ProTable 的 request 直接拉；这里只保留分组下拉（搜索框 valueEnum 用）
  const { data: groups } = useApiGroups();
  const deleteMut = useDeleteApi({
    onSuccess: () => {
      message.success('删除成功');
      reload();
    },
    onError: (err) => message.error(`删除失败：${(err as Error).message ?? '未知错误'}`),
  });
  const syncMut = useSyncApisApi({
    onSuccess: (res) => {
      message.success(`同步成功：新增 ${res.added}，跳过 ${res.skipped}，共 ${res.total}`);
      reload();
    },
    onError: (err) => message.error(`同步失败：${(err as Error).message ?? '未知错误'}`),
  });

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<SysApi | null>(null);
  const openCreate = () => {
    setEditing(null);
    setDrawerOpen(true);
  };
  const openEdit = (row: SysApi) => {
    setEditing(row);
    setDrawerOpen(true);
  };

  // 全部展开/折叠：受控 expandedRowKeys，与菜单管理语义一致
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [currentTree, setCurrentTree] = useState<ApiTreeNode[]>([]);
  /** 筛选后的接口条数（与分页 total=分组数 并列展示） */
  const [itemTotal, setItemTotal] = useState(0);
  const allExpandableKeys = useMemo(
    () => collectExpandableKeys(currentTree),
    [currentTree],
  );
  const allExpanded =
    allExpandableKeys.length > 0 &&
    allExpandableKeys.every((k) => expandedKeys.includes(k));

  const groupEnum = useMemo(() => {
    const out: Record<string, { text: string }> = {};
    (groups ?? []).forEach((g) => {
      out[g] = { text: g };
    });
    return out;
  }, [groups]);

  /* ---------- ProTable request：后端按分组分页，扁平 items → 分组树 ---------- */
  async function fetchApiRows(params: {
    current?: number;
    pageSize?: number;
    name?: string;
    path?: string;
    method?: HttpMethod;
    apiGroup?: string;
    isEnabled?: 0 | 1;
  }) {
    const { current = 1, pageSize = 20, name, path, method, apiGroup, isEnabled } = params;
    // pageSize 作用于「分组」数量；total 为分组总数，itemTotal 为接口条数
    const res = await listApisApi({
      page: current,
      pageSize,
      name: name || undefined,
      path: path || undefined,
      method,
      group: apiGroup || undefined,
      status: isEnabled,
    });
    setItemTotal(res.itemTotal ?? 0);
    return { data: buildApiGroupTree(res.items), total: res.total, success: true };
  }

  const columns: ProColumns<ApiTreeNode>[] = [
    {
      title: '接口名',
      dataIndex: 'name',
      width: 220,
      ellipsis: true,
      render: (_, r) =>
        r.isGroup ? (
          <span style={{ fontWeight: 600 }}>{r.name}</span>
        ) : (
          r.name
        ),
    },
    {
      title: 'ID',
      dataIndex: 'id',
      width: 70,
      search: false,
      render: (_, r) => (r.isGroup ? <span style={{ color: '#999' }}>-</span> : r.id),
    },
    {
      title: '方法',
      dataIndex: 'method',
      width: 90,
      valueType: 'select',
      valueEnum: {
        GET: { text: 'GET' },
        POST: { text: 'POST' },
        PUT: { text: 'PUT' },
        DELETE: { text: 'DELETE' },
        PATCH: { text: 'PATCH' },
        OPTIONS: { text: 'OPTIONS' },
        HEAD: { text: 'HEAD' },
      },
      render: (_, r) =>
        r.isGroup ? (
          <span style={{ color: '#999' }}>-</span>
        ) : (
          <Tag color={METHOD_COLOR[r.method]}>{r.method}</Tag>
        ),
    },
    {
      title: '路径',
      dataIndex: 'path',
      width: 240,
      ellipsis: true,
      render: (_, r) =>
        r.isGroup ? (
          <span style={{ color: '#999' }}>-</span>
        ) : (
          <span style={{ fontFamily: 'monospace' }}>{r.path}</span>
        ),
    },
    {
      title: '权限码',
      dataIndex: 'permissionCode',
      width: 180,
      ellipsis: true,
      search: false,
      render: (_, r) =>
        r.isGroup ? (
          <span style={{ color: '#999' }}>-</span>
        ) : (
          <Tag color="default">{r.permissionCode}</Tag>
        ),
    },
    {
      // 仅作搜索筛选；列表由分组父行表达，不再单独渲染分组列
      title: '分组',
      dataIndex: 'apiGroup',
      width: 110,
      valueType: 'select',
      valueEnum: groupEnum,
      hideInTable: true,
    },
    {
      title: '状态',
      dataIndex: 'isEnabled',
      width: 90,
      valueType: 'select',
      valueEnum: { 1: { text: '启用' }, 0: { text: '禁用' } },
      render: (_, r) => {
        if (r.isGroup) return <span style={{ color: '#999' }}>-</span>;
        const t = STATUS_TAG[r.isEnabled];
        return <Tag color={t.color}>{t.text}</Tag>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, r) =>
        r.isGroup ? <span style={{ color: '#999' }}>-</span> : formatDateTime(r.createdAt),
    },
    {
      title: '操作',
      valueType: 'option',
      width: 120,
      fixed: 'right',
      search: false,
      render: (_, r) => {
        if (r.isGroup) return null;
        return [
          <a key="edit" onClick={() => openEdit(r)}>
            <Space size={2}>
              <EditOutlined /> 编辑
            </Space>
          </a>,
          <Popconfirm
            key="del"
            title="确认删除"
            description={`确定删除「${r.name}」吗？`}
            onConfirm={() => deleteMut.mutate(r.id)}
          >
            <a style={{ color: '#ff4d4f' }}>
              <DeleteOutlined /> 删除
            </a>
          </Popconfirm>,
        ];
      },
    },
  ];

  const toggleExpandAll = useCallback(() => {
    setExpandedKeys(allExpanded ? [] : allExpandableKeys);
  }, [allExpanded, allExpandableKeys]);

  return (
    <ContentContainer scrollable>
      <ProTable<ApiTreeNode>
        rowKey="rowKey"
        headerTitle="接口管理"
        actionRef={actionRef}
        columns={columns}
        request={fetchApiRows}
        onDataSourceChange={(ds) => setCurrentTree((ds as ApiTreeNode[]) ?? [])}
        search={{ labelWidth: 'auto' }}
        pagination={{
          // pageSize = 每页分组数（默认 20 组）；total 为分组数
          defaultPageSize: 20,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 个分组，${itemTotal} 条数据`,
        }}
        scroll={{ x: 1200 }}
        toolBarRender={() => [
          <Popconfirm
            key="sync"
            title="确认同步"
            description="将从后端路由清单重新扫描并登记接口（命中则跳过）"
            onConfirm={() => syncMut.mutate()}
          >
            <Button icon={<SyncOutlined />} loading={syncMut.isPending}>
              同步接口
            </Button>
          </Popconfirm>,
          <Button
            key="create"
            type="primary"
            icon={<PlusOutlined />}
            onClick={openCreate}
          >
            新增接口
          </Button>,
          <Button
            key="toggle-expand"
            icon={allExpanded ? <UpOutlined /> : <DownOutlined />}
            onClick={toggleExpandAll}
            disabled={allExpandableKeys.length === 0}
          >
            {allExpanded ? '折叠全部' : '展开全部'}
          </Button>,
        ]}
        expandable={{
          expandedRowKeys: expandedKeys,
          onExpand: (expanded, record) => {
            setExpandedKeys((prev) =>
              expanded
                ? prev.includes(record.rowKey)
                  ? prev
                  : [...prev, record.rowKey]
                : prev.filter((k) => k !== record.rowKey),
            );
          },
        }}
        tableAlertRender={false}
        dateFormatter="string"
      />
      <ApiFormDrawer
        open={drawerOpen}
        row={editing}
        onClose={() => setDrawerOpen(false)}
        onSaved={reload}
      />
    </ContentContainer>
  );
};

export default ApiPage;
