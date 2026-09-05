import { useMutation, type UseMutationOptions, useQuery, type UseQueryOptions } from '@tanstack/react-query';
import { queryClient } from '@/core';
import {
  bindMcpToRevisionApi,
  bindRevisionToSessionApi,
  bindSkillToRevisionApi,
  createAgentApi,
  createAgentRevisionApi,
  createAgentSessionApi,
  deleteAgentApi,
  deleteAgentRevisionApi,
  deleteAgentSessionApi,
  disableAgentApi,
  enableAgentApi,
  getActiveAgentDraftApi,
  getAgentApi,
  listAgentApi,
  listAgentRevisionsApi,
  listAgentSessionsApi,
  listRevisionMcpBindingsApi,
  listRevisionSkillBindingsApi,
  publishAgentRevisionApi,
  rollbackAgentApi,
  unbindMcpFromRevisionApi,
  unbindSkillFromRevisionApi,
  updateAgentApi,
  updateAgentRevisionApi,
} from '@/api/rest/agent';
import type {
  Agent,
  AgentQuery,
  AgentRevision,
  AgentSession,
  BindMcpRequest,
  BindSkillRequest,
  CreateAgentRequest,
  PageResult,
  RevisionMcpBinding,
  RevisionSkillBinding,
  SaveAgentRevisionRequest,
  UpdateAgentRequest,
} from '@/api/rest/types';

export function useListAgent(
  query: AgentQuery = {},
  options?: UseQueryOptions<PageResult<Agent>, Error>,
) {
  return useQuery({
    queryKey: ['listAgent', query],
    queryFn: () => listAgentApi(query),
    ...options,
  });
}

export function useGetAgent(id: number | null | undefined) {
  return useQuery({
    queryKey: ['getAgent', id],
    queryFn: () => getAgentApi(id as number),
    enabled: typeof id === 'number',
  });
}

export function useCreateAgent(options?: UseMutationOptions<Agent, Error, CreateAgentRequest>) {
  return useMutation({ mutationFn: (body) => createAgentApi(body), ...options });
}

export function useUpdateAgent(options?: UseMutationOptions<Agent, Error, UpdateAgentRequest>) {
  return useMutation({ mutationFn: (req) => updateAgentApi(req), ...options });
}

export function useDeleteAgent(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (id) => deleteAgentApi(id), ...options });
}

export function useDisableAgent(options?: UseMutationOptions<Agent, Error, number>) {
  return useMutation({ mutationFn: (id) => disableAgentApi(id), ...options });
}

export function useEnableAgent(options?: UseMutationOptions<Agent, Error, number>) {
  return useMutation({ mutationFn: (id) => enableAgentApi(id), ...options });
}

export function useRollbackAgent(options?: UseMutationOptions<Agent, Error, { id: number; revisionId: number }>) {
  return useMutation({ mutationFn: ({ id, revisionId }) => rollbackAgentApi(id, revisionId), ...options });
}

// ---------- Revision ----------

export function useListAgentRevisions(
  definitionId: number | null | undefined,
  options?: UseQueryOptions<AgentRevision[], Error>,
) {
  return useQuery({
    queryKey: ['listAgentRevisions', definitionId],
    queryFn: () => listAgentRevisionsApi(definitionId as number),
    enabled: typeof definitionId === 'number',
    ...options,
  });
}

export function useActiveAgentDraft(definitionId: number | null | undefined) {
  return useQuery({
    queryKey: ['activeAgentDraft', definitionId],
    queryFn: () => getActiveAgentDraftApi(definitionId as number),
    enabled: typeof definitionId === 'number',
  });
}

export function useCreateAgentRevision(
  options?: UseMutationOptions<AgentRevision, Error, { definitionId: number; body: SaveAgentRevisionRequest }>,
) {
  return useMutation({ mutationFn: ({ definitionId, body }) => createAgentRevisionApi(definitionId, body), ...options });
}

export function useUpdateAgentRevision(
  options?: UseMutationOptions<AgentRevision, Error, { revisionId: number; body: SaveAgentRevisionRequest }>,
) {
  return useMutation({ mutationFn: ({ revisionId, body }) => updateAgentRevisionApi(revisionId, body), ...options });
}

export function usePublishAgentRevision(options?: UseMutationOptions<AgentRevision, Error, number>) {
  return useMutation({ mutationFn: (revisionId) => publishAgentRevisionApi(revisionId), ...options });
}

export function useDeleteAgentRevision(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (revisionId) => deleteAgentRevisionApi(revisionId), ...options });
}

// ---------- Revision bindings ----------

export function useListRevisionSkillBindings(revisionId: number | null | undefined) {
  return useQuery({
    queryKey: ['listRevisionSkillBindings', revisionId],
    queryFn: () => listRevisionSkillBindingsApi(revisionId as number),
    enabled: typeof revisionId === 'number',
  });
}

export function useBindSkillToRevision(
  options?: UseMutationOptions<RevisionSkillBinding, Error, { revisionId: number; body: BindSkillRequest }>,
) {
  return useMutation({
    mutationFn: ({ revisionId, body }) => bindSkillToRevisionApi(revisionId, body),
    ...options,
  });
}

export function useUnbindSkillFromRevision(
  options?: UseMutationOptions<unknown, Error, { revisionId: number; bindingId: number }>,
) {
  return useMutation({
    mutationFn: ({ revisionId, bindingId }) => unbindSkillFromRevisionApi(revisionId, bindingId),
    ...options,
  });
}

export function useListRevisionMcpBindings(revisionId: number | null | undefined) {
  return useQuery({
    queryKey: ['listRevisionMcpBindings', revisionId],
    queryFn: () => listRevisionMcpBindingsApi(revisionId as number),
    enabled: typeof revisionId === 'number',
  });
}

export function useBindMcpToRevision(
  options?: UseMutationOptions<RevisionMcpBinding, Error, { revisionId: number; body: BindMcpRequest }>,
) {
  return useMutation({
    mutationFn: ({ revisionId, body }) => bindMcpToRevisionApi(revisionId, body),
    ...options,
  });
}

export function useUnbindMcpFromRevision(
  options?: UseMutationOptions<unknown, Error, { revisionId: number; bindingId: number }>,
) {
  return useMutation({
    mutationFn: ({ revisionId, bindingId }) => unbindMcpFromRevisionApi(revisionId, bindingId),
    ...options,
  });
}

// ---------- Session ----------

export function useListAgentSessions(
  definitionId: number | null | undefined,
  query: { page?: number; pageSize?: number } = {},
) {
  return useQuery({
    queryKey: ['listAgentSessions', definitionId, query],
    queryFn: () => listAgentSessionsApi(definitionId as number, query),
    enabled: typeof definitionId === 'number',
  });
}

export function useCreateAgentSession(
  options?: UseMutationOptions<AgentSession, Error, { definitionId: number; body: { remark?: string } }>,
) {
  return useMutation({
    mutationFn: ({ definitionId, body }) => createAgentSessionApi(definitionId, body),
    ...options,
  });
}

export function useDeleteAgentSession(options?: UseMutationOptions<unknown, Error, number>) {
  return useMutation({ mutationFn: (sessionId) => deleteAgentSessionApi(sessionId), ...options });
}

export function useBindRevisionToSession(options?: UseMutationOptions<AgentSession, Error, number>) {
  return useMutation({ mutationFn: (sessionId) => bindRevisionToSessionApi(sessionId), ...options });
}

export async function fetchListAgent(query: AgentQuery = {}) {
  return queryClient.fetchQuery({ queryKey: ['listAgent', query], queryFn: () => listAgentApi(query), retry: 0 });
}
