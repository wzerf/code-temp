import { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { createModelDraftApi, createModelDraftBatchApi, probeModelCatalogApi, updateModelDraftApi } from '@/api/rest/model';
import type { ModelDraft } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface Props {
  open: boolean;
  row: ModelDraft | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  scope: 'OFFICIAL' | 'PRIVATE';
  code?: string;
  provider: 'openai-compatible' | 'anthropic';
  baseUrl: string;
  modelName: string;
  capabilities?: string;
  parameterGuardrails?: string;
  contextLength?: number;
  plainSecret?: string;
  remark?: string;
}

interface CatalogRow {
  key: string;
  modelName: string;
  name: string;
  code: string;
  contextLength: number;
}

const { TextArea } = Input;

const DEFAULT_CAPABILITIES =
  '{"text":true,"thinking":false,"tool_use":true,"vision":false,"json_mode":true}';
const DEFAULT_GUARDRAILS =
  '{"temperature":{"min":0,"max":2,"default":0.7},"top_p":{"min":0,"max":1,"default":1},"max_tokens":{"min":1,"max":128000,"default":4096}}';

const CAP_KEYS = ['text', 'thinking', 'tool_use', 'vision', 'json_mode'] as const;
const DEFAULT_CONTEXT_LENGTH = 500_000;

const ModelFormDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const { t } = useTranslation('model');
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const [probing, setProbing] = useState(false);
  const [catalog, setCatalog] = useState<CatalogRow[]>([]);
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [filter, setFilter] = useState('');
  const [caps, setCaps] = useState<string[]>(['text', 'tool_use', 'json_mode']);
  const isEdit = !!row;
  const initialValues = useMemo<FormValues>(
    () =>
      row
        ? {
            name: row.name,
            scope: row.scope as 'OFFICIAL' | 'PRIVATE',
            code: row.code,
            provider: row.provider as FormValues['provider'],
            baseUrl: row.baseUrl,
            modelName: row.modelName,
            capabilities: row.capabilities || DEFAULT_CAPABILITIES,
            parameterGuardrails: row.parameterGuardrails || DEFAULT_GUARDRAILS,
            contextLength: row.contextLength || DEFAULT_CONTEXT_LENGTH,
            remark: row.remark,
          }
        : {
            scope: 'PRIVATE',
            provider: 'openai-compatible',
            capabilities: DEFAULT_CAPABILITIES,
            parameterGuardrails: DEFAULT_GUARDRAILS,
            contextLength: DEFAULT_CONTEXT_LENGTH,
          },
    [row],
  );

  const filteredCatalog = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return catalog;
    return catalog.filter((r) => r.modelName.toLowerCase().includes(q) || r.name.toLowerCase().includes(q));
  }, [catalog, filter]);

  const capabilitiesJson = () =>
    JSON.stringify(Object.fromEntries(CAP_KEYS.map((k) => [k, caps.includes(k)])));

  const handleProbe = async () => {
    try {
      const values = await form.validateFields(['provider', 'baseUrl']);
      setProbing(true);
      const res = await probeModelCatalogApi({
        provider: values.provider,
        baseUrl: values.baseUrl,
        plainSecret: form.getFieldValue('plainSecret') || undefined,
      });
      const prev = new Map(catalog.map((r) => [r.modelName, r]));
      const next = (res.remoteModelIds ?? []).map((id) => {
        const old = prev.get(id);
        return old ?? { key: id, modelName: id, name: id, code: '', contextLength: DEFAULT_CONTEXT_LENGTH };
      });
      setCatalog(next);
      setSelectedKeys((keys) => keys.filter((k) => next.some((r) => r.key === k)));
      message.success(t('probeCatalogSuccess', { count: next.length }));
    } catch (err) {
      message.error(`${t('verifyFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setProbing(false);
    }
  };

  const patchRow = (key: string, patch: Partial<CatalogRow>) => {
    setCatalog((rows) => rows.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  };

  const addManual = () => {
    const id = form.getFieldValue('modelName') as string | undefined;
    const modelName = (id ?? '').trim();
    if (!modelName) {
      message.error(t('requiredModelName'));
      return;
    }
    if (catalog.some((r) => r.modelName === modelName)) {
      setSelectedKeys((keys) => (keys.includes(modelName) ? keys : [...keys, modelName]));
      form.setFieldValue('modelName', undefined);
      return;
    }
    const rowItem: CatalogRow = { key: modelName, modelName, name: modelName, code: '', contextLength: DEFAULT_CONTEXT_LENGTH };
    setCatalog((rows) => [...rows, rowItem]);
    setSelectedKeys((keys) => [...keys, modelName]);
    form.setFieldValue('modelName', undefined);
  };

  const handleSave = async () => {
    if (isEdit && row) {
      const values = await form.validateFields();
      setSaving(true);
      try {
        await updateModelDraftApi({
          id: row.id,
          name: values.name,
          code: values.code,
          provider: values.provider,
          baseUrl: values.baseUrl,
          modelName: values.modelName,
          capabilities: values.capabilities,
          parameterGuardrails: values.parameterGuardrails,
          contextLength: values.contextLength,
          plainSecret: values.plainSecret || undefined,
          remark: values.remark,
        });
        message.success(t('updateSuccess'));
        onSaved();
        onClose();
      } catch (err) {
        message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
      } finally {
        setSaving(false);
      }
      return;
    }

    const values = await form.validateFields(['scope', 'provider', 'baseUrl', 'plainSecret']);
    const selected = catalog.filter((r) => selectedKeys.includes(r.key));
    if (selected.length === 0) {
      message.error(t('requiredSelection'));
      return;
    }
    setSaving(true);
    try {
      if (selected.length === 1) {
        const item = selected[0];
        await createModelDraftApi({
          name: item.name || item.modelName,
          scope: values.scope,
          code: item.code,
          provider: values.provider,
          baseUrl: values.baseUrl,
          modelName: item.modelName,
          capabilities: capabilitiesJson(),
          parameterGuardrails: values.parameterGuardrails || DEFAULT_GUARDRAILS,
          contextLength: item.contextLength,
          plainSecret: values.plainSecret,
          remark: values.remark,
        });
      } else {
        await createModelDraftBatchApi({
          scope: values.scope,
          provider: values.provider,
          baseUrl: values.baseUrl,
          plainSecret: values.plainSecret,
          capabilities: capabilitiesJson(),
          parameterGuardrails: values.parameterGuardrails || DEFAULT_GUARDRAILS,
          remark: values.remark,
          items: selected.map((item) => ({
            name: item.name || item.modelName,
            modelName: item.modelName,
            code: item.code,
            contextLength: item.contextLength,
          })),
        });
      }
      message.success(t('batchCreateSuccess', { count: selected.length }));
      onSaved();
      onClose();
    } catch (err) {
      message.error(`${t('createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? t('editTitle') : t('createTitle')}
      open={open}
      onClose={onClose}
      width={isEdit ? 680 : 960}
      destroyOnClose
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={saving}>
            {t('cancel')}
          </Button>
          <Button type="primary" onClick={handleSave} loading={saving}>
            {t('save')}
          </Button>
        </Space>
      }
    >
      <Form form={form} initialValues={initialValues} layout="vertical" preserve={false}>
        {!isEdit && (
          <Form.Item name="scope" label={t('scope')} rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'PRIVATE', label: t('scopeMap.PRIVATE') },
                { value: 'OFFICIAL', label: t('scopeMap.OFFICIAL') },
              ]}
            />
          </Form.Item>
        )}
        {isEdit && (
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label={t('name')} rules={[{ required: true, message: t('requiredName') }]}>
                <Input maxLength={128} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="code" label={t('code')} extra={t('codeHint')}>
                <Input maxLength={64} placeholder="video / image" />
              </Form.Item>
            </Col>
          </Row>
        )}
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="provider" label={t('provider')} rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'openai-compatible', label: t('providerMap.openai-compatible') },
                  { value: 'anthropic', label: t('providerMap.anthropic') },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="plainSecret"
              label={t('plainSecret')}
              extra={t('secretHint')}
              rules={isEdit ? undefined : [{ required: true, message: '请输入 API Key' }]}
            >
              <Input.Password placeholder={t('secretPlaceholder')} autoComplete="new-password" />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="baseUrl" label={t('baseUrl')} rules={[{ required: true, message: t('requiredUrl') }]}>
          <Input placeholder={t('urlPlaceholder')} />
        </Form.Item>
        {isEdit ? (
          <Form.Item name="modelName" label={t('modelName')} rules={[{ required: true, message: t('requiredModelName') }]}>
            <Input placeholder="gpt-4o" />
          </Form.Item>
        ) : (
          <>
            <Alert type="info" showIcon message={t('probeHint')} style={{ marginBottom: 12 }} />
            <Space wrap style={{ marginBottom: 12 }}>
              <Button type="primary" onClick={handleProbe} loading={probing}>
                {t('probeCatalog')}
              </Button>
              <Tag color={selectedKeys.length ? 'blue' : 'default'}>
                {t('selectedCount', { count: selectedKeys.length })}
              </Tag>
            </Space>
            <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
              <Form.Item name="modelName" noStyle>
                <Input placeholder={t('manualModelName')} onPressEnter={addManual} />
              </Form.Item>
              <Button icon={<PlusOutlined />} onClick={addManual}>
                {t('addManual')}
              </Button>
            </Space.Compact>
            <Input
              allowClear
              prefix={<SearchOutlined />}
              placeholder={t('filterModels')}
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              style={{ marginBottom: 12 }}
            />
            <Table<CatalogRow>
              rowKey="key"
              size="small"
              pagination={{ pageSize: 8, showSizeChanger: false }}
              dataSource={filteredCatalog}
              rowSelection={{
                selectedRowKeys: selectedKeys,
                onChange: (keys) => setSelectedKeys(keys.map(String)),
              }}
              locale={{ emptyText: t('catalogEmpty') }}
              columns={[
                { title: t('modelName'), dataIndex: 'modelName', ellipsis: true, width: 280 },
                {
                  title: t('name'),
                  dataIndex: 'name',
                  render: (_, r) => (
                    <Input
                      size="small"
                      maxLength={128}
                      value={r.name}
                      onChange={(e) => patchRow(r.key, { name: e.target.value })}
                    />
                  ),
                },
                {
                  title: t('code'),
                  dataIndex: 'code',
                  width: 140,
                  render: (_, r) => (
                    <Input
                      size="small"
                      maxLength={64}
                      value={r.code}
                      placeholder="video / image"
                      onChange={(e) => patchRow(r.key, { code: e.target.value })}
                    />
                  ),
                },
                {
                  title: '上下文长度',
                  dataIndex: 'contextLength',
                  width: 150,
                  render: (_, r) => (
                    <InputNumber
                      size="small"
                      min={1}
                      precision={0}
                      value={r.contextLength}
                      onChange={(value) => patchRow(r.key, { contextLength: value ?? DEFAULT_CONTEXT_LENGTH })}
                    />
                  ),
                },
              ]}
            />
            <Form.Item label={t('capabilities')} style={{ marginTop: 16 }}>
              <Checkbox.Group
                value={caps}
                onChange={(v) => setCaps(v.map(String))}
                options={CAP_KEYS.map((k) => ({ value: k, label: t(`cap.${k}`) }))}
              />
            </Form.Item>
          </>
        )}
        {isEdit && (
          <Form.Item name="contextLength" label="上下文长度（token）" rules={[{ required: true, type: 'number', min: 1 }]}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
        )}
        {isEdit && (
          <Form.Item name="capabilities" label={t('capabilities')}>
            <TextArea rows={3} style={{ fontFamily: 'monospace' }} />
          </Form.Item>
        )}
        <Form.Item name="parameterGuardrails" label={t('parameterGuardrails')}>
          <TextArea rows={isEdit ? 4 : 3} style={{ fontFamily: 'monospace' }} />
        </Form.Item>
        <Form.Item name="remark" label={t('remark')}>
          <TextArea rows={2} maxLength={512} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default ModelFormDrawer;
