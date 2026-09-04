import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Col,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
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
  updateAgentRevisionApi,
} from '@/api/rest/agent';
import { fetchMcpMarket } from '@/api/hooks/mcp';
import { fetchSkillBindable } from '@/api/hooks/skill';
import type {
  Agent,
  AgentRevision,
  AgentSession,
  McpRelease,
  RevisionMcpBinding,
  RevisionSkillBinding,
  SkillRelease,
} from '@/api/rest/types';
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
        draftForm.setFieldsValue({
          systemPrompt: nextDraft.systemPrompt ?? '',
          modelConfig: nextDraft.modelConfig ?? '',
          permissionPolicy: nextDraft.permissionPolicy ?? '',
          memoryPolicy: nextDraft.memoryPolicy ?? '',
          compressionPolicy: nextDraft.compressionPolicy ?? '',
          remark: nextDraft.remark ?? '',
        });
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
    try {
      await createOrUpdateDraft(values);
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
      title: t('hasSecret'),
      key: 'hasSecret',
      render: (_: unknown, r: RevisionMcpBinding) => (r.hasSecret ? <Tag color="green">✓</Tag> : <Tag>—</Tag>),
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

  const mcpSelectWithSecret = () => (
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
            label: `${s.name} v${s.version} [${s.visibility}]`,
          }))}
        />
        <Input.Password
          style={{ width: 240 }}
          placeholder={t('secretPlaceholder')}
          value={mcpSecretInput}
          onChange={(e) => setMcpSecretInput(e.target.value)}
          autoComplete="new-password"
        />
        <Button
          type="primary"
          disabled={!pendingMcpId}
          onClick={async () => {
            if (!pendingMcpId) return;
            await handleBindMcp(pendingMcpId, mcpSecretInput || undefined);
            setPendingMcpId(null);
            setMcpSecretInput('');
          }}
        >
          {t('bindMcp')}
        </Button>
      </Space>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {t('secretPlaceholder')}
      </Typography.Text>
    </Space>
  );

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
        <Alert type="info" showIcon message={t('draftExists')} style={{ marginBottom: 12 }} />
        <Form.Item name="systemPrompt" label={t('systemPrompt')} rules={[{ required: true, message: '必填' }]}>
          <TextArea rows={5} placeholder={t('systemPromptPlaceholder')} />
        </Form.Item>
        <Row gutter={12}>
          <Col span={12}>
            <Form.Item name="modelConfig" label={t('modelConfig')}>
              <TextArea rows={3} style={{ fontFamily: 'monospace' }} placeholder="{}" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="permissionPolicy" label={t('permissionPolicy')}>
              <TextArea rows={3} style={{ fontFamily: 'monospace' }} placeholder={'{"allowedTools":[]}'} />
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
          width={400}
          onClose={() => setSessionOpen(false)}
          destroyOnClose
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
      width={920}
      destroyOnClose
      extra={<ReloadOutlined onClick={load} />}
    >
      {agent && (
        <>
          <Alert
            type={agent.isEnabled === 1 ? 'success' : 'error'}
            showIcon
            message={`${agent.description || ''} ${agent.isEnabled === 1 ? '● 已启用' : '● 已禁用'}`}
            style={{ marginBottom: 12 }}
          />
          <Tabs items={items} activeKey={tab} onChange={setTab} />
        </>
      )}
    </Drawer>
  );
};

export default AgentDetailDrawer;
