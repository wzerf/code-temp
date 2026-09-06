import { useMemo } from 'react';
import {
  Button,
  Col,
  DatePicker,
  Drawer,
  Form,
  Input,
  Row,
  Select,
  Space,
  Switch,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  useCreateUser,
  useUpdateUser,
} from '@/api/hooks/user';
import { useAllRoles } from '@/api/hooks/role';
import { useListAllI18nLocale } from '@/api/hooks/i18n';
import type { UserListItem } from '@/api/rest/types';

export type UserFormKind = 'create' | 'edit';

interface Props {
  open: boolean;
  kind: UserFormKind;
  row: UserListItem | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  username: string;
  nickname: string;
  email?: string;
  phone?: string;
  avatar?: string;
  languageCode?: string | null;
  isEnabled: boolean;
  password?: string;
  confirmPassword?: string;
  roleIds?: number[];
  remark?: string;
  /** null/空 = 永不过期 */
  accountExpiresAt?: Dayjs | null;
}

const SectionTitle = ({ children }: { children: React.ReactNode }) => (
  <div style={{ color: '#94a3b8', fontSize: 12, fontWeight: 500, marginBottom: 12, marginTop: 8 }}>
    {children}
  </div>
);

/** 由列表行构建表单初值；新建时给默认空值。 */
function buildUserFormValues(row: UserListItem | null): FormValues {
  if (row) {
    return {
      username: row.username,
      nickname: row.nickname,
      email: row.email || undefined,
      phone: row.phone || undefined,
      avatar: row.avatar || undefined,
      languageCode: row.languageCode ?? undefined,
      isEnabled: row.isEnabled === 1,
      password: '',
      confirmPassword: '',
      roleIds: row.roleIds ?? [],
      remark: row.remark || undefined,
      accountExpiresAt: row.accountExpiresAt ? dayjs(row.accountExpiresAt) : null,
    };
  }
  return {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    avatar: '',
    languageCode: undefined,
    isEnabled: true,
    password: '',
    confirmPassword: '',
    roleIds: [],
    remark: '',
    accountExpiresAt: null,
  };
}

const UserFormDrawer = ({ open, kind, row, onClose, onSaved }: Props) => {
  const isEdit = kind === 'edit' && !!row;
  const [form] = Form.useForm<FormValues>();

  const createMut = useCreateUser({
    onSuccess: () => {
      message.success('创建成功');
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`创建失败：${(err as Error).message ?? '未知错误'}`),
  });
  const updateMut = useUpdateUser({
    onSuccess: () => {
      message.success('保存成功');
      onSaved();
      onClose();
    },
    onError: (err) => message.error(`保存失败：${(err as Error).message ?? '未知错误'}`),
  });

  // 角色下拉：仅启用角色（禁用角色不可新绑）
  const { data: roleOptions } = useAllRoles({ status: 1 }, { enabled: open });
  // 语言下拉：全量语言
  const { data: localeOptions } = useListAllI18nLocale(undefined, { enabled: open });

  // Form 用 key + initialValues 保证 destroyOnHidden 挂载即回显，
  // 避免 useEffect + setFieldsValue 在字段注册前执行导致丢值。
  const formInitialValues = useMemo(() => buildUserFormValues(row), [row]);
  const formKey = row ? `edit-${row.id}` : 'create';

  const roleSelectOptions = useMemo(
    () => (roleOptions ?? []).map((r) => ({ label: `${r.name}（${r.code}）`, value: r.id })),
    [roleOptions],
  );
  const localeSelectOptions = useMemo(
    () => (localeOptions ?? []).map((l) => ({ label: `${l.name}（${l.code}）`, value: l.code })),
    [localeOptions],
  );

  const handleSave = async () => {
    const values = await form.validateFields();
    if (isEdit && row) {
      // 编辑：不传 username/password；roleIds 传当前选中
      updateMut.mutate({
        id: row.id,
        data: {
          nickname: values.nickname,
          email: values.email || undefined,
          phone: values.phone || undefined,
          avatar: values.avatar || undefined,
          languageCode: values.languageCode ?? null,
          isEnabled: values.isEnabled ? 1 : 0,
          roleIds: values.roleIds ?? [],
          remark: values.remark ?? '',
          accountExpiresAt: values.accountExpiresAt
            ? values.accountExpiresAt.toISOString()
            : null,
        },
      });
    } else {
      createMut.mutate({
        username: values.username,
        password: values.password ?? '',
        nickname: values.nickname,
        email: values.email || undefined,
        phone: values.phone || undefined,
        avatar: values.avatar || undefined,
        languageCode: values.languageCode ?? null,
        isEnabled: values.isEnabled ? 1 : 0,
        roleIds: values.roleIds ?? [],
        remark: values.remark ?? '',
        accountExpiresAt: values.accountExpiresAt
          ? values.accountExpiresAt.toISOString()
          : null,
      });
    }
  };

  const submitting = createMut.isPending || updateMut.isPending;

  return (
    <Drawer
      title={isEdit ? '编辑用户' : '新建用户'}
      open={open}
      onClose={onClose}
      size={640}
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
      <Form
        key={formKey}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={formInitialValues}
      >
        {/* 基础信息段 */}
        <SectionTitle>基础信息</SectionTitle>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="用户名"
              name="username"
              rules={[{ required: true, message: '请输入用户名' }, { max: 64 }]}
            >
              <Input placeholder="如 admin" disabled={isEdit} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="昵称"
              name="nickname"
              rules={[{ required: true, message: '请输入昵称' }, { max: 64 }]}
            >
              <Input placeholder="如 管理员" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="邮箱"
              name="email"
              rules={[{ type: 'email', message: '邮箱格式不正确' }]}
            >
              <Input placeholder="name@example.com" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="手机号" name="phone">
              <Input placeholder="如 13800000000" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item label="头像 URL" name="avatar">
              <Input placeholder="https://..." />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item label="默认语言" name="languageCode">
              <Select
                allowClear
                placeholder="请选择默认语言"
                options={localeSelectOptions}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item label="启用状态" name="isEnabled" valuePropName="checked">
          <Switch checkedChildren="启用" unCheckedChildren="禁用" />
        </Form.Item>

        <Form.Item
          label="账号过期时间"
          name="accountExpiresAt"
          tooltip="留空表示永不过期；到期后不可登录，已登录会话也会被拒绝"
        >
          <DatePicker
            showTime
            allowClear
            style={{ width: '100%' }}
            placeholder="永不过期"
          />
        </Form.Item>

        {/* 安全段 */}
        <SectionTitle>安全{isEdit ? '（留空表示不修改密码）' : ''}</SectionTitle>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              label="密码"
              name="password"
              rules={isEdit ? [] : [{ required: true, message: '请输入密码' }, { min: 6, message: '密码至少 6 位' }]}
            >
              <Input.Password placeholder={isEdit ? '留空不修改' : '请输入密码'} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              label="确认密码"
              name="confirmPassword"
              dependencies={['password']}
              rules={
                isEdit
                  ? [
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          const pwd = getFieldValue('password');
                          if (!value && !pwd) return Promise.resolve();
                          if (value !== pwd) return Promise.reject(new Error('两次密码不一致'));
                          return Promise.resolve();
                        },
                      }),
                    ]
                  : [
                      { required: true, message: '请确认密码' },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (value !== getFieldValue('password')) {
                            return Promise.reject(new Error('两次密码不一致'));
                          }
                          return Promise.resolve();
                        },
                      }),
                    ]
              }
            >
              <Input.Password placeholder="请再次输入密码" />
            </Form.Item>
          </Col>
        </Row>

        {/* 角色段 */}
        <SectionTitle>角色</SectionTitle>
        <Form.Item label="角色" name="roleIds">
          <Select
            mode="multiple"
            allowClear
            placeholder="请选择角色"
            options={roleSelectOptions}
            style={{ width: '100%' }}
          />
        </Form.Item>

        {/* 备注段 */}
        <SectionTitle>备注</SectionTitle>
        <Form.Item label="备注" name="remark">
          <Input.TextArea rows={3} placeholder="选填" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default UserFormDrawer;
