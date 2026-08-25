import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, CreateAxiosDefaults, InternalAxiosRequestConfig } from 'axios';

import axios from 'axios';

import { merge, bindMethods } from '@/utils';

import { FileDownloader } from './modules/downloader';
import { InterceptorManager } from './modules/interceptor';
import { FileUploader } from './modules/uploader';
import {
  authenticateResponseInterceptor,
  errorMessageResponseInterceptor,
} from './preset-interceptors';
import {
  createSecurityRequestInterceptor,
  createSecurityResponseInterceptor,
  getSecurityClientConfig,
  isRequestKeyFailedCode,
  SECURITY_HEADERS,
  shouldSkipReAuthForKeyFailure,
} from './security';
import { defaultIdGenerator, getDefaultErrorMsg } from './utils';
import type { RequestClientCallbacks, RequestClientOptions, RequestContentType } from './types';

class RequestClient {
  private readonly instance: AxiosInstance;

  public addRequestInterceptor: InterceptorManager['addRequestInterceptor'];
  public addResponseInterceptor: InterceptorManager['addResponseInterceptor'];

  public download: FileDownloader['download'];
  public upload: FileUploader['upload'];

  /** 并发 401 / 1006 只处理一次，对齐 Vue doReAuthenticate 单飞锁 */
  private reAuthPromise: Promise<void> | null = null;

  // ==========================
  // 静态单例管理
  // ==========================
  private static _instance: RequestClient | null = null;

  /**
   * 初始化全局单例（在 bootstrap 时调用一次）
   */
  static init(baseURL: string, callbacks: RequestClientCallbacks) {
    RequestClient._instance = new RequestClient({ baseURL }, callbacks);
  }

  /**
   * 获取全局单例
   */
  static getInstance(): RequestClient {
    if (!RequestClient._instance) {
      throw new Error('RequestClient not initialized. Call RequestClient.init() during bootstrap.');
    }
    return RequestClient._instance;
  }

  /**
   * 构造函数，创建 Axios 实例并注册拦截器
   * @param options - Axios 请求配置
   * @param callbacks - 业务回调接口（token、认证、错误处理），由应用层注入
   */
  constructor(options: RequestClientOptions = {}, callbacks?: RequestClientCallbacks) {
    // 合并默认配置和传入的配置
    const defaultConfig: CreateAxiosDefaults<RequestContentType> = {
      headers: {
        'Content-Type': 'application/json;charset=utf-8' as RequestContentType,
      },
      timeout: 10_000,
      // 数组 query 用重复键 typeCode=a&typeCode=b，避免默认 typeCode[]= 导致
      // 签名 AAD 的 key 与 mock/Java 解析结果不一致（1008 签名错误）。
      // 与 security/request-encryption.normalizeParams（多值取首项）配套。
      paramsSerializer: {
        indexes: null,
      },
    };
    const { ...axiosConfig } = options;
    const requestConfig = merge(axiosConfig, defaultConfig);
    this.instance = axios.create(requestConfig);

    bindMethods(this);

    // 实例化拦截器管理器
    const interceptorManager = new InterceptorManager(this.instance);
    this.addRequestInterceptor = interceptorManager.addRequestInterceptor.bind(interceptorManager);
    this.addResponseInterceptor =
      interceptorManager.addResponseInterceptor.bind(interceptorManager);

    // 实例化文件上传器
    const fileUploader = new FileUploader(this);
    this.upload = fileUploader.upload.bind(fileUploader);
    // 实例化文件下载器
    const fileDownloader = new FileDownloader(this);
    this.download = fileDownloader.download.bind(fileDownloader);

    // ==========================
    // 注册内置拦截器
    // ==========================
    if (callbacks) {
      this.setupInterceptors(callbacks);
    }
  }

  /**
   * 格式化令牌
   */
  private formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  /**
   * 注册所有内置拦截器
   */
  private setupInterceptors(callbacks: RequestClientCallbacks) {
    this.useTokenInterceptor(callbacks);
    this.useRequestIdInterceptor();
    this.useLocaleInterceptor(callbacks);
    this.useSecurityRequestInterceptor(callbacks);
    // auth 拦截器必须在 responseData 之前，否则 401 错误会丢失 AxiosError 结构
    this.useAuthInterceptor(callbacks);
    // 解密必须在 code/msg/data 解析之前
    this.useSecurityResponseInterceptor();
    // 业务码解析：1006 密钥错误在此触发重认证（HTTP 仍为 200，auth 拦截器看不到）
    this.useResponseDataInterceptor(callbacks);
    this.useErrorMessageInterceptor(callbacks);
  }

  /**
   * 重新认证（单飞）：清会话并回登录。401 与 1006 共用。
   */
  private async doReAuthenticate(
    callbacks: RequestClientCallbacks,
    reason: string,
  ): Promise<void> {
    if (this.reAuthPromise) {
      return this.reAuthPromise;
    }
    this.reAuthPromise = (async () => {
      console.warn(reason);
      if (callbacks.onReAuthenticate) {
        await callbacks.onReAuthenticate(true);
      } else {
        console.error(
          'onReAuthenticate callback not set. Call RequestClient.init() during bootstrap.',
        );
      }
    })().finally(() => {
      this.reAuthPromise = null;
    });
    return this.reAuthPromise;
  }

  /**
   * 请求拦截器：注入 Authorization Token
   */
  private useTokenInterceptor(callbacks: RequestClientCallbacks) {
    this.addRequestInterceptor({
      fulfilled: (config) => {
        // public 端点不需要登录态
        if (config.url?.startsWith('/public/')) {
          return config as never;
        }
        if (callbacks.getToken) {
          const token = callbacks.getToken();
          config.headers.Authorization = this.formatToken(token);
        }
        return config as never;
      },
    });
  }

  /**
   * 请求拦截器：注入 X-Request-ID 和 XMLHttpRequest 标识。
   * Nonce 关且 Encrypt/Sign 均关时不强制 Request-ID；安全拦截器仍可能按协议补齐。
   */
  private useRequestIdInterceptor() {
    this.addRequestInterceptor({
      fulfilled: (config) => {
        const sec = getSecurityClientConfig();
        const shouldInjectId =
          sec.nonceEnabled || sec.encryptEnabled || sec.signEnabled;
        if (shouldInjectId) {
          const requestId =
            (config.headers as Record<string, unknown>)[SECURITY_HEADERS.REQUEST_ID] ??
            defaultIdGenerator();
          (config as InternalAxiosRequestConfig & { _requestId?: string })._requestId =
            String(requestId);
          config.headers[SECURITY_HEADERS.REQUEST_ID] = requestId;
        }
        config.headers['X-Requested-With'] = 'XMLHttpRequest';
        return config as never;
      },
    });
  }

  /**
   * 请求拦截器：注入 Accept-Language（X-Language 由安全拦截器按开关写入）
   */
  private useLocaleInterceptor(callbacks: RequestClientCallbacks) {
    this.addRequestInterceptor({
      fulfilled: (config) => {
        if (callbacks.getLocale) {
          config.headers['Accept-Language'] = callbacks.getLocale();
        }
        return config as never;
      },
    });
  }

  /**
   * 请求拦截器：Timestamp / Encrypt / Sign / X-Language
   */
  private useSecurityRequestInterceptor(callbacks: RequestClientCallbacks) {
    const baseURL = String(this.instance.defaults.baseURL ?? '');
    const fulfilled = createSecurityRequestInterceptor({
      baseURL,
      getLocale: callbacks.getLocale,
      nonce: defaultIdGenerator,
    });
    this.addRequestInterceptor({
      fulfilled: async (config) => (await fulfilled(config)) as never,
    });
  }

  /**
   * 响应拦截器：加密响应解密
   */
  private useSecurityResponseInterceptor() {
    const fulfilled = createSecurityResponseInterceptor();
    this.addResponseInterceptor({
      fulfilled: async (response) => (await fulfilled(response)) as never,
    });
  }

  /**
   * 响应拦截器：解构响应数据
   * 兼容两种返回形态：
   *   1) 统一契约包装：`{ code, msg, data }` → 返回 `data` 字段
   *   2) 裸数据：直接返回（如 ALTCHA challenge）
   * 非 2xx 响应抛错，包含原始响应体供上层处理
   * 业务码 1006（密钥错误）：会话钥失效，对齐 Vue 会话失效 → 清会话回登录
   */
  private useResponseDataInterceptor(callbacks: RequestClientCallbacks) {
    this.addResponseInterceptor({
      fulfilled: async (response) => {
        const { data: responseData, status } = response;

        if (status >= 200 && status < 400) {
          if (
            responseData &&
            typeof responseData === 'object' &&
            'code' in responseData &&
            'data' in responseData
          ) {
            // java-admin / mock 统一 Result 包装
            const wrapped = responseData as {
              code: unknown;
              data: unknown;
              msg?: string;
              message?: string;
            };
            if (wrapped.code === 0 || wrapped.code === '0') {
              return wrapped.data;
            }
            // code 非 0 视为业务错误
            const errMsg =
              (typeof wrapped.msg === 'string' && wrapped.msg) ||
              (typeof wrapped.message === 'string' && wrapped.message) ||
              String(wrapped.code);

            const requestUrl = String(response.config?.url ?? '');
            if (
              isRequestKeyFailedCode(wrapped.code) &&
              !shouldSkipReAuthForKeyFailure(requestUrl)
            ) {
              // 先提示密钥错误，再单飞登出（避免并发风暴重复 toast / 重复 forceLogout）
              callbacks.onError?.(errMsg);
              await this.doReAuthenticate(
                callbacks,
                'Request key failed (1006), redirecting to login...',
              );
              throw Object.assign(new Error(errMsg), {
                code: wrapped.code,
                msg: wrapped.msg,
                message: errMsg,
                response: { data: responseData, status },
                __handledByResponseInterceptor: true,
                __handledByAuthInterceptor: true,
              });
            }

            throw Object.assign(new Error(errMsg), {
              code: wrapped.code,
              msg: wrapped.msg,
              message: errMsg,
              response: { data: responseData, status },
              __handledByResponseInterceptor: true,
            });
          }
          return responseData;
        }

        throw Object.assign({}, responseData, { response });
      },
    });
  }

  /**
   * 401 认证拦截器：单 token 模式下直接 forceLogout / 重新登录
   */
  private useAuthInterceptor(callbacks: RequestClientCallbacks) {
    this.addResponseInterceptor(
      authenticateResponseInterceptor({
        doReAuthenticate: async () => {
          await this.doReAuthenticate(
            callbacks,
            'Token expired, redirecting to login...',
          );
        },
      }),
    );
  }

  /**
   * 统一错误消息拦截器：提取错误文本并回调
   */
  private useErrorMessageInterceptor(callbacks: RequestClientCallbacks) {
    this.addResponseInterceptor(
      errorMessageResponseInterceptor((msg: string) => {
        callbacks.onError?.(msg);
      }, callbacks.getErrorMsg ?? getDefaultErrorMsg),
    );
  }

  /**
   * DELETE请求方法
   */
  public delete<T = never>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>(url, { ...config, method: 'DELETE' });
  }

  /**
   * GET请求方法
   */
  public get<T = never>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>(url, { ...config, method: 'GET' });
  }

  /**
   * POST请求方法
   */
  public post<T = never>(url: string, data?: never, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>(url, { ...config, data, method: 'POST' });
  }

  /**
   * PUT请求方法
   */
  public put<T = never>(url: string, data?: never, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>(url, { ...config, data, method: 'PUT' });
  }

  /**
   * 通用的请求方法
   */
  public async request<T>(url: string, config: AxiosRequestConfig): Promise<T> {
    try {
      const response: AxiosResponse<T> = await this.instance({
        url,
        ...config,
      });
      return response as T;
    } catch (error: unknown) {
      // @ts-expect-error 忽略类型检查
      throw error.response ? error.response.data : error;
    }
  }
}

export { RequestClient };
