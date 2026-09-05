import { Sender } from '@ant-design/x';
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
  /** 有消息时贴底；空态居中时不加底栏 padding */
  docked?: boolean;
}

/** ChatSender：圆角输入条，底栏仅模型名下拉 + 发送/取消 */
export default function ChatSender({
  sessionId,
  requesting,
  modelValue,
  modelLoading,
  onModelChange,
  onSend,
  onCancel,
  disabled,
  docked = true,
}: Props) {
  const { t } = useTranslation('agent-conversation');
  const [value, setValue] = useState('');

  return (
    <div className={docked ? 'agent-chat-composer agent-chat-composer-docked' : 'agent-chat-composer'}>
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
        autoSize={{ minRows: 1, maxRows: 8 }}
        suffix={false}
        footer={(oriNode) => (
          <div className="agent-chat-sender-footer">
            <ModelPicker
              sessionId={sessionId}
              value={modelValue}
              onChange={onModelChange}
              disabled={disabled || requesting}
              loading={modelLoading}
            />
            {oriNode}
          </div>
        )}
      />
    </div>
  );
}
