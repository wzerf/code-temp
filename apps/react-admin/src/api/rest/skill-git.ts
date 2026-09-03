import { del, get, post, put } from './request';
import type {
  CreateGitSourceRequest,
  GitPreviewResult,
  GitSource,
  GitSyncResult,
  UpdateGitSourceRequest,
} from './types';

/** Git 来源列表 */
export function listGitSourceApi(params?: { scope?: string; ownerUserId?: number }) {
  return get<GitSource[]>('/system/skill/git-source/list', (params ?? {}) as Record<string, unknown>);
}

/** Git 来源详情 */
export function getGitSourceApi(id: number) {
  return get<GitSource>(`/system/skill/git-source/${id}`);
}

/** 创建 Git 来源 */
export function createGitSourceApi(body: CreateGitSourceRequest) {
  return post<GitSource>('/system/skill/git-source', body);
}

/** 更新 Git 来源 */
export function updateGitSourceApi({ id, ...patch }: UpdateGitSourceRequest) {
  return put<GitSource>(`/system/skill/git-source/${id}`, patch);
}

/** 软删 Git 来源 */
export function deleteGitSourceApi(id: number) {
  return del<never>(`/system/skill/git-source/${id}`);
}

/** 预览(解析 ref + 扫描包,不写草稿) */
export function previewGitSourceApi(id: number) {
  return post<GitPreviewResult>(`/system/skill/git-source/${id}/preview`);
}

/** 同步(expectedCommitSha 校验) */
export function syncGitSourceApi(id: number, commitSha: string) {
  return post<GitSyncResult>(`/system/skill/git-source/${id}/sync`, { commitSha });
}
