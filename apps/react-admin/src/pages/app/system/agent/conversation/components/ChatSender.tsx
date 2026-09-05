import { Sender } from '@ant-design/x';
import { Space } from 'antd';
import { useTranslation } from 'react-i18next';
import ModelPicker from './ModelPicker';
import { useState } from 'react';

interface Props {
  sessionId: number | null;
  requesting: boolean;
  modelValue: number | null;
  modelLoading?: boolean;
  onModelChange: (releaseId: number | null) => Promise<void>;
  onSend: (content: string) => void;
  onCancel: () => void;
  disabled?: boolean;
}

/** ChatSender：输入区（Sender + loading 取消） + 模型选择 */
export default function ChatSender({
  sessionId,
  requesting,
  modelValue,
  modelLoading,
  onModelChange,
  onSend,
  onCancel,
  disabled,
}: Props) {
  const { t } = useTranslation('agent-conversation');
  const [value, setValue] = useState('');

  return (
    <div style={{ padding: '12px 24px 16px' }}>
      <Sender
        value={value}
        onChange={(v) => setValue(v)}
        onSubmit={(text) => {
          onSend(text);
          setValue('');
        }}
        onCancel={onCancel}
        loading={requesting}
        disabled={disabled || !sessionId}
        placeholder={t('senderPlaceholder')}
        header={
          <Space size={8} wrap>
            <ModelPicker
              sessionId={sessionId}
              value={modelValue}
              onChange={onModelChange}
              disabled={disabled}
              loading={modelLoading}
            />
          </Space>
        }
      />
    </div>
  );
}
