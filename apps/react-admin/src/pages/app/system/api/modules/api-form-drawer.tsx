import { useMemo } from 'react';
import {
  AutoComplete,
  Button,
  Drawer,
  Form,
  Input,
  Select,
  Space,
  Switch,
  message,
} from 'antd';
import { useApiGroups, useCreateApi, useUpdateApi } from '@/api/hooks/api';
import type { CreateApiRequest, HttpMethod, SysApi } from '@/api/rest/types';

interface Props {
  open: boolean;
  row: SysApi | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup: string;
  remark?: string;
  isEnabled?: boolean;
}

const METHOD_OPTIONS: { label: string; value: HttpMethod }[] = [
  'GET',
  'POST',
  'PUT',
  'DELETE',
  'PATCH',
  'OPTIONS',
  'HEAD',
].map((m) => ({ label: m, value: m as HttpMethod }));

function buildApiFormValues(row: SysApi | null): FormValues {
  if (row) {
    return {
      name: row.name,
      method: row.method,
      path: row.path,
      permissionCode: row.permissionCode,
      apiGroup: row.apiGroup,
      remark: row.remark,
      isEnabled: row.isEnabled === 1,
    };
  }
  return {
    name: '',
    method: 'GET',
    path: '',
    permissionCode: '',
    apiGroup: '',
    remark: '',
    isEnabled: true,
  };
}

const ApiFormDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const isEdit = !!row;
  const [form] = Form.useForm<FormValues>();
  const { data: groups } = useApiGroups({ enabled: open });

  const createMut = useCreateApi({
    onSuccess: () => {
      message.success('创建成功');
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`创建失败：${(err as Error).message ?? '未知错误'}`),
  });
  const updateMut = useUpdateApi({
    onSuccess: () => {
      message.success('保存成功');
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`保存失败：${(err as Error).message ?? '未知错误'}`),
  });

  const submitting = createMut.isPending || updateMut.isPending;

  const groupOptions = useMemo(
    () => (groups ?? []).map((g) => ({ label: g, value: g })),
    [groups],
  );

  // Form 用 key + initialValues 保证 destroyOnHidden 挂载即回显，
  // 避免 useEffect + setFieldsValue 在字段注册前执行导致丢值。
  const formInitialValues = useMemo(() => buildApiFormValues(row), [row]);
  const formKey = row ? `edit-${row.id}` : 'create';

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: CreateApiRequest = {
      name: values.name,
      method: values.method,
      path: values.path,
      permissionCode: values.permissionCode,
      apiGroup: values.apiGroup ?? '',
      remark: values.remark ?? '',
      isEnabled: values.isEnabled ? 1 : 0,
    };
    if (isEdit && row) {
      updateMut.mutate({ id: row.id, data: payload });
    } else {
      createMut.mutate(payload);
    }
  };

  return (
    <Drawer
      title={isEdit ? '编辑接口' : '新增接口'}
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
      <Form
        key={formKey}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={formInitialValues}
      >
        <Form.Item
          label="接口名"
          name="name"
          rules={[{ required: true, message: '请输入接口名' }, { max: 64 }]}
        >
          <Input placeholder="展示用，如 用户分页列表" />
        </Form.Item>
        <Form.Item
          label="HTTP 方法"
          name="method"
          rules={[{ required: true, message: '请选择 HTTP 方法' }]}
        >
          <Select options={METHOD_OPTIONS} />
        </Form.Item>
        <Form.Item label="分组" name="apiGroup">
          <AutoComplete
            options={groupOptions}
            placeholder="选已有或输入新分组"
            filterOption={(input, option) =>
              (option?.value ?? '').toLowerCase().includes(input.toLowerCase())
            }
            allowClear
          />
        </Form.Item>
        <Form.Item
          label="路径"
          name="path"
          rules={[{ required: true, message: '请输入路径' }]}
        >
          <Input placeholder="如 /api/admin/users/:id" style={{ fontFamily: 'monospace' }} />
        </Form.Item>
        <Form.Item
          label="权限码"
          name="permissionCode"
          rules={[{ required: true, message: '请输入权限码' }]}
        >
          <Input placeholder="如 admin:user:list" />
        </Form.Item>
        <Form.Item label="备注" name="remark">
          <Input.TextArea rows={3} placeholder="选填" />
        </Form.Item>
        <Form.Item label="启用" name="isEnabled" valuePropName="checked">
          <Switch checkedChildren="启用" unCheckedChildren="禁用" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default ApiFormDrawer;