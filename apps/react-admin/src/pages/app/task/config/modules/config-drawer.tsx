import { useMemo } from 'react';
import { Button, Col, Drawer, Form, Input, InputNumber, Row, Select, Space, Switch } from 'antd';
import { message } from '@/core/feedback/message';
import { useTranslation } from 'react-i18next';
import {
  useCreateTaskConfig,
  useListTaskQueues,
  useListTaskWorkflowTypes,
  useUpdateTaskConfig } from '@/api/hooks/task-config';
import type { CreateTaskConfigRequest, TaskConfig, TaskSelectOption } from '@/api/rest/types';
import { getApiErrorMessage } from '../../modules/error-message';

/** 编辑时若当前值不在选项中，合并进去以保证回显 */
function mergeCurrentOption(
  options: TaskSelectOption[] | undefined,
  current: string | undefined | null,
): TaskSelectOption[] {
  const base = options ?? [];
  const v = (current ?? '').trim();
  if (!v) return base;
  if (base.some((o) => o.value === v)) return base;
  return [{ label: v, value: v }, ...base];
}

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

/** 与 Vue task form / React user-form 一致的分段标题 */
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
  row: TaskConfig | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  code: string;
  name: string;
  workflowType: string;
  taskQueue: string;
  cronExpr?: string;
  timeoutSeconds?: number | null;
  retryPolicyText?: string;
  remark?: string;
  isEnabled?: boolean;
}

function formatRetryPolicy(policy: Record<string, unknown> | null | undefined): string {
  if (!policy) return '';
  try {
    return JSON.stringify(policy, null, 2);
  } catch {
    return '';
  }
}

function parseRetryPolicy(
  text: string | undefined,
): { ok: true; value: Record<string, unknown> | null } | { ok: false; message: string } {
  const raw = (text ?? '').trim();
  if (!raw) return { ok: true, value: null };
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { ok: false, message: 'object' };
    }
    return { ok: true, value: parsed as Record<string, unknown> };
  } catch {
    return { ok: false, message: 'json' };
  }
}

/** 由列表行构建表单初值；新建时给默认空值（与 Vue 一致：重试策略默认空） */
function buildFormValues(row: TaskConfig | null): FormValues {
  if (row) {
    return {
      code: row.code,
      name: row.name,
      workflowType: row.workflowType,
      taskQueue: row.taskQueue,
      cronExpr: row.cronExpr ?? '',
      timeoutSeconds: row.timeoutSeconds,
      retryPolicyText: formatRetryPolicy(row.retryPolicy),
      remark: row.remark ?? '',
      isEnabled: row.isEnabled === 1,
    };
  }
  return {
    code: '',
    name: '',
    workflowType: '',
    taskQueue: '',
    cronExpr: '',
    timeoutSeconds: undefined,
    retryPolicyText: '',
    remark: '',
    isEnabled: true,
  };
}

const TaskConfigDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const { t } = useTranslation('task');
  const [form] = Form.useForm<FormValues>();
  const { data: workflowTypeOptions, isLoading: workflowTypesLoading } =
    useListTaskWorkflowTypes({ enabled: open });
  const { data: taskQueueOptions, isLoading: taskQueuesLoading } = useListTaskQueues({
    enabled: open,
  });
  const workflowSelectOptions = useMemo(
    () => mergeCurrentOption(workflowTypeOptions, row?.workflowType),
    [workflowTypeOptions, row?.workflowType],
  );
  const queueSelectOptions = useMemo(
    () => mergeCurrentOption(taskQueueOptions, row?.taskQueue),
    [taskQueueOptions, row?.taskQueue],
  );
  const createMut = useCreateTaskConfig({
    onSuccess: () => {
      message.success(t('createSuccess'));
      onSaved();
      onClose();
    },
    onError: (err) => {
      message.error(`${t('createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    },
  });
  const updateMut = useUpdateTaskConfig({
    onSuccess: () => {
      message.success(t('updateSuccess'));
      onSaved();
      onClose();
    },
    onError: (err) => {
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    },
  });
  const isEdit = !!row;
  const submitting = createMut.isPending || updateMut.isPending;

  // Form 用 key + initialValues 保证 destroyOnHidden 挂载即回显，
  // 避免 useEffect + setFieldsValue 在字段注册前执行导致丢值。
  const formInitialValues = useMemo(() => buildFormValues(row), [row]);
  const formKey = row ? `edit-${row.id}` : 'create';

  const handleOk = async () => {
    const values = await form.validateFields();
    const parsed = parseRetryPolicy(values.retryPolicyText);
    if (!parsed.ok) {
      form.setFields([
        {
          name: 'retryPolicyText',
          errors: [t(parsed.message === 'object' ? 'retryPolicyObject' : 'retryPolicyInvalid')],
        },
      ]);
      return;
    }

    const cronRaw = values.cronExpr;
    const cronExpr =
      cronRaw !== undefined && cronRaw !== null && String(cronRaw).trim() !== ''
        ? String(cronRaw).trim()
        : null;
    const timeoutSeconds =
      values.timeoutSeconds === undefined || values.timeoutSeconds === null
        ? null
        : Number(values.timeoutSeconds);
    const isEnabled = values.isEnabled ? (1 as const) : (0 as const);
    const base = {
      name: values.name.trim(),
      workflowType: values.workflowType.trim(),
      taskQueue: values.taskQueue.trim(),
      cronExpr,
      timeoutSeconds,
      retryPolicy: parsed.value,
      remark: values.remark ?? '',
      isEnabled,
    };

    if (isEdit && row) {
      updateMut.mutate({
        id: row.id,
        code: values.code.trim(),
        ...base,
      });
    } else {
      const body: CreateTaskConfigRequest = {
        code: values.code.trim(),
        ...base,
      };
      createMut.mutate(body);
    }
  };

  return (
    <Drawer
      title={isEdit ? t('editConfig') : t('createConfig')}
      open={open}
      onClose={onClose}
      size={640}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={submitting}>
            {t('cancel')}
          </Button>
          <Button type="primary" onClick={handleOk} loading={submitting}>
            {t('save')}
          </Button>
        </Space>
      }
    >
      <Form
        key={formKey}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={formInitialValues}
      >
        {/* 基础信息：两列网格，对齐 Vue form.vue */}
        <SectionTitle>基础信息</SectionTitle>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label={t('code')}
              name="code"
              rules={[
                { required: true, message: t('requiredCode') },
                { pattern: CODE_PATTERN, message: t('codeRule') },
              ]}
            >
              <Input
                placeholder={t('codePlaceholder')}
                maxLength={64}
                disabled={isEdit}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label={t('name')}
              name="name"
              rules={[
                { required: true, message: t('requiredName') },
                { max: 128, message: t('nameMax') },
              ]}
            >
              <Input placeholder={t('namePlaceholder')} maxLength={128} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label={t('workflowType')}
              name="workflowType"
              rules={[{ required: true, message: t('requiredWorkflowType') }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                loading={workflowTypesLoading}
                options={workflowSelectOptions}
                placeholder={t('workflowTypePlaceholder')}
                allowClear
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label={t('taskQueue')}
              name="taskQueue"
              rules={[{ required: true, message: t('requiredTaskQueue') }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                loading={taskQueuesLoading}
                options={queueSelectOptions}
                placeholder={t('taskQueuePlaceholder')}
                allowClear
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label={t('cronExpr')}
              name="cronExpr"
              rules={[{ max: 64, message: t('cronMax') }]}
            >
              <Input placeholder={t('cronPlaceholder')} allowClear maxLength={64} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label={t('timeoutSeconds')} name="timeoutSeconds">
              <InputNumber
                min={0}
                precision={0}
                style={{ width: '100%' }}
                placeholder={t('timeoutPlaceholder')}
              />
            </Form.Item>
          </Col>
          <Col span={24}>
            <Form.Item
              label={t('status')}
              name="isEnabled"
              valuePropName="checked"
              getValueFromEvent={(v) => !!v}
              getValueProps={(v) => ({ checked: v !== false })}
            >
              <Switch checkedChildren={t('enabled')} unCheckedChildren={t('disabled')} />
            </Form.Item>
          </Col>
        </Row>

        {/* 重试策略：独立分段，与 Vue 一致 */}
        <SectionTitle>{t('retryPolicy')}</SectionTitle>
        <Form.Item name="retryPolicyText" extra={t('retryPolicyHint')}>
          <Input.TextArea
            rows={4}
            autoSize={{ minRows: 4, maxRows: 10 }}
            placeholder='{"maxAttempts": 3}'
            style={{ fontFamily: 'monospace', fontSize: 12 }}
          />
        </Form.Item>

        {/* 备注：独立分段 */}
        <SectionTitle>{t('remark')}</SectionTitle>
        <Form.Item name="remark">
          <Input.TextArea rows={3} autoSize={{ minRows: 3 }} placeholder={t('remarkPlaceholder')} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default TaskConfigDrawer;
