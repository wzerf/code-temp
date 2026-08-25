import React, { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Form, Input, Button, Checkbox, App } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { prepareGlobalPublicKey } from '@/core/transport/rest/security';
import { useAuthStore } from '@/stores';
import { useNavigate, useSearchParams } from 'react-router-dom';
import AltchaWidget, { type AltchaWidgetHandle } from './AltchaWidget';
import '../auth-form.style.less';

const Login: React.FC = () => {
  const { t } = useTranslation('auth');
  const { login, loginLoading } = useAuthStore();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const altchaRef = useRef<AltchaWidgetHandle>(null);

  // 进入登录页即预拉全局公钥，避免复用 storage 中死会话钥（对齐 Vue login.vue）
  useEffect(() => {
    void prepareGlobalPublicKey(import.meta.env.VITE_API_URL || '/api');
  }, []);

  /** 登录失败：payload 已被服务端一次性消费，必须重新勾选验证 */
  const resetAltcha = () => {
    form.setFieldValue('altcha', undefined);
    altchaRef.current?.reset();
  };

  const handleSubmit = async (values: {
    username: string;
    password: string;
    remember?: boolean;
    altcha?: string;
  }) => {
    try {
      await login(
        {
          username: values.username,
          password: values.password,
          altcha: values.altcha,
        },
        { remember: values.remember ?? true },
      );

      message.success(t('loginSuccess'));

      // 跳转到重定向页面或首页
      const redirect = searchParams.get('redirect') || '/';
      setTimeout(() => {
        navigate(redirect);
      }, 300);
    } catch {
      // 错误 toast 由 request 错误拦截器统一弹出；失败后强制重新人机校验
      resetAltcha();
    }
  };

  return (
    <div className="auth-form-container">
      {/* 标题 */}
      <div className="auth-form-header">
        <h2 className="auth-form-title">{t('welcomeBack')}</h2>
        <p className="auth-form-description">
          {t('loginDescription')}
        </p>
      </div>

      {/* 登录表单 */}
      <Form
        form={form}
        name="login"
        onFinish={handleSubmit}
        size="large"
        initialValues={{ remember: true }}
      >
        <Form.Item
          name="username"
          className="auth-form-item"
          rules={[
            {
              required: true,
              message: t('usernameRequired'),
            },
          ]}
        >
          <Input
            prefix={<UserOutlined />}
            placeholder={t('usernamePlaceholder')}
            autoComplete="username"
          />
        </Form.Item>

        <Form.Item
          name="password"
          className="auth-form-item"
          rules={[
            {
              required: true,
              message: t('passwordRequired'),
            },
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder={t('passwordPlaceholder')}
            autoComplete="current-password"
          />
        </Form.Item>

        <Form.Item
          name="altcha"
          className="auth-form-item"
          rules={[
            {
              required: true,
              message: t('altchaRequired', { defaultValue: '请先完成人机校验' }),
            },
          ]}
        >
          <AltchaWidget ref={altchaRef} language="zh" />
        </Form.Item>
        <Form.Item className="auth-remember-checkbox">
          <div className="flex items-center justify-between">
            <Form.Item name="remember" valuePropName="checked" noStyle>
              <Checkbox>{t('rememberAccount')}</Checkbox>
            </Form.Item>
          </div>
        </Form.Item>

        <Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            loading={loginLoading}
            block
            className="auth-submit-button"
          >
            {loginLoading ? t('loggingIn') : t('loginButton')}
          </Button>
        </Form.Item>
      </Form>

      {/* 底部链接已移除（精简后不支持注册） */}
    </div>
  );
};

export default Login;
