import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  message,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Steps,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
} from 'antd';
import {
  CheckCircleOutlined,
  DownloadOutlined,
  InboxOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  useImportI18nBatch,
  useListAllI18nLocale,
} from '@/api/hooks/i18n';
import { previewI18nImportApi } from '@/api/rest/i18n';
import type { I18nLocale } from '@/api/rest/types';
import {
  buildPreviewRows,
  filterChangedOnly,
  mergeAllFiles,
  normalizePrefix,
  previewStats,
  type ImportFormat,
  type PreviewRow,
  type StagedFile,
} from './import-utils';

const { Text } = Typography;
const { Dragger } = Upload;

interface I18nImportModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface FileConfigRow {
  key: string;
  index: number;
  name: string;
  format: ImportFormat;
  parseOk: boolean;
  errorMessage?: string;
  localeCode: string;
  prefix: string;
  payloadLocale?: StagedFile['payloadLocale'];
}

const I18nImportModal: React.FC<I18nImportModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const [step, setStep] = useState(0);
  const [format, setFormat] = useState<ImportFormat>('simple');
  const [staged, setStaged] = useState<StagedFile[]>([]);
  const [showChangedOnly, setShowChangedOnly] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const { data: locales = [] } = useListAllI18nLocale(
    { status: 1 },
    { enabled: open },
  );

  const importMutation = useImportI18nBatch();

  const localeOptions = useMemo(
    () =>
      locales.map((l: I18nLocale) => ({
        label: `${l.name}（${l.code}）`,
        value: l.code,
      })),
    [locales],
  );

  // 打开时由父组件通过 key 强制重挂载，无需 useEffect 重置

  // 步骤 1 → 步骤 2 同步
  const onFormatChange = (next: ImportFormat) => {
    setFormat(next);
    // 切换格式时清空 staged（避免 mixed format）
    setStaged([]);
  };

  // 下载模板
  const downloadTemplate = () => {
    let template: unknown;
    if (format === 'raw') {
      template = {
        '@type': 'raw',
        locale: {
          code: 'zh-CN',
          name: '简体中文',
          isDefault: 1,
          sort: 0,
          isEnabled: 1,
        },
        translations: [
          {
            translationKey: 'page.title',
            value: '页面标题',
            isEnabled: 1,
          },
          {
            translationKey: 'page.desc',
            value: '页面描述',
            isEnabled: 1,
          },
        ],
      };
    } else {
      template = {
        '@type': 'simple',
        page: {
          title: '页面标题',
          desc: '页面描述',
        },
        common: {
          save: '保存',
          cancel: '取消',
        },
      };
    }
    const blob = new Blob([JSON.stringify(template, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `i18n-${format}-template.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // 占位/已入列文件的复合 key。name+size 足够防 antd Dragger 同一次拖拽
  // 多次 onChange 派发的 race（每次 originFileObj 引用不同但 size 相同）。
  const stagedKey = (s: { name: string; file: File }) => `${s.name}@${s.file.size}`;

  // 解析文件并同位替换 staged 中的占位。handleUploadChange 已经同步把
  // parseOk=false 的占位推进了 staged，这里只 fill 真实字段。
  const ingestFile = async (f: File, key: string) => {
    const fill = (patch: Partial<StagedFile>) => {
      setStaged((prev) => {
        const i = prev.findIndex((s) => stagedKey(s) === key);
        if (i < 0) return prev; // 已被用户移除：什么都不做
        const next = [...prev];
        next[i] = { ...next[i], ...patch };
        return next;
      });
    };

    let text: string;
    try {
      text = await f.text();
    } catch (err: unknown) {
      fill({ parseOk: false, errorMessage: `读取文件失败：${(err as Error).message ?? '未知错误'}` });
      message.error(`读取文件 ${f.name} 失败`);
      return;
    }

    let payload: unknown;
    try {
      payload = JSON.parse(text);
    } catch {
      fill({ parseOk: false, errorMessage: 'JSON 解析失败' });
      message.error(`文件 ${f.name} 解析失败`);
      return;
    }

    const obj = payload as Record<string, unknown> | null;
    const detectedFormat =
      obj && typeof obj === 'object' && obj['@type'] === 'raw'
        ? 'raw'
        : ('simple' as ImportFormat);
    if (detectedFormat !== format) {
      message.warning(
        `文件 ${f.name} 检测为 ${detectedFormat} 格式，与当前选定的 ${format} 不一致`,
      );
    }
    const payloadLocale =
      detectedFormat === 'raw' && obj
        ? (obj.locale as StagedFile['payloadLocale'])
        : undefined;
    const localeCode =
      detectedFormat === 'raw' && payloadLocale?.code
        ? payloadLocale.code
        : '';
    fill({
      payload,
      parseOk: true,
      errorMessage: undefined,
      localeCode,
      payloadLocale,
    });
  };

  const handleUploadChange: UploadProps['onChange'] = (info) => {
    const files = info.fileList
      .map((f) => f.originFileObj)
      .filter((f): f is NonNullable<typeof f> => !!f);

    setStaged((prev) => {
      // 同步写入占位 + 同帧去重：第二次 onChange 在同帧内看到 prev 已含本文件 key
      // → 不会再 append 一次。这正是修复 antd Dragger 多次 onChange 竞态的关键。
      const known = new Set(prev.map(stagedKey));
      const newFiles = files.filter((f) => {
        const k = `${f.name}@${f.size}`;
        if (known.has(k)) return false;
        known.add(k);
        return true;
      });
      if (newFiles.length === 0) return prev;
      const placeholders: StagedFile[] = newFiles.map((f) => ({
        name: f.name,
        file: f,
        payload: null,
        parseOk: false,
        errorMessage: '解析中…',
        localeCode: '',
        prefix: '',
      }));
      // 同步起异步解析（不 await，UI 立即可点下一步）
      for (const f of newFiles) {
        void ingestFile(f, `${f.name}@${f.size}`);
      }
      return [...prev, ...placeholders];
    });
  };

  // 步骤 1 → 2 校验
  const canGoStep2 = useMemo(() => {
    return staged.length > 0 && staged.some((s) => s.parseOk);
  }, [staged]);

  // 步骤 2 → 3 校验
  const canGoStep3 = useMemo(() => {
    return staged
      .filter((s) => s.parseOk)
      .every((s) => s.localeCode && s.localeCode.length > 0);
  }, [staged]);

  // 步骤 2 行数据
  const fileConfigRows: FileConfigRow[] = useMemo(
    () =>
      staged.map((s, idx) => ({
        key: `${idx}-${s.name}`,
        index: idx,
        name: s.name,
        format: s.parseOk
          ? ((s.payload as Record<string, unknown>)?.['@type'] === 'raw'
              ? 'raw'
              : 'simple')
          : 'simple',
        parseOk: s.parseOk,
        errorMessage: s.errorMessage,
        localeCode: s.localeCode,
        prefix: s.prefix,
        payloadLocale: s.payloadLocale,
      })),
    [staged],
  );

  const updateStaged = (idx: number, patch: Partial<StagedFile>) => {
    setStaged((prev) => {
      const next = [...prev];
      next[idx] = { ...next[idx], ...patch };
      return next;
    });
  };

  const removeStaged = (idx: number) => {
    setStaged((prev) => prev.filter((_, i) => i !== idx));
  };

  // 步骤 3 预览数据
  const mergedRows = useMemo(() => mergeAllFiles(staged), [staged]);
  const previewKeysByLocale = useMemo(() => {
    const m = new Map<string, string[]>();
    for (const r of mergedRows) {
      const arr = m.get(r.localeCode) ?? [];
      arr.push(r.key);
      m.set(r.localeCode, arr);
    }
    return Array.from(m.entries()).map(([localeCode, keys]) => ({
      localeCode,
      keys: Array.from(new Set(keys)),
    }));
  }, [mergedRows]);

  // 当进入步骤 3 时拉一次 preview
  const goToStep3 = () => {
    setStep(2);
    void previewMutation(previewKeysByLocale);
  };

  // 直接用一个独立 mutation 来拉 preview，避免 queryKey 重建带来的复杂
  const [previewState, setPreviewState] = useState<{
    loading: boolean;
    currentRows: Array<{
      localeCode: string;
      translationKey: string;
      value: string;
      isEnabled: 0 | 1;
    }>;
  }>({ loading: false, currentRows: [] });

  const previewMutation = async (
    items: Array<{ localeCode: string; keys: string[] }>,
  ) => {
    setPreviewState({ loading: true, currentRows: [] });
    try {
      const res = await previewI18nImportApi({ items });
      setPreviewState({
        loading: false,
        currentRows: (res.currentRows ?? []).map((r) => ({
          localeCode: r.localeCode ?? '',
          translationKey: r.translationKey,
          value: r.value,
          isEnabled: r.isEnabled,
        })),
      });
    } catch (err) {
      setPreviewState({ loading: false, currentRows: [] });
      message.error(
        `预览失败：${(err as Error).message ?? '未知错误'}`,
      );
    }
  };

  const previewRows = useMemo(
    () => buildPreviewRows(mergedRows, previewState.currentRows),
    [mergedRows, previewState.currentRows],
  );
  const stats = useMemo(() => previewStats(previewRows), [previewRows]);
  const visiblePreviewRows = useMemo(
    () => (showChangedOnly ? filterChangedOnly(previewRows) : previewRows),
    [previewRows, showChangedOnly],
  );

  // 步骤 3 列
  const previewColumns: ColumnsType<PreviewRow> = [
    {
      title: '变更类型',
      dataIndex: 'op',
      align: 'center',
      width: 80,
      render: (_, r) => {
        if (r.op === 'create') return <Tag color="green">新增</Tag>;
        if (r.op === 'update') return <Tag color="blue">修改</Tag>;
        return <Tag>未变更</Tag>;
      },
    },
    {
      title: '语言代码',
      align: 'center',
      dataIndex: 'localeCode',
      width: 100,
    },
    {
      title: '翻译键',
      dataIndex: 'key',
      minWidth: 200,
      render: (_, r) => (
        <Space size={4}>
          {r.duplicate && <Tag color="red" style={{ margin: 0 }}>重复</Tag>}
          <span style={{ fontFamily: 'monospace' }}>{r.key}</span>
          {r.oldIsEnabled !== undefined &&
            (r.oldIsEnabled === 0 ? (
              <Tooltip title="现状禁用">
                <MinusCircleOutlined style={{ color: '#bfbfbf' }} />
              </Tooltip>
            ) : (
              <Tooltip title="现状启用">
                <CheckCircleOutlined style={{ color: '#52c41a' }} />
              </Tooltip>
            ))}
        </Space>
      ),
    },
    {
      title: '旧值 / 新值',
      minWidth: 320,
      render: (_, r) => (
        <Space direction="vertical" size={0} style={{ lineHeight: 1.5 }}>
          <span style={{ color: '#ff4d4f' }}>
            {r.oldValue ?? '—'}
          </span>
          <span style={{ color: '#52c41a' }}>{r.newValue}</span>
        </Space>
      ),
    },
    {
      title: '备注',
      dataIndex: 'remark',
      minWidth: 120,
      render: (v) => v || <span style={{ color: '#999' }}>-</span>,
    },
    {
      title: '来源文件',
      dataIndex: 'sourceFile',
      minWidth: 140,
      ellipsis: true,
    },
  ];

  // 步骤 2 列
  const fileConfigColumns: ColumnsType<FileConfigRow> = [
    {
      title: '文件名',
      dataIndex: 'name',
      minWidth: 200,
      align: 'center',
      ellipsis: true
    },
    {
      title: '格式',
      dataIndex: 'format',
      width: 80,
      align: 'center',
      render: (v) => (
        <Tag color={v === 'raw' ? 'blue' : 'green'}>{v}</Tag>
      ),
    },
    {
      title: '语言代码',
      dataIndex: 'localeCode',
      width: 240,
      align: 'center',
      render: (_, r) => {
        if (r.format === 'raw' || !r.parseOk) {
          const code = r.payloadLocale?.code ?? r.localeCode;
          return (
            <Space size={4}>
              <Tag color="blue" style={{ margin: 0 }}>
                {code || '—'}
              </Tag>
              <Text type="secondary" style={{ fontSize: 12 }}>
                来自文件
              </Text>
            </Space>
          );
        }
        return (
          <Select
            value={r.localeCode}
            options={localeOptions}
            placeholder="选择语言"
            showSearch
            optionFilterProp="label"
            style={{ width: 220 }}
            onChange={(v) => updateStaged(r.index, { localeCode: v })}
          />
        );
      },
    },
    {
      title: '前缀',
      dataIndex: 'prefix',
      width: 180,
      align: 'center',
      render: (_, r) => {
        if (r.format === 'raw' || !r.parseOk) {
          return (
            <Text type="secondary" style={{ fontSize: 12 }}>
              —
            </Text>
          );
        }
        return (
          <Input
            value={r.prefix}
            placeholder="可选，如 app."
            onChange={(e) =>
              updateStaged(r.index, { prefix: normalizePrefix(e.target.value) })
            }
          />
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'parseOk',
      width: 90,
      align: 'center',
      render: (_, r) =>
        r.parseOk ? (
          <Tag color="success">解析成功</Tag>
        ) : (
          <Tag color="error">{r.errorMessage ?? '失败'}</Tag>
        ),
    },
    {
      title: '操作',
      width: 80,
      align: 'center',
      render: (_, r) => (
        <Button
          type="text"
          danger
          onClick={() => removeStaged(r.index)}
        >
          移除
        </Button>
      ),
    },
  ];

  // 提交
  const submitImport = async () => {
    setSubmitting(true);
    try {
      const items = staged
        .filter((s) => s.parseOk)
        .map((s) => ({
          name: s.name,
          prefix: s.prefix || undefined,
          localeCode: s.localeCode,
          format: ((s.payload as Record<string, unknown>)?.['@type'] === 'raw'
            ? 'raw'
            : 'simple') as ImportFormat,
          payload: s.payload,
        }));
      const res = await importMutation.mutateAsync({ items });
      const perFile = res.affected.perFile ?? [];
      const failed = perFile.filter((p) => !p.ok);
      if (failed.length > 0) {
        Modal.warning({
          title: `导入完成（部分失败）`,
          content: (
            <div>
              <p>
                成功 {perFile.length - failed.length} 个文件，失败{' '}
                {failed.length} 个文件。
              </p>
              <ul>
                {failed.map((f) => (
                  <li key={f.name}>
                    <Text type="danger">{f.name}</Text>：{f.error}
                  </li>
                ))}
              </ul>
            </div>
          ),
        });
      } else {
        message.success(
          `导入成功：新增 ${res.affected.createdTranslations} 条翻译`,
        );
      }
      onSuccess();
      onClose();
    } catch (err) {
      message.error(
        `导入失败：${(err as Error).message ?? '未知错误'}`,
      );
    } finally {
      setSubmitting(false);
    }
  };

  const goConfirm = () => {
    Modal.confirm({
      title: '确认导入',
      content: (
        <div>
          <p>即将导入 {stats.total} 条翻译：</p>
          <ul>
            <li>新增 {stats.create} 条</li>
            <li>修改 {stats.update} 条</li>
            <li>
              重复 {stats.duplicate} 条（后者覆盖前者）
            </li>
            {stats.unchanged > 0 && (
              <li>与现状一致 {stats.unchanged} 条（仍会写入）</li>
            )}
            {stats.oldDisabled > 0 && (
              <li>其中现状禁用 {stats.oldDisabled} 条</li>
            )}
          </ul>
        </div>
      ),
      okText: '确认导入',
      cancelText: '取消',
      onOk: submitImport,
    });
  };

  return (
    <Modal
      title="导入 JSON"
      open={open}
      onCancel={onClose}
      footer={null}
      width={1200}
      destroyOnHidden
      maskClosable={false}
    >
      <Steps
        current={step}
        items={[
          { title: '选择格式与文件' },
          { title: '配置选项' },
          { title: '预览与导入' },
        ]}
        style={{ marginBottom: 24 }}
      />

      {/* Step 1 */}
      {step === 0 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div>
            <Text strong>导入格式</Text>
            <div style={{ marginTop: 8 }}>
              <Space>
                <Button
                  type={format === 'simple' ? 'primary' : 'default'}
                  onClick={() => onFormatChange('simple')}
                >
                  Simple（嵌套字典）
                </Button>
                <Button
                  type={format === 'raw' ? 'primary' : 'default'}
                  onClick={() => onFormatChange('raw')}
                >
                  Raw（含语言元数据）
                </Button>
              </Space>
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {format === 'simple'
                ? 'Simple 适合批量导入纯翻译文案；嵌套字典会自动展开为扁平键。'
                : 'Raw 包含语言元数据（isDefault、name 等），可同时新增/更新语言。'}
            </Text>
          </div>

          <Button icon={<DownloadOutlined />} onClick={downloadTemplate}>
            下载 {format === 'simple' ? 'Simple' : 'Raw'} 模板
          </Button>

          <div>
            <Text strong>选择文件（可多选）</Text>
            <div style={{ marginTop: 8 }}>
              <Dragger
                multiple
                onChange={handleUploadChange}
                accept=".json"
                showUploadList={false}
                beforeUpload={() => false}
                fileList={[]}
              >
                <p className="ant-upload-drag-icon">
                  <InboxOutlined />
                </p>
                <p className="ant-upload-text">
                  点击或拖拽 {format === 'simple' ? 'Simple' : 'Raw'} JSON
                  文件到此区域
                </p>
                <p className="ant-upload-hint">仅支持 .json 格式</p>
              </Dragger>
            </div>
          </div>

          {staged.length > 0 && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                已选 {staged.length} 个文件，其中{' '}
                {staged.filter((s) => s.parseOk).length} 个解析成功
              </Text>
            </div>
          )}

          <div style={{ textAlign: 'right' }}>
            <Space>
              <Button onClick={onClose}>取消</Button>
              <Button
                type="primary"
                disabled={!canGoStep2}
                onClick={() => setStep(1)}
              >
                下一步
              </Button>
            </Space>
          </div>
        </Space>
      )}

      {/* Step 2 */}
      {step === 1 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type="info"
            showIcon
            message="为 simple 文件配置语言代码与前缀；raw 文件自动从文件内读取，不可修改。"
          />
          <Table
            rowKey="key"
            columns={fileConfigColumns}
            dataSource={fileConfigRows}
            pagination={false}
            size="small"
          />
          <div style={{ textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setStep(0)}>上一步</Button>
              <Button
                type="primary"
                disabled={!canGoStep3}
                onClick={goToStep3}
              >
                下一步
              </Button>
            </Space>
          </div>
        </Space>
      )}

      {/* Step 3 */}
      {step === 2 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {previewState.loading ? (
            <div style={{ textAlign: 'center', padding: '24px 0' }}>
              <Progress percent={50} status="active" showInfo={false} />
              <Text type="secondary">正在加载现状快照…</Text>
            </div>
          ) : (
            <>
              <Space wrap size={16}>
                <Tag color="green">新增 {stats.create}</Tag>
                <Tag color="blue">修改 {stats.update}</Tag>
                <Tag color="red">重复 {stats.duplicate}</Tag>
                <Tag>未变更 {stats.unchanged}</Tag>
                {stats.oldDisabled > 0 && (
                  <Tag color="warning">
                    现状禁用 {stats.oldDisabled}
                  </Tag>
                )}
                <Popconfirm
                  title={
                    showChangedOnly ? '显示全部行？' : '仅显示有变更的行？'
                  }
                  onConfirm={() => setShowChangedOnly((v) => !v)}
                  okText="切换"
                  cancelText="取消"
                >
                  <Button size="small">
                    {showChangedOnly ? '显示全部行' : '仅显示变更行'}
                  </Button>
                </Popconfirm>
              </Space>

              <Table
                rowKey={(r) => r._rowKey}
                columns={previewColumns}
                dataSource={visiblePreviewRows}
                size="small"
                pagination={{
                  defaultPageSize: 20,
                  showSizeChanger: true,
                  showTotal: (t) => `共 ${t} 条`,
                }}
              />
            </>
          )}

          <div style={{ textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setStep(1)}>上一步</Button>
              <Button
                type="primary"
                loading={submitting}
                disabled={
                  previewState.loading ||
                  previewRows.length === 0 ||
                  !canGoStep3
                }
                onClick={goConfirm}
              >
                下一步
              </Button>
            </Space>
          </div>
        </Space>
      )}
    </Modal>
  );
};

export default I18nImportModal;
