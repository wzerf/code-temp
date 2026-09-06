import { useRef, useState } from 'react';
import JSZip from 'jszip';
import { Button, Col, Modal, Popconfirm, Row, Space, Table, Tag, Typography } from 'antd';
import { message } from '@/core/feedback/message';
import {
  DeleteOutlined,
  DownloadOutlined,
  ImportOutlined,
  PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useDictLookups } from '@/api/hooks/dict';
import {
  batchI18nLocaleApi,
  batchI18nTranslationApi,
  deleteI18nLocaleApi,
  deleteI18nTranslationApi,
  exportI18nBatchApi,
  listI18nLocaleApi,
  listI18nTranslationApi,
  listI18nTranslationKeyApi } from '@/api/rest/i18n';
import type {
  I18nLocale,
  I18nTranslation,
  I18nTranslationKey } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import { formatDateTime } from '@/utils/date';
import I18nLocaleDrawer from './modules/locale-drawer';
import I18nImportModal from './modules/import-modal';
import I18nTranslationKeyDrawer from './modules/translation-key-drawer';

type BulkAction = 'enable' | 'disable' | 'delete';

const I18nPage = () => {
  const localeActionRef = useRef<ActionType | undefined>(undefined);
  const translationActionRef = useRef<ActionType | undefined>(undefined);
  const translationKeyActionRef = useRef<ActionType | undefined>(undefined);

  // 左表选中态
  const [selectedLocaleId, setSelectedLocaleId] = useState<number | null>(null);
  const [selectedLocale, setSelectedLocale] = useState<I18nLocale | null>(null);

  // 右表当前 localeCode（点击行 / 关闭按钮写入；fetchTranslationRows 直接读 ref）
  const entryLocaleCodeRef = useRef<string | undefined>(undefined);
  const entryLocaleIdRef = useRef<number | undefined>(undefined);
  const [entryLocaleCode, setEntryLocaleCode] = useState<string | undefined>(
    undefined,
  );

  // 多选状态
  const [localeSelectedRowKeys, setLocaleSelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [translationSelectedRowKeys, setTranslationSelectedRowKeys] = useState<
    React.Key[]
  >([]);
  const [localeBulkLoading, setLocaleBulkLoading] = useState(false);
  const [translationBulkLoading, setTranslationBulkLoading] = useState(false);

  // 导入 / 导出
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importModalKey, setImportModalKey] = useState(0);
  const [exportModalOpen, setExportModalOpen] = useState(false);
  const [exportType, setExportType] = useState<'raw' | 'simple'>('simple');

  // 默认视图下展开的主行 translationKey
  const [expandedTranslationKeys, setExpandedTranslationKeys] = useState<
    React.Key[]
  >([]);

  // 字典驱动：默认列 + 状态列走 useDictLookups，列表 load 后才显示彩色 Tag。
  // useDictLookups 内部走 useListDictData(includeGeneral=true, platform=currentPlatform)，
  // 跨页面与字典页共用同一份字典数据。
  const dictLookups = useDictLookups();

  // 抽屉
  const [localeDrawerOpen, setLocaleDrawerOpen] = useState(false);
  const [editingLocale, setEditingLocale] = useState<I18nLocale | null>(null);
  const [translationDrawerOpen, setTranslationDrawerOpen] = useState(false);
  const [editingTranslation, setEditingTranslation] =
    useState<I18nTranslation | null>(null);
  const [newTranslationKey, setNewTranslationKey] = useState<string>('');

  // 二态视图判断
  const isCollapsedView = selectedLocaleId === null;

  // 列定义
  const localeColumns: ProColumns<I18nLocale>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    { title: '语言代码', dataIndex: 'code', width: 140, ellipsis: true },
    { title: '语言名称', dataIndex: 'name', width: 140, ellipsis: true },
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
      title: '排序',
      dataIndex: 'sort',
      width: 80,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'isEnabled',
      width: 90,
      search: false,
      valueType: 'select',
      valueEnum: {
        1: { text: '启用' },
        0: { text: '禁用' },
      },
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
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, r) => formatDateTime(r.updatedAt),
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 120,
      fixed: 'right',
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      render: (_: any, record: I18nLocale) => [
        <a
          key="edit"
          onClick={(e) => {
            e.stopPropagation();
            setEditingLocale(record);
            setLocaleDrawerOpen(true);
          }}
        >
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title="确认删除该语言？"
          description="若仍有翻译将无法删除；默认语言禁止删除。"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          onConfirm={async () => {
            try {
              await deleteI18nLocaleApi(record.id);
              message.success('删除成功');
              localeActionRef.current?.reload?.();
              if (selectedLocaleId === record.id) {
                clearTranslationSelection();
              }
            } catch (err) {
              message.error(
                `删除失败：${(err as Error).message ?? '未知错误'}`,
              );
            }
          }}
        >
          <a
            key="delete-link"
            style={{ color: '#ff4d4f' }}
            onClick={(e) => e.stopPropagation()}
          >
            删除
          </a>
        </Popconfirm>,
      ],
    },
  ];

  const translationColumns: ProColumns<I18nTranslation>[] = [
    { title: 'ID', dataIndex: 'id', width: 80, search: false },
    {
      title: '翻译键',
      dataIndex: 'translationKey',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '翻译值',
      dataIndex: 'value',
      width: 200,
      ellipsis: true,
      search: false,
    },
    {
      title: '状态',
      dataIndex: 'isEnabled',
      width: 90,
      valueType: 'select',
      search: false,
      valueEnum: {
        1: { text: '启用' },
        0: { text: '禁用' },
      },
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
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 170,
      valueType: 'dateTime',
      search: false,
      render: (_, r) => formatDateTime(r.updatedAt),
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 120,
      fixed: 'right',
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      render: (_: any, record: I18nTranslation) => [
        <a
          key="edit"
          onClick={() => {
            setEditingTranslation(record);
            setTranslationDrawerOpen(true);
          }}
        >
          编辑
        </a>,
        <Popconfirm
          key="delete"
          title="确认删除该翻译？"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          onConfirm={async () => {
            try {
              await deleteI18nTranslationApi(record.id);
              message.success('删除成功');
              translationActionRef.current?.reload?.();
            } catch (err) {
              message.error(
                `删除失败：${(err as Error).message ?? '未知错误'}`,
              );
            }
          }}
        >
          <a key="delete-link" style={{ color: '#ff4d4f' }}>
            删除
          </a>
        </Popconfirm>,
      ],
    },
  ];

  // 翻译列：把搜索区 value 字段改为"键或值"模糊搜索（让 value 搜索支持 translation_key）
  const translationSearchColumns: ProColumns<I18nTranslation>[] = [
    ...translationColumns.slice(0, 2),
    {
      title: '键或值',
      dataIndex: 'value',
      hideInTable: true,
    },
    ...translationColumns.slice(2),
  ];

  /* ============================================================
   * 默认视图：按 translationKey 聚合的主行 + expandable 子表
   * ============================================================ */

  const translationKeyColumns: ProColumns<I18nTranslationKey>[] = [
    {
      title: '翻译键',
      dataIndex: 'translationKey',
      ellipsis: true,
      search: false,
    },
    {
      title: '语言数',
      dataIndex: 'localeCount',
      width: 100,
      search: false,
      render: (_, r) => <Tag color="blue">{r.localeCount} 语言</Tag>,
    },
    {
      title: '操作',
      valueType: 'option',
      key: 'option',
      width: 120,
      fixed: 'right',
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      render: (_: any, record: I18nTranslationKey) => [
        <a
          key="edit"
          onClick={() => {
            // 用 sampleRowId 作为 sourceRow（抽屉内部走 byKeyQuery，只看 translationKey）
            const placeholder: I18nTranslation = {
              id: record.sampleRowId,
              localeId: record.sampleLocaleId,
              translationKey: record.translationKey,
              localeCode: record.sampleLocaleCode,
              value: '',
              remark: '',
              isEnabled: 1,
              deletedAt: 0,
              createdAt: '',
              createdBy: 0,
              updatedAt: record.sampleUpdatedAt,
              updatedBy: 0,
            };
            setEditingTranslation(placeholder);
            setTranslationDrawerOpen(true);
          }}
        >
          编辑
        </a>,
      ],
    },
  ];

  // 默认视图下：主行的 queryParams（保留 ProTable 的搜索区交互，但去掉 value 的列匹配）
  const translationKeySearchColumns: ProColumns<I18nTranslationKey>[] = [
    {
      title: '翻译键',
      dataIndex: 'translationKey',
      hideInTable: true,
    },
    ...translationKeyColumns,
  ];

  // 主行展开时按需拉该 key 下的所有 locale 行
  const [childRowsByKey, setChildRowsByKey] = useState<
    Record<string, I18nTranslation[]>
  >({});
  const [childLoadingKey, setChildLoadingKey] = useState<string | null>(null);

  async function ensureChildRows(translationKey: string) {
    if (childRowsByKey[translationKey]) return;
    setChildLoadingKey(translationKey);
    try {
      const res = await listI18nTranslationApi({ page: 1, pageSize: 1000 });
      const filtered = res.items.filter(
        (it) => it.translationKey === translationKey,
      );
      setChildRowsByKey((prev) => ({ ...prev, [translationKey]: filtered }));
    } finally {
      setChildLoadingKey(null);
    }
  }

  function deleteChildRow(parentKey: string, row: I18nTranslation) {
    Modal.confirm({
      title: '确认删除该翻译？',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteI18nTranslationApi(row.id);
          message.success('删除成功');
          setChildRowsByKey((prev) => ({
            ...prev,
            [parentKey]: (prev[parentKey] ?? []).filter(
              (it) => it.id !== row.id,
            ),
          }));
          // 主行 localeCount 变化，重载主表
          translationKeyActionRef.current?.reload?.();
        } catch (err) {
          message.error(
            `删除失败：${(err as Error).message ?? '未知错误'}`,
          );
        }
      },
    });
  }

  const childColumns = [
    {
      title: '语言',
      dataIndex: 'localeCode',
      key: 'localeCode',
      width: 120,
      render: (text: string | undefined) => text || '-',
    },
    {
      title: '翻译值',
      dataIndex: 'value',
      key: 'value',
      ellipsis: true,
      width: 240,
    },
    {
      title: '状态',
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      width: 90,
      render: (_: unknown, r: I18nTranslation) => {
        const tagType = dictLookups.lookupSwitchTagType(r.isEnabled);
        return (
          <Tag color={tagType && tagType !== 'default' ? tagType : undefined}>
            {dictLookups.lookupSwitchLabel(r.isEnabled)}
          </Tag>
        );
      },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      key: 'remark',
      ellipsis: true,
      width: 160,
      render: (text: string | undefined) =>
        text ? (
          <span title={text}>{text}</span>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (text: string | undefined) => formatDateTime(text),
    },
    {
      title: '操作',
      key: 'childAction',
      width: 140,
      fixed: 'right' as const,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      render: (_: any, record: I18nTranslation) => {
        const parentKey = record.translationKey;
        return (
          <Space size="middle">
            <a
              key="edit"
              onClick={() => {
                setEditingTranslation(record);
                setTranslationDrawerOpen(true);
              }}
            >
              编辑
            </a>
            <a
              key="delete"
              style={{ color: '#ff4d4f' }}
              onClick={() => deleteChildRow(parentKey, record)}
            >
              删除
            </a>
          </Space>
        );
      },
    },
  ];

  // 列表请求
  async function fetchLocaleRows(
    params: Record<string, unknown> & {
      current?: number;
      pageSize?: number;
      code?: string;
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
    const status =
      isEnabled === '' || isEnabled === undefined
        ? undefined
        : (Number(isEnabled) as 0 | 1);
    const res = await listI18nLocaleApi({
      page: current,
      pageSize,
      code: code || undefined,
      name: name || undefined,
      status,
    });
    return { data: res.items, total: res.total, success: true };
  }

  async function fetchTranslationRows(
    params: Record<string, unknown> & {
      current?: number;
      pageSize?: number;
      value?: string;
      isEnabled?: number | '';
    },
  ) {
    const {
      current = 1,
      pageSize = 20,
      value,
      isEnabled,
    } = params;
    const status =
      isEnabled === '' || isEnabled === undefined
        ? undefined
        : (Number(isEnabled) as 0 | 1);
    const res = await listI18nTranslationApi({
      page: current,
      pageSize,
      localeCode: entryLocaleCodeRef.current,
      value: value || undefined,
      status,
    });
    return { data: res.items, total: res.total, success: true };
  }

  async function fetchTranslationKeyRows(
    params: Record<string, unknown> & {
      current?: number;
      pageSize?: number;
      translationKey?: string;
    },
  ) {
    const { current = 1, pageSize = 20, translationKey } = params;
    const res = await listI18nTranslationKeyApi({
      page: current,
      pageSize,
      value: translationKey || undefined,
    });
    return { data: res.items, total: res.total, success: true };
  }

  function clearTranslationSelection() {
    entryLocaleCodeRef.current = undefined;
    entryLocaleIdRef.current = undefined;
    setEntryLocaleCode(undefined);
    setSelectedLocaleId(null);
    setSelectedLocale(null);
    translationActionRef.current?.reload?.();
    translationKeyActionRef.current?.reload?.();
  }

  // 保存回调
  function onLocaleSaved() {
    localeActionRef.current?.reload?.();
    if (!editingLocale) {
      // 新建语言时让右表回到「全部」状态
      clearTranslationSelection();
    } else {
      // 编辑后让右表跟随最新 localeCode 刷新
      translationActionRef.current?.reload?.();
    }
  }
  function onTranslationSaved() {
    translationActionRef.current?.reload?.();
    translationKeyActionRef.current?.reload?.();
  }

  // 批量操作
  async function runBulkLocale(action: BulkAction) {
    if (localeSelectedRowKeys.length === 0) {
      message.warning('请先勾选要操作的语言');
      return;
    }
    setLocaleBulkLoading(true);
    try {
      await batchI18nLocaleApi({
        action,
        ids: localeSelectedRowKeys.map((k) => Number(k)),
      });
      message.success(
        action === 'delete'
          ? '批量删除成功'
          : action === 'enable'
            ? '批量启用成功'
            : '批量禁用成功',
      );
      setLocaleSelectedRowKeys([]);
      localeActionRef.current?.reload?.();
      // 避免引用已删除语言
      if (action === 'delete' && selectedLocaleId !== null) {
        clearTranslationSelection();
      }
    } catch (err) {
      message.error(`批量操作失败：${(err as Error).message ?? '未知错误'}`);
    } finally {
      setLocaleBulkLoading(false);
    }
  }

  async function runBulkTranslation(action: BulkAction) {
    if (translationSelectedRowKeys.length === 0) {
      message.warning('请先勾选要操作的翻译');
      return;
    }
    setTranslationBulkLoading(true);
    try {
      await batchI18nTranslationApi({
        action,
        ids: translationSelectedRowKeys.map((k) => Number(k)),
      });
      message.success(
        action === 'delete'
          ? '批量删除成功'
          : action === 'enable'
            ? '批量启用成功'
            : '批量禁用成功',
      );
      setTranslationSelectedRowKeys([]);
      translationActionRef.current?.reload?.();
    } catch (err) {
      message.error(`批量操作失败：${(err as Error).message ?? '未知错误'}`);
    } finally {
      setTranslationBulkLoading(false);
    }
  }

  // 导入 / 导出
  function openExportModal() {
    const ids = localeSelectedRowKeys.map((k) => Number(k));
    if (ids.length === 0) {
      message.warning('请先勾选要导出的语言');
      return;
    }
    setExportModalOpen(true);
  }

  async function confirmExport() {
    const ids = localeSelectedRowKeys.map((k) => Number(k));
    try {
      const data = await exportI18nBatchApi({ ids, type: exportType });
      const zip = new JSZip();
      for (const file of data.files) {
        const filename = `${file.code}.${file.format}.json`;
        zip.file(filename, JSON.stringify(file.content, null, 2));
      }
      const blob = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `i18n-export-${exportType}.zip`;
      a.click();
      URL.revokeObjectURL(url);
      message.success(
        `导出成功：${data.files.length} 个语言已打包下载`,
      );
      setExportModalOpen(false);
    } catch (error: unknown) {
      message.error(`导出失败：${(error as Error).message ?? '未知错误'}`);
    }
  }

  function onImportSuccess() {
    localeActionRef.current?.reload?.();
    translationActionRef.current?.reload?.();
    translationKeyActionRef.current?.reload?.();
  }

  // 工具栏
  const localeToolbar = () => [
    <Button
      key="import"
      icon={<ImportOutlined />}
      onClick={() => {
        setImportModalOpen(true);
        setImportModalKey((k) => k + 1);
      }}
    >
      导入
    </Button>,
    <Button
      key="export"
      icon={<DownloadOutlined />}
      disabled={localeSelectedRowKeys.length === 0}
      onClick={openExportModal}
    >
      导出
    </Button>,
     <Button
      key="create"
      type="primary"
      icon={<PlusOutlined />}
      onClick={() => {
        setEditingLocale(null);
        setLocaleDrawerOpen(true);
      }}
    >
      新建语言
    </Button>,
  ];
  const translationToolbar = () => [
    <Button
      key="create"
      type="primary"
      icon={<PlusOutlined />}
      onClick={() => {
        setEditingTranslation(null);
        setNewTranslationKey('');
        setTranslationDrawerOpen(true);
      }}
    >
      新建翻译
    </Button>,
  ];

  // 多选
  const localeRowSelection = {
    selectedRowKeys: localeSelectedRowKeys,
    onChange: (keys: React.Key[]) => setLocaleSelectedRowKeys(keys),
    preserveSelectedRowKeys: true,
  };
  const translationRowSelection = {
    selectedRowKeys: translationSelectedRowKeys,
    onChange: (keys: React.Key[]) => setTranslationSelectedRowKeys(keys),
    preserveSelectedRowKeys: true,
  };

  // 选中条
  const renderLocaleAlert = ({
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
          loading={localeBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkLocale('enable')}
        >
          批量启用
        </Button>
        <Button
          size="small"
          loading={localeBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkLocale('disable')}
        >
          批量禁用
        </Button>
        <Popconfirm
          title="确认删除选中的语言？"
          description="若仍有翻译将无法删除；默认语言禁止删除。"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          disabled={count === 0}
          onConfirm={() => runBulkLocale('delete')}
        >
          <Button
            size="small"
            danger
            ghost
            icon={<DeleteOutlined />}
            loading={localeBulkLoading}
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

  const renderTranslationAlert = ({
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
          loading={translationBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkTranslation('enable')}
        >
          批量启用
        </Button>
        <Button
          size="small"
          loading={translationBulkLoading}
          disabled={count === 0}
          onClick={() => runBulkTranslation('disable')}
        >
          批量禁用
        </Button>
        <Popconfirm
          title="确认删除选中的翻译？"
          okText="删除"
          okButtonProps={{ danger: true }}
          cancelText="取消"
          disabled={count === 0}
          onConfirm={() => runBulkTranslation('delete')}
        >
          <Button
            size="small"
            danger
            ghost
            icon={<DeleteOutlined />}
            loading={translationBulkLoading}
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
    <ContentContainer heightMode="fixed" scrollable padding="16px">
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <ProTable<I18nLocale>
            headerTitle="语言"
            cardBordered
            rowKey="id"
            actionRef={localeActionRef}
            columns={localeColumns}
            search={{ labelWidth: 'auto' }}
            request={fetchLocaleRows}
            pagination={{
              defaultPageSize: 10,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 条`,
            }}
            toolBarRender={localeToolbar}
            options={{
              reload: () => localeActionRef.current?.reload?.(),
              setting: { listsHeight: 400 },
            }}
            dateFormatter="string"
            onRow={(record) => ({
              onClick: () => {
                setSelectedLocaleId(record.id);
                setSelectedLocale(record);
                entryLocaleCodeRef.current = record.code;
                entryLocaleIdRef.current = record.id;
                setEntryLocaleCode(record.code);
                translationActionRef.current?.reload?.();
                translationKeyActionRef.current?.reload?.();
              },
              style: {
                cursor: 'pointer',
                background:
                  selectedLocaleId === record.id
                    ? 'rgba(59,130,246,0.08)'
                    : undefined,
              },
            })}
            rowSelection={localeRowSelection}
            tableAlertRender={renderLocaleAlert}
            tableAlertOptionRender={false}
          />
        </Col>

        <Col xs={24} md={12}>
          {isCollapsedView ? (
            <ProTable<I18nTranslationKey>
              headerTitle={
                <Space size={8} align="center" wrap>
                  <span>翻译</span>
                  <Tag color="default" style={{ margin: 0 }}>
                    按 key 聚合视图
                  </Tag>
                </Space>
              }
              cardBordered
              rowKey="translationKey"
              actionRef={translationKeyActionRef}
              columns={translationKeySearchColumns}
              search={{ labelWidth: 'auto' }}
              request={fetchTranslationKeyRows}
              pagination={{
                defaultPageSize: 20,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
              }}
              scroll={{ x: 'max-content' }}
              toolBarRender={translationToolbar}
              options={{
                reload: () => translationKeyActionRef.current?.reload?.(),
                setting: { listsHeight: 400 },
              }}
              dateFormatter="string"
              locale={{ emptyText: '暂无数据' }}
              expandable={{
                expandedRowKeys: expandedTranslationKeys,
                onExpand: (expanded, record) => {
                  const key = record.translationKey;
                  const next = expanded
                    ? [...expandedTranslationKeys, key]
                    : expandedTranslationKeys.filter((k) => k !== key);
                  setExpandedTranslationKeys(next);
                  if (expanded) {
                    void ensureChildRows(key);
                  }
                },
                expandedRowRender: (record) => (
                  <Table<I18nTranslation>
                    columns={childColumns}
                    dataSource={childRowsByKey[record.translationKey] ?? []}
                    loading={childLoadingKey === record.translationKey}
                    pagination={false}
                    size="small"
                    rowKey="id"
                    scroll={{ x: 'max-content' }}
                  />
                ),
              }}
            />
          ) : (
            <ProTable<I18nTranslation>
              headerTitle={
                <Space size={8} align="center" wrap>
                  <span>翻译</span>
                  {selectedLocale && (
                    <Tag
                      closable
                      onClose={(e) => {
                        e.preventDefault();
                        clearTranslationSelection();
                      }}
                      style={{ margin: 0 }}
                    >
                      {selectedLocale.name}（{selectedLocale.code}）
                    </Tag>
                  )}
                </Space>
              }
              cardBordered
              rowKey="id"
              actionRef={translationActionRef}
              columns={translationSearchColumns}
              search={{ labelWidth: 'auto' }}
              request={fetchTranslationRows}
              pagination={{
                defaultPageSize: 20,
                showSizeChanger: true,
                showTotal: (total) => `共 ${total} 条`,
              }}
              scroll={{ x: 'max-content' }}
              toolBarRender={translationToolbar}
              options={{
                reload: () => translationActionRef.current?.reload?.(),
                setting: { listsHeight: 400 },
              }}
              dateFormatter="string"
              locale={{ emptyText: '暂无数据' }}
              rowSelection={translationRowSelection}
              tableAlertRender={renderTranslationAlert}
              tableAlertOptionRender={false}
            />
          )}
        </Col>
      </Row>

      <I18nLocaleDrawer
        open={localeDrawerOpen}
        row={editingLocale}
        onClose={() => setLocaleDrawerOpen(false)}
        onSaved={onLocaleSaved}
      />
      <I18nImportModal
        key={importModalKey}
        open={importModalOpen}
        onClose={() => setImportModalOpen(false)}
        onSuccess={onImportSuccess}
      />
      <Modal
        title="导出 JSON"
        open={exportModalOpen}
        onOk={confirmExport}
        onCancel={() => setExportModalOpen(false)}
        okText="导出"
        cancelText="取消"
        width={400}
        destroyOnHidden
      >
        <Space size="middle" align="center">
          <Typography.Text type="secondary">导出格式</Typography.Text>
          <Button
            size="small"
            type={exportType === 'simple' ? 'primary' : 'default'}
            onClick={() => setExportType('simple')}
          >
            Simple
          </Button>
          <Button
            size="small"
            type={exportType === 'raw' ? 'primary' : 'default'}
            onClick={() => setExportType('raw')}
          >
            Raw
          </Button>
        </Space>
      </Modal>
      <I18nTranslationKeyDrawer
        open={translationDrawerOpen}
        sourceRow={editingTranslation}
        defaultLocaleCode={entryLocaleCode ?? 'zh-CN'}
        initialKey={newTranslationKey}
        onClose={() => setTranslationDrawerOpen(false)}
        onSaved={onTranslationSaved}
      />
    </ContentContainer>
  );
};

export default I18nPage;
