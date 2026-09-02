import { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Col, Form, Input, Row, Select, Space, Tag, message } from 'antd';
import { listAgentDefinitionsApi, createAgentRevisionApi, listBindableSkillsApi, publishAgentRevisionApi } from '@/api/rest/agent';
import type { AgentDefinition, BindableSkill, SkillBindingRequest } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';

type Values = { definitionId?: number; systemPrompt: string; remark?: string; skillReleaseIds?: number[]; winners?: number[] };
export default function AgentManagePage() {
  const [form] = Form.useForm<Values>();
  const [agents, setAgents] = useState<AgentDefinition[]>([]);
  const [skills, setSkills] = useState<BindableSkill[]>([]);
  const [saving, setSaving] = useState(false);
  const [draftId, setDraftId] = useState<number | null>(null);
  useEffect(() => {
 void Promise.all([listAgentDefinitionsApi(), listBindableSkillsApi()]).then(([definitions, bindable]) => {
 setAgents(definitions); setSkills(bindable); 
}).catch((error: Error) => message.error(error.message)); 
}, []);
  const selected = Form.useWatch('skillReleaseIds', form) ?? [];
  const names = new Map<number, string>(skills.map((skill) => [skill.skillReleaseId, skill.name]));
  const conflicts = selected.reduce<Record<string, number[]>>((groups, id) => {
 const name = names.get(id); if (name) (groups[name] ??= []).push(id); return groups; 
}, {});
  const buildBindings = (): SkillBindingRequest[] => {
    const winners = new Set(form.getFieldValue('winners') ?? []);
    return selected.map((skillReleaseId) => ({ skillReleaseId, overrideWinner: winners.has(skillReleaseId) }));
  };
  const save = async () => {
    const values = await form.validateFields();
    if (!values.definitionId) return;
    for (const ids of Object.values(conflicts)) {
      if (ids.length > 1 && ids.filter((id) => (values.winners ?? []).includes(id)).length !== 1) {
 message.error(`同名 Skill 必须明确且仅选择一个覆盖胜者：${names.get(ids[0])}`); return; 
}
    }
    setSaving(true);
    try {
 const draft = await createAgentRevisionApi(values.definitionId, { systemPrompt: values.systemPrompt, remark: values.remark, skillBindings: buildBindings() }); setDraftId(draft.id); message.success('已创建 Agent 草稿 Revision；发布后仅新会话会使用这些 Skill。'); 
} catch (error) {
 message.error((error as Error).message); 
} finally {
 setSaving(false); 
}
  };
  const publish = async () => {
 if (!draftId) return; setSaving(true); try {
 await publishAgentRevisionApi(draftId); message.success('Revision 已发布；新会话将固定该 Skill 快照。'); 
} catch (error) {
 message.error((error as Error).message); 
} finally {
 setSaving(false); 
} 
};
  return <ContentContainer scrollable heightMode="auto"><Card extra={draftId && <Tag color="processing">草稿 #{draftId}</Tag>}>
    <Alert type="info" showIcon message="安装不等于绑定。此表单只列出当前用户可绑定的 Release；发布后新会话固定到 Release 的 contentHash。" style={{ marginBottom: 16 }} />
    <Form form={form} layout="vertical" initialValues={{ systemPrompt: '', skillReleaseIds: [], winners: [] }}>
      <Row gutter={16}><Col xs={24} md={12}><Form.Item name="definitionId" label="Agent" rules={[{ required: true, message: '请选择 Agent' }]}><Select options={agents.map((agent) => ({ value: agent.id, label: `${agent.name}${agent.currentPublishedRevisionId ? ` · 已发布 #${agent.currentPublishedRevisionId}` : ''}` }))} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="remark" label="备注"><Input /></Form.Item></Col></Row>
      <Form.Item name="systemPrompt" label="系统提示词" rules={[{ required: true }]}><Input.TextArea rows={6} /></Form.Item>
      <Form.Item name="skillReleaseIds" label="绑定的 Skill Release"><Checkbox.Group style={{ width: '100%' }}><Row>{skills.map((skill) => <Col span={24} key={skill.skillReleaseId}><Checkbox value={skill.skillReleaseId}><Space><span>{skill.name} v{skill.version}</span><Tag>{skill.visibility}</Tag><span style={{ color: '#64748b' }}>{skill.contentHash.slice(0, 12)}</span></Space></Checkbox></Col>)}</Row></Checkbox.Group></Form.Item>
      {Object.entries(conflicts).filter(([, ids]) => ids.length > 1).map(([name, ids]) => <Card size="small" key={name} title={`同名冲突：${name}`} style={{ marginBottom: 12 }}><Form.Item name="winners" noStyle><Checkbox.Group options={ids.map((id) => ({ value: id, label: `${names.get(id)} #${id}（覆盖胜者）` }))} /></Form.Item></Card>)}
      <Space><Button type="primary" loading={saving} onClick={save}>创建草稿 Revision</Button><Button disabled={!draftId || saving} onClick={publish}>发布 Revision</Button></Space>
    </Form>
  </Card></ContentContainer>;
}
