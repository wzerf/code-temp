import { useState } from 'react';
import { Alert, Button, Form, Input, Modal, Space, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  StopOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { AguiInterrupt } from '../types';

interface Props {
  interrupts: AguiInterrupt[];
  onResume: (resume: {
    interruptId: string;
    status: 'resolved' | 'cancelled';
    payload?: Record<string, unknown>;
  }[]) => void;
}

const { Text, Paragraph } = Typography;

/**
 * HITL 审批条：渲染 RUN_FINISHED(outcome.interrupts) 的待决工具调用。
 *
 * 对齐 docs/agent-conversation-architecture.md §7.2：
 * - 确认 = status:resolved + payload.approved:true（整参由服务端按编辑后的 editedArgs 重建）
 * - 编辑参数后确认 = resolved + payload{approved:true, editedArgs}
 * - 拒绝 = resolved + payload{approved:false}
 * - 取消中断本身 = cancelled
 */
export default function HitlApproveBar({ interrupts, onResume }: Props) {
  const { t } = useTranslation('agent-conversation');
  const [editing, setEditing] = useState<AguiInterrupt | null>(null);
  const [editText, setEditText] = useState('');
  const [editForm] = Form.useForm();

  const approveAll = () => {
    onResume(
      interrupts.map((i) => ({
        interruptId: i.id,
        status: 'resolved',
        payload: { approved: true },
      })),
    );
  };

  const rejectAll = () => {
    onResume(
      interrupts.map((i) => ({
        interruptId: i.id,
        status: 'resolved',
        payload: { approved: false },
      })),
    );
  };

  const cancelInterrupts = () => {
    onResume(
      interrupts.map((i) => ({
        interruptId: i.id,
        status: 'cancelled',
      })),
    );
  };

  const openEdit = (interrupt: AguiInterrupt) => {
    setEditing(interrupt);
    setEditText(JSON.stringify(interrupt.responseSchema ?? {}, null, 2));
  };

  const submitEdit = async () => {
    if (!editing) return;
    try {
      let editedArgs: unknown;
      try {
        editedArgs = JSON.parse(editText);
      } catch {
        editedArgs = editText;
      }
      onResume([
        {
          interruptId: editing.id,
          status: 'resolved',
          payload: { approved: true, editedArgs },
        },
      ]);
      setEditing(null);
    } finally {
      void editForm;
    }
  };

  const reasonText = (reason: string) =>
    reason === 'tool_call' ? t('hitl.toolCallReason') : (reason || t('hitl.approval'));

  return (
    <>
      <Alert
        type="warning"
        showIcon
        icon={<ToolOutlined />}
        message={
        <Space direction="vertical" size={4} style={{ width: '100%' }}>
          <Space wrap>
            <Text strong>{t('hitl.title')}</Text>
            <Tag color="gold">{interrupts.length}</Tag>
          </Space>
          {interrupts.map((i) => (
            <div key={i.id}>
              <Space wrap size={6}>
                <Text code style={{ fontSize: 12 }}>
                  {reasonText(i.reason)}
                </Text>
                {i.toolCallId && <Text type="secondary">#{i.toolCallId}</Text>}
                {i.message && <Text type="secondary">{i.message}</Text>}
              </Space>
              <Space size={4} style={{ marginTop: 4 }}>
                <Button size="small" type="link" onClick={() => openEdit(i)}>
                  <EditOutlined /> {t('hitl.editArgs')}
                </Button>
              </Space>
            </div>
          ))}
          <Space wrap style={{ marginTop: 4 }}>
            <Button type="primary" size="small" icon={<CheckCircleOutlined />} onClick={approveAll}>
              {t('hitl.approve')}
            </Button>
            <Button size="small" danger icon={<CloseCircleOutlined />} onClick={rejectAll}>
              {t('hitl.reject')}
            </Button>
            <Button size="small" icon={<StopOutlined />} onClick={cancelInterrupts}>
              {t('hitl.cancelRun')}
            </Button>
          </Space>
        </Space>
      }
      style={{ marginBlock: 4 }}
      />

      <Modal
        title={t('hitl.editArgsTitle')}
        open={!!editing}
        onCancel={() => setEditing(null)}
        onOk={submitEdit}
        okText={t('hitl.confirmEdit')}
        cancelText={t('cancel')}
        width={560}
        destroyOnHidden
      >
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          {t('hitl.editArgsHint')}
        </Paragraph>
        <Input.TextArea
          rows={10}
          value={editText}
          onChange={(e) => setEditText(e.target.value)}
          style={{ fontFamily: 'monospace', fontSize: 12 }}
        />
      </Modal>
    </>
  );
}
