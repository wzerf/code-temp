import { describe, expect, it } from 'vitest';

import {
  isRequestKeyFailedCode,
  SecurityResultCode,
  shouldSkipReAuthForKeyFailure,
} from './result-codes';

describe('security result-codes', () => {
  it('识别 1006 密钥错误（number / string）', () => {
    expect(isRequestKeyFailedCode(SecurityResultCode.REQUEST_KEY_FAILED)).toBe(
      true,
    );
    expect(isRequestKeyFailedCode(1006)).toBe(true);
    expect(isRequestKeyFailedCode('1006')).toBe(true);
    expect(isRequestKeyFailedCode(0)).toBe(false);
    expect(isRequestKeyFailedCode(1008)).toBe(false);
  });

  it('登录与公开接口跳过因密钥错误触发的重认证', () => {
    expect(shouldSkipReAuthForKeyFailure('/auth/login')).toBe(true);
    expect(shouldSkipReAuthForKeyFailure('/api/auth/login')).toBe(true);
    expect(shouldSkipReAuthForKeyFailure('/public/i18n')).toBe(true);
    expect(shouldSkipReAuthForKeyFailure('/menu/all')).toBe(false);
    expect(shouldSkipReAuthForKeyFailure('/user/list')).toBe(false);
  });
});
