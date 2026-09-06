import { Descriptions, Drawer, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { TaskExecution } from '@/api/rest/types';
import { formatDateTime } from '@/utils/date';
import { formatDuration, statusColor, statusLabelKey } from './shared';

interface Props {
  open: boolean;
  row: TaskExecution | null;
  onClose: () => void;
}

function dash(v: string | number | null | undefined) {
  if (v === null || v === undefined || v === '') return '—';
  return String(v);
}

function formatJson(v: Record<string, unknown> | null | undefined) {
  if (!v) return '—';
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return String(v);
  }
}

const TaskExecutionDetailDrawer = ({ open, row, onClose }: Props) => {
  const { t } = useTranslation('task');

  const statusLabel = (status: string) => {
    const key = statusLabelKey(status);
    const label = t(key);
    return label === key ? status : label;
  };

  return (
    <Drawer
      title={t('executionDetail')}
      size={640}
      open={open}
      onClose={onClose}
      destroyOnHidden
    >
      {row ? (
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="ID">{row.id}</Descriptions.Item>
          <Descriptions.Item label={t('configName')}>
            {row.configName || <span style={{ color: '#999' }}>—</span>}
          </Descriptions.Item>
          <Descriptions.Item label={t('workflowId')}>{dash(row.workflowId)}</Descriptions.Item>
          <Descriptions.Item label={t('runId')}>{dash(row.runId)}</Descriptions.Item>
          <Descriptions.Item label={t('workflowType')}>{dash(row.workflowType)}</Descriptions.Item>
          <Descriptions.Item label={t('taskQueue')}>{dash(row.taskQueue)}</Descriptions.Item>
          <Descriptions.Item label={t('execStatus')}>
            <Tag color={statusColor(row.status)}>{statusLabel(row.status)}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label={t('retryCount')}>{row.retryCount ?? 0}</Descriptions.Item>
          <Descriptions.Item label={t('createdAt')}>{formatDateTime(row.createdAt)}</Descriptions.Item>
          <Descriptions.Item label={t('pendingAt')}>{formatDateTime(row.pendingAt)}</Descriptions.Item>
          <Descriptions.Item label={t('startedAt')}>{formatDateTime(row.startedAt)}</Descriptions.Item>
          <Descriptions.Item label={t('closedAt')}>{formatDateTime(row.closedAt)}</Descriptions.Item>
          <Descriptions.Item label={t('duration')}>
            {formatDuration(row.startedAt, row.closedAt)}
          </Descriptions.Item>
          <Descriptions.Item label={t('inputSummary')}>
            <Typography.Paragraph
              style={{ marginBottom: 0, whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}
            >
              {formatJson(row.inputSummary)}
            </Typography.Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label={t('resultSummary')}>
            <Typography.Paragraph
              style={{ marginBottom: 0, whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}
            >
              {formatJson(row.resultSummary)}
            </Typography.Paragraph>
          </Descriptions.Item>
          <Descriptions.Item label={t('failureReason')}>
            {row.failureReason ? (
              <Typography.Paragraph
                style={{ marginBottom: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}
                copyable
              >
                {row.failureReason}
              </Typography.Paragraph>
            ) : (
              <span style={{ color: '#999' }}>-</span>
            )}
          </Descriptions.Item>
        </Descriptions>
      ) : null}
    </Drawer>
  );
};

export default TaskExecutionDetailDrawer;
