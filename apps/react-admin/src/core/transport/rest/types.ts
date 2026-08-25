import type {
  AxiosError,
  AxiosResponse,
  CreateAxiosDefaults,
  InternalAxiosRequestConfig,
} from 'axios';

type RequestResponse<T = unknown> = AxiosResponse<T>;

type RequestContentType =
  | 'application/json;charset=utf-8'
  | 'application/octet-stream;charset=utf-8'
  | 'application/x-www-form-urlencoded;charset=utf-8'
  | 'multipart/form-data;charset=utf-8';

type RequestClientOptions = CreateAxiosDefaults;

interface RequestInterceptorConfig<T = unknown> {
  fulfilled?: (
    config: InternalAxiosRequestConfig<T>,
  ) => InternalAxiosRequestConfig<T> | Promise<InternalAxiosRequestConfig<T>>;
  rejected?: (error: AxiosError<T>) => AxiosError<T> | Promise<AxiosError<T>>;
}

interface ResponseInterceptorConfig<T = unknown> {
  fulfilled?: (response: AxiosResponse<T>) => AxiosResponse<T> | Promise<AxiosResponse<T>>;
  rejected?: (error: AxiosError<T>) => AxiosError<T> | Promise<AxiosError<T>>;
}

type MakeErrorMessageFn = (message: string, error: AxiosError) => void;

interface HttpResponse {
  code: number;
  /** 与 java-admin Result.msg 对齐 */
  msg?: string;
  /** 兼容旧 mock / 部分错误体 */
  message?: string;
  reason?: string;
  data?: unknown;
  metadata?: object;
}

/**
 * 请求客户端回调接口
 * 由应用层（bootstrap）注入具体实现，基础设施层不感知业务逻辑
 */
interface RequestClientCallbacks {
  /** 获取当前 access token */
  getToken: () => string | null;
  /** 获取当前语言标识（如 zh-CN、en-US） */
  getLocale?: () => string;
  /** Token / 会话钥失效时的处理（登出 + 跳转登录页）；401 与业务码 1006 触发 */
  onReAuthenticate: (redirect?: boolean) => Promise<void>;
  /** 错误消息处理（弹窗、toast 等） */
  onError?: (message: string) => void;
  /** 从错误对象中提取用户友好的错误消息（可接入 i18n） */
  getErrorMsg?: (error: unknown) => string;
}

export type {
  RequestClientCallbacks,
  HttpResponse,
  MakeErrorMessageFn,
  RequestClientOptions,
  RequestContentType,
  RequestInterceptorConfig,
  RequestResponse,
  ResponseInterceptorConfig,
};