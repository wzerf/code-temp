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
  const [activeKeys, setActiveKeys] = useState<string[]>([]);

  const items = useMemo(() => {
    const result: NonNullable<Parameters<typeof Collapse>[0]['items']> = [];
    if (thinking && thinking.trim()) {
      result.push({
        key: 'thinking',
        label: (
          <Text type="secondary" style={{ fontSize: 12 }}>
            <SyncOutlined spin={streaming} /> 思考过程
          </Text>
        ),
        children: (
          <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0, fontSize: 13, opacity: 0.85 }}>
            {thinking}
          </Paragraph>
        ),
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
            {tool.argsText && (
              <pre
                style={{
                  margin: 0,
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-all',
                  fontSize: 12,
                  background: 'rgba(0,0,0,0.03)',
                  padding: 8,
                  borderRadius: 6,
                }}
              >
                {tool.argsText}
              </pre>
            )}
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

  // 新工具/思考节点出现时自动展开；折叠面板 items 更新后保持用户手动展开状态
  if (items.length === 0) return null;
  return (
    <Collapse
      ghost
      size="small"
      items={items}
      style={{ marginBottom: 4, maxWidth: '100%' }}
      activeKey={activeKeys}
      onChange={(keys) => setActiveKeys(Array.isArray(keys) ? (keys as string[]) : [])}
    />
  );
}
