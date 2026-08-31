import { describe, expect, it } from 'vitest';
import { parseAgentEventStream } from './agent';

describe('parseAgentEventStream', () => {
  it('仅在完整事件帧到达后交付，并保留未完成尾部', () => {
    const events: Array<{ type: string; text: string | null }> = [];
    const pending = parseAgentEventStream(
      'event: TEXT_DELTA\ndata: {"type":"TEXT_DELTA","text":"你好"}\n\n' +
        'event: COMPLETED\ndata: {"type":"COM',
      (event) => events.push({ text: event.text, type: event.type }),
    );

    expect(events).toEqual([{ text: '你好', type: 'TEXT_DELTA' }]);
    expect(pending).toBe('event: COMPLETED\ndata: {"type":"COM');
  });

  it('解析多行 data 载荷', () => {
    const events: string[] = [];
    parseAgentEventStream(
      'event: TEXT_DELTA\ndata: {"type":"TEXT_DELTA",\ndata: "text":"你好"}\n\n',
      (event) => events.push(event.text ?? ''),
    );

    expect(events).toEqual(['你好']);
  });
});
