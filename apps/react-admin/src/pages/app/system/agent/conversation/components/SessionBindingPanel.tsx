import { useCallback, useEffect, useState } from 'react';
import { Button, Drawer, Empty, Form, Input, List, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import {
  bindMcpToSessionApi,
  bindSkillToSessionApi,
  listSessionMcpBindingsApi,
  listSessionSkillBindingsApi,
  unbindMcpFromSessionApi,
  unbindSkillFromSessionApi,
} from '@/api/rest/agent';
import { listMcpBindableApi } from '@/api/rest/mcp';
import { listSkillBindableApi } from '@/api/rest/skill';
import type { McpRelease, SessionMcpBinding, SessionSkillBinding, SkillRelease } from '@/api/rest/types';
import { getApiErrorMessage } from '../../../blacklist/modules/error-message';

interface Props {
  sessionId: number | null;
  open: boolean;
  onClose: () => void;
  onChanged: () => void;
}

const { Text } = Typography;

/**
 * SessionBindingPanel：会话级装配（运行面对话内临时绑定 Skill/MCP）。
 * 装配真相在服务端；本面板只负责选择 Release + 增删会话绑定。
 */
export default function SessionBindingPanel({ sessionId, open, onClose, onChanged }: Props) {
  const { t } = useTranslation('agent-conversation');
  const [skillBindings, setSkillBindings] = useState<SessionSkillBinding[]>([]);
  const [mcpBindings, setMcpBindings] = useState<SessionMcpBinding[]>([]);
  const [skillOptions, setSkillOptions] = useState<SkillRelease[]>([]);
  const [mcpOptions, setMcpOptions] = useState<McpRelease[]>([]);
  const [loading, setLoading] = useState(false);
  const [pendingSkillId, setPendingSkillId] = useState<number | null>(null);
  const [pendingMcpId, setPendingMcpId] = useState<number | null>(null);
  const [mcpSecret, setMcpSecret] = useState('');
  const [bindSkillForm] = Form.useForm();

  const load = useCallback(async () => {
    if (!sessionId) return;
    setLoading(true);
    try {
      const [skills, mcps, sk, mc] = await Promise.all([
        listSessionSkillBindingsApi(sessionId),
        listSessionMcpBindingsApi(sessionId),
        listSkillBindableApi(),
        listMcpBindableApi(),
      ]);
      setSkillBindings(skills ?? []);
      setMcpBindings(mcps ?? []);
      setSkillOptions(sk ?? []);
      setMcpOptions(mc ?? []);
    } catch (err) {
      message.error(getApiErrorMessage(err, t('loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [sessionId, t]);

  useEffect(() => {
    if (open && sessionId) {
      // setTimeout 包裹：load() 内部首个 setState 在 effect 里属同步调用
      const timer = window.setTimeout(() => {
        void load();
      }, 0);
      return () => window.clearTimeout(timer);
    }
  }, [open, sessionId, load]);

  const bindSkill = async () => {
    if (!sessionId || !pendingSkillId) return;
    const rel = skillOptions.find((s) => s.id === pendingSkillId);
    if (!rel) return;
    try {
      await bindSkillToSessionApi(sessionId, {
        skillReleaseId: rel.id,
        skillName: rel.name,
        contentHash: rel.contentHash,
      });
      message.success(t('bindSuccess'));
      setPendingSkillId(null);
      bindSkillForm.resetFields();
      await load();
      onChanged();
    } catch (err) {
      message.error(getApiErrorMessage(err, t('bindFailed')));
    }
  };

  const bindMcp = async () => {
    if (!sessionId || !pendingMcpId) return;
    const rel = mcpOptions.find((m) => m.id === pendingMcpId);
    if (!rel) return;
    try {
      await bindMcpToSessionApi(sessionId, {
        mcpReleaseId: rel.id,
        mcpName: rel.name,
        // MARKET MCP 无密钥时可不配；此处允许补配（仅本次提交传输，不缓存明文）
        plainSecret: mcpSecret || undefined,
      });
      message.success(t('bindSuccess'));
      setPendingMcpId(null);
      setMcpSecret('');
      await load();
      onChanged();
    } catch (err) {
      message.error(getApiErrorMessage(err, t('bindFailed')));
    }
  };

  const unbindSkill = async (binding: SessionSkillBinding) => {
    try {
      await unbindSkillFromSessionApi(binding.sessionId, binding.id);
      await load();
      onChanged();
    } catch (err) {
      message.error(getApiErrorMessage(err, t('unbindFailed')));
    }
  };

  const unbindMcp = async (binding: SessionMcpBinding) => {
    try {
      await unbindMcpFromSessionApi(binding.sessionId, binding.id);
      await load();
      onChanged();
    } catch (err) {
      message.error(getApiErrorMessage(err, t('unbindFailed')));
    }
  };

  const toOptions = (rows: Array<SkillRelease | McpRelease>) =>
    rows.map((r) => ({ value: r.id, label: `${r.name} v${r.version}${r.visibility === 'PRIVATE' ? ' (私有)' : ''}` }));

  return (
    <Drawer
      title={t('sessionBinding')}
      open={open}
      onClose={onClose}
      size={520}
      destroyOnHidden
    >
      <Spin spinning={loading}>
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          {/* Skill 绑定 */}
          <div>
            <Typography.Title level={5}>{t('skillBindings')}</Typography.Title>
            {skillBindings.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noSkillBindings')} />
            ) : (
              <List
                size="small"
                dataSource={skillBindings}
                renderItem={(b) => (
                  <List.Item
                    actions={[
                      <Button key="del" type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => unbindSkill(b)} />,
                    ]}
                  >
                    <Space size={8}>
                      <Text>{b.skillName}</Text>
                      <Tag>#{b.skillReleaseId}</Tag>
                    </Space>
                  </List.Item>
                )}
              />
            )}
            <Space.Compact style={{ width: '100%', marginTop: 8 }}>
              <Select<number>
                showSearch
                optionFilterProp="label"
                placeholder={t('selectSkill')}
                style={{ flex: 1 }}
                value={pendingSkillId ?? undefined}
                onChange={(v) => setPendingSkillId(v)}
                options={toOptions(skillOptions)}
              />
              <Button type="primary" icon={<PlusOutlined />} disabled={!pendingSkillId} onClick={() => void bindSkill()}>
                {t('bind')}
              </Button>
            </Space.Compact>
          </div>

          {/* MCP 绑定 */}
          <div>
            <Typography.Title level={5}>{t('mcpBindings')}</Typography.Title>
            {mcpBindings.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('noMcpBindings')} />
            ) : (
              <List
                size="small"
                dataSource={mcpBindings}
                renderItem={(b) => (
                  <List.Item
                    actions={[
                      <Button key="del" type="text" danger size="small" icon={<DeleteOutlined />} onClick={() => unbindMcp(b)} />,
                    ]}
                  >
                    <Space size={8}>
                      <Text>{b.mcpName}</Text>
                      {b.hasSecret ? <Tag color="green">{t('hasSecret')}</Tag> : <Tag>{t('noSecret')}</Tag>}
                    </Space>
                  </List.Item>
                )}
              />
            )}
            <Space.Compact style={{ width: '100%', marginTop: 8 }}>
              <Select<number>
                showSearch
                optionFilterProp="label"
                placeholder={t('selectMcp')}
                style={{ flex: 1 }}
                value={pendingMcpId ?? undefined}
                onChange={(v) => setPendingMcpId(v)}
                options={toOptions(mcpOptions)}
              />
              <Button type="primary" icon={<PlusOutlined />} disabled={!pendingMcpId} onClick={() => void bindMcp()}>
                {t('bind')}
              </Button>
            </Space.Compact>
            <Form form={bindSkillForm} layout="vertical" style={{ marginTop: 8 }}>
              <Form.Item label={t('mcpSecretLabel')} style={{ marginBottom: 0 }}>
                <Input.Password
                  placeholder={t('mcpSecretPlaceholder')}
                  value={mcpSecret}
                  onChange={(e) => setMcpSecret(e.target.value)}
                  autoComplete="off"
                />
              </Form.Item>
            </Form>
          </div>
        </Space>
      </Spin>
    </Drawer>
  );
}
