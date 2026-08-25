import { message } from 'antd';
import { initI18n } from '@/core/i18n';
import { fetchBackendI18n } from '@/core/i18n/utils';
import { usePreferencesStore } from '@/core/preferences';
import { type HttpResponse, RequestClient } from '@/core/transport/rest';
import i18n from 'i18next';
import { useAuthStore } from '@/stores';
import type { SupportedLocale } from '@/locales';

/**
 * 应用启动初始化
 */
export async function bootstrap() {
  // 1. 先把持久化的 token 从 storage 读回 store
  useAuthStore.getState().hydrate();

  await _initI18n();

  console.log('✅ 应用启动初始化完成');
}

async function _initI18n() {
  // 从 preferences 获取初始语言
  const initialLocale = usePreferencesStore.getState().preferences.app
    .locale as SupportedLocale;

  // 1) 本地 i18n bundle（不依赖 RequestClient）
  await initI18n(initialLocale);

  // 2) 注入 RequestClient（必须先于任何依赖 getInstance 的请求）
  RequestClient.init(import.meta.env.VITE_API_URL, {
    getToken: () => useAuthStore.getState().accessToken,
    getLocale: () => i18n.language,
    // 单 token：401 / 业务码 1006（密钥错误）直接 forceLogout，无前端 refresh
    onReAuthenticate: async () => {
      useAuthStore.getState().forceLogout();
    },
    // 与 Vue 侧 message.error 对齐：接口失败统一 toast
    onError: (msg) => {
      if (msg) {
        message.error(msg);
      }
    },
    getErrorMsg: getErrorMsg,
  });

  // 3) 进页拉取后端 public 翻译（fire-and-forget，对齐 Vue loadMessages）
  fetchBackendI18n(initialLocale);
}

/**
 * 按优先级获取错误提示文本
 * 1. reason → i18n error.xxx
 * 2. reason 无翻译 → 使用 message
 * 3. 都无 → 使用 status → i18n status.xxx
 * 4. 都无 → fallback
 */
export function getErrorMsg(error: unknown) {
  const i18nPrefix = 'request.';

  // 网络错误
  const errStr = String(error ?? '');
  if (errStr.includes('Network Error')) {
    return i18n.t(i18nPrefix + 'error.networkError');
  }

  // 超时
  if (
    error &&
    typeof error === 'object' &&
    'message' in error &&
    String(error.message).includes('timeout')
  ) {
    return i18n.t(i18nPrefix + 'error.timeout');
  }

  // 获取后端返回数据
  const resData =
    error &&
    typeof error === 'object' &&
    'response' in error &&
    error.response &&
    typeof error.response === 'object' &&
    'data' in error.response
      ? (error.response.data as HttpResponse)
      : undefined;

  if (!resData) {
    return i18n.t(i18nPrefix + 'error.unknownError');
  }

  const { reason, msg, message, code } = resData;

  // =========================================
  // 1. 优先：reason → request.reason.xxx
  // =========================================
  if (reason) {
    const key = i18nPrefix + `reason.${reason}`;
    // 使用 i18n.exists() 时需要指定命名空间
    if (i18n.exists(key, { ns: 'common' })) {
      return i18n.t(key, { ns: 'common' });
    }
  }

  // =========================================
  // 2. java-admin / 新 mock：msg
  // =========================================
  if (typeof msg === 'string' && msg.trim()) {
    return msg.trim();
  }

  // =========================================
  // 3. 兼容旧 message
  // =========================================
  if (typeof message === 'string' && message.trim()) {
    return message.trim();
  }

  // =========================================
  // 4. 使用 code 查 status
  // =========================================
  if (code) {
    const statusKey = i18nPrefix + `status.${code}`;
    if (i18n.exists(statusKey, { ns: 'common' })) {
      return i18n.t(statusKey, { ns: 'common' });
    }
  }

  // =========================================
  // 5. 全部失败 → 兜底
  // =========================================
  return i18n.t(i18nPrefix + 'error.unknownError', { ns: 'common' });
}
