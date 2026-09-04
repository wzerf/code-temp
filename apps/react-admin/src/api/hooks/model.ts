import { useMutation, type UseMutationOptions, useQuery, type UseQueryOptions } from '@tanstack/react-query';
import {
  approveModelDraftApi,
  createModelDraftApi,
  deleteModelDraftApi,
  getModelDraftApi,
  listModelAvailableApi,
  listModelDraftApi,
  listModelReleaseApi,
  rejectModelDraftApi,
  submitModelDraftApi,
  updateModelDraftApi,
  verifyModelDraftApi,
  withdrawModelDraftApi,
} from '@/api/rest/model';
import type {
  CreateModelDraftRequest,
  ModelDraft,
  ModelDraftQuery,
  ModelRelease,
  ModelReleaseQuery,
  ModelVerifyResult,
  PageResult,
  UpdateModelDraftRequest,
} from '@/api/rest/types';

export function useListModelDraft(
  query: ModelDraftQuery = {},
  options?: UseQueryOptions<PageResult<ModelDraft>, Error>,
) {
  return useQuery({
    queryKey: ['listModelDraft', query],
    queryFn: () => listModelDraftApi(query),
    ...options,
  });
}

export function useGetModelDraft(id: number | null | undefined) {
  return useQuery({
    queryKey: ['getModelDraft', id],
    queryFn: () => getModelDraftApi(id as number),
    enabled: typeof id === 'number',
  });
}

export function useCreateModelDraft(options?: UseMutationOptions<ModelDraft, Error, CreateModelDraftRequest>) {
  return useMutation({ mutationFn: (body) => createModelDraftApi(body), ...options });
}

export function useUpdateModelDraft(options?: UseMutationOptions<ModelDraft, Error, UpdateModelDraftRequest>) {
  return useMutation({ mutationFn: (req) => updateModelDraftApi(req), ...options });
}

export function useDeleteModelDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => deleteModelDraftApi(id), ...options });
}

export function useSubmitModelDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => submitModelDraftApi(id), ...options });
}

export function useWithdrawModelDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => withdrawModelDraftApi(id), ...options });
}

export function useApproveModelDraft(options?: UseMutationOptions<ModelRelease, Error, number>) {
  return useMutation({ mutationFn: (id) => approveModelDraftApi(id), ...options });
}

export function useRejectModelDraft(options?: UseMutationOptions<unknown, Error, { id: number; reason?: string }>) {
  return useMutation({ mutationFn: ({ id, reason }) => rejectModelDraftApi(id, reason), ...options });
}

export function useVerifyModelDraft(options?: UseMutationOptions<ModelVerifyResult, Error, number>) {
  return useMutation({ mutationFn: (id) => verifyModelDraftApi(id), ...options });
}

export function useListModelRelease(
  query: ModelReleaseQuery = {},
  options?: UseQueryOptions<PageResult<ModelRelease>, Error>,
) {
  return useQuery({
    queryKey: ['listModelRelease', query],
    queryFn: () => listModelReleaseApi(query),
    ...options,
  });
}

export function useListModelAvailable(options?: UseQueryOptions<ModelRelease[], Error>) {
  return useQuery({
    queryKey: ['listModelAvailable'],
    queryFn: () => listModelAvailableApi(),
    ...options,
  });
}
