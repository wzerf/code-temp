import { useMutation, type UseMutationOptions, useQuery, type UseQueryOptions } from '@tanstack/react-query';
import { queryClient } from '@/core';
import {
  approveSkillDraftApi,
  createSkillDraftApi,
  deleteSkillDraftApi,
  getSkillDraftApi,
  listSkillDraftApi,
  rejectSkillDraftApi,
  submitSkillDraftApi,
  updateSkillDraftApi,
  withdrawSkillDraftApi,
  listSkillMarketApi,
  listSkillReleaseApi,
  listSkillBindableApi,
} from '@/api/rest/skill';
import type {
  CreateSkillDraftRequest,
  PageResult,
  SkillDraft,
  SkillDraftQuery,
  SkillRelease,
  SkillReleaseQuery,
  UpdateSkillDraftRequest,
} from '@/api/rest/types';

export function useListSkillDraft(
  query: SkillDraftQuery = {},
  options?: UseQueryOptions<PageResult<SkillDraft>, Error>,
) {
  return useQuery({
    queryKey: ['listSkillDraft', query],
    queryFn: () => listSkillDraftApi(query),
    ...options,
  });
}

export function useGetSkillDraft(id: number | null | undefined) {
  return useQuery({
    queryKey: ['getSkillDraft', id],
    queryFn: () => getSkillDraftApi(id as number),
    enabled: typeof id === 'number',
  });
}

export function useCreateSkillDraft(options?: UseMutationOptions<SkillDraft, Error, CreateSkillDraftRequest>) {
  return useMutation({ mutationFn: (body) => createSkillDraftApi(body), ...options });
}

export function useUpdateSkillDraft(options?: UseMutationOptions<SkillDraft, Error, UpdateSkillDraftRequest>) {
  return useMutation({ mutationFn: (req) => updateSkillDraftApi(req), ...options });
}

export function useDeleteSkillDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => deleteSkillDraftApi(id), ...options });
}

export function useSubmitSkillDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => submitSkillDraftApi(id), ...options });
}

export function useWithdrawSkillDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => withdrawSkillDraftApi(id), ...options });
}

export function useApproveSkillDraft(options?: UseMutationOptions<SkillRelease, Error, number>) {
  return useMutation({ mutationFn: (id) => approveSkillDraftApi(id), ...options });
}

export function useRejectSkillDraft(
  options?: UseMutationOptions<unknown, Error, { id: number; reason?: string }>,
) {
  return useMutation({ mutationFn: ({ id, reason }) => rejectSkillDraftApi(id, reason), ...options });
}

export function useListSkillRelease(
  query: SkillReleaseQuery = {},
  options?: UseQueryOptions<PageResult<SkillRelease>, Error>,
) {
  return useQuery({
    queryKey: ['listSkillRelease', query],
    queryFn: () => listSkillReleaseApi(query),
    ...options,
  });
}

export function useListSkillMarket(options?: UseQueryOptions<SkillRelease[], Error>) {
  return useQuery({
    queryKey: ['listSkillMarket'],
    queryFn: () => listSkillMarketApi(),
    ...options,
  });
}

export async function fetchSkillMarket() {
  return queryClient.fetchQuery({ queryKey: ['listSkillMarket'], queryFn: () => listSkillMarketApi(), retry: 0 });
}

export async function fetchSkillBindable() {
  return queryClient.fetchQuery({ queryKey: ['listSkillBindable'], queryFn: () => listSkillBindableApi(), retry: 0 });
}
