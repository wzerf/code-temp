import { useCallback, useEffect, useState } from 'react';
import { Button, Modal, Space, Spin, Tag, Typography} from 'antd';
import { message } from '@/core/feedback/message';
import { useTranslation } from 'react-i18next';
import {
  getMcpOauthStatusApi,
  listMcpBindableApi,
  refreshMcpOauthApi,
  revokeMcpOauthApi,
  startMcpOauthApi } from '@/api/rest/mcp';
import type { McpRelease } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface OauthStatus {
  loggedIn: boolean;
  expired: boolean;
  expiresAt: string | null;
  scope: string;
}

interface Props {
  /** 草稿 verify 场景：按草稿名找同名最新 OAuth Release 做登录 */
  draftId?: number | null;
  draftName?: string | null;
  /** 直接指定 Release 登录（Release/市场/绑定场景） */
  releaseId?: number | null;
  onLoggedIn?: () => void;
}

/** 同一窗口只允许一个 OAuth 弹窗轮询，避免多开互相覆盖 */
let activePoll: { stop: () => void } | null = null;

/**
 * MCP OAuth 登录区：登录态徽标 + 一键登录（弹窗） + 刷新/解绑。
 * 草稿 verify 时按同名 Release 登录；Release/绑定场景直接按 releaseId 登录。
 */
const McpOauthSection = ({ draftId, draftName, releaseId, onLoggedIn }: Props) => {
  const { t } = useTranslation('mcp');
  const [release, setRelease] = useState<McpRelease | null>(null);
  const [status, setStatus] = useState<OauthStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [loggingIn, setLoggingIn] = useState(false);

  const targetId = release?.id ?? releaseId ?? null;

  const loadStatus = useCallback(
    async (id: number) => {
      try {
        setStatus(await getMcpOauthStatusApi(id));
      } catch {
        setStatus(null);
      }
    },
    [],
  );

  // 解析目标 Release：直接指定或按草稿同名找最新版
  useEffect(() => {
    let cancelled = false;
    const resolve = async () => {
      if (releaseId) {
        if (!cancelled) {
          setRelease(null);
          await loadStatus(releaseId);
        }
        return;
      }
      if (!draftId) return;
      setLoading(true);
      try {
        const bindable = await listMcpBindableApi();
        // 草稿名 → 同名最新 OAuth Release（取 id 最大）；首次发布前无旧版则不展示
        const same = bindable.filter((r) => r.authType === 'OAUTH' && (!draftName || r.name === draftName));
        const target = same.length > 0 ? same.reduce((a, b) => (a.id > b.id ? a : b)) : null;
        if (!cancelled) {
          setRelease(target);
          if (target) await loadStatus(target.id);
        }
      } catch {
        if (!cancelled) setRelease(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void resolve();
    return () => {
      cancelled = true;
    };
  }, [draftId, draftName, releaseId, loadStatus]);

  // 跨窗口登录完成通知（回调页换票成功后广播）
  useEffect(() => {
    if (!targetId) return;
    const handler = (e: StorageEvent) => {
      if (e.key === 'mcp-oauth-done' && e.newValue) {
        try {
          const done = JSON.parse(e.newValue) as { releaseId: number; ok: boolean };
          if (done.releaseId === targetId && done.ok) {
            void loadStatus(targetId).then(() => onLoggedIn?.());
          }
        } catch {
          /* 忽略 */
        }
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, [targetId, loadStatus, onLoggedIn]);

  const stopActivePoll = () => {
    activePoll?.stop();
    activePoll = null;
  };

  /** 单次轮询：登录成功则收尾并返回 true */
  const pollLoginOnce = async (id: number): Promise<boolean> => {
    try {
      const s = await getMcpOauthStatusApi(id);
      if (!s.loggedIn || s.expired) return false;
      setStatus(s);
      stopActivePoll();
      setLoggingIn(false);
      onLoggedIn?.();
      return true;
    } catch {
      return false;
    }
  };

  const handleLogin = async () => {
    if (!targetId) {
      message.warning(t('oauthLoginFailed'));
      return;
    }
    stopActivePoll();
    setLoggingIn(true);
    try {
      const redirectUri = `${window.location.origin}/system/mcp/oauth-callback`;
      const { authorizationUrl, state } = await startMcpOauthApi(targetId, { redirectUri });
      const popup = window.open(authorizationUrl, `mcp-oauth-${targetId}`, 'width=640,height=720');
      if (!popup) {
        // 弹窗被拦截：退化为当前窗口跳转（回调页完成后返回）
        sessionStorage.setItem('mcp-oauth-state', JSON.stringify({ releaseId: targetId, state }));
        window.location.href = authorizationUrl;
        return;
      }
      // 轮询登录态（用户在弹窗完成登录后回调页换票落库）
      let stopped = false;
      activePoll = {
        stop: () => {
          stopped = true;
        },
      };
      for (let i = 0; i < 60 && !stopped; i++) {
        await new Promise((r) => setTimeout(r, 2000));
        if (stopped || popup.closed) break;
        const done = await pollLoginOnce(targetId);
        if (done || stopped) break;
      }
      stopActivePoll();
      setLoggingIn(false);
      // 超时或关闭弹窗后刷新一次状态
      await loadStatus(targetId);
    } catch (err) {
      stopActivePoll();
      setLoggingIn(false);
      message.error(`${t('oauthLoginFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handleRefresh = async () => {
    if (!targetId) return;
    try {
      const s = await refreshMcpOauthApi(targetId);
      setStatus(s);
      message.success(t('oauthRefreshSuccess'));
    } catch (err) {
      message.error(getApiErrorMessage(err, t('unknownError')));
    }
  };

  const handleRevoke = () => {
    if (!targetId) return;
    Modal.confirm({
      title: t('oauthRevokeConfirm'),
      okText: t('confirm'),
      cancelText: t('cancel'),
      onOk: async () => {
        try {
          await revokeMcpOauthApi(targetId);
          setStatus({ loggedIn: false, expired: false, expiresAt: null, scope: '' });
          message.success(t('oauthRevokeSuccess'));
        } catch (err) {
          message.error(getApiErrorMessage(err, t('unknownError')));
        }
      },
    });
  };

  if (loading) return <Spin size="small" />;
  if (!targetId) return null;

  const badge = !status ? null : status.loggedIn && !status.expired ? (
    <Tag color="green">{t('oauthStatusLoggedIn')}</Tag>
  ) : status.loggedIn && status.expired ? (
    <Tag color="gold">{t('oauthStatusExpired')}</Tag>
  ) : (
    <Tag>{t('oauthStatusNotLogged')}</Tag>
  );

  return (
    <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
      <Space>
        {badge}
        {status?.scope ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('oauthScopeLabel')}：{status.scope}
          </Typography.Text>
        ) : null}
      </Space>
      <Space>
        {!status?.loggedIn || status?.expired ? (
          <Button type="primary" loading={loggingIn} onClick={handleLogin}>
            {t('oauthLogin')}
          </Button>
        ) : null}
        {status?.loggedIn && status?.expired ? <Button onClick={handleRefresh}>{t('oauthRefresh')}</Button> : null}
        {status?.loggedIn ? (
          <Button danger onClick={handleRevoke}>
            {t('oauthRevoke')}
          </Button>
        ) : null}
      </Space>
    </Space>
  );
};

export default McpOauthSection;
