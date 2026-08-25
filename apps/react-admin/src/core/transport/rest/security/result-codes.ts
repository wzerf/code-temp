/**
 * 请求安全相关 Result 业务码，与 Java ResultCode / mock SecurityResultCode 对齐。
 */
export const SecurityResultCode = {
  SUCCESS: 0,
  INTERNAL_ERROR: 1003,
  REQUEST_EXPIRED: 1004,
  REQUEST_ERROR: 1005,
  /** 会话/全局 RSA 钥不匹配或解密失败 → 需清会话并重新登录 */
  REQUEST_KEY_FAILED: 1006,
  REQUEST_NONCE_CONFLICT: 1007,
  REQUEST_SIGN_FAILED: 1008,
} as const;

export function isRequestKeyFailedCode(code: unknown): boolean {
  return Number(code) === SecurityResultCode.REQUEST_KEY_FAILED;
}

/** 登录 / 公开接口的密钥错误不应触发 forceLogout */
export function shouldSkipReAuthForKeyFailure(url: string): boolean {
  return url.includes('/auth/login') || url.includes('/public/');
}
