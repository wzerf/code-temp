import { useEffect } from 'react';
import { Button, Col, DatePicker, Drawer, Form, Input, Row, Select, Space, Switch } from 'antd';
import { message } from '@/core/feedback/message';
import dayjs, { type Dayjs } from 'dayjs';
import { useTranslation } from 'react-i18next';
import {
  useCreateBlacklist,
  useUpdateBlacklist } from '@/api/hooks/blacklist';
import type {
  Blacklist,
  BlacklistScope,
  BlacklistTargetType,
  CreateBlacklistRequest } from '@/api/rest/types';
import { getApiErrorMessage } from './error-message';

const TARGET_TYPES: BlacklistTargetType[] = ['IP', 'SYS_USER', 'DEVICE'];
const SCOPES: BlacklistScope[] = ['LOGIN', 'API', 'ALL'];

/** 与 task/user 表单一致的分段标题 */
const SectionTitle = ({ children }: { children: React.ReactNode }) => (
  <div
    style={{
      color: '#94a3b8',
      fontSize: 12,
      fontWeight: 500,
      marginBottom: 16,
      marginTop: 8,
    }}
  >
    {children}
  </div>
);

interface Props {
  open: boolean;
  row: Blacklist | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  targetType: BlacklistTargetType;
  targetValue: string;
  scope: BlacklistScope;
  reason?: string;
  startsAt?: Dayjs | null;
  expiresAt?: Dayjs | null;
  remark?: string;
  isEnabled: boolean;
}

/** 提交给后端：LocalDateTime / mock 可解析的 ISO 本地串 */
function formatDateTime(value: Dayjs | null | undefined): string | null {
  if (!value || !value.isValid()) return null;
  return value.format('YYYY-MM-DDTHH:mm:ss');
}

function parseDateTime(raw: string | null | undefined): Dayjs | null {
  if (!raw) return null;
  const d = dayjs(raw);
  return d.isValid() ? d : null;
}

const BlacklistFormDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const { t } = useTranslation('blacklist');
  const isEdit = !!row;
  const [form] = Form.useForm<FormValues>();

  const createMut = useCreateBlacklist({
    onSuccess: () => {
      message.success(t('createSuccess'));
      onSaved();
      onClose();
    },
    onError: (err) =>
      message.error(`${t('createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`),
  });
  const updateMut = useUpdateBlacklist({
    onSuccess: () => {
      message.success(t('updateSuccess'));
      onSaved();
      onClose();
    },
    onError: (err) =>
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`),
  });

  useEffect(() => {
    if (!open) return;
    if (row) {
      form.setFieldsValue({
        targetType: (row.targetType as BlacklistTargetType) || 'IP',
        targetValue: row.targetValue,
        scope: (row.scope as BlacklistScope) || 'ALL',
        reason: row.reason ?? '',
        startsAt: parseDateTime(row.startsAt),
        expiresAt: parseDateTime(row.expiresAt),
        remark: row.remark ?? '',
        isEnabled: row.isEnabled === 1,
      });
    } else {
      form.setFieldsValue({
        targetType: 'IP',
        targetValue: '',
        scope: 'ALL',
        reason: '',
        startsAt: dayjs(),
        expiresAt: null,
        remark: '',
        isEnabled: true,
      });
    }
  }, [open, row, form]);

  const handleSave = async () => {
    const values = await form.validateFields();
    const startsAt = formatDateTime(values.startsAt);
    const expiresAt = formatDateTime(values.expiresAt);

    if (expiresAt && startsAt && dayjs(expiresAt).valueOf() <= dayjs(startsAt).valueOf()) {
      message.error(t('expiresAfterStarts'));
      return;
    }

    if (isEdit && row) {
      const hadExpires = row.expiresAt !== null && String(row.expiresAt).trim() !== '';
      const clearExpiresAt = hadExpires && expiresAt === null;
      updateMut.mutate({
        id: row.id,
        targetType: values.targetType,
        targetValue: values.targetValue.trim(),
        scope: values.scope,
        reason: values.reason?.trim() ?? '',
        startsAt: startsAt ?? undefined,
        expiresAt: expiresAt,
        clearExpiresAt: clearExpiresAt || undefined,
        remark: values.remark?.trim() ?? '',
        isEnabled: values.isEnabled ? 1 : 0,
      });
      return;
    }

    const payload: CreateBlacklistRequest = {
      targetType: values.targetType,
      targetValue: values.targetValue.trim(),
      scope: values.scope,
      reason: values.reason?.trim() ?? '',
      startsAt,
      expiresAt,
      remark: values.remark?.trim() ?? '',
      isEnabled: values.isEnabled ? 1 : 0,
    };
    createMut.mutate(payload);
  };

  const submitting = createMut.isPending || updateMut.isPending;

  const targetTypeOptions = TARGET_TYPES.map((v) => ({
    label: t(`targetTypeMap.${v}`),
    value: v,
  }));
  const scopeOptions = SCOPES.map((v) => ({
    label: t(`scopeMap.${v}`),
    value: v,
  }));

  return (
    <Drawer
      title={isEdit ? t('editTitle') : t('createTitle')}
      open={open}
      onClose={onClose}
      size={560}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={submitting}>
            {t('cancel')}
          </Button>
          <Button type="primary" onClick={handleSave} loading={submitting}>
            {t('save')}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <SectionTitle>{t('sectionTarget')}</SectionTitle>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="targetType"
              label={t('targetType')}
              rules={[{ required: true, message: t('requiredTargetType') }]}
            >
              <Select options={targetTypeOptions} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="scope"
              label={t('scope')}
              rules={[{ required: true, message: t('requiredScope') }]}
            >
              <Select options={scopeOptions} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item
          name="targetValue"
          label={t('targetValue')}
          rules={[
            { required: true, message: t('requiredTargetValue') },
            { max: 128, message: t('targetValueMax') },
          ]}
          extra={t('targetValueHint')}
        >
          <Input placeholder={t('targetValuePlaceholder')} allowClear maxLength={128} />
        </Form.Item>

        <SectionTitle>{t('sectionWindow')}</SectionTitle>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="startsAt"
              label={t('startsAt')}
              rules={[{ required: true, message: t('requiredStartsAt') }]}
            >
              <DatePicker
                showTime
                style={{ width: '100%' }}
                format="YYYY-MM-DD HH:mm:ss"
                placeholder={t('startsAtPlaceholder')}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="expiresAt"
              label={t('expiresAt')}
              extra={t('expiresAtHint')}
            >
              <DatePicker
                showTime
                style={{ width: '100%' }}
                format="YYYY-MM-DD HH:mm:ss"
                placeholder={t('expiresAtPlaceholder')}
                allowClear
              />
            </Form.Item>
          </Col>
        </Row>

        <SectionTitle>{t('sectionMeta')}</SectionTitle>
        <Form.Item name="reason" label={t('reason')} rules={[{ max: 512, message: t('reasonMax') }]}>
          <Input.TextArea
            rows={2}
            placeholder={t('reasonPlaceholder')}
            maxLength={512}
            showCount
          />
        </Form.Item>
        <Form.Item name="remark" label={t('remark')} rules={[{ max: 512, message: t('remarkMax') }]}>
          <Input.TextArea
            rows={2}
            placeholder={t('remarkPlaceholder')}
            maxLength={512}
            showCount
          />
        </Form.Item>
        <Form.Item
          name="isEnabled"
          label={t('isEnabled')}
          valuePropName="checked"
        >
          <Switch checkedChildren={t('enabled')} unCheckedChildren={t('disabled')} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default BlacklistFormDrawer;
