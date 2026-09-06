import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Checkbox, Col, Form, Modal, Popconfirm, Row, Select, Space, Tag, Typography } from 'antd';
import { message } from '@/core/feedback/message';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  batchDictDataApi,
  deleteDictDataApi,
  listDictDataApi } from '@/api/rest/dict-data';
import {
  batchDictTypeApi,
  deleteDictTypeApi,
  listAllDictTypeApi,
  listDictTypeApi } from '@/api/rest/dict-type';
import type { DictData, DictType } from '@/api/rest/types';
import { useDictLookups } from '@/api/hooks/dict';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import DictTypeDrawer from './modules/dict-type-drawer';
import DictDataDrawer from './modules/dict-data-drawer';
import { SEARCH_PLATFORM_OPTIONS, getCurrentPlatform } from './modules/shared';

type BulkAction = 'enable' | 'disable' | 'delete';

/* ---------- 状态字段辅助 ---------- */
function statusOrUndefined(v: number | '' | undefined): 0 | 1 | undefined {
  if (v === '' || v === undefined) return undefined;
  return Number(v) as 0 | 1;
}

/* ---------- 字典类型下拉选项（用于左右两个搜索框的「类型编码」下拉） ---------- */
// 表格/搜索下拉的 label 只展示编码，不拼接名称，避免列被撑宽
async function fetchDictTypeCodeEnum() {
  const list = await listAllDictTypeApi();
  return list.map((t) => ({
    label: t.code,
    value: t.code,
  }));
}

/* ---------- 字典驱动渲染工具 ---------- */
// 所有 fallback（label / tagType / valueEnum / 平台命中）由 useDictLookups 提供。
// 本文件只消费 helper，不再持有 SWITCH_STATUS_FALLBACK / 平台命中策略。

/* ---------- 列：字典类型 / 字典项 ---------- */
// 列定义改为组件内构造函数：渲染层依赖 useDictLookups 返回的 helper。
// dict 未返回时（首次 render 或请求失败）hook 内部走静态 fallback，渲染行为
// 与改造前一致，避免出现 undefined / 空白。
function buildTypeColumns(
  dictLookups: ReturnType<typeof useDictLookups>,
  // renderOption 签名固定为 (text, record, index, action) 4 个参数，是 antd ProTable
  // 的 API 约束。这里用 eslint-disable 允许超出 max-params=3 限制。
  // eslint-disable-next-line max-params
  renderOption: (
    _: unknown,
    record: DictType,
    __: unknown,
    action?: ActionType,
  ) => React.ReactNode[],
): ProColumns<DictType>[] {
  const statusValueEnum = dictLookups.switchValueEnum;
  // 状态列 render：tagType 缺失/默认时不传 color，纯文本（保留改前 React 视觉）。
  const renderStatus = (_: unknown, r: { isEnabled: number }) => {
    const tagType = dictLookups.lookupSwitchTagType(r.isEnabled);
    return (
      <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
        {dictLookups.lookupSwitchLabel(r.isEnabled)}
      </Tag>
    );
  };
  return [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    {
      title: '类型编码',
      dataIndex: 'code',
      width: 140,
      ellipsis: true,
      valueType: 'select',
      fieldProps: {
        mode: 'multiple',
        showSearch: true,
        allowClear: true,
        placeholder: '请选择类型编码',
      },
      request: fetchDictTypeCodeEnum,
    },
    { title: '类型名称', dataIndex: 'name', width: 140, ellipsis: true },
    {
      title: '备注',
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
      title: '状态',
      dataIndex: 'isEnabled',
      width: 90,
      search: false,
      valueType: 'select',
      valueEnum: statusValueEnum,
      render: renderStatus,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 120,
      fixed: 'right',
      render: renderOption,
    },
  ];
}

function buildDataColumns(
  dictLookups: ReturnType<typeof useDictLookups>,
  // renderOption 签名固定为 (text, record, index, action) 4 个参数，是 antd ProTable
  // 的 API 约束。这里用 eslint-disable 允许超出 max-params=3 限制。
  // eslint-disable-next-line max-params
  renderOption: (
    _: unknown,
    record: DictData,
    __: unknown,
    action?: ActionType,
  ) => React.ReactNode[],
): ProColumns<DictData>[] {
  const statusValueEnum = dictLookups.switchValueEnum;
  const renderStatus = (_: unknown, r: { isEnabled: number }) => {
    const tagType = dictLookups.lookupSwitchTagType(r.isEnabled);
    return (
      <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
        {dictLookups.lookupSwitchLabel(r.isEnabled)}
      </Tag>
    );
  };
  // 平台 valueEnum（搜索下拉）：hook 已内置 platformLabels 兜底逻辑。
  const platformValueEnum = dictLookups.platformValueEnum;
  const renderPlatform = (_: unknown, r: { platform?: string }) => {
    if (!r.platform) {
      return <span style={{ color: '#999' }}>-</span>;
    }
    const tagType = dictLookups.lookupPlatformTagType(r.platform);
    return (
      <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
        {dictLookups.lookupPlatformLabel(r.platform)}
      </Tag>
    );
  };
  return [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    {
      title: '类型编码',
      dataIndex: 'typeCode',
      width: 140,
      ellipsis: true,
      // 不在搜索表单中显示；筛选逻辑由联动 / 点击行同步在代码里完成。
      hideInSearch: true,
    },
    { title: '字典值', dataIndex: 'value', width: 120 },
    { title: '字典标签', dataIndex: 'label', width: 140, ellipsis: true, search: false },
    {
      title: '归属平台',
      dataIndex: 'platform',
      width: 110,
      // 表单未选 = 不限 platform（左表无 platform 概念，不应用此过滤）。
      // valueEnum 用 SEARCH_PLATFORM_OPTIONS 全集；allowClear 让用户回到不限。
      // 不要在 column 顶层设置 initialValue：ProTable 内部已经用一个
      // Form.Item name="platform" 包裹搜索输入（cellRenderToFromItem.js），
      // 这里再设会让 antd 报 "Multiple Field with path 'platform' set
      // 'initialValue'"。真正的 initialValue 在下方 renderFormItem 内部
      // 的 Form.Item 上设置。
      valueType: 'select',
      fieldProps: {
        allowClear: true,
        placeholder: '请选择归属平台',
      },
      valueEnum: platformValueEnum,
      render: renderPlatform,
      // 搜索区把「包含通用」复选框合并到 platform 旁边（Input.Group compact），
      // 避免两个独立表单项挤在一起出现「宽度不够需要展开」的问题。
      // platform=general 时包含通用强制 disabled checked（后端忽略该参数）。
      // - platform 表单项由 ProTable 自动包裹（cellRenderToFromItem.js），
      //   这里不能再加 Form.Item name="platform"，否则会重复注入且导致
      //   ProTable 跟踪不到 platform 的值变化（取消「通用」时 Select 视图错位）。
      // - includeGeneral 通过内嵌 Form.Item name="includeGeneral" 注册到
      //   同一个 ProTable form 实例上，提交时一起进 request params。
      // - 通过 Field 订阅 platform 字段变化，仅在外层修改 platform 时同步
      //   「通用」checkbox 的 disabled 与勾选状态，避免与 ProTable 内置
      //   Form.Item 的 onChange 互相覆盖。
      renderFormItem: (schema, config, form) => {
        if (!form) return config.defaultRender(schema);
        const PlatformSelectWithGeneral = (
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.platform !== next.platform}>
            {({ getFieldValue }) => {
              const platform = getFieldValue('platform') as string | undefined;
              const disabled = platform === 'general';
              return (
                <span style={{ display: 'inline-flex', alignItems: 'center', width: '100%' }}>
                  <Form.Item name="platform" noStyle initialValue={getCurrentPlatform()}>
                    <Select
                      allowClear
                      placeholder="请选择归属平台"
                      options={SEARCH_PLATFORM_OPTIONS}
                      style={{ flex: 1, minWidth: 140 }}
                    />
                  </Form.Item>
                  <Form.Item
                    name="includeGeneral"
                    noStyle
                    valuePropName="checked"
                    initialValue={true}
                  >
                    <Checkbox
                      disabled={disabled}
                      style={{ marginLeft: 12, whiteSpace: 'nowrap' }}
                    >
                      通用
                    </Checkbox>
                  </Form.Item>
                </span>
              );
            }}
          </Form.Item>
        );
        return PlatformSelectWithGeneral;
      },
    },
    { title: '排序', dataIndex: 'sort', width: 80, search: false },
    {
      title: '默认',
      dataIndex: 'isDefault',
      width: 80,
      search: false,
      render: (_, r) => {
        const tagType = dictLookups.lookupDefaultTagType(r.isDefault);
        return (
          <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
            {dictLookups.lookupDefaultLabel(r.isDefault)}
          </Tag>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'isEnabled',
      width: 90,
      search: false,
      valueType: 'select',
      valueEnum: statusValueEnum,
      render: renderStatus,
    },
    {
      title: '备注',
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
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 120,
      fixed: 'right',
      render: renderOption,
    },
  ];
}

const DictPage = () => {
  const typeActionRef = useRef<ActionType | undefined>(undefined);
  const entryActionRef = useRef<ActionType | undefined>(undefined);

  const [selectedTypeId, setSelectedTypeId] = useState<number | null>(null);
  const [selectedType, setSelectedType] = useState<DictType | null>(null);

  // 右表当前 typeCode：由左表点击行 / 关闭按钮写入，fetchEntryRows 直接读这个 ref。
  // typeCode 不再走表单：搜索栏也不显示该字段，逻辑完全在代码里完成。
  const entryTypeCodeRef = useRef<string | undefined>(undefined);

  // 多选状态（左右两表各自独立）
  const [typeSelectedRowKeys, setTypeSelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [entrySelectedRowKeys, setEntrySelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [typeBulkLoading, setTypeBulkLoading] = useState(false);
  const [entryBulkLoading, setEntryBulkLoading] = useState(false);

  // 抽屉
  const [typeDrawerOpen, setTypeDrawerOpen] = useState(false);
  const [editingType, setEditingType] = useState<DictType | null>(null);
  const [entryDrawerOpen, setEntryDrawerOpen] = useState(false);
  const [editingEntry, setEditingEntry] = useState<DictData | null>(null);

  // 「新建条目」下拉
  const [typeOptions, setTypeOptions] = useState<
    { label: string; value: number }[]
  >([]);
  useEffect(() => {
    listAllDictTypeApi({ status: 1 })
      .then((list) =>
        setTypeOptions(
          list.map((t: DictType) => ({
            label: `${t.name}（${t.code}）`,
            value: t.id,
          })),
        ),
      )
      .catch(() => undefined);
  }, []);

  // 渲染「操作」列
  // ProColumns<DictType>['render'] 签名固定为 (text, record, index, action) 4 个参数，
  // 是 antd ProTable 的 API 约束。这里用 eslint-disable 允许超出 max-params=3 限制。
  // eslint-disable-next-line max-params
  function renderTypeActions(
    _: unknown,
    record: DictType,
    __: unknown,
    action?: ActionType,
  ) {
    const onDelete = () => {
      Modal.confirm({
        title: '确认删除该字典类型？',
        okText: '删除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: async () => {
          try {
            await deleteDictTypeApi(record.id);
            message.success('删除成功');
            action?.reload?.();
            if (selectedTypeId === record.id) {
              setSelectedTypeId(null);
              setSelectedType(null);
              // 清除右表 typeCode 过滤，避免引用已删除类型
              clearEntrySelection();
            }
          } catch (err) {
            message.error(`删除失败：${(err as Error).message ?? '未知错误'}`);
          }
        },
      });
    };
    return [
      <a
        key="edit"
        onClick={(e) => {
          e.stopPropagation();
          setEditingType(record);
          setTypeDrawerOpen(true);
        }}
      >
        编辑
      </a>,
      <a
        key="delete"
        style={{ color: '#ff4d4f' }}
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }}
      >
        删除
      </a>,
    ];
  }

  // eslint-disable-next-line max-params
  function renderEntryActions(
    _: unknown,
    record: DictData,
    __: unknown,
    action?: ActionType,
  ) {
    const onDelete = () => {
      Modal.confirm({
        title: '确认删除该字典项？',
        okText: '删除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: async () => {
          try {
            await deleteDictDataApi(record.id);
            message.success('删除成功');
            action?.reload?.();
          } catch (err) {
            message.error(`删除失败：${(err as Error).message ?? '未知错误'}`);
          }
        },
      });
    };
    return [
      <a
        key="edit"
        onClick={(e) => {
          e.stopPropagation();
          setEditingEntry(record);
          setEntryDrawerOpen(true);
        }}
      >
        编辑
      </a>,
      <a
        key="delete"
        style={{ color: '#ff4d4f' }}
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }}
      >
        删除
      </a>,
    ];
  }

  /* ---------- 一次 list 调用拉两份字典（sys_switch_status + sys_platform） ---------- */
  // 通过 useDictLookups 注入 typeCode 数组；hook 内部合并 platform / includeGeneral，
  // 走平台优先命中拆 map、构造 fallback label / tagType / valueEnum。
  // dict 未返回时（首次 render / 错误）hook helper 走内置静态 fallback，渲染行为
  // 与改造前一致。
  // platformLabels 用 SEARCH_PLATFORM_OPTIONS 兜底：dict 还没拉回来时,
  // 平台列 valueEnum 仍展示「通用 / React Admin / Vue Admin」三项友好文案。
  const platformLabels = useMemo(() => {
    const out: Record<string, string> = {};
    for (const { value, label } of SEARCH_PLATFORM_OPTIONS) {
      out[value] = label;
    }
    return out;
  }, []);
  const dictLookups = useDictLookups({
    typeCodes: ['sys_switch_status', 'sys_platform'],
    includeGeneral: true,
    platformLabels,
  });

  // 列定义：每次 render 直接构造，保证 renderTypeActions / renderEntryActions
  // 闭包内捕获的是最新 state（selectedTypeId / selectedType / entryTypeCodeRef 等）。
  // 数组很小，构造开销可忽略；正确性优先于 memoization。
  const typeCols: ProColumns<DictType>[] = buildTypeColumns(dictLookups, renderTypeActions);
  const entryCols: ProColumns<DictData>[] = buildDataColumns(dictLookups, renderEntryActions);

  /* ---------- 列表请求 ---------- */
  async function fetchTypeRows(
    params: Record<string, unknown> & {
      current?: number;
      pageSize?: number;
      code?: string | string[];
      name?: string;
      isEnabled?: number | '';
    },
  ) {
    const {
      current = 1,
      pageSize = 10,
      code,
      name,
      isEnabled,
    } = params;
    const res = await listDictTypeApi({
      page: current,
      pageSize,
      code: code || undefined,
      name: name || undefined,
      status: statusOrUndefined(isEnabled),
    });

    return { data: res.items, total: res.total, success: true };
  }

  async function fetchEntryRows(
    params: Record<string, unknown> & {
      current?: number;
      pageSize?: number;
      value?: string;
      isEnabled?: number | '';
      platform?: string;
      includeGeneral?: boolean;
    },
  ) {
    const {
      current = 1,
      pageSize = 20,
      value,
      isEnabled,
      platform,
      includeGeneral,
    } = params;
    // typeCode 不在搜索表单里，由 entryTypeCodeRef 提供（点击行 / 关闭按钮 / 删除后清空）。
    // 左表（字典类型）本身没有 platform 概念，点击行后不应让 platform 过滤挡住结果：
    // platform 仅在用户实际选择了某个平台时才传；表单未选 = 不限 platform。
    // includeGeneral 仅在 platform 限定到具体平台（非 undefined、非 general）时才有意义。
    const res = await listDictDataApi({
      page: current,
      pageSize,
      typeCode: entryTypeCodeRef.current,
      value: value || undefined,
      status: statusOrUndefined(isEnabled),
      platform: platform || undefined,
      includeGeneral:
        Boolean(platform) &&
        platform !== 'general' &&
        includeGeneral === true,
    });
    return { data: res.items, total: res.total, success: true };
  }

  /**
   * 清除右表的「点击行筛选」状态：
   * - 清空 typeCode ref
   * - 清空 selectedType / selectedTypeId（标题变回「字典数据」）
   * - 重新拉一次右表数据，typeCode 变 undefined 后右表回到「全部」
   */
  function clearEntrySelection() {
    entryTypeCodeRef.current = undefined;
    setSelectedTypeId(null);
    setSelectedType(null);
    entryActionRef.current?.reload?.();
  }

  /* ---------- 保存后回调 ---------- */
  function onTypeSaved() {
    message.success(editingType ? '保存成功' : '创建成功');
    typeActionRef.current?.reload?.();
    if (!editingType) {
      entryActionRef.current?.reload?.();
    }
  }
  function onEntrySaved() {
    message.success(editingEntry ? '保存成功' : '创建成功');
    entryActionRef.current?.reload?.();
  }

  /* ---------- 批量操作 ---------- */
  async function runBulkType(action: BulkAction) {
    if (typeSelectedRowKeys.length === 0) {
      message.warning('请先勾选要操作的字典类型');
      return;
    }
    setTypeBulkLoading(true);
    try {
      await batchDictTypeApi({
        action,
        ids: typeSelectedRowKeys.map((k) => Number(k)),
      });
      message.success(
        action === 'delete'
          ? '批量删除成功'
          : action === 'enable'
            ? '批量启用成功'
            : '批量禁用成功',
      );
      setTypeSelectedRowKeys([]);
      typeActionRef.current?.reload?.();
    } catch (err) {
      message.error(`批量操作失败：${(err as Error).message ?? '未知错误'}`);
    } finally {
      setTypeBulkLoading(false);
    }
  }

  async function runBulkEntry(action: BulkAction) {
    if (entrySelectedRowKeys.length === 0) {
      message.warning('请先勾选要操作的字典项');
      return;
    }
    setEntryBulkLoading(true);
    try {
      await batchDictDataApi({
        action,
        ids: entrySelectedRowKeys.map((k) => Number(k)),
      });
      message.success(
        action === 'delete'
          ? '批量删除成功'
          : action === 'enable'
            ? '批量启用成功'
            : '批量禁用成功',
      );
      setEntrySelectedRowKeys([]);
      entryActionRef.current?.reload?.();
    } catch (err) {
      message.error(`批量操作失败：${(err as Error).message ?? '未知错误'}`);
    } finally {
      setEntryBulkLoading(false);
    }
  }

  /* ---------- 工具栏按钮 ---------- */
  const typeToolbar = () => [
    <Button
      key="create"
      type="primary"
      icon={<PlusOutlined />}
      onClick={() => {
        setEditingType(null);
        setTypeDrawerOpen(true);
      }}
    >
      新建类型
    </Button>,
  ];

  const entryToolbar = () => [
    <Button
      key="create"
      type="primary"
      icon={<PlusOutlined />}
      onClick={() => {
        setEditingEntry(null);
        setEntryDrawerOpen(true);
      }}
    >
      新建条目
    </Button>,
  ];

  /* ---------- 表格多选配置 + 批量操作工具栏 ---------- */
  const typeRowSelection = {
    selectedRowKeys: typeSelectedRowKeys,
    onChange: (keys: React.Key[]) => setTypeSelectedRowKeys(keys),
    preserveSelectedRowKeys: true,
  };
  const entryRowSelection = {
    selectedRowKeys: entrySelectedRowKeys,
    onChange: (keys: React.Key[]) => setEntrySelectedRowKeys(keys),
    preserveSelectedRowKeys: true,
  };

  const renderTypeAlert = ({
    selectedRowKeys,
    onCleanSelected,
  }: {
    selectedRowKeys: React.Key[];
    onCleanSelected: () => void;
  }) => {
    const count = selectedRowKeys.length;
    return (
      <Space size={8}>
        <Typography.Text>
          已选 <strong>{count}</strong> 条
        </Typography.Text>
        <Button
          size="small"
          loading={typeBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkType('enable')}
        >
          批量启用
        </Button>
        <Button
          size="small"
          loading={typeBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkType('disable')}
        >
          批量禁用
        </Button>
        <Popconfirm
          title="确认删除选中的字典类型？"
          description="若仍有字典项将无法删除。"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          disabled={count === 0}
          onConfirm={() => runBulkType('delete')}
        >
          <Button
            size="small"
            danger
            ghost
            icon={<DeleteOutlined />}
            loading={typeBulkLoading}
            disabled={count === 0}
          >
            批量删除
          </Button>
        </Popconfirm>
        <Button size="small" type="text" onClick={onCleanSelected}>
          取消选择
        </Button>
      </Space>
    );
  };

  const renderEntryAlert = ({
    selectedRowKeys,
    onCleanSelected,
  }: {
    selectedRowKeys: React.Key[];
    onCleanSelected: () => void;
  }) => {
    const count = selectedRowKeys.length;
    return (
      <Space size={8}>
        <Typography.Text>
          已选 <strong>{count}</strong> 条
        </Typography.Text>
        <Button
          size="small"
          loading={entryBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkEntry('enable')}
        >
          批量启用
        </Button>
        <Button
          size="small"
          loading={entryBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkEntry('disable')}
        >
          批量禁用
        </Button>
        <Popconfirm
          title="确认删除选中的字典项？"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          disabled={count === 0}
          onConfirm={() => runBulkEntry('delete')}
        >
          <Button
            size="small"
            danger
            ghost
            icon={<DeleteOutlined />}
            loading={entryBulkLoading}
            disabled={count === 0}
          >
            批量删除
          </Button>
        </Popconfirm>
        <Button size="small" type="text" onClick={onCleanSelected}>
          取消选择
        </Button>
      </Space>
    );
  };

  return (
    <ContentContainer heightMode="auto" scrollable padding="16px">
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <ProTable<DictType>
            headerTitle="字典类型"
            cardBordered
            rowKey="id"
            actionRef={typeActionRef}
            columns={typeCols}
            search={{ labelWidth: 'auto' }}
            request={fetchTypeRows}
            pagination={{
              // 用 defaultPageSize 代替 pageSize：ProTable 会把 pageSize 透传给 antd Table，
              // 而 antd Table 在受控模式下会一直用这个值，导致改变分页大小后显示不更新。
              defaultPageSize: 10,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
            }}
            toolBarRender={typeToolbar}
            options={{
              reload: () => typeActionRef.current?.reload?.(),
              setting: { listsHeight: 400 },
            }}
            dateFormatter="string"
            onRow={(record) => ({
              onClick: () => {
                setSelectedTypeId(record.id);
                setSelectedType(record);
                // 选中左表行后，把右表的 typeCode（由 entryTypeCodeRef 持有）
                // 同步为该类型编码。
                entryTypeCodeRef.current = record.code;
                entryActionRef.current?.reload?.();
              },
              style: {
                cursor: 'pointer',
                background:
                  selectedTypeId === record.id
                    ? 'rgba(59,130,246,0.08)'
                    : undefined,
              },
            })}
            rowSelection={typeRowSelection}
            tableAlertRender={renderTypeAlert}
            tableAlertOptionRender={false}
          />
        </Col>

        <Col xs={24} md={12}>
          <ProTable<DictData>
            headerTitle={
              <Space size={8} align="center" wrap>
                <span>字典数据</span>
                {selectedType && (
                  <Tag
                    closable
                    onClose={(e) => {
                      e.preventDefault();
                      clearEntrySelection();
                    }}
                    style={{ margin: 0 }}
                  >
                    {selectedType.name}（{selectedType.code}）
                  </Tag>
                )}
              </Space>
            }
            cardBordered
            rowKey="id"
            actionRef={entryActionRef}
            columns={entryCols}
            search={{ labelWidth: 'auto' }}
            request={fetchEntryRows}
            pagination={{
              // 同左表：用 defaultPageSize 让 antd Table 用自身受控状态显示当前分页大小
              defaultPageSize: 20,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
            }}
            // 右表数据量较大，启用表内垂直滚动，避免整页被顶长。
            // 高度为视口减去头部 / 搜索栏 / 分页 / 卡片 padding 后的估算值，
            // 通过 scroll.y 给 antd Table 一个明确的可滚动高度。
            scroll={{ x: 'max-content', y: 'calc(100vh - 360px)' }}
            toolBarRender={entryToolbar}
            options={{
              reload: () => entryActionRef.current?.reload?.(),
              setting: { listsHeight: 400 },
            }}
            dateFormatter="string"
            locale={{
              emptyText: '暂无数据',
            }}
            rowSelection={entryRowSelection}
            tableAlertRender={renderEntryAlert}
            tableAlertOptionRender={false}
          />
        </Col>
      </Row>

      <DictTypeDrawer
        open={typeDrawerOpen}
        row={editingType}
        onClose={() => setTypeDrawerOpen(false)}
        onSaved={onTypeSaved}
      />
      <DictDataDrawer
        open={entryDrawerOpen}
        row={editingEntry}
        defaultTypeId={selectedTypeId ?? undefined}
        typeOptions={typeOptions}
        onClose={() => setEntryDrawerOpen(false)}
        onSaved={() => onEntrySaved()}
      />
    </ContentContainer>
  );
};

export default DictPage;
