import { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Collapse, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  LoadingOutlined,
  SyncOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { ToolCallView } from '../types';

interface Props {
  thinking?: string;
  thinkingStartedAt?: number;
  thinkingEndedAt?: number;
  toolCalls?: ToolCallView[];
  /** 流式进行中（loading 态显示动效） */
  streaming?: boolean;
}

const { Text, Paragraph } = Typography;

/** 思考链 + 工具调用渲染：折叠面板，随流式更新保持展开当前节点 */
export default function ThoughtChainBubble({
  thinking,
  thinkingStartedAt,
  thinkingEndedAt,
  toolCalls,
  streaming,
}: Props) {
  const tools = useMemo(() => toolCalls ?? [], [toolCalls]);
  const itemKeys = useMemo(() => {
    const next: string[] = [];
    if (thinking?.trim()) next.push('thinking');
    for (const tool of tools) next.push(`tool-${tool.id}`);
    return next;
  }, [thinking, tools]);
  const runningKeys = useMemo(
    () => tools.filter((tool) => tool.status === 'running').map((tool) => `tool-${tool.id}`),
    [tools],
  );
  const [manualKeys, setManualKeys] = useState<string[] | undefined>(undefined);
  const [collapsedAll, setCollapsedAll] = useState(false);
  const activeKeys = collapsedAll
    ? []
    : manualKeys ?? [...new Set([...itemKeys, ...runningKeys, ...(streaming && thinking?.trim() ? ['thinking'] : [])])];
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const running = streaming || tools.some((t) => t.status === 'running');
    if (!running) return;
    const timer = setInterval(() => setNow(Date.now()), 500);
    return () => clearInterval(timer);
  }, [streaming, tools]);
  const durationText = useCallback(
    (startedAt?: number, endedAt?: number) => {
      if (!startedAt) return null;
      if (endedAt == null) return null;
      const duration = Math.max(0, endedAt - startedAt);
      return `${(duration / 1000).toFixed(1)} 秒`;
    },
    [],
  );
  const runningDurationText = useCallback(
    (startedAt?: number) => {
      if (!startedAt) return null;
      const duration = Math.max(0, now - startedAt);
      return `${(duration / 1000).toFixed(1)} 秒`;
    },
    [now],
  );

  const items = useMemo(() => {
    const result: NonNullable<Parameters<typeof Collapse>[0]['items']> = [];
    if (thinking && thinking.trim()) {
      result.push({
        key: 'thinking',
        label: (
          <Text type="secondary" style={{ fontSize: 13 }}>
            <SyncOutlined spin={streaming && thinkingEndedAt == null} /> {streaming && thinkingEndedAt == null ? '思考中…' : '思考过程'}
            {(thinkingEndedAt != null ? durationText(thinkingStartedAt, thinkingEndedAt) : runningDurationText(thinkingStartedAt)) && (
              <Text type="secondary" className="agent-chat-duration">
                {thinkingEndedAt != null ? durationText(thinkingStartedAt, thinkingEndedAt) : runningDurationText(thinkingStartedAt)}
              </Text>
            )}
          </Text>
        ),
        children: <pre className="agent-chat-thinking-body">{thinking}</pre>,
      });
    }
    tools.forEach((tool, index) => {
      const running = tool.status === 'running';
      const icon = running ? (
        <LoadingOutlined />
      ) : tool.status === 'error' ? (
        <CheckCircleOutlined style={{ color: '#ff4d4f' }} />
      ) : (
        <CheckCircleOutlined style={{ color: '#52c41a' }} />
      );
      result.push({
        key: `tool-${tool.id}`,
        label: (
          <span>
            {icon}
            <ThunderboltOutlined style={{ marginInline: 6 }} />
            <Text code>{tool.name || `工具 ${index + 1}`}</Text>
            {running && <Tag style={{ marginInlineStart: 8 }}>执行中</Tag>}
            {(tool.status !== 'running' ? durationText(tool.startedAt, tool.endedAt) : runningDurationText(tool.startedAt)) && (
              <Text type="secondary" className="agent-chat-duration">
                {tool.status !== 'running' ? durationText(tool.startedAt, tool.endedAt) : runningDurationText(tool.startedAt)}
              </Text>
            )}
          </span>
        ),
        children: (
          <div>
            {tool.argsText && <pre className="agent-chat-tool-body">{tool.argsText}</pre>}
            {tool.resultText !== undefined && tool.resultText !== null && (
              <Paragraph
                type="secondary"
                style={{ marginTop: 8, marginBottom: 0, fontSize: 12 }}
                ellipsis={{ rows: 3, expandable: true, symbol: '展开' }}
              >
                结果: {tool.resultText}
              </Paragraph>
            )}
          </div>
        ),
      });
    });
    return result;
  }, [thinking, thinkingStartedAt, thinkingEndedAt, tools, streaming, durationText, runningDurationText]);

  if (items.length === 0) return null;
  return (
    <div className="agent-chat-thought-chain-wrap">
      <div className="agent-chat-thought-chain-actions">
        <Button
          type="link"
          size="small"
          onClick={() => {
            setCollapsedAll((value) => !value);
            setManualKeys(undefined);
          }}
        >
          {collapsedAll ? '展开过程' : '折叠过程'}
        </Button>
      </div>
      <Collapse
        ghost
        size="small"
        className="agent-chat-thought-chain"
        items={items}
        activeKey={activeKeys}
        onChange={(keys) => {
          setCollapsedAll(false);
          setManualKeys(Array.isArray(keys) ? (keys as string[]) : []);
        }}
      />
    </div>
  );
}
