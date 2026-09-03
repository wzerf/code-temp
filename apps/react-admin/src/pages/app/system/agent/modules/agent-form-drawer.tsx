import { useEffect } from 'react';
import { Button, Drawer, Form, Input, Space, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { createAgentApi, updateAgentApi } from '@/api/rest/agent';
import type { Agent, CreateAgentRequest } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface Props {
  open: boolean;
  row: Agent | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  description?: string;
  remark?: string;
}

const AgentFormDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const { t } = useTranslation('agent');
  const [form] = Form.useForm<FormValues>();
  const isEdit = !!row;

  useEffect(() => {
    if (!open) return;
    if (row) {
      form.setFieldsValue({
        name: row.name,
        description: row.description,
        remark: row.remark,
      });
    } else {
      form.resetFields();
    }
  }, [open, row, form]);

  const handleSave = async () => {
    const values = await form.validateFields();
    try {
      if (isEdit && row) {
        await updateAgentApi({ id: row.id, ...values });
        message.success(t('updateSuccess'));
      } else {
        const body: CreateAgentRequest = { ...values, isEnabled: 1 };
        await createAgentApi(body);
        message.success(t('createSuccess'));
      }
      onSaved();
      onClose();
    } catch (err) {
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  return (
    <Drawer
      title={isEdit ? t('editTitle') : t('createTitle')}
      open={open}
      onClose={onClose}
      width={560}
      destroyOnClose
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose}>{t('cancel')}</Button>
          <Button type="primary" onClick={handleSave}>
            {t('save')}
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="name" label={t('name')} rules={[{ required: true, message: t('requiredName') }]}>
          <Input placeholder={t('namePlaceholder')} maxLength={128} />
        </Form.Item>
        <Form.Item name="description" label={t('description')}>
          <Input.TextArea rows={3} maxLength={512} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={512} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default AgentFormDrawer;
