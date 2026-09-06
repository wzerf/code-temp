import { Button, Drawer, Form, Input, InputNumber, Space, Switch, Tooltip, Typography, theme as antdTheme } from 'antd';
import { message } from '@/core/feedback/message';
import {
  CodeOutlined,
  CheckCircleOutlined,
  FieldStringOutlined,
  FieldNumberOutlined,
  StarOutlined,
  InfoCircleOutlined,
  LockOutlined } from '@ant-design/icons';
import {
  useCreateI18nLocale,
  useUpdateI18nLocale } from '@/api/hooks/i18n';
import type { CreateI18nLocaleRequest, I18nLocale } from '@/api/rest/types';

interface Props {
  open: boolean;
  row: I18nLocale | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  code: string;
  name: string;
  sort?: number;
  remark?: string;
  isDefault?: boolean;
  isEnabled?: boolean;
}

const CODE_PATTERN = /^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$/;

const I18nLocaleDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const [form] = Form.useForm<FormValues>();
  const { token } = antdTheme.useToken();
  const createMut = useCreateI18nLocale({
    onSuccess: () => {
      message.success('创建成功');
      onSaved();
      onClose();
    },
    onError: (err) => {
      message.error(`创建失败：${(err as Error).message ?? '未知错误'}`);
    },
  });
  const updateMut = useUpdateI18nLocale({
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

  const initialValues: FormValues = row
    ? {
        code: row.code,
        name: row.name,
        sort: row.sort,
        remark: row.remark,
        isDefault: row.isDefault === 1,
        isEnabled: row.isEnabled === 1,
      }
    : {
        code: '',
        name: '',
        sort: 0,
        remark: '',
        isDefault: false,
        isEnabled: true,
      };

  // Form 用 key 强制在 row 变化时 remount,确保 initialValues 生效;
  // destroyOnHidden 已卸载,新 mount 的 Form 会读取 initialValues,避免
  // useEffect + setFieldsValue 在 field 注册前执行导致回显丢失。
  const formKey = row ? `edit-${row.id}` : 'create';

  const handleOk = async () => {
    const values = await form.validateFields();
    const isDefault = values.isDefault ? (1 as const) : (0 as const);
    const isEnabled = values.isEnabled ? (1 as const) : (0 as const);
    if (isEdit) {
      updateMut.mutate({
        id: row.id,
        code: values.code,
        name: values.name,
        sort: values.sort ?? 0,
        remark: values.remark ?? '',
        isDefault,
        isEnabled,
      });
    } else {
      const body: CreateI18nLocaleRequest = {
        code: values.code,
        name: values.name,
        sort: values.sort ?? 0,
        remark: values.remark ?? '',
        isDefault,
        isEnabled,
      };
      createMut.mutate(body);
    }
  };

  return (
    <Drawer
      title={isEdit ? '编辑语言' : '新建语言'}
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
        initialValues={initialValues}
      >
        {/* 语种名称 */}
        <Form.Item
          label="语种名称"
          name="name"
          rules={[{ required: true, message: '请输入语言名称' }, { max: 64 }]}
        >
          <Input
            placeholder="例如 简体中文"
            prefix={<FieldStringOutlined style={{ color: token.colorTextTertiary }} />}
            style={{ borderRadius: 4 }}
          />
        </Form.Item>

        {/* Locale code */}
        <Form.Item
          label={
            <Space size={4} align="center">
              <span>语言代码</span>
              {isEdit && (
                <Tooltip title="语言代码在创建后不可修改（关联翻译键和用户偏好）">
                  <LockOutlined
                    style={{ color: token.colorTextTertiary, fontSize: 12, cursor: 'help' }}
                  />
                </Tooltip>
              )}
            </Space>
          }
          name="code"
          rules={[
            { required: true, message: '请输入语言代码' },
            {
              pattern: CODE_PATTERN,
              message: '形如 zh-CN / en-US / ja-JP（BCP-47 风格）',
            },
          ]}
        >
          <Input
            placeholder="例如 zh-CN"
            disabled={isEdit}
            prefix={<CodeOutlined style={{ color: token.colorTextTertiary }} />}
            style={{ borderRadius: 4 }}
          />
        </Form.Item>
        <Typography.Paragraph
          type="secondary"
          style={{ fontSize: 12, marginTop: -16, marginBottom: 16 }}
        >
          BCP-47 风格（如 zh-CN / en-US / ja-JP），建议与 i18n 标准库一致
        </Typography.Paragraph>

        {/* 排序 */}
        <Form.Item label="排序" name="sort" rules={[{ type: 'number' }]}>
          <InputNumber
            style={{ width: '100%', borderRadius: 4 }}
            placeholder="升序排序"
            prefix={<FieldNumberOutlined style={{ color: token.colorTextTertiary }} />}
            min={0}
            step={1}
            precision={0}
          />
        </Form.Item>
        <Typography.Paragraph
          type="secondary"
          style={{ fontSize: 12, marginTop: -16, marginBottom: 16 }}
        >
          数值越小越靠前；用于语言下拉与切换面板的展示顺序
        </Typography.Paragraph>

        {/* 备注 */}
        <Form.Item label="备注" name="remark">
          <Input.TextArea
            rows={3}
            maxLength={200}
            placeholder="选填；可记录使用范围、地区变体等"
            style={{ borderRadius: 4 }}
          />
        </Form.Item>

        {/* 是否默认 */}
        <div
          style={{
            padding: '10px 12px',
            marginTop: 8,
            borderRadius: 6,
            border: `1px solid ${token.colorBorderSecondary}`,
            background: token.colorFillQuaternary,
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Space size={6} align="center">
              <StarOutlined style={{ color: token.colorPrimary }} />
              <span style={{ fontSize: 13, color: token.colorText }}>设为默认语言</span>
            </Space>
            <Form.Item
              name="isDefault"
              valuePropName="checked"
              noStyle
              getValueFromEvent={(v) => !!v}
              getValueProps={(v) => ({ checked: !!v })}
            >
              <Switch checkedChildren="默认" unCheckedChildren="否" />
            </Form.Item>
          </div>
          <Typography.Paragraph
            type="secondary"
            style={{ fontSize: 12, margin: 0 }}
          >
            同一时刻仅一个语言可标记为默认；新用户偏好会沿用此值
          </Typography.Paragraph>
        </div>

        {/* 启用 */}
        <div
          style={{
            padding: '10px 12px',
            marginTop: 12,
            borderRadius: 6,
            border: `1px solid ${token.colorBorderSecondary}`,
            background: token.colorFillQuaternary,
            display: 'flex',
            flexDirection: 'column',
            gap: 6,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Space size={6} align="center">
              <CheckCircleOutlined style={{ color: token.colorPrimary }} />
              <span style={{ fontSize: 13, color: token.colorText }}>启用</span>
            </Space>
            <Form.Item
              name="isEnabled"
              valuePropName="checked"
              noStyle
              getValueFromEvent={(v) => !!v}
              getValueProps={(v) => ({ checked: v !== false })}
            >
              <Switch checkedChildren="启用" unCheckedChildren="禁用" />
            </Form.Item>
          </div>
          <Typography.Paragraph
            type="secondary"
            style={{ fontSize: 12, margin: 0 }}
          >
            禁用后该语言不会出现在用户切换面板与翻译键可选范围
          </Typography.Paragraph>
        </div>

        {/* 底部说明卡片：与翻译 drawer 视觉一致 */}
        <div
          style={{
            padding: '10px 12px',
            marginTop: 16,
            fontSize: 12,
            color: token.colorTextSecondary,
            background: token.colorPrimaryBg,
            borderLeft: `3px solid ${token.colorPrimary}`,
            borderRadius: 4,
            display: 'flex',
            alignItems: 'flex-start',
            gap: 6,
          }}
        >
          <InfoCircleOutlined
            style={{ color: token.colorPrimary, fontSize: 14, marginTop: 1 }}
          />
          <span>
            默认语言禁止删除；存在翻译时禁止删除该语言。语言代码一旦创建不可修改。
          </span>
        </div>
      </Form>
    </Drawer>
  );
};

export default I18nLocaleDrawer;
