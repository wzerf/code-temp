import { useRef, useState } from 'react';
import { Button, Drawer, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined } from '@ant-design/icons';
import {
  createGitSkillSourceApi, deleteGitSkillSourceApi, listGitSkillSourcesApi,
  previewGitSkillSourceApi, syncGitSkillSourceApi, updateGitSkillSourceApi,
} from '@/api/rest/agent';
import type { CreateGitSkillSourceRequest, GitSkillPreview, GitSkillSource } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';

type FormValues = { scope: 'MARKET' | 'PRIVATE'; url: string; ref?: string; subdirectory?: string; secretRef?: string };
export default function SkillGitPage() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<GitSkillSource | null>(null);
  const [preview, setPreview] = useState<GitSkillPreview | null>(null);
  const [previewSource, setPreviewSource] = useState<GitSkillSource | null>(null);
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<FormValues>();
  const reload = () => actionRef.current?.reload();
  const edit = (row?: GitSkillSource) => {
 setEditing(row ?? null); setOpen(true); 
};
  const action = async (work: () => Promise<unknown>, text: string) => {
 try {
 await work(); message.success(text); reload(); 
} catch (error) {
 message.error((error as Error).message); 
} 
};
  const save = async () => {
    const values = await form.validateFields(); setSaving(true);
    try {
      if (editing) await updateGitSkillSourceApi(editing.id, { url: values.url, ref: values.ref, subdirectory: values.subdirectory, secretRef: values.secretRef });
      else await createGitSkillSourceApi(values satisfies CreateGitSkillSourceRequest);
      message.success('来源已保存'); setOpen(false); reload();
    } catch (error) {
 message.error((error as Error).message); 
} finally {
 setSaving(false); 
}
  };
  const showPreview = async (source: GitSkillSource) => {
    try {
 const result = await previewGitSkillSourceApi(source.id); setPreview(result); setPreviewSource(source); setSelected(result.skills.map((item) => item.skillPath)); 
} catch (error) {
 message.error((error as Error).message); 
}
  };
  const sync = async () => {
    if (!preview || !previewSource) return;
    try {
 const result = await syncGitSkillSourceApi(previewSource.id, preview.commitSha, selected as string[]); message.success(`同步完成：${result.results.map((item) => item.status).join('、')}`); setPreview(null); reload(); 
} catch (error) {
 message.error((error as Error).message); 
}
  };
  const columns: ProColumns<GitSkillSource>[] = [
    { title: '范围', dataIndex: 'scope', width: 100, render: (_, row) => <Tag color={row.scope === 'MARKET' ? 'blue' : 'default'}>{row.scope}</Tag> },
    { title: '仓库 URL', dataIndex: 'url', ellipsis: true }, { title: 'Ref', dataIndex: 'ref', width: 150 },
    { title: '子目录', dataIndex: 'subdirectory', width: 160, render: (_, row) => row.subdirectory || '-' },
    { title: '提交', dataIndex: 'lastCommitSha', width: 150, ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 100, render: (_, row) => <Tag color={row.status === 'READY' ? 'success' : 'error'}>{row.status}</Tag> },
    { title: '操作', valueType: 'option', width: 220, fixed: 'right', render: (_, row) => [
      <a key="preview" onClick={() => showPreview(row)}>预览并同步</a>, <a key="edit" onClick={() => edit(row)}>编辑</a>,
      <Popconfirm key="delete" title="删除 Git 来源" description="不会删除已经创建的草稿、Release 或 Binding。" onConfirm={() => action(() => deleteGitSkillSourceApi(row.id), '已删除')}><a style={{ color: '#ff4d4f' }}>删除</a></Popconfirm>,
    ] },
  ];
  return <ContentContainer scrollable>
    <ProTable<GitSkillSource> rowKey="id" actionRef={actionRef} headerTitle="Git Skill 来源" columns={columns} request={async () => ({ data: await listGitSkillSourcesApi(), success: true })} search={{ labelWidth: 'auto' }} scroll={{ x: 1050 }}
      toolBarRender={() => [<Button key="create" type="primary" icon={<PlusOutlined />} onClick={() => edit()}>新建来源</Button>]}/>
    <Drawer title={editing ? '编辑 Git 来源' : '新建 Git 来源'} open={open} onClose={() => setOpen(false)} width={600} destroyOnClose
      footer={<Space style={{ float: 'right' }}><Button onClick={() => setOpen(false)}>取消</Button><Button type="primary" loading={saving} onClick={save}>保存</Button></Space>}>
      <Form key={editing?.id ?? 'new'} form={form} layout="vertical" initialValues={editing ?? { scope: 'PRIVATE', ref: 'HEAD', subdirectory: '' }} preserve={false}>
        <Form.Item name="scope" label="范围" rules={[{ required: true }]}><Select disabled={!!editing} options={[{ value: 'PRIVATE', label: 'PRIVATE' }, { value: 'MARKET', label: 'MARKET（仅管理员）' }]} /></Form.Item>
        <Form.Item name="url" label="HTTPS 仓库 URL" rules={[{ required: true, type: 'url' }]} extra="仅 HTTPS；禁止 URL 中包含用户名、密码或 token"><Input placeholder="https://github.com/org/skills.git" /></Form.Item>
        <Form.Item name="ref" label="Ref"><Input placeholder="HEAD" /></Form.Item>
        <Form.Item name="subdirectory" label="Skill 根子目录" extra="为空时扫描仓库根目录；通常为 skills"><Input placeholder="skills" /></Form.Item>
        <Form.Item name="secretRef" label="凭据引用" extra="只提交密钥系统中已登记的引用；前端不会展示或缓存凭据明文"><Input placeholder="可选" /></Form.Item>
      </Form>
    </Drawer>
    <Modal title={preview ? `选择要同步的包 · ${preview.commitSha}` : ''} open={!!preview} onCancel={() => setPreview(null)} onOk={sync} okText="同步为草稿" okButtonProps={{ disabled: selected.length === 0 }} width={850}>
      <Table rowKey="skillPath" dataSource={preview?.skills ?? []} pagination={false} rowSelection={{ selectedRowKeys: selected, onChange: setSelected }} columns={[
        { title: '路径', dataIndex: 'skillPath' }, { title: '名称', dataIndex: 'name' }, { title: '描述', dataIndex: 'description' }, { title: '资源', dataIndex: 'resourceCount', width: 80 }, { title: '字节', dataIndex: 'totalBytes', width: 100 },
      ]} />
    </Modal>
  </ContentContainer>;
}
