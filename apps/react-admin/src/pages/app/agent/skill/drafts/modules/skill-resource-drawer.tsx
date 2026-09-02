import { useMemo, useState } from 'react';
import { Button, Drawer, Form, Input, Space, message } from 'antd';
import { createSkillDraftResourceApi, updateSkillDraftResourceApi } from '@/api/rest/agent';
import type { SkillResource } from '@/api/rest/types';

interface Props {
  open: boolean;
  draftId: number | null;
  row: SkillResource | null;
  editable: boolean;
  onClose: () => void;
  onSaved: () => void;
}

type FormValues = {
  path: string;
  content?: string;
};

const PATH_HINT = '相对路径；不可使用 ..、绝对路径或反斜杠';
const EMPTY_VALUES: FormValues = { path: '', content: '' };

export default function SkillResourceDrawer({
  open,
  draftId,
  row,
  editable,
  onClose,
  onSaved,
}: Props) {
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const isEdit = !!row;

  const initialValues = useMemo<FormValues>(
    () =>
      row
        ? {
            path: row.path ?? '',
            content: row.content ?? '',
          }
        : EMPTY_VALUES,
    [row],
  );

  const handleOk = async () => {
    if (!draftId) {
      message.warning('请先选择 Skill 草稿');
      return;
    }
    if (!editable) {
      message.warning('仅 DRAFT / REJECTED 状态可编辑资源');
      return;
    }
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (isEdit) {
        if (row.id == null) {
          message.error('资源缺少 id，无法更新');
          return;
        }
        await updateSkillDraftResourceApi(draftId, row.id, {
          path: values.path,
          content: values.content ?? '',
        });
      } else {
        await createSkillDraftResourceApi(draftId, {
          path: values.path,
          content: values.content ?? '',
        });
      }
      message.success(isEdit ? '已保存资源' : '已新增资源');
      onSaved();
      onClose();
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title={isEdit ? `编辑资源 ${row.path}` : '新建资源文件'}
      open={open}
      onClose={onClose}
      width={720}
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
          <Button type="primary" loading={saving} onClick={handleOk} disabled={!editable}>
            保存
          </Button>
        </Space>
      }
    >
      <Form
        key={row?.id != null ? `resource-${row.id}` : 'resource-new'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={initialValues}
      >
        <Form.Item
          name="path"
          label="相对路径"
          extra={PATH_HINT}
          rules={[{ required: true, message: '请输入相对路径' }, { max: 500 }]}
        >
          <Input placeholder="如 scripts/check.sh" disabled={!editable} />
        </Form.Item>
        <Form.Item name="content" label="文件内容">
          <Input.TextArea
            rows={16}
            style={{ fontFamily: 'ui-monospace, monospace' }}
            disabled={!editable}
          />
        </Form.Item>
      </Form>
    </Drawer>
  );
}
