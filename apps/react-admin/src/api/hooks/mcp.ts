import { useMutation, type UseMutationOptions, useQuery, type UseQueryOptions } from '@tanstack/react-query';
import { queryClient } from '@/core';
import {
  approveMcpDraftApi,
  createMcpDraftApi,
  deleteMcpDraftApi,
  getMcpDraftApi,
  listMcpDraftApi,
  rejectMcpDraftApi,
  submitMcpDraftApi,
  updateMcpDraftApi,
  verifyMcpDraftApi,
  withdrawMcpDraftApi,
  listMcpMarketApi,
  listMcpReleaseApi,
} from '@/api/rest/mcp';
import type {
  CreateMcpDraftRequest,
  McpDraft,
  McpDraftQuery,
  McpRelease,
  McpReleaseQuery,
  McpVerifyResult,
  PageResult,
  UpdateMcpDraftRequest,
} from '@/api/rest/types';

export function useListMcpDraft(
  query: McpDraftQuery = {},
  options?: UseQueryOptions<PageResult<McpDraft>, Error>,
) {
  return useQuery({
    queryKey: ['listMcpDraft', query],
    queryFn: () => listMcpDraftApi(query),
    ...options,
  });
}

export function useGetMcpDraft(id: number | null | undefined) {
  return useQuery({
    queryKey: ['getMcpDraft', id],
    queryFn: () => getMcpDraftApi(id as number),
    enabled: typeof id === 'number',
  });
}

export function useCreateMcpDraft(options?: UseMutationOptions<McpDraft, Error, CreateMcpDraftRequest>) {
  return useMutation({ mutationFn: (body) => createMcpDraftApi(body), ...options });
}

export function useUpdateMcpDraft(options?: UseMutationOptions<McpDraft, Error, UpdateMcpDraftRequest>) {
  return useMutation({ mutationFn: (req) => updateMcpDraftApi(req), ...options });
}

export function useDeleteMcpDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => deleteMcpDraftApi(id), ...options });
}

export function useSubmitMcpDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => submitMcpDraftApi(id), ...options });
}

export function useWithdrawMcpDraft(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => withdrawMcpDraftApi(id), ...options });
}

export function useApproveMcpDraft(options?: UseMutationOptions<McpRelease, Error, number>) {
  return useMutation({ mutationFn: (id) => approveMcpDraftApi(id), ...options });
}

export function useRejectMcpDraft(options?: UseMutationOptions<unknown, Error, { id: number; reason?: string }>) {
  return useMutation({ mutationFn: ({ id, reason }) => rejectMcpDraftApi(id, reason), ...options });
}

export function useVerifyMcpDraft(options?: UseMutationOptions<McpVerifyResult, Error, number>) {
  return useMutation({ mutationFn: (id) => verifyMcpDraftApi(id), ...options });
}

export function useListMcpRelease(
  query: McpReleaseQuery = {},
  options?: UseQueryOptions<PageResult<McpRelease>, Error>,
) {
  return useQuery({
    queryKey: ['listMcpRelease', query],
    queryFn: () => listMcpReleaseApi(query),
    ...options,
  });
}

export function useListMcpMarket(options?: UseQueryOptions<McpRelease[], Error>) {
  return useQuery({
    queryKey: ['listMcpMarket'],
    queryFn: () => listMcpMarketApi(),
    ...options,
  });
}

export async function fetchMcpMarket() {
  return queryClient.fetchQuery({ queryKey: ['listMcpMarket'], queryFn: () => listMcpMarketApi(), retry: 0 });
}
