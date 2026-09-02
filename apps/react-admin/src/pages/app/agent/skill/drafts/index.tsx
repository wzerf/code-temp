import { useMemo, useRef, useState } from 'react';
import { Button, Drawer, Form, Input, Popconfirm, Select, Space, Tag, message } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined } from '@ant-design/icons';
import {
  approveSkillDraftApi,
  createSkillDraftApi,
  listSkillDraftsApi,
  rejectSkillDraftApi,
  submitSkillDraftApi,
  updateSkillDraftApi,
  withdrawSkillDraftApi,
} from '@/api/rest/agent';
import type { CreateSkillDraftRequest, SkillDraft, SkillVisibility } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';

const DEFAULT_SKILL = '---\nname: my-skill\ndescription: Describe when the agent should use this skill\n---\n\nWrite instructions for the agent.\n';
type FormValues = { name: string; description?: string; skillContent: string; visibility: SkillVisibility; resourcesText?: string; remark?: string };

function parseResources(value?: string): Record<string, string> {
  if (!value?.trim()) return {};
  const parsed: unknown = JSON.parse(value);
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('资源必须是“相对路径: 文件内容”的 JSON 对象');
  return parsed as Record<string, string>;
}
function tag(status: SkillDraft['status']) {
  const color: Record<SkillDraft['status'], string> = {
    DRAFT: 'default',
    PENDING_REVIEW: 'processing',
    REJECTED: 'error',
    CONSUMED: 'success',
  };
  return <Tag color={color[status]}>{status}</Tag>;
}

export default function SkillDraftsPage() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SkillDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const reload = () => actionRef.current?.reload();
  const initialValues = useMemo<FormValues>(() => editing ? {
    name: editing.name, description: editing.description, skillContent: editing.skillContent,
    visibility: editing.visibility, resourcesText: JSON.stringify(Object.fromEntries(editing.resources.map((item) => [item.path, item.content])), null, 2), remark: editing.remark,
  } : { name: '', description: '', skillContent: DEFAULT_SKILL, visibility: 'PRIVATE', resourcesText: '{}', remark: '' }, [editing]);
  const openEditor = (row?: SkillDraft) => {
 setEditing(row ?? null); setOpen(true); 
};
  const save = async () => {
    const values = await form.validateFields();
    const resources = parseResources(values.resourcesText);
    setSaving(true);
    try {
      if (editing) await updateSkillDraftApi(editing.id, { description: values.description, skillContent: values.skillContent, resources, remark: values.remark });
      else await createSkillDraftApi({ name: values.name, description: values.description, skillContent: values.skillContent, visibility: values.visibility, resources, remark: values.remark } satisfies CreateSkillDraftRequest);
      message.success('已保存草稿'); setOpen(false); reload();
    } catch (error) {
 message.error((error as Error).message); 
} finally {
 setSaving(false); 
}
  };
  const action = async (work: () => Promise<unknown>, text: string) => {
 try {
 await work(); message.success(text); reload(); 
} catch (error) {
 message.error((error as Error).message); 
} 
};
  const columns: ProColumns<SkillDraft>[] = [
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: '范围', dataIndex: 'visibility', width: 100, valueEnum: { MARKET: { text: 'MARKET' }, PRIVATE: { text: 'PRIVATE' } } },
    { title: '状态', dataIndex: 'status', width: 140, render: (_, row) => tag(row.status) },
    { title: '内容哈希', dataIndex: 'contentHash', search: false, ellipsis: true, width: 170 },
    { title: '审核意见', dataIndex: 'reviewComment', search: false, ellipsis: true },
    { title: '操作', valueType: 'option', width: 260, fixed: 'right', render: (_, row) => [
      (row.status === 'DRAFT' || row.status === 'REJECTED') && <a key="edit" onClick={() => openEditor(row)}>编辑</a>,
      (row.status === 'DRAFT' || row.status === 'REJECTED') && <a key="submit" onClick={() => action(() => submitSkillDraftApi(row.id), '已提交审核')}>提交</a>,
      row.status === 'PENDING_REVIEW' && <a key="withdraw" onClick={() => action(() => withdrawSkillDraftApi(row.id), '已撤回')}>撤回</a>,
      row.status === 'PENDING_REVIEW' && <a key="approve" onClick={() => action(() => approveSkillDraftApi(row.id), '已发布 Release')}>通过</a>,
      row.status === 'PENDING_REVIEW' && <Popconfirm key="reject" title="驳回草稿" description="确定驳回此草稿？" onConfirm={() => action(() => rejectSkillDraftApi(row.id), '已驳回')}><a>驳回</a></Popconfirm>,
    ] },
  ];
  return <ContentContainer scrollable>
    <ProTable<SkillDraft> rowKey="id" headerTitle="Skill 草稿" actionRef={actionRef} columns={columns}
      request={async () => ({ data: await listSkillDraftsApi(), success: true })} search={{ labelWidth: 'auto' }} pagination={{ defaultPageSize: 20 }} scroll={{ x: 1050 }}
      toolBarRender={() => [<Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => openEditor()}>新建 Skill</Button>]}/>
    <Drawer title={editing ? `编辑 ${editing.name}` : '新建 Skill'} open={open} onClose={() => setOpen(false)} width={760} destroyOnClose
      footer={<Space style={{ float: 'right' }}><Button onClick={() => setOpen(false)} disabled={saving}>取消</Button><Button type="primary" loading={saving} onClick={save}>保存</Button></Space>}>
      <Form key={editing?.id ?? 'new'} form={form} initialValues={initialValues} layout="vertical" preserve={false}>
        <Form.Item name="name" label="Skill 名称" rules={[{ required: true }, { max: 255 }]}><Input disabled={!!editing} placeholder="如 code-reviewer" /></Form.Item>
        <Form.Item name="visibility" label="可见范围" rules={[{ required: true }]}><Select disabled={!!editing} options={[{ value: 'PRIVATE', label: 'PRIVATE（仅自己可绑定）' }, { value: 'MARKET', label: 'MARKET（需审核后进入市场）' }]} /></Form.Item>
        <Form.Item name="description" label="描述" extra="必须与 SKILL.md frontmatter 中的 description 一致"><Input /></Form.Item>
        <Form.Item name="skillContent" label="SKILL.md" rules={[{ required: true }]}><Input.TextArea rows={14} style={{ fontFamily: 'ui-monospace, monospace' }} /></Form.Item>
        <Form.Item name="resourcesText" label="资源文件 JSON" extra="键为相对路径；不可使用 ..、绝对路径或反斜杠"><Input.TextArea rows={6} style={{ fontFamily: 'ui-monospace, monospace' }} /></Form.Item>
        <Form.Item name="remark" label="备注"><Input.TextArea rows={2} /></Form.Item>
      </Form>
    </Drawer>
  </ContentContainer>;
}
