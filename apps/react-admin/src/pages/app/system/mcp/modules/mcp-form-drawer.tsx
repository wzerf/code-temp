import { useMemo, useState } from 'react';
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Alert} from 'antd';
import { message } from '@/core/feedback/message';
import { useTranslation } from 'react-i18next';
import { createMcpDraftApi, updateMcpDraftApi } from '@/api/rest/mcp';
import type { McpDraft } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface Props {
  open: boolean;
  row: McpDraft | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  transport: 'sse' | 'http';
  url: string;
  headersJson?: string;
  visibility: 'MARKET' | 'PRIVATE';
  plainSecret?: string;
  connectTimeoutMs?: number;
  remark?: string;
  authType?: 'NONE' | 'OAUTH';
  oauthClientId?: string;
  plainOauthClientSecret?: string;
  oauthScope?: string;
  oauthRequireLogin?: number;
}

const { TextArea } = Input;
const DEFAULT_CONNECT_TIMEOUT_MS = 5000;

function buildMcpFormValues(row: McpDraft | null): FormValues {
  if (row) {
    return {
      name: row.name,
      transport: row.transport,
      url: row.url,
      headersJson: row.headersJson || undefined,
      visibility: row.visibility as 'MARKET' | 'PRIVATE',
      connectTimeoutMs: row.connectTimeoutMs ?? DEFAULT_CONNECT_TIMEOUT_MS,
      remark: row.remark,
      authType: (row.authType as 'NONE' | 'OAUTH') ?? 'NONE',
      oauthClientId: row.oauthClientId || undefined,
      oauthScope: row.oauthScope || undefined,
      oauthRequireLogin: row.oauthRequireLogin ?? 1,
    };
  }
  return {
    name: '',
    transport: 'http',
    url: '',
    visibility: 'PRIVATE',
    connectTimeoutMs: DEFAULT_CONNECT_TIMEOUT_MS,
    authType: 'NONE',
    oauthRequireLogin: 1,
  };
}

const McpFormDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const { t } = useTranslation('mcp');
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const visibility = Form.useWatch('visibility', form) ?? 'PRIVATE';
  const authType = Form.useWatch('authType', form) ?? 'NONE';
  const isEdit = !!row;
  // Form 用 key + initialValues 保证 destroyOnHidden 挂载即回显，
  // 避免 useEffect + setFieldsValue 在字段注册前执行导致丢值。
  const formInitialValues = useMemo(() => buildMcpFormValues(row), [row]);
  const formKey = row ? `edit-${row.id}` : 'create';

  const handleSave = async () => {
    const values = await form.validateFields();
    const vis = values.visibility ?? visibility;
    const auth = values.authType ?? authType ?? 'NONE';
    const isOauth = auth === 'OAUTH';
    setSaving(true);
    try {
      if (isEdit && row) {
        await updateMcpDraftApi({
          id: row.id,
          name: values.name,
          transport: values.transport,
          url: values.url,
          headersJson: values.headersJson,
          plainSecret: vis === 'PRIVATE' && !isOauth ? values.plainSecret || undefined : undefined,
          connectTimeoutMs: values.connectTimeoutMs,
          remark: values.remark,
          authType: auth,
          oauthClientId: isOauth ? values.oauthClientId || undefined : undefined,
          plainOauthClientSecret:
            isOauth && vis === 'PRIVATE' ? values.plainOauthClientSecret || undefined : undefined,
          oauthScope: isOauth ? values.oauthScope || undefined : undefined,
          oauthRequireLogin: isOauth ? (values.oauthRequireLogin ?? 1) : undefined,
        });
        message.success(t('updateSuccess'));
      } else {
        await createMcpDraftApi({
          name: values.name,
          transport: values.transport,
          url: values.url,
          headersJson: values.headersJson,
          visibility: values.visibility,
          plainSecret: vis === 'PRIVATE' && !isOauth ? values.plainSecret || undefined : undefined,
          connectTimeoutMs: values.connectTimeoutMs ?? DEFAULT_CONNECT_TIMEOUT_MS,
          remark: values.remark,
          authType: auth,
          oauthClientId: isOauth ? values.oauthClientId || undefined : undefined,
          plainOauthClientSecret:
            isOauth && vis === 'PRIVATE' ? values.plainOauthClientSecret || undefined : undefined,
          oauthScope: isOauth ? values.oauthScope || undefined : undefined,
          oauthRequireLogin: isOauth ? (values.oauthRequireLogin ?? 1) : undefined,
        });
        message.success(t('createSuccess'));
      }
      onSaved();
      onClose();
    } catch (err) {
      message.error(`${t(isEdit ? 'updateFailed' : 'createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? t('editTitle') : t('createTitle')}
      open={open}
      onClose={onClose}
      size={640}
      destroyOnHidden
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
      <Form key={formKey} form={form} layout="vertical" preserve={false} initialValues={formInitialValues}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="name" label={t('name')} rules={[{ required: true, message: t('requiredName') }]}>
              <Input maxLength={128} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="visibility" label={t('visibility')} rules={[{ required: true }]}>
              <Select
                disabled={isEdit}
                onChange={() => form.setFieldsValue({ plainSecret: undefined })}
                options={[
                  { value: 'MARKET', label: t('visibilityMap.MARKET') },
                  { value: 'PRIVATE', label: t('visibilityMap.PRIVATE') },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="transport" label={t('transport')} rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'http', label: t('transportMap.http', { defaultValue: 'HTTP' }) },
                  { value: 'sse', label: t('transportMap.sse', { defaultValue: 'SSE' }) },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="connectTimeoutMs" label={t('connectTimeoutMs')}>
              <InputNumber min={100} max={60000} step={500} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="url" label={t('url')} rules={[{ required: true, message: t('requiredUrl') }]}>
          <Input placeholder={t('urlPlaceholder')} />
        </Form.Item>
        <Form.Item name="authType" label={t('authType')} extra={t('authTypeHint')}>
          <Select
            options={[
              { value: 'NONE', label: t('authTypeMap.NONE') },
              { value: 'OAUTH', label: t('authTypeMap.OAUTH') },
            ]}
          />
        </Form.Item>
        {authType === 'OAUTH' ? (
          <>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="oauthClientId" label={t('oauthClientId')} extra={t('oauthClientIdHint')}>
                  <Input placeholder="client_id" autoComplete="off" />
                </Form.Item>
              </Col>
              <Col span={12}>
                {visibility === 'PRIVATE' ? (
                  <Form.Item
                    name="plainOauthClientSecret"
                    label={t('oauthClientSecret')}
                    extra={t('oauthClientSecretHint')}
                  >
                    <Input.Password placeholder="client_secret" autoComplete="new-password" />
                  </Form.Item>
                ) : (
                  <Alert type="info" showIcon title={t('oauthMarketNoSecretHint')} style={{ marginBottom: 24 }} />
                )}
              </Col>
            </Row>
            <Form.Item name="oauthScope" label={t('oauthScope')} extra={t('oauthScopeHint')}>
              <Input placeholder="read write" autoComplete="off" />
            </Form.Item>
            {visibility === 'MARKET' ? (
              <Form.Item name="oauthRequireLogin" label={t('oauthRequireLogin')} extra={t('oauthRequireLoginHint')}>
                <Select
                  options={[
                    { value: 1, label: t('oauthRequireLoginMap.required') },
                    { value: 0, label: t('oauthRequireLoginMap.optional') },
                  ]}
                />
              </Form.Item>
            ) : null}
          </>
        ) : null}
        <Form.Item name="headersJson" label={t('headersJson')} extra={t('headersPlaceholder')}>
          <TextArea rows={3} style={{ fontFamily: 'monospace' }} placeholder='{"Accept":"application/json"}' />
        </Form.Item>
        {visibility === 'PRIVATE' && authType !== 'OAUTH' ? (
          <Form.Item name="plainSecret" label={t('plainSecret')} extra={t('secretHint')}>
            <Input.Password placeholder={t('secretPlaceholder')} autoComplete="new-password" />
          </Form.Item>
        ) : visibility === 'PRIVATE' && authType === 'OAUTH' ? (
          <Alert type="info" showIcon title={t('oauthNoStaticSecretHint')} style={{ marginBottom: 16 }} />
        ) : (
          <Alert type="info" showIcon title={t('marketNoSecretHint')} style={{ marginBottom: 16 }} />
        )}
        <Form.Item name="remark" label="备注">
          <TextArea rows={2} maxLength={512} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default McpFormDrawer;
