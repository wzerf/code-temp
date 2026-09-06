import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Result, Spin } from 'antd';
import { useTranslation } from 'react-i18next';
import { callbackMcpOauthApi } from '@/api/rest/mcp';
import { getApiErrorMessage } from '../blacklist/modules/error-message';

/**
 * MCP OAuth 回调页（redirect_uri 落点）。
 * 路由由 pageMap 自动注册：/system/mcp/oauth-callback。
 * 从 query 取 code/state/error → 调后端换票 → 广播给 opener/同源页 → 关窗或提示返回。
 */
const McpOauthCallbackPage = () => {
  const { t } = useTranslation('mcp');
  const [phase, setPhase] = useState<'working' | 'ok' | 'fail'>('working');
  const [error, setError] = useState('');
  const done = useRef(false);

  useEffect(() => {
    if (done.current) return;
    done.current = true;
    const run = async () => {
      const query = new URLSearchParams(window.location.search);
      const err = query.get('error');
      const errDesc = query.get('error_description');
      if (err) {
        setError(errDesc || err);
        setPhase('fail');
        return;
      }
      const code = query.get('code');
      const state = query.get('state');
      if (!code || !state) {
        setError(t('oauthLoginFailed'));
        setPhase('fail');
        return;
      }
      try {
        await callbackMcpOauthApi({ code, state });
        // 通知 opener + 同源其他页（verify 弹窗轮询/或 storage 监听）
        const stored = sessionStorage.getItem('mcp-oauth-state');
        let releaseId: number | null = null;
        try {
          const parsed = stored ? (JSON.parse(stored) as { releaseId: number }) : null;
          releaseId = parsed?.releaseId ?? null;
        } catch {
          /* 忽略 */
        }
        sessionStorage.removeItem('mcp-oauth-state');
        if (releaseId !== null) {
          localStorage.setItem('mcp-oauth-done', JSON.stringify({ releaseId, ok: true, at: Date.now() }));
        }
        setPhase('ok');
        // 弹窗场景自动关窗；整页跳转场景留给用户点返回
        if (window.opener) {
          setTimeout(() => window.close(), 1200);
        }
      } catch (e) {
        setError(getApiErrorMessage(e, t('unknownError')));
        setPhase('fail');
      }
    };
    void run();
  }, [t]);

  if (phase === 'working') {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
        <div style={{ marginTop: 12 }}>{t('oauthCallbackProcessing')}</div>
      </div>
    );
  }

  if (phase === 'ok') {
    return (
      <Result
        status="success"
        title={t('oauthLoginSuccess')}
        extra={
          window.opener ? null : (
            <Button type="primary" onClick={() => (window.location.href = '/system/mcp')}>
              {t('oauthBackToList')}
            </Button>
          )
        }
      />
    );
  }

  return (
    <div style={{ padding: 48, maxWidth: 640, margin: '0 auto' }}>
      <Alert type="error" showIcon title={t('oauthLoginFailed')} description={error} />
      <div style={{ marginTop: 16 }}>
        <Button type="primary" onClick={() => (window.location.href = '/system/mcp')}>
          {t('oauthBackToList')}
        </Button>
      </div>
    </div>
  );
};

export default McpOauthCallbackPage;
