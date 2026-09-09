import { useEffect, useMemo, useRef, useState } from 'react';
import {Alert, Button, Checkbox, Col, Drawer, Empty, Form, Input, List, Modal, Row, Select, Space, Spin, Table, Tabs, Tag, Typography } from 'antd';
import { message } from '@/core/feedback/message';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import {
  bindMcpToRevisionApi,
  bindRevisionToSessionApi,
  bindSkillToRevisionApi,
  createAgentRevisionApi,
  createAgentSessionApi,
  deleteAgentRevisionApi,
  getActiveAgentDraftApi,
  listAgentRevisionsApi,
  listAgentSessionsApi,
  listRevisionMcpBindingsApi,
  listRevisionSkillBindingsApi,
  publishAgentRevisionApi,
  rollbackAgentApi,
  unbindMcpFromRevisionApi,
  unbindSkillFromRevisionApi,
  updateAgentRevisionApi } from '@/api/rest/agent';
import { probeModelCatalogApi } from '@/api/rest/model';
import { fetchMcpMarket } from '@/api/hooks/mcp';
import { fetchSkillBindable } from '@/api/hooks/skill';
import McpOauthSection from '../../mcp/modules/mcp-oauth-section';
import type {
  Agent,
  AgentRevision,
  AgentSession,
  McpRelease,
  RevisionMcpBinding,
  RevisionSkillBinding,
  SkillRelease } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface Props {
  open: boolean;
  agent: Agent | null;
  onClose: () => void;
  onChanged: () => void;
}

interface DraftValues {
  systemPrompt: string;
  modelConfig?: string;
  permissionPolicy?: string;
  memoryPolicy?: string;
  compressionPolicy?: string;
  imageProvider?: string;
  imageBaseUrl?: string;
  imageModelName?: string;
  imagePlainSecret?: string;
  remark?: string;
}

const { TextArea } = Input;

const AgentDetailDrawer = ({ open, agent, onClose, onChanged }: Props) => {
  const { t } = useTranslation('agent');
  const [tab, setTab] = useState('revisions');
  const [loading, setLoading] = useState(false);
  const [draft, setDraft] = useState<AgentRevision | null>(null);
  const [publishedList, setPublishedList] = useState<AgentRevision[]>([]);
  const [sessions, setSessions] = useState<AgentSession[]>([]);
  const [skillBindings, setSkillBindings] = useState<RevisionSkillBinding[]>([]);
  const [mcpBindings, setMcpBindings] = useState<RevisionMcpBinding[]>([]);
  const [skillOptions, setSkillOptions] = useState<SkillRelease[]>([]);
  const [mcpOptions, setMcpOptions] = useState<McpRelease[]>([]);
  const [draftForm] = Form.useForm<DraftValues>();
  const [sessionForm] = Form.useForm<{ remark?: string }>();
  const [sessionOpen, setSessionOpen] = useState(false);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [pendingMcpId, setPendingMcpId] = useState<number | null>(null);
  const [mcpSecretInput, setMcpSecretInput] = useState('');
  const [pendingSkillId, setPendingSkillId] = useState<number | null>(null);
  const [imageEnabled, setImageEnabled] = useState(false);
  const [probing, setProbing] = useState(false);
  const [imageModelOptions, setImageModelOptions] = useState<string[]>([]);
  const syncingRef = useRef(false);
  const imageToModelConfig = (v: DraftValues) => {
    const baseRaw = v.modelConfig?.trim() ?? '';
    let base: Record<string, unknown> = {};
    if (baseRaw) {
      try {
        const parsed = JSON.parse(baseRaw) as unknown;
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) base = parsed as Record<string, unknown>;
      } catch {
        return v.modelConfig ?? '';
      }
    }
    const hasImageFields = !!(v.imageProvider || v.imageBaseUrl || v.imageModelName || v.imagePlainSecret);
    const hasImageKey = Object.prototype.hasOwnProperty.call(base, 'image');
    if (!hasImageFields && !hasImageKey) return Object.keys(base).length ? JSON.stringify(base, null, 2) : (baseRaw || '');
    const prev = (base.image && typeof base.image === 'object' && !Array.isArray(base.image) ? base.image as Record<string, unknown> : {}) as Record<string, unknown>;
    const next: Record<string, unknown> = { ...prev };
    if (v.imageProvider !== undefined) {
      if (!v.imageProvider) delete next.provider;
      else next.provider = v.imageProvider;
    }
    if (v.imageBaseUrl !== undefined) {
      if (!v.imageBaseUrl) delete next.base_url;
      else next.base_url = v.imageBaseUrl;
    }
    if (v.imageModelName !== undefined) {
      if (!v.imageModelName) delete next.model_name;
      else next.model_name = v.imageModelName;
    }
    if (v.imagePlainSecret !== undefined) {
      if (!v.imagePlainSecret) delete next.encrypted_secret;
      else next.encrypted_secret = v.imagePlainSecret;
    }
    const hasAny = !!(next.provider || next.base_url || next.model_name || next.encrypted_secret);
    if (!hasAny) delete base.image;
    else base.image = next;
    return JSON.stringify(base, null, 2);
  };
  const modelConfigToImage = (raw?: string) => {
    if (!raw || !raw.trim()) return { provider: '', base_url: '', model_name: '', hasSecret: false };
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      const image = parsed?.image as Record<string, unknown> | undefined;
      if (!image || typeof image !== 'object' || Array.isArray(image)) return { provider: '', base_url: '', model_name: '', hasSecret: false };
      return {
        provider: typeof image.provider === 'string' ? image.provider : '',
        base_url: typeof image.base_url === 'string' ? image.base_url : '',
        model_name: typeof image.model_name === 'string' ? image.model_name : '',
        hasSecret: typeof image.encrypted_secret === 'string' ? !!image.encrypted_secret : false,
      };
    } catch {
      return { provider: '', base_url: '', model_name: '', hasSecret: false };
    }
  };

  const agentId = agent?.id;

  const load = async () => {
    if (!agentId) return;
    setLoading(true);
    // 立即清掉上一 agent 的残留草稿/绑定,避免加载间隙渲染陈旧数据
    setDraft(null);
    setSkillBindings([]);
    setMcpBindings([]);
    void ensureOptions();
    try {
      const [revs, activeDraft, sessionRes] = await Promise.all([
        listAgentRevisionsApi(agentId),
        getActiveAgentDraftApi(agentId).catch(() => null),
        listAgentSessionsApi(agentId, { page: 1, pageSize: 50 }),
      ]);
      // 防御:清掉上一 agent 的残留草稿;仅当拿到有效数字 id 才加载绑定
      const nextDraft = activeDraft && typeof activeDraft.id === 'number' ? activeDraft : null;
      setDraft(nextDraft);
      setPublishedList(revs.filter((r) => r.status === 'PUBLISHED'));
      setSessions(sessionRes.items);
      if (nextDraft) {
        await loadBindings(nextDraft.id);
        const image = modelConfigToImage(nextDraft.modelConfig ?? '');
        if (image.model_name) setImageModelOptions((prev) => (prev.includes(image.model_name) ? prev : [...prev, image.model_name]));
        draftForm.setFieldsValue({
          systemPrompt: nextDraft.systemPrompt ?? '',
          modelConfig: nextDraft.modelConfig ?? '',
          permissionPolicy: nextDraft.permissionPolicy ?? '',
          memoryPolicy: nextDraft.memoryPolicy ?? '',
          compressionPolicy: nextDraft.compressionPolicy ?? '',
          imageProvider: image.provider,
          imageBaseUrl: image.base_url,
          imageModelName: image.model_name,
          imagePlainSecret: '',
          remark: nextDraft.remark ?? '',
        });
        const enabled = !!(image.provider || image.base_url || image.model_name || image.hasSecret);
        setImageEnabled(enabled);
        if (!enabled) {
          draftForm.setFieldsValue({ imageProvider: undefined, imageBaseUrl: undefined, imageModelName: undefined, imagePlainSecret: undefined });
        }
      } else {
        setSkillBindings([]);
        setMcpBindings([]);
      }
    } catch (err) {
      message.error(`加载失败：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setLoading(false);
    }
  };

  const loadBindings = async (revisionId: number) => {
    if (typeof revisionId !== 'number' || Number.isNaN(revisionId)) {
      return;
    }
    const [sks, mcps] = await Promise.all([
      listRevisionSkillBindingsApi(revisionId),
      listRevisionMcpBindingsApi(revisionId),
    ]);
    setSkillBindings(sks);
    setMcpBindings(mcps);
  };

  const ensureOptions = async () => {
    try {
      const [skills, mcps] = await Promise.all([fetchSkillBindable(), fetchMcpMarket()]);
      setSkillOptions(skills);
      setMcpOptions(mcps);
    } catch {
      /* 忽略 */
    }
  };

  useEffect(() => {
    if (open && agentId) {
      const timer = setTimeout(() => {
        void load();
      }, 0);
      return () => clearTimeout(timer);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, agentId]);

  const onSaveDraft = async () => {
    if (!agentId || !draft) return;
    const values = await draftForm.validateFields();
    const baseValues: DraftValues = imageEnabled ? values : { ...values, imageProvider: '', imageBaseUrl: '', imageModelName: '', imagePlainSecret: '' };
    const merged = imageEnabled ? imageToModelConfig(baseValues) : (() => {
      const raw = values.modelConfig ?? '';
      if (!raw.trim()) return '';
      try {
        const parsed = JSON.parse(raw) as Record<string, unknown>;
        delete parsed.image;
        return Object.keys(parsed).length ? JSON.stringify(parsed, null, 2) : '';
      } catch {
 return raw; 
}
    })();
    const payload = { ...baseValues, modelConfig: merged, systemPrompt: values.systemPrompt ?? '' } as DraftValues & Record<string, unknown>;
    if (!payload.imagePlainSecret) delete (payload as Record<string, unknown>).imagePlainSecret;
    if (!imageEnabled) {
      delete (payload as Record<string, unknown>).imageProvider;
      delete (payload as Record<string, unknown>).imageBaseUrl;
      delete (payload as Record<string, unknown>).imageModelName;
      delete (payload as Record<string, unknown>).imagePlainSecret;
    } else {
      const hasImage = !!(payload.imageProvider || payload.imageBaseUrl || payload.imageModelName || payload.imagePlainSecret);
      if (!hasImage && !(values.modelConfig && values.modelConfig.includes('"image"'))) {
        delete (payload as Record<string, unknown>).imageProvider;
        delete (payload as Record<string, unknown>).imageBaseUrl;
        delete (payload as Record<string, unknown>).imageModelName;
        delete (payload as Record<string, unknown>).imagePlainSecret;
      }
    }
    try {
      await createOrUpdateDraft(payload as DraftValues);
      message.success(t('updateSuccess'));
      load();
    } catch (err) {
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const createOrUpdateDraft = async (values: DraftValues) => {
    if (!agentId) return;
    if (!draft) {
      await createAgentRevisionApi(agentId, { ...values, systemPrompt: values.systemPrompt ?? '' });
    } else {
      await updateAgentRevisionApi(draft.id, { ...values, systemPrompt: values.systemPrompt ?? '' });
    }
  };
  const handlePublish = () => {
    if (!draft) return;
    Modal.confirm({
      title: t('confirmPublish'),
      okText: t('confirm'),
      cancelText: t('cancel'),
      onOk: async () => {
        try {
          await publishAgentRevisionApi(draft.id);
          message.success(t('publishSuccess'));
          load();
          onChanged();
        } catch (err) {
          message.error(`${t('publishFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
        }
      },
    });
  };

  const handleRollback = (revisionId: number) => {
    if (!agentId) return;
    Modal.confirm({
      title: t('confirmRollback'),
      okText: t('confirm'),
      cancelText: t('cancel'),
      onOk: async () => {
        try {
          await rollbackAgentApi(agentId, revisionId);
          message.success(t('rollbackSuccess'));
          load();
          onChanged();
        } catch (err) {
          message.error(`${t('rollbackFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
        }
      },
    });
  };

  const handleDeleteDraft = async () => {
    if (!draft) return;
    try {
      await deleteAgentRevisionApi(draft.id);
      message.success(t('deleteSuccess'));
      load();
    } catch (err) {
      message.error(`${t('deleteFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handleBindSkill = async (skillReleaseId: number) => {
    if (!draft) return;
    const release = skillOptions.find((s) => s.id === skillReleaseId);
    try {
      await bindSkillToRevisionApi(draft.id, {
        skillReleaseId,
        skillName: release?.name ?? `skill-${skillReleaseId}`,
        overrideWinner: 0,
      });
      message.success(t('updateSuccess'));
      loadBindings(draft.id);
    } catch (err) {
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handleBindMcp = async (mcpReleaseId: number, plainSecret?: string) => {
    if (!draft) return;
    const release = mcpOptions.find((s) => s.id === mcpReleaseId);
    try {
      await bindMcpToRevisionApi(draft.id, {
        mcpReleaseId,
        mcpName: release?.name ?? `mcp-${mcpReleaseId}`,
        plainSecret,
      });
      message.success(t('updateSuccess'));
      loadBindings(draft.id);
    } catch (err) {
      message.error(`${t('updateFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const bindColumns = [
    { title: t('skillName'), dataIndex: 'skillName' },
    {
      title: t('action'),
      key: 'action',
      width: 90,
      render: (_: unknown, r: RevisionSkillBinding) => (
        <a
          style={{ color: '#ff4d4f' }}
          onClick={async () => {
            if (!draft) return;
            await unbindSkillFromRevisionApi(draft.id, r.id);
            loadBindings(draft.id);
          }}
        >
          {t('unbind')}
        </a>
      ),
    },
  ];

  const mcpBindColumns = [
    { title: t('mcpName'), dataIndex: 'mcpName' },
    {
      title: t('authType', { defaultValue: '认证' }),
      key: 'authType',
      width: 110,
      render: (_: unknown, r: RevisionMcpBinding) =>
        r.authType === 'OAUTH' ? <Tag color="purple">OAuth</Tag> : <Tag>{t('staticSecret', { defaultValue: '静态密钥' })}</Tag>,
    },
    {
      title: t('hasSecret'),
      key: 'hasSecret',
      render: (_: unknown, r: RevisionMcpBinding) => (r.hasSecret ? <Tag color="green">✓</Tag> : <Tag>—</Tag>),
    },
    {
      title: t('oauthStatus', { defaultValue: '登录态' }),
      key: 'oauthStatus',
      width: 220,
      render: (_: unknown, r: RevisionMcpBinding) =>
        r.authType === 'OAUTH' ? (
          <McpOauthSection releaseId={r.mcpReleaseId} onLoggedIn={() => draft && loadBindings(draft.id)} />
        ) : (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            —
          </Typography.Text>
        ),
    },
    {
      title: t('action'),
      key: 'action',
      width: 90,
      render: (_: unknown, r: RevisionMcpBinding) => (
        <a
          style={{ color: '#ff4d4f' }}
          onClick={async () => {
            if (!draft) return;
            await unbindMcpFromRevisionApi(draft.id, r.id);
            loadBindings(draft.id);
          }}
        >
          {t('unbind')}
        </a>
      ),
    },
  ];

  const mcpSelectWithSecret = () => {
    const selected = mcpOptions.find((s) => s.id === pendingMcpId) ?? null;
    const selectedIsOauth = selected?.authType === 'OAUTH';
    return (
    <Space direction="vertical" style={{ width: '100%' }}>
      <Space>
        <Select
          style={{ width: 240 }}
          placeholder="选择 MCP Release"
          value={pendingMcpId ?? undefined}
          onChange={(v) => {
            setPendingMcpId(Number(v));
            setMcpSecretInput('');
          }}
          options={mcpOptions.map((s) => ({
            value: s.id,
            label: `${s.name} v${s.version} [${s.visibility}]${s.authType === 'OAUTH' ? ' [OAuth]' : ''}`,
          }))}
        />
        {selectedIsOauth ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {t('oauthBindHint', { defaultValue: 'OAuth 类型：绑定后在上方列表完成登录，无需填密钥' })}
          </Typography.Text>
        ) : (
          <Input.Password
            style={{ width: 240 }}
            placeholder={t('secretPlaceholder')}
            value={mcpSecretInput}
            onChange={(e) => setMcpSecretInput(e.target.value)}
            autoComplete="new-password"
          />
        )}
        <Button
          type="primary"
          disabled={!pendingMcpId}
          onClick={async () => {
            if (!pendingMcpId) return;
            await handleBindMcp(pendingMcpId, selectedIsOauth ? undefined : mcpSecretInput || undefined);
            setPendingMcpId(null);
            setMcpSecretInput('');
          }}
        >
          {t('bindMcp')}
        </Button>
      </Space>
      {selectedIsOauth && pendingMcpId ? (
        <McpOauthSection releaseId={pendingMcpId} onLoggedIn={() => draft && loadBindings(draft.id)} />
      ) : (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {selected === null
            ? t('marketSecretHint')
            : selected.visibility === 'MARKET'
              ? t('marketSecretHint')
              : t('privateSecretHint')}
        </Typography.Text>
      )}
    </Space>
    );
  };

  const renderDraftEditor = () => {
    if (!draft) {
      return (
        <Empty description={t('noDraft')}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={async () => {
              await createOrUpdateDraft({ systemPrompt: '' });
              load();
            }}
          >
            {t('createDraft')}
          </Button>
        </Empty>
      );
    }
    return (
      <Form form={draftForm} layout="vertical" preserve={false}>
        <Alert type="info" showIcon title={t('draftExists')} style={{ marginBottom: 12 }} />
        <Form.Item name="systemPrompt" label={t('systemPrompt')} rules={[{ required: true, message: '必填' }]}>
          <TextArea rows={5} placeholder={t('systemPromptPlaceholder')} />
        </Form.Item>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item
              name="modelConfig"
              label={t('modelConfig')}
              extra={t('modelConfigExtra', { defaultValue: '含默认模型与生图 image 配置，勾选后同步更新此处 JSON' })}
            >
              <TextArea
                rows={4}
                style={{ fontFamily: 'monospace' }}
                placeholder={'{"default_model_release_id": 1, "image": {"provider":"openai-compatible","base_url":"https://...","model_name":"...","encrypted_secret":"..."}}'}
                onChange={(e) => {
                  if (syncingRef.current) return;
                  syncingRef.current = true;
                  try {
                    const image = modelConfigToImage(e.target.value);
                    draftForm.setFieldsValue({
                      imageProvider: image.provider,
                      imageBaseUrl: image.base_url,
                      imageModelName: image.model_name,
                    });
                    const enabled = !!(image.provider || image.base_url || image.model_name || image.hasSecret);
                    setImageEnabled(enabled);
                  } finally {
                    syncingRef.current = false;
                  }
                }}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="permissionPolicy" label={t('permissionPolicy')}>
              <TextArea rows={4} style={{ fontFamily: 'monospace' }} placeholder={'{"allowedTools":[]}'} />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="memoryPolicy" label={t('memoryPolicy')}>
              <TextArea rows={2} style={{ fontFamily: 'monospace' }} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="compressionPolicy" label={t('compressionPolicy')}>
              <TextArea rows={2} style={{ fontFamily: 'monospace' }} />
            </Form.Item>
          </Col>
        </Row>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
          {t('policyHint')}
        </Typography.Paragraph>
        <Form.Item style={{ marginBottom: 8 }}>
          <Checkbox
            checked={imageEnabled}
            onChange={(e) => {
              const checked = e.target.checked;
              setImageEnabled(checked);
              if (!checked) {
                const raw = (draftForm.getFieldValue('modelConfig') as string) ?? '';
                if (raw.trim()) {
                  try {
                    const parsed = JSON.parse(raw) as Record<string, unknown>;
                    if (parsed.image) {
                      delete parsed.image;
                      syncingRef.current = true;
                      draftForm.setFieldsValue({ modelConfig: Object.keys(parsed).length ? JSON.stringify(parsed, null, 2) : '' });
                      syncingRef.current = false;
                    }
                  } catch { /* 忽略 JSON 错误，保存时后端会校验 */ }
                }
                draftForm.setFieldsValue({ imageProvider: undefined, imageBaseUrl: undefined, imageModelName: undefined, imagePlainSecret: undefined });
              }
            }}
          >
            {t('imageModel', { defaultValue: '生图模型' })}
          </Checkbox>
          <Typography.Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
            {t('imageModelHint', { defaultValue: '勾选后配置生图 BaseUrl 与密钥，保存时同步写入模型配置 JSON 的 image 字段。' })}
          </Typography.Text>
        </Form.Item>
        {imageEnabled && (
          <>
            <Row gutter={12}>
              <Col span={12}>
                <Form.Item name="imageBaseUrl" label={t('imageBaseUrl', { defaultValue: 'BaseUrl（HTTPS）' })}>
                  <Input
                    placeholder="https://api.example.com/v1"
                    onChange={() => {
                      if (syncingRef.current) return;
                      syncingRef.current = true;
                      try {
                        const v = draftForm.getFieldsValue() as DraftValues;
                        draftForm.setFieldsValue({ modelConfig: imageToModelConfig(v) });
                      } finally {
                        syncingRef.current = false;
                      }
                    }}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item name="imagePlainSecret" label={t('imageSecret', { defaultValue: '密钥' })}>
                  <Input.Password
                    placeholder={t('secretPlaceholder')}
                    autoComplete="new-password"
                    onChange={() => {
                      if (syncingRef.current) return;
                      syncingRef.current = true;
                      try {
                        const v = draftForm.getFieldsValue() as DraftValues;
                        draftForm.setFieldsValue({ modelConfig: imageToModelConfig(v) });
                      } finally {
                        syncingRef.current = false;
                      }
                    }}
                  />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={12}>
              <Col span={16}>
                <Form.Item name="imageModelName" label={t('imageModelName', { defaultValue: 'ModelName' })}>
                  <Select
                    showSearch
                    allowClear
                    placeholder={t('selectModelName', { defaultValue: '探测后选择' })}
                    options={imageModelOptions.map((m) => ({ value: m, label: m }))}
                    filterOption={(input, option) => (option?.label as string).toLowerCase().includes(input.toLowerCase())}
                    onChange={() => {
                      if (syncingRef.current) return;
                      syncingRef.current = true;
                      try {
                        const v = draftForm.getFieldsValue() as DraftValues;
                        draftForm.setFieldsValue({ modelConfig: imageToModelConfig(v) });
                      } finally {
                        syncingRef.current = false;
                      }
                    }}
                  />
                </Form.Item>
              </Col>
              <Col span={8} style={{ display: 'flex', alignItems: 'flex-end', paddingBottom: 24 }}>
                <Button
                  loading={probing}
                  onClick={async () => {
                    const baseUrl = (draftForm.getFieldValue('imageBaseUrl') as string) ?? '';
                    const plainSecret = (draftForm.getFieldValue('imagePlainSecret') as string) ?? '';
                    if (!baseUrl) {
                      message.error(t('probeNeedProviderAndUrl', { defaultValue: '请先填写 BaseUrl' }));
                      return;
                    }
                    if (!baseUrl.startsWith('https://')) {
                      message.error(t('probeNeedHttps', { defaultValue: 'BaseUrl 必须为 https 地址' }));
                      return;
                    }
                    const inferredProvider = 'openai-compatible';
                    draftForm.setFieldsValue({ imageProvider: inferredProvider });
                    syncingRef.current = true;
                    try {
                      const v = draftForm.getFieldsValue() as DraftValues;
                      draftForm.setFieldsValue({ modelConfig: imageToModelConfig({ ...v, imageProvider: inferredProvider }) });
                    } finally {
                      syncingRef.current = false;
                    }
                    setProbing(true);
                    try {
                      const res = await probeModelCatalogApi({ provider: 'openai-compatible', baseUrl, plainSecret: plainSecret || undefined });
                      const ids = res.remoteModelIds ?? [];
                      setImageModelOptions(ids);
                      if (ids.length === 0) message.warning(t('probeEmpty', { defaultValue: '探测成功但远端目录为空' }));
                      else message.success(t('probeCatalogSuccess', { count: ids.length, defaultValue: `探测成功，共 ${ids.length} 个模型` }));
                    } catch (err) {
                      message.error(`${t('verifyFailed', { defaultValue: '探测失败' })}：${getApiErrorMessage(err, t('unknownError'))}`);
                    } finally {
                      setProbing(false);
                    }
                  }}
                >
                  {t('probeCatalog', { defaultValue: '探测模型' })}
                </Button>
              </Col>
            </Row>
            <Form.Item name="imageProvider" hidden>
              <Input />
            </Form.Item>
          </>
        )}
        <Space>
          <Button type="primary" onClick={onSaveDraft}>
            {t('save')}
          </Button>
          <Button onClick={handleDeleteDraft} danger>
            {t('delete')}
          </Button>
        </Space>
      </Form>
    );
  };

  const renderBindings = () => {
    if (!draft) {
      return <Empty description={t('noDraft')} />;
    }
    return (
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Typography.Title level={5}>{t('skillBindings')}</Typography.Title>
        <Table<RevisionSkillBinding>
          rowKey="id"
          size="small"
          columns={bindColumns as never}
          dataSource={skillBindings}
          pagination={false}
        />
        <Space>
          <Select
            showSearch
            style={{ width: 280 }}
            placeholder="选择 Skill Release"
            optionFilterProp="label"
            value={pendingSkillId ?? undefined}
            onChange={(v) => setPendingSkillId(Number(v))}
            options={skillOptions.map((s) => ({
              value: s.id,
              label: `${s.name} v${s.version}`,
            }))}
          />
          <Button
            type="primary"
            disabled={!pendingSkillId}
            onClick={async () => {
              if (!pendingSkillId) return;
              await handleBindSkill(pendingSkillId);
              setPendingSkillId(null);
            }}
          >
            {t('bindSkill')}
          </Button>
        </Space>
        <Typography.Title level={5} style={{ marginTop: 8 }}>
          {t('mcpBindings')}
        </Typography.Title>
        <Table<RevisionMcpBinding>
          rowKey="id"
          size="small"
          columns={mcpBindColumns as never}
          dataSource={mcpBindings}
          pagination={false}
        />
        <Space>{mcpSelectWithSecret()}</Space>
      </Space>
    );
  };

  const renderSessions = () => {
    const createSession = async () => {
      if (!agentId) return;
      setSessionLoading(true);
      try {
        const values = await sessionForm.validateFields();
        await createAgentSessionApi(agentId, { remark: values.remark ?? '' });
        message.success(t('createSuccess'));
        setSessionOpen(false);
        sessionForm.resetFields();
        load();
      } catch (err) {
        message.error(`${t('createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
      } finally {
        setSessionLoading(false);
      }
    };
    return (
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setSessionOpen(true)}>
            {t('createSession')}
          </Button>
        </Space>
        <Table<AgentSession>
          rowKey="id"
          size="small"
          columns={[
            { title: t('id'), dataIndex: 'id', width: 80 },
            { title: t('session'), dataIndex: 'id', width: 120 },
            { title: t('revision'), dataIndex: 'agentRevisionId', width: 120 },
            { title: '状态', dataIndex: 'status', width: 100 },
            {
              title: t('action'),
              key: 'action',
              width: 140,
              render: (_: unknown, s: AgentSession) => (
                <a
                  onClick={async () => {
                    try {
                      await bindRevisionToSessionApi(s.id);
                      message.success(t('bindRevision') + ' OK');
                      load();
                    } catch (err) {
                      message.error(getApiErrorMessage(err, t('unknownError')));
                    }
                  }}
                >
                  {t('bindRevision')}
                </a>
              ),
            },
          ]}
          dataSource={sessions}
          pagination={false}
        />
        <Drawer
          title={t('createSession')}
          open={sessionOpen}
          size={400}
          onClose={() => setSessionOpen(false)}
          destroyOnHidden
          footer={
            <Space style={{ float: 'right' }}>
              <Button onClick={() => setSessionOpen(false)}>{t('cancel')}</Button>
              <Button type="primary" loading={sessionLoading} onClick={createSession}>
                {t('save')}
              </Button>
            </Space>
          }
        >
          <Form form={sessionForm} layout="vertical">
            <Form.Item name="remark" label={t('sessionRemark')}>
              <Input.TextArea rows={2} />
            </Form.Item>
          </Form>
        </Drawer>
      </Space>
    );
  };

  const revisionHistory = useMemo(
    () =>
      publishedList.map((r) => ({
        id: r.id,
        isCurrent: agent?.currentPublishedRevisionId === r.id,
      })),
    [publishedList, agent],
  );

  const items = [
    {
      key: 'revisions',
      label: t('draftRevision') + ' / ' + t('publish'),
      children: (
        <Spin spinning={loading}>
          {renderDraftEditor()}
          <div style={{ marginTop: 16 }}>
            <Typography.Title level={5}>{t('revisions')}</Typography.Title>
            <List
              size="small"
              dataSource={revisionHistory}
              renderItem={(item) => (
                <List.Item
                  actions={[
                    item.isCurrent ? (
                      <Tag color="green">当前</Tag>
                    ) : (
                      <a onClick={() => handleRollback(item.id)}>{t('rollback')}</a>
                    ),
                  ]}
                >
                  <Space>
                    <Typography.Text code>#{item.id}</Typography.Text>
                    <Tag color="blue">PUBLISHED</Tag>
                    <Button type="link" size="small" onClick={() => handleRollback(item.id)}>
                      {t('rollback')}
                    </Button>
                  </Space>
                </List.Item>
              )}
            />
          </div>
          {draft && (
            <Space style={{ marginTop: 12 }}>
              <Button type="primary" onClick={handlePublish} disabled={!draft}>
                {t('publish')}
              </Button>
            </Space>
          )}
        </Spin>
      ),
    },
    {
      key: 'bindings',
      label: t('sectionBindings'),
      children: <Spin spinning={loading}>{renderBindings()}</Spin>,
    },
    {
      key: 'sessions',
      label: t('sessions'),
      children: <Spin spinning={loading}>{renderSessions()}</Spin>,
    },
  ];

  return (
    <Drawer
      title={agent ? `${agent.name}（#${agent.id}）` : ''}
      open={open}
      onClose={onClose}
      size={920}
      destroyOnHidden
      extra={<ReloadOutlined onClick={load} />}
    >
      {agent && (
        <>
          <Alert
            type={agent.isEnabled === 1 ? 'success' : 'error'}
            showIcon
            title={`${agent.description || ''} ${agent.isEnabled === 1 ? '● 已启用' : '● 已禁用'}`}
            style={{ marginBottom: 12 }}
          />
          <Tabs items={items} activeKey={tab} onChange={setTab} />
        </>
      )}
    </Drawer>
  );
};

export default AgentDetailDrawer;
