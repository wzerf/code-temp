import { useMemo, useState } from 'react';
import { Collapse, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  LoadingOutlined,
  SyncOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { ToolCallView } from '../types';

interface Props {
  thinking?: string;
  toolCalls?: ToolCallView[];
  /** 流式进行中（loading 态显示动效） */
  streaming?: boolean;
}

const { Text, Paragraph } = Typography;

/** 思考链 + 工具调用渲染：折叠面板，随流式更新保持展开当前节点 */
export default function ThoughtChainBubble({ thinking, toolCalls, streaming }: Props) {
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
  const activeKeys = [
    ...new Set([...(manualKeys ?? itemKeys), ...runningKeys, ...(streaming && thinking?.trim() ? ['thinking'] : [])]),
  ];

  const items = useMemo(() => {
    const result: NonNullable<Parameters<typeof Collapse>[0]['items']> = [];
    if (thinking && thinking.trim()) {
      result.push({
        key: 'thinking',
        label: (
          <Text type="secondary" style={{ fontSize: 13 }}>
            <SyncOutlined spin={streaming} /> {streaming ? '思考中…' : '思考过程'}
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
  }, [thinking, tools, streaming]);

  if (items.length === 0) return null;
  return (
    <Collapse
      ghost
      size="small"
      className="agent-chat-thought-chain"
      items={items}
      activeKey={activeKeys}
      onChange={(keys) => setManualKeys(Array.isArray(keys) ? (keys as string[]) : [])}
    />
  );
}
