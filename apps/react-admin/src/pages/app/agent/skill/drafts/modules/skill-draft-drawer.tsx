import { useMemo, useState } from 'react';
import { Button, Drawer, Form, Input, Select, Space, message } from 'antd';
import { createSkillDraftApi, updateSkillDraftApi } from '@/api/rest/agent';
import type { CreateSkillDraftRequest, SkillDraft, SkillVisibility } from '@/api/rest/types';

const DEFAULT_SKILL =
  '---\nname: my-skill\ndescription: Describe when the agent should use this skill\n---\n\nWrite instructions for the agent.\n';

interface Props {
  open: boolean;
  row: SkillDraft | null;
  onClose: () => void;
  onSaved: (row: SkillDraft) => void;
}

type FormValues = {
  name: string;
  description?: string;
  skillContent: string;
  visibility: SkillVisibility;
  remark?: string;
};

export default function SkillDraftDrawer({ open, row, onClose, onSaved }: Props) {
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const isEdit = !!row;

  const initialValues = useMemo<FormValues>(
    () =>
      row
        ? {
            name: row.name,
            description: row.description,
            skillContent: row.skillContent,
            visibility: row.visibility,
            remark: row.remark,
          }
        : {
            name: '',
            description: '',
            skillContent: DEFAULT_SKILL,
            visibility: 'PRIVATE',
            remark: '',
          },
    [row],
  );

  const handleOk = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const saved = isEdit
        ? await updateSkillDraftApi(row.id, {
            description: values.description,
            skillContent: values.skillContent,
            remark: values.remark,
          })
        : await createSkillDraftApi({
            name: values.name,
            description: values.description,
            skillContent: values.skillContent,
            visibility: values.visibility,
            remark: values.remark,
          } satisfies CreateSkillDraftRequest);
      message.success(isEdit ? '已保存草稿' : '已创建草稿');
      onSaved(saved);
      onClose();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? `编辑 ${row.name}` : '新建 Skill'}
      open={open}
      onClose={onClose}
      width={760}
      destroyOnClose
      afterOpenChange={(visible) => {
        if (visible) {
          form.setFieldsValue(initialValues);
        } else {
          form.resetFields();
        }
      }}
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button type="primary" loading={saving} onClick={handleOk}>
            保存
          </Button>
        </Space>
      }
    >
      <Form
        key={row?.id != null ? `draft-${row.id}` : 'draft-new'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={initialValues}
      >
        <Form.Item name="name" label="Skill 名称" rules={[{ required: true }, { max: 255 }]}>
          <Input disabled={isEdit} placeholder="如 code-reviewer" />
        </Form.Item>
        <Form.Item name="visibility" label="可见范围" rules={[{ required: true }]}>
          <Select
            disabled={isEdit}
            options={[
              { value: 'PRIVATE', label: 'PRIVATE（仅自己可绑定）' },
              { value: 'MARKET', label: 'MARKET（需审核后进入市场）' },
            ]}
          />
        </Form.Item>
        <Form.Item
          name="description"
          label="描述"
          extra="必须与 SKILL.md frontmatter 中的 description 一致"
        >
          <Input />
        </Form.Item>
        <Form.Item name="skillContent" label="SKILL.md" rules={[{ required: true }]}>
          <Input.TextArea rows={14} style={{ fontFamily: 'ui-monospace, monospace' }} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
