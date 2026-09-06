import { useEffect } from 'react';
import {Form, Input, Modal} from 'antd';
import { message } from '@/core/feedback/message';
import { useResetUserPassword } from '@/api/hooks/user';

interface Props {
  open: boolean;
  /** 待重置密码的用户 id */
  userId: number | null;
  onClose: () => void;
  onSaved?: () => void;
}

interface FormValues {
  password: string;
  confirmPassword: string;
}

const ResetPasswordModal = ({ open, userId, onClose, onSaved }: Props) => {
  const [form] = Form.useForm<FormValues>();

  const resetMut = useResetUserPassword({
    onSuccess: () => {
      message.success('密码已重置');
      onClose();
      onSaved?.();
    },
    onError: (err) => message.error(`重置失败：${(err as Error).message ?? '未知错误'}`),
  });

  useEffect(() => {
    if (!open) return;
    form.resetFields();
  }, [open, form]);

  const handleOk = async () => {
    if (userId === null) return;
    const values = await form.validateFields();
    resetMut.mutate({ id: userId, password: values.password });
  };

  return (
    <Modal
      title="重置密码"
      open={open}
      onCancel={onClose}
      onOk={handleOk}
      confirmLoading={resetMut.isPending}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item
          label="新密码"
          name="password"
          rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少 6 位' }]}
        >
          <Input.Password placeholder="请输入新密码" />
        </Form.Item>
        <Form.Item
          label="确认密码"
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: '请确认新密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (value !== getFieldValue('password')) {
                  return Promise.reject(new Error('两次密码不一致'));
                }
                return Promise.resolve();
              },
            }),
          ]}
        >
          <Input.Password placeholder="请再次输入新密码" />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ResetPasswordModal;