import { del, get, post, put } from './request';
import type {
  BatchCreateModelDraftRequest,
  CreateModelDraftRequest,
  ModelDraft,
  ModelDraftQuery,
  ModelRelease,
  ModelReleaseQuery,
  ModelVerifyResult,
  PageResult,
  ProbeModelRequest,
  UpdateModelDraftRequest,
} from './types';

/** 模型草稿分页 */
export function listModelDraftApi(query: ModelDraftQuery = {}) {
  return get<PageResult<ModelDraft>>('/system/model/draft/list', query as Record<string, unknown>);
}

/** 模型草稿全量 */
export function listAllModelDraftApi(params?: Omit<ModelDraftQuery, 'page' | 'pageSize'>) {
  return get<ModelDraft[]>('/system/model/draft/all', (params ?? {}) as Record<string, unknown>);
}

/** 模型草稿详情 */
export function getModelDraftApi(id: number) {
  return get<ModelDraft>(`/system/model/draft/${id}`);
}

/** 创建模型草稿 */
export function createModelDraftApi(body: CreateModelDraftRequest) {
  return post<ModelDraft>('/system/model/draft', body);
}

/** 批量创建模型草稿 */
export function createModelDraftBatchApi(body: BatchCreateModelDraftRequest) {
  return post<ModelDraft[]>('/system/model/draft/batch', body);
}

/** 创建前探测远端模型目录（不落库） */
export function probeModelCatalogApi(body: ProbeModelRequest) {
  return post<ModelVerifyResult>('/system/model/probe', body);
}

/** 更新模型草稿 */
export function updateModelDraftApi({ id, ...patch }: UpdateModelDraftRequest) {
  return put<ModelDraft>(`/system/model/draft/${id}`, patch);
}

/** 删除模型草稿 */
export function deleteModelDraftApi(id: number) {
  return del<never>(`/system/model/draft/${id}`);
}

/** 探测验证 */
export function verifyModelDraftApi(id: number) {
  return post<ModelVerifyResult>(`/system/model/draft/${id}/verify`);
}

/** 提交审核 */
export function submitModelDraftApi(id: number) {
  return post<never>(`/system/model/draft/${id}/submit`);
}

/** 撤回审核 */
export function withdrawModelDraftApi(id: number) {
  return post<never>(`/system/model/draft/${id}/withdraw`);
}

/** 通过/发布 Release */
export function approveModelDraftApi(id: number) {
  return post<ModelRelease>(`/system/model/draft/${id}/approve`);
}

/** 驳回草稿 */
export function rejectModelDraftApi(id: number, reason?: string) {
  return post<never>(`/system/model/draft/${id}/reject`, { reason });
}

export function listModelReleaseApi(query: ModelReleaseQuery = {}) {
  return get<PageResult<ModelRelease>>('/system/model/release/list', query as Record<string, unknown>);
}

export function getModelReleaseApi(id: number) {
  return get<ModelRelease>(`/system/model/release/${id}`);
}

/** 可用模型池 */
export function listModelAvailableApi(ownerUserId?: number) {
  return get<ModelRelease[]>('/system/model/available', ownerUserId ? { ownerUserId } : {});
}

/** 弃用单个 Release */
export function deprecateModelReleaseApi(id: number) {
  return post<never>(`/system/model/release/${id}/deprecate`);
}
