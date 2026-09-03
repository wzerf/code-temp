import { del, get, post, put } from './request';
import type {
  CreateSkillDraftRequest,
  PageResult,
  SkillDraft,
  SkillDraftQuery,
  SkillDraftBundle,
  SkillRelease,
  SkillReleaseQuery,
  UpdateSkillDraftRequest,
} from './types';

/** Skill 草稿分页 */
export function listSkillDraftApi(query: SkillDraftQuery = {}) {
  return get<PageResult<SkillDraft>>('/system/skill/draft/list', query as Record<string, unknown>);
}

/** Skill 草稿全量 */
export function listAllSkillDraftApi(params?: Omit<SkillDraftQuery, 'page' | 'pageSize'>) {
  return get<SkillDraft[]>('/system/skill/draft/all', (params ?? {}) as Record<string, unknown>);
}

/** Skill 草稿详情 */
export function getSkillDraftApi(id: number) {
  return get<SkillDraft>(`/system/skill/draft/${id}`);
}

/** Skill 草稿内容包（SKILL.md + 资源） */
export function getSkillDraftBundleApi(id: number) {
  return get<SkillDraftBundle>(`/system/skill/draft/${id}/resources`);
}

/** 创建 Skill 草稿 */
export function createSkillDraftApi(body: CreateSkillDraftRequest) {
  return post<SkillDraft>('/system/skill/draft', body);
}

/** 更新 Skill 草稿 */
export function updateSkillDraftApi({ id, ...patch }: UpdateSkillDraftRequest) {
  return put<SkillDraft>(`/system/skill/draft/${id}`, patch);
}

/** 删除 Skill 草稿 */
export function deleteSkillDraftApi(id: number) {
  return del<never>(`/system/skill/draft/${id}`);
}

/** 提交审核 */
export function submitSkillDraftApi(id: number) {
  return post<never>(`/system/skill/draft/${id}/submit`);
}

/** 撤回审核 */
export function withdrawSkillDraftApi(id: number) {
  return post<never>(`/system/skill/draft/${id}/withdraw`);
}

/** 通过审核并发布 Release */
export function approveSkillDraftApi(id: number) {
  return post<SkillRelease>(`/system/skill/draft/${id}/approve`);
}

/** 驳回草稿 */
export function rejectSkillDraftApi(id: number, reason?: string) {
  return post<never>(`/system/skill/draft/${id}/reject`, { reason });
}

// ---------- Release / 市场 ----------

export function listSkillReleaseApi(query: SkillReleaseQuery = {}) {
  return get<PageResult<SkillRelease>>('/system/skill/release/list', query as Record<string, unknown>);
}

export function getSkillReleaseApi(id: number) {
  return get<SkillRelease>(`/system/skill/release/${id}`);
}

/** Skill Release 内容包(SKILL.md 全文 + 冻结资源) */
export function getSkillReleaseBundleApi(id: number) {
  return get<SkillDraftBundle>(`/system/skill/release/${id}/resources`);
}

/** Skill 市场列表（按 name 取最新 MARKET PUBLISHED） */
export function listSkillMarketApi() {
  return get<SkillRelease[]>('/system/skill/market');
}

/** 可绑定候选（MARKET 最新） */
export function listSkillBindableApi() {
  return get<SkillRelease[]>('/system/skill/release/bindable');
}

/** 市场下架 */
export function takeDownSkillMarketApi(id: number) {
  return post<never>(`/system/skill/market/${id}/take-down`);
}

/** 弃用单个 Release */
export function deprecateSkillReleaseApi(id: number) {
  return post<never>(`/system/skill/release/${id}/deprecate`);
}
