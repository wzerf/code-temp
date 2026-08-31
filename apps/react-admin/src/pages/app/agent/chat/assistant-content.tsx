import { useMemo, useState } from 'react';
import { ThoughtChain } from '@ant-design/x';
import { MdPreview } from 'md-editor-rt';
import 'md-editor-rt/lib/preview.css';

export interface AssistantContentProps {
  content: string;
  messageKey: string;
  thinking?: string;
  thinkingDurationSec?: number;
  thinkingStreaming?: boolean;
}

/** 跨流式重渲染记住用户是否展开过思考过程 */
const thinkingExpandedPreference = new Map<string, boolean>();

function thinkingTitle(durationSec?: number, streaming?: boolean): string {
  if (streaming) {
    return '思考中…';
  }
  if (typeof durationSec === 'number' && durationSec > 0) {
    return `思考了 ${durationSec} 秒`;
  }
  return '思考过程';
}

function resolveExpandedKeys(messageKey: string, thinkingStreaming?: boolean): string[] {
  if (thinkingStreaming) {
    return ['thinking'];
  }
  return thinkingExpandedPreference.get(messageKey) ? ['thinking'] : [];
}

/** AI 气泡：可折叠思考过程 + Markdown 正文。 */
const AssistantContent = ({
  content,
  messageKey,
  thinking,
  thinkingDurationSec,
  thinkingStreaming,
}: AssistantContentProps) => {
  const hasThinking = Boolean(thinking?.trim());
  const [, bump] = useState(0);

  const thoughtItems = useMemo(
    () =>
      hasThinking
        ? [
            {
              key: 'thinking',
              title: thinkingTitle(thinkingDurationSec, thinkingStreaming),
              content: <pre className="agent-chat-thinking-body">{thinking}</pre>,
              status: (thinkingStreaming ? 'loading' : 'success') as 'loading' | 'success',
              collapsible: true,
            },
          ]
        : [],
    [hasThinking, thinking, thinkingDurationSec, thinkingStreaming],
  );

  return (
    <div className="agent-chat-assistant-content">
      {hasThinking && (
        <ThoughtChain
          className="agent-chat-thought-chain"
          expandedKeys={resolveExpandedKeys(messageKey, thinkingStreaming)}
          items={thoughtItems}
          onExpand={(keys) => {
            thinkingExpandedPreference.set(messageKey, keys.includes('thinking'));
            bump((value) => value + 1);
          }}
        />
      )}
      {content ? (
        <MdPreview
          className="agent-chat-markdown"
          editorId={`agent-md-${messageKey}`}
          previewTheme="vuepress"
          value={content}
        />
      ) : null}
    </div>
  );
};

export default AssistantContent;
