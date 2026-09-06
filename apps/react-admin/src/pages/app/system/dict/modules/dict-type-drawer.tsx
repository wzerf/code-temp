import { useEffect } from 'react';
import { Button, Drawer, Form, Input, Space, Switch } from 'antd';
import { message } from '@/core/feedback/message';
import {
  useCreateDictType,
  useUpdateDictType } from '@/api/hooks/dict';
import type { CreateDictTypeRequest, DictType } from '@/api/rest/types';
import { CODE_PATTERN } from './shared';

interface Props {
  open: boolean;
  row: DictType | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  code: string;
  name: string;
  remark?: string;
  isEnabled?: boolean;
}

const DictTypeDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const [form] = Form.useForm<FormValues>();
  const createMut = useCreateDictType({
    onSuccess: () => {
      message.success('创建成功');
      onSaved();
      onClose();
    },
    onError: (err) => {
      message.error(`创建失败：${(err as Error).message ?? '未知错误'}`);
    },
  });
  const updateMut = useUpdateDictType({
    onSuccess: () => {
      message.success('保存成功');
      onSaved();
      onClose();
    },
    onError: (err) => {
      message.error(`保存失败：${(err as Error).message ?? '未知错误'}`);
    },
  });
  const isEdit = !!row;
  const submitting = createMut.isPending || updateMut.isPending;

  useEffect(() => {
    if (open) {
      form.setFieldsValue(
        row
          ? {
              code: row.code,
              name: row.name,
              remark: row.remark,
              isEnabled: row.isEnabled === 1,
            }
          : { code: '', name: '', remark: '', isEnabled: true },
      );
    }
  }, [open, row, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const isEnabled = values.isEnabled ? (1 as const) : (0 as const);
    if (isEdit) {
      updateMut.mutate({
        id: row.id,
        code: values.code,
        name: values.name,
        remark: values.remark ?? '',
        isEnabled,
      });
    } else {
      const body: CreateDictTypeRequest = {
        code: values.code,
        name: values.name,
        remark: values.remark ?? '',
        isEnabled,
      };
      createMut.mutate(body);
    }
  };

  return (
    <Drawer
      title={isEdit ? '编辑字典类型' : '新建字典类型'}
      open={open}
      onClose={onClose}
      size={560}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={submitting}>
            取消
          </Button>
          <Button type="primary" onClick={handleOk} loading={submitting}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          label="类型编码"
          name="code"
          rules={[
            { required: true, message: '请输入类型编码' },
            {
              pattern: CODE_PATTERN,
              message: '以小写字母开头，仅含字母数字下划线',
            },
          ]}
        >
          <Input placeholder="例如 sys_user_sex" />
        </Form.Item>
        <Form.Item
          label="类型名称"
          name="name"
          rules={[{ required: true, message: '请输入类型名称' }, { max: 64 }]}
        >
          <Input />
        </Form.Item>
        <Form.Item label="备注" name="remark">
          <Input.TextArea rows={3} placeholder="选填" />
        </Form.Item>
        <Form.Item
          label="启用"
          name="isEnabled"
          valuePropName="checked"
          getValueFromEvent={(v) => !!v}
          getValueProps={(v) => ({ checked: v !== false })}
        >
          <Switch checkedChildren="启用" unCheckedChildren="禁用" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default DictTypeDrawer;