import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import { describe, expect, it, vi } from 'vitest';

import { RequestClient } from './request-client';

function installJsonAdapter(
  client: RequestClient,
  handler: (config: InternalAxiosRequestConfig) => {
    status: number;
    data: unknown;
  },
) {
  const instance = (
    client as unknown as {
      instance: { defaults: { adapter?: AxiosAdapter } };
    }
  ).instance;

  instance.defaults.adapter = async (config) => {
    const result = handler(config as InternalAxiosRequestConfig);
    return {
      data: result.data,
      status: result.status,
      statusText: 'OK',
      headers: {},
      config: config as InternalAxiosRequestConfig,
    };
  };
}

describe('RequestClient 业务码 1006 密钥错误', () => {
  it('业务接口返回 1006 时触发 onReAuthenticate，并先 toast', async () => {
    const onReAuthenticate = vi.fn().mockResolvedValue(undefined);
    const onError = vi.fn();

    const client = new RequestClient(
      { baseURL: '/api' },
      {
        getToken: () => 'token',
        onReAuthenticate,
        onError,
      },
    );

    installJsonAdapter(client, () => ({
      status: 200,
      data: { code: 1006, msg: '密钥错误', data: null },
    }));

    await expect(client.get('/menu/all')).rejects.toBeTruthy();
    expect(onError).toHaveBeenCalledWith('密钥错误');
    expect(onReAuthenticate).toHaveBeenCalledTimes(1);
  });

  it('登录接口 1006 不触发 onReAuthenticate', async () => {
    const onReAuthenticate = vi.fn().mockResolvedValue(undefined);
    const onError = vi.fn();

    const client = new RequestClient(
      { baseURL: '/api' },
      {
        getToken: () => null,
        onReAuthenticate,
        onError,
      },
    );

    installJsonAdapter(client, () => ({
      status: 200,
      data: { code: 1006, msg: '密钥错误', data: null },
    }));

    await expect(
      client.post('/auth/login', { username: 'root' } as never),
    ).rejects.toBeTruthy();
    expect(onReAuthenticate).not.toHaveBeenCalled();
  });

  it('并发多个 1006 只 forceLogout 一次（单飞）', async () => {
    let resolveReauth!: () => void;
    const reauthGate = new Promise<void>((resolve) => {
      resolveReauth = resolve;
    });
    const onReAuthenticate = vi.fn().mockImplementation(() => reauthGate);
    const onError = vi.fn();

    const client = new RequestClient(
      { baseURL: '/api' },
      {
        getToken: () => 'token',
        onReAuthenticate,
        onError,
      },
    );

    installJsonAdapter(client, () => ({
      status: 200,
      data: { code: 1006, msg: '密钥错误', data: null },
    }));

    const p1 = client.get('/menu/all');
    const p2 = client.get('/user/list');
    // 让两个请求都进入拦截器并挂起在同一 reAuthPromise 上
    await vi.waitFor(() => {
      expect(onReAuthenticate).toHaveBeenCalledTimes(1);
    });
    resolveReauth();
    await Promise.allSettled([p1, p2]);
    expect(onReAuthenticate).toHaveBeenCalledTimes(1);
  });
});

