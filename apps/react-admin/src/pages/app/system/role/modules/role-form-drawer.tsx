 
import { useEffect, useMemo } from 'react';
import {
  Button,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  message,
} from 'antd';
import {
  useAllRoles,
  useCreateRole,
  useUpdateRole,
} from '@/api/hooks/role';
import type {
  CreateRoleRequest,
  RoleOption,
  SysRole,
} from '@/api/rest/types';

export type RoleFormKind = 'create' | 'edit';

interface Props {
  open: boolean;
  kind: RoleFormKind;
  row: SysRole | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  code: string;
  name: string;
  parentId?: number | null;
  sort?: number;
  isEnabled: boolean;
  remark?: string;
}

const RoleFormDrawer = ({ open, kind, row, onClose, onSaved }: Props) => {
  const isEdit = kind === 'edit' && !!row;
  const [form] = Form.useForm<FormValues>();

  const createMut = useCreateRole({
    onSuccess: () => {
      message.success('创建成功');
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`创建失败：${(err as Error).message ?? '未知错误'}`),
  });
  const updateMut = useUpdateRole({
    onSuccess: () => {
      message.success('保存成功');
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`保存失败：${(err as Error).message ?? '未知错误'}`),
  });

  // 父角色下拉：全量角色，编辑时排除自己
  const { data: allRoles } = useAllRoles(undefined, { enabled: open });

  useEffect(() => {
    if (!open) return;
    if (isEdit && row) {
      form.setFieldsValue({
        code: row.code,
        name: row.name,
        parentId: row.parentId,
        sort: row.sort,
        isEnabled: row.isEnabled === 1,
        remark: row.remark,
      });
    } else {
      form.setFieldsValue({
        code: '',
        name: '',
        parentId: undefined,
        sort: 0,
        isEnabled: true,
        remark: '',
      });
    }
  }, [open, isEdit, row, form]);

  // 父角色选项：编辑时排除自己
  const parentOptions = useMemo(() => {
    const list: RoleOption[] = allRoles ?? [];
    return list
      .filter((r) => !(isEdit && row && r.id === row.id))
      .map((r) => ({ label: `${r.name}（${r.code}）`, value: r.id }));
  }, [allRoles, isEdit, row]);

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: CreateRoleRequest = {
      code: values.code,
      name: values.name,
      parentId: values.parentId ?? null,
      sort: values.sort ?? 0,
      isEnabled: values.isEnabled ? 1 : 0,
      remark: values.remark ?? '',
    };
    if (isEdit && row) {
      // code 不可改
      updateMut.mutate({
        id: row.id,
        data: {
          name: payload.name,
          parentId: payload.parentId,
          sort: payload.sort,
          isEnabled: payload.isEnabled,
          remark: payload.remark,
        },
      });
    } else {
      createMut.mutate(payload);
    }
  };

  const submitting = createMut.isPending || updateMut.isPending;

  return (
    <Drawer
      title={isEdit ? '编辑角色' : '新增角色'}
      open={open}
      onClose={onClose}
      size={560}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={submitting}>
            取消
          </Button>
          <Button type="primary" onClick={handleSave} loading={submitting}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="角色编码"
              name="code"
              rules={[{ required: true, message: '请输入角色编码' }, { max: 64 }]}
              extra={isEdit ? '编码创建后不可修改' : undefined}
            >
              <Input placeholder="如 operator" disabled={isEdit} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="角色名称"
              name="name"
              rules={[{ required: true, message: '请输入角色名称' }, { max: 64 }]}
            >
              <Input placeholder="如 超级管理员" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item label="父角色" name="parentId">
              <Select
                allowClear
                placeholder="— 顶级 —"
                options={parentOptions}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="排序" name="sort">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item label="启用状态" name="isEnabled" valuePropName="checked">
          <Switch checkedChildren="启用" unCheckedChildren="禁用" />
        </Form.Item>

        <Form.Item label="备注" name="remark">
          <Input.TextArea rows={3} placeholder="选填" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default RoleFormDrawer;