import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  clearCachedPublicKey,
  ensurePublicKey,
  getCachedPublicKey,
  prepareGlobalPublicKey,
  setCachedPublicKey,
} from './public-key';

describe('public-key prepareGlobalPublicKey', () => {
  beforeEach(() => {
    clearCachedPublicKey();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    clearCachedPublicKey();
  });

  it('清掉残留会话钥后强制拉取全局公钥', async () => {
    setCachedPublicKey('stale-session-key');
    expect(getCachedPublicKey()).toBe('stale-session-key');

    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ data: { publicKey: 'fresh-global-key' } }),
    } as Response);

    const key = await prepareGlobalPublicKey('/api');
    expect(key).toBe('fresh-global-key');
    expect(getCachedPublicKey()).toBe('fresh-global-key');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/encrypt/public/key',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('force=true 时忽略已有缓存', async () => {
    setCachedPublicKey('cached');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ data: { publicKey: 'forced' } }),
    } as Response);

    const key = await ensurePublicKey('/api', { force: true });
    expect(key).toBe('forced');
    expect(fetchMock).toHaveBeenCalled();
  });
});
