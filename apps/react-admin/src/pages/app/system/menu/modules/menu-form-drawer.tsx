/* eslint-disable react-hooks/set-state-in-effect --
 * useEffect 同步服务端数据(boundApis)与受控 open prop 到本地 state 是合法用例;
 * Drawer destroyOnHidden 已在父层设,关闭/重开会重新挂载本组件,useState 不会残留 */
import { useEffect, useMemo, useState } from 'react';
import { Button, Checkbox, Col, Collapse, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch, Tabs, Tag } from 'antd';
import { message } from '@/core/feedback/message';
import type { CheckboxChangeEvent } from 'antd';
import {
  useAllMenus,
  useCreateMenu,
  useMenuApis,
  useSetMenuApis,
  useUpdateMenu } from '@/api/hooks/menu';
import { useAllApis } from '@/api/hooks/api';
import type {
  CreateMenuRequest,
  MenuBindApiItem,
  MenuType,
  SysMenu } from '@/api/rest/types';
import { useAccessRefreshStore } from '@/stores';

export type MenuFormKind = 'create' | 'edit';

interface Props {
  open: boolean;
  kind: MenuFormKind;
  row: SysMenu | null;
  /** 「添加子项」时预置的父菜单 id */
  presetParentId: number | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  parentId: number | null;
  name: string;
  type: MenuType;
  path?: string;
  component?: string;
  redirect?: string;
  icon?: string;
  permissionCode?: string;
  sort?: number;
  isHidden?: boolean;
  isEnabled?: boolean;
  remark?: string;
  metadata?: string;
}

const DEFAULT_METADATA_TEXT = JSON.stringify(
  {
    badge: '',
    hideInBreadcrumb: false,
    keepAlive: true,
    affix: false,
    activeMenu: '',
  },
  null,
  2,
);

/** metadata 安全美化：非法 JSON 原样回显，避免抛错导致整表空白 */
function formatMetadataForForm(raw: string | null | undefined): string {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
}

function buildMenuFormValues(
  kind: MenuFormKind,
  row: SysMenu | null,
  presetParentId: number | null,
): FormValues {
  if (kind === 'edit' && row) {
    return {
      parentId: row.parentId,
      name: row.name,
      type: row.type,
      path: row.path ?? undefined,
      component: row.component ?? undefined,
      redirect: row.redirect,
      icon: row.icon,
      permissionCode: row.permissionCode ?? undefined,
      sort: row.sort,
      isHidden: row.isHidden === 1,
      isEnabled: row.isEnabled === 1,
      remark: row.remark,
      metadata: formatMetadataForForm(row.metadata),
    };
  }
  return {
    parentId: presetParentId ?? null,
    name: '',
    type: 'MENU',
    path: '',
    component: '',
    redirect: '',
    icon: '',
    permissionCode: '',
    sort: 0,
    isHidden: false,
    isEnabled: true,
    remark: '',
    metadata: '',
  };
}

const MenuFormDrawer = ({
  open,
  kind,
  row,
  presetParentId,
  onClose,
  onSaved,
}: Props) => {
  const isEdit = kind === 'edit' && !!row;
  const [form] = Form.useForm<FormValues>();
  const [activeTab, setActiveTab] = useState('basic');
  const [apiSearch, setApiSearch] = useState('');

  const createMut = useCreateMenu({
    onSuccess: () => {
      message.success('创建成功');
      useAccessRefreshStore.getState().refreshAccess();
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`创建失败：${(err as Error).message ?? '未知错误'}`),
  });
  const updateMut = useUpdateMenu({
    onSuccess: () => {
      message.success('保存成功');
      useAccessRefreshStore.getState().refreshAccess();
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`保存失败：${(err as Error).message ?? '未知错误'}`),
  });

  // 父菜单下拉：DIR/MENU（BUTTON 不能作父），排除自己及其后代
  const { data: allMenus } = useAllMenus({ enabled: open });
  const { data: allApis } = useAllApis({ enabled: open });
  // 编辑时拉当前菜单已绑定接口
  const { data: boundApis, refetch: refetchBound } = useMenuApis(
    isEdit && row ? row.id : null,
    {
      enabled: open && isEdit,
    },
  );
  const setApisMut = useSetMenuApis({
    onSuccess: () => message.success('接口绑定已保存'),
    onError: (err) => message.error(`保存绑定失败：${(err as Error).message ?? '未知错误'}`),
  });

  // 绑定选中态：api_id 集合
  const [boundIds, setBoundIds] = useState<Set<number>>(new Set());

  // Form 用 key + initialValues 保证 destroyOnHidden 挂载即回显，
  // 避免 useEffect + setFieldsValue 在字段注册前执行导致丢值。
  const formInitialValues = useMemo(
    () => buildMenuFormValues(kind, row, presetParentId),
    [kind, row, presetParentId],
  );
  const formKey =
    kind === 'edit' && row
      ? `edit-${row.id}`
      : `create-${presetParentId ?? 'root'}`;

  // 打开抽屉时回到基础 tab 并清空搜索；不在这里 setFieldsValue
  useEffect(() => {
    if (!open) return;
    setActiveTab('basic');
    setApiSearch('');
  }, [open, formKey]);

  // 绑定接口初始化：编辑时用 boundApis 的 bound 标记
  useEffect(() => {
    const initial = new Set<number>();
    if (isEdit && boundApis) {
      boundApis.forEach((a) => {
        if (a.bound) initial.add(a.id);
      });
    }
    setBoundIds(initial);
  }, [boundApis, isEdit]);

  // 当前 type（决定哪些字段显示）
  const watchType = Form.useWatch('type', form) as MenuType | undefined;
  const currentType = watchType ?? formInitialValues.type;

  // 父菜单选项：排除自己与后代（编辑时）
  const parentOptions = useMemo(() => {
    if (!allMenus) return [];
    const banned = new Set<number>();
    if (isEdit && row) {
      banned.add(row.id);
      // 后代：tree_path 以本节点 tree_path 开头
      allMenus.forEach((m) => {
        if (m.treePath.startsWith(row.treePath) && m.id !== row.id) {
          banned.add(m.id);
        }
      });
    }
    return allMenus
      .filter((m) => m.type !== 'BUTTON' && !banned.has(m.id))
      .map((m) => ({
        label: `${m.name}（${m.type}）`,
        value: m.id,
      }));
  }, [allMenus, isEdit, row]);

  // 接口按 api_group 分组 + 搜索过滤
  const groupedApis = useMemo(() => {
    const groups = new Map<string, MenuBindApiItem[]>();
    const source: MenuBindApiItem[] = allApis
      ? allApis.map((a) => ({
          ...a,
          bound: boundIds.has(a.id),
        }))
      : (boundApis ?? []);
    const kw = apiSearch.trim().toLowerCase();
    source.forEach((a) => {
      if (kw) {
        const hit =
          a.name.toLowerCase().includes(kw) ||
          a.path.toLowerCase().includes(kw) ||
          a.permissionCode.toLowerCase().includes(kw);
        if (!hit) return;
      }
      const arr = groups.get(a.apiGroup) ?? [];
      arr.push(a);
      groups.set(a.apiGroup, arr);
    });
    return [...groups.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [allApis, boundApis, boundIds, apiSearch]);

  const toggleApi = (id: number, checked: boolean) => {
    setBoundIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const toggleGroup = (apis: MenuBindApiItem[], checked: boolean) => {
    setBoundIds((prev) => {
      const next = new Set(prev);
      apis.forEach((a) => {
        if (checked) next.add(a.id);
        else next.delete(a.id);
      });
      return next;
    });
  };

  const handleSaveBasic = async () => {
    const values = await form.validateFields();

    let metadata: string | null = null;
    const rawMetadata = values.metadata?.trim();
    if (rawMetadata) {
      try {
        metadata = JSON.stringify(JSON.parse(rawMetadata));
      } catch {
        message.error('前端扩展 (metadata) 不是合法 JSON');
        return;
      }
    }

    const payload: CreateMenuRequest = {
      parentId: values.parentId ?? null,
      name: values.name,
      type: values.type,
      path: values.type === 'MENU' ? values.path || null : null,
      component: values.type === 'MENU' ? values.component || null : null,
      icon: values.icon ?? '',
      redirect: values.redirect ?? '',
      permissionCode: values.permissionCode || null,
      sort: values.sort ?? 0,
      isHidden: values.isHidden ? 1 : 0,
      isEnabled: values.isEnabled ? 1 : 0,
      remark: values.remark ?? '',
      metadata,
    };
    if (isEdit && row) {
      updateMut.mutate({ id: row.id, data: payload });
    } else {
      createMut.mutate(payload);
    }
  };

  const handleSaveBind = () => {
    if (!isEdit || !row) return;
    setApisMut.mutate({ id: row.id, apiIds: [...boundIds] });
    refetchBound();
  };

  const submitting = createMut.isPending || updateMut.isPending || setApisMut.isPending;

  const renderBasicForm = () => (
    <Form
      key={formKey}
      form={form}
      layout="vertical"
      preserve={false}
      initialValues={formInitialValues}
    >
      <div style={{ color: '#94a3b8', fontSize: 12, fontWeight: 500, marginBottom: 16 }}>
        基础（{currentType === 'DIR' ? '目录' : currentType === 'MENU' ? '菜单' : '按钮'}）
      </div>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item label="父菜单" name="parentId">
            <Select
              allowClear
              placeholder="— 顶级 —"
              options={parentOptions}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            label="类型"
            name="type"
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select
              options={[
                { label: 'DIR — 目录', value: 'DIR' },
                { label: 'MENU — 菜单/路由', value: 'MENU' },
                { label: 'BUTTON — 按钮', value: 'BUTTON' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            label="菜单名"
            name="name"
            rules={[{ required: true, message: '请输入菜单名' }, { max: 64 }]}
          >
            <Input placeholder="如 用户管理" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="排序" name="sort">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
      </Row>

      {currentType === 'MENU' && (
        <>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="路由路径"
                name="path"
                rules={[{ required: true, message: 'MENU 必须填写路由路径' }]}
              >
                <Input placeholder="如 /admin/users" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="图标" name="icon">
                <Input placeholder="如 user" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="前端组件" name="component">
                <Input placeholder="如 views/admin/users/index" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="重定向" name="redirect">
                <Input placeholder="如 /admin/users/list" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="权限码" name="permissionCode">
            <Input placeholder="如 admin:user:list" />
          </Form.Item>
        </>
      )}

      {currentType === 'BUTTON' && (
        <Form.Item
          label="权限码"
          name="permissionCode"
          rules={[{ required: true, message: 'BUTTON 必须填写权限码' }]}
        >
          <Input placeholder="如 admin:user:create" />
        </Form.Item>
      )}

      <Row gutter={16}>
        <Col span={24}>
          <Form.Item label="前端隐藏" name="isHidden" valuePropName="checked">
            <Switch checkedChildren="隐藏" unCheckedChildren="显示" />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item label="状态" name="isEnabled" valuePropName="checked">
        <Switch checkedChildren="启用" unCheckedChildren="禁用" />
      </Form.Item>

      <Form.Item label="备注" name="remark">
        <Input.TextArea rows={3} placeholder="选填" />
      </Form.Item>

      <div style={{ color: '#94a3b8', fontSize: 12, fontWeight: 500, marginBottom: 16 }}>
        前端扩展（METADATA）
      </div>
      <Form.Item
        label=""
        name="metadata"
        style={{ marginBottom: 0 }}
      >
        <Input.TextArea
          rows={8}
          placeholder={DEFAULT_METADATA_TEXT}
          style={{ fontFamily: 'ui-monospace, "JetBrains Mono", monospace', fontSize: 12 }}
        />
      </Form.Item>
      <div style={{ fontSize: 11, color: '#64748b', marginTop: 4 }}>
        JSON 格式，用于前端框架的路由元信息
      </div>
    </Form>
  );

  return (
    <Drawer
      title={isEdit ? '编辑菜单' : '新增菜单'}
      open={open}
      onClose={onClose}
      size={640}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={submitting}>
            取消
          </Button>
          {activeTab === 'basic' ? (
            <Button type="primary" onClick={handleSaveBasic} loading={submitting}>
              保存
            </Button>
          ) : (
            <Button type="primary" onClick={handleSaveBind} loading={submitting} disabled={!isEdit}>
              保存绑定
            </Button>
          )}
        </Space>
      }
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          { key: 'basic', label: '基础信息', disabled: false },
          { key: 'bind', label: `绑定接口（${boundIds.size}）`, disabled: !isEdit },
        ]}
      />

      {/* 始终挂载基础 Form，避免切 tab 卸载后 initialValues 丢失 */}
      <div style={{ display: activeTab === 'basic' ? 'block' : 'none' }}>
        {renderBasicForm()}
      </div>

      {activeTab === 'bind' && (
        <div>
          <Input.Search
            placeholder="按路径或名称搜索接口..."
            allowClear
            value={apiSearch}
            onChange={(e) => setApiSearch(e.target.value)}
            style={{ marginBottom: 12 }}
          />
          <div style={{ marginBottom: 8, color: '#666' }}>
            已选 <strong>{boundIds.size}</strong> 个接口
          </div>
          <Collapse
            defaultActiveKey={[]}
            items={groupedApis.map(([group, apis]) => {
              const allSelected = apis.length > 0 && apis.every((a) => boundIds.has(a.id));
              return {
                key: group,
                label: (
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span>
                      {group} · {apis.length} 个
                    </span>
                    <Checkbox
                      checked={allSelected}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e: CheckboxChangeEvent) => toggleGroup(apis, e.target.checked)}
                    >
                      全选
                    </Checkbox>
                  </div>
                ),
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {apis.map((a) => (
                      <Checkbox
                        key={a.id}
                        checked={boundIds.has(a.id)}
                        onChange={(e: CheckboxChangeEvent) => toggleApi(a.id, e.target.checked)}
                      >
                        <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
                          <Tag color="blue" style={{ marginRight: 6 }}>
                            {a.method}
                          </Tag>
                          {a.path}
                        </span>
                        <span style={{ fontSize: 12, color: '#999', marginLeft: 8 }}>
                          {a.name}
                        </span>
                      </Checkbox>
                    ))}
                  </div>
                ),
              };
            })}
          />
        </div>
      )}
    </Drawer>
  );
};

export default MenuFormDrawer;
