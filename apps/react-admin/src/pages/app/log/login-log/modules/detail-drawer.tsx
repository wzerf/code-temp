import { Descriptions, Drawer, Tag } from 'antd';
import type { LoginLogListItem } from '@/api/rest/types';
import { formatDateTime } from '@/utils/date';

interface Props {
  open: boolean;
  row: LoginLogListItem | null;
  onClose: () => void;
}

function dash(v: string | number | null | undefined) {
  if (v === null || v === undefined || v === '') return '-';
  return String(v);
}

const LoginLogDetailDrawer = ({ open, row, onClose }: Props) => {
  return (
    <Drawer
      title="登录日志详情"
      size={560}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      {row ? (
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="ID">{row.id}</Descriptions.Item>
          <Descriptions.Item label="用户名">{row.username || '-'}</Descriptions.Item>
          <Descriptions.Item label="结果">
            <Tag color={row.success === 1 ? 'success' : 'error'}>
              {row.success === 1 ? '成功' : '失败'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="失败原因">{dash(row.reason)}</Descriptions.Item>
          <Descriptions.Item label="状态码">{dash(row.statusCode)}</Descriptions.Item>
          <Descriptions.Item label="用户 ID">{dash(row.sysUserId)}</Descriptions.Item>
          <Descriptions.Item label="登录方式">{dash(row.loginMethod)}</Descriptions.Item>
          <Descriptions.Item label="登录时间">{formatDateTime(row.loginTime)}</Descriptions.Item>
          <Descriptions.Item label="登录 IP">{dash(row.loginIp)}</Descriptions.Item>
          <Descriptions.Item label="MAC">{dash(row.loginMac)}</Descriptions.Item>
          <Descriptions.Item label="客户端 ID">{dash(row.clientId)}</Descriptions.Item>
          <Descriptions.Item label="客户端名">{dash(row.clientName)}</Descriptions.Item>
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

export default LoginLogDetailDrawer;
