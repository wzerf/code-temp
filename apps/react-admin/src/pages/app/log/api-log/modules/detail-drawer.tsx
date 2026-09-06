import type { CSSProperties } from 'react';
import { Descriptions, Drawer, Tag } from 'antd';
import type { ApiLogListItem } from '@/api/rest/types';
import { formatDateTime } from '@/utils/date';

interface Props {
  open: boolean;
  row: ApiLogListItem | null;
  onClose: () => void;
}

function dash(v: string | number | null | undefined) {
  if (v === null || v === undefined || v === '') return '-';
  return String(v);
}

function methodColor(method: string) {
  const m = (method || '').toUpperCase();
  if (m === 'GET') return 'blue';
  if (m === 'POST') return 'green';
  if (m === 'PUT' || m === 'PATCH') return 'orange';
  if (m === 'DELETE') return 'red';
  return 'default';
}

const preStyle: CSSProperties = {
  margin: 0,
  maxHeight: 160,
  overflow: 'auto',
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-all',
  fontSize: 12,
};

const ApiLogDetailDrawer = ({ open, row, onClose }: Props) => {
  return (
    <Drawer
      title="API 日志详情"
      size={720}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      {row ? (
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="ID">{row.id}</Descriptions.Item>
          <Descriptions.Item label="方法">
            <Tag color={methodColor(row.method)}>{row.method}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="模块">{dash(row.module)}</Descriptions.Item>
          <Descriptions.Item label="路径">{dash(row.path)}</Descriptions.Item>
          <Descriptions.Item label="状态码">{dash(row.statusCode)}</Descriptions.Item>
          <Descriptions.Item label="结果">
            <Tag color={row.success === 1 ? 'success' : 'error'}>
              {row.success === 1 ? '成功' : '失败'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="失败原因">{dash(row.reason)}</Descriptions.Item>
          <Descriptions.Item label="耗时(ms)">{dash(row.costTime)}</Descriptions.Item>
          <Descriptions.Item label="请求 ID">
            <span style={{ wordBreak: 'break-all' }}>{dash(row.requestId)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="用户 ID">{dash(row.sysUserId)}</Descriptions.Item>
          <Descriptions.Item label="用户名">{dash(row.username)}</Descriptions.Item>
          <Descriptions.Item label="完整 URI">
            <span style={{ wordBreak: 'break-all' }}>{dash(row.requestUri)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Query">
            <span style={{ wordBreak: 'break-all' }}>{dash(row.requestQuery)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="请求头">
            <pre style={preStyle}>{dash(row.requestHeader)}</pre>
          </Descriptions.Item>
          <Descriptions.Item label="请求体">
            <pre style={preStyle}>{dash(row.requestBody)}</pre>
          </Descriptions.Item>
          <Descriptions.Item label="响应">
            <pre style={preStyle}>{dash(row.response)}</pre>
          </Descriptions.Item>
          <Descriptions.Item label="变更前">
            <pre style={preStyle}>{dash(row.beforeChange)}</pre>
          </Descriptions.Item>
          <Descriptions.Item label="变更后">
            <pre style={preStyle}>{dash(row.afterChange)}</pre>
          </Descriptions.Item>
          <Descriptions.Item label="变更摘要">{dash(row.formatChange)}</Descriptions.Item>
          <Descriptions.Item label="Referer">
            <span style={{ wordBreak: 'break-all' }}>{dash(row.referer)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="客户端 ID">{dash(row.clientId)}</Descriptions.Item>
          <Descriptions.Item label="客户端名">{dash(row.clientName)}</Descriptions.Item>
          <Descriptions.Item label="客户端 IP">{dash(row.clientIp)}</Descriptions.Item>
          <Descriptions.Item label="浏览器">
            {[row.browserName, row.browserVersion].filter(Boolean).join(' ') || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="操作系统">
            {[row.osName, row.osVersion].filter(Boolean).join(' ') || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="地理位置">{dash(row.location)}</Descriptions.Item>
          <Descriptions.Item label="User-Agent">
            <span style={{ wordBreak: 'break-all' }}>{dash(row.userAgent)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatDateTime(row.createdAt)}</Descriptions.Item>
          {row.archivedAt ? (
            <Descriptions.Item label="归档时间">{formatDateTime(row.archivedAt)}</Descriptions.Item>
          ) : null}
        </Descriptions>
      ) : null}
    </Drawer>
  );
};

export default ApiLogDetailDrawer;
