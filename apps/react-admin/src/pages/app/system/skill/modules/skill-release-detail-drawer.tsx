import { useEffect, useState } from 'react';
import { Descriptions, Drawer, Empty, Spin, Tabs, Tag, Typography, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { getSkillReleaseBundleApi } from '@/api/rest/skill';
import type { SkillDraftBundle, SkillRelease } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface Props {
  open: boolean;
  release: SkillRelease | null;
  onClose: () => void;
}

const STATUS_COLOR: Record<string, string> = {
  PUBLISHED: 'green',
  DEPRECATED: 'default',
};

/** Release / 市场只读详情抽屉(SKILL.md 全文 + 冻结资源列表)。 */
const SkillReleaseDetailDrawer = ({ open, release, onClose }: Props) => {
  const { t } = useTranslation('skill');
  const [bundle, setBundle] = useState<SkillDraftBundle | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !release) return;
    const timer = setTimeout(() => {
      setBundle(null);
      setLoading(true);
      getSkillReleaseBundleApi(release.id)
        .then(setBundle)
        .catch((err) => message.error(`加载内容失败：${getApiErrorMessage(err, t('unknownError'))}`))
        .finally(() => setLoading(false));
    }, 0);
    return () => clearTimeout(timer);
  }, [open, release, t]);

  const statusLabel = (s: string) => t(`statusMap.${s}`, { defaultValue: s });

  const markdown = (
    <pre style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
      {bundle?.skillContent ?? '(加载中…)'}
    </pre>
  );

  const resources = (
    <Spin spinning={loading}>
      {!bundle || bundle.resources.length === 0 ? (
        <Empty description="该 Release 无附属资源文件（SKILL.md 见「内容」页签）" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        bundle.resources.map((r) => (
          <div key={r.resourcePath} style={{ marginBottom: 12 }}>
            <Typography.Text strong copyable>
              {r.resourcePath}
            </Typography.Text>
            <pre
              style={{
                fontFamily: 'ui-monospace, monospace',
                fontSize: 12,
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                background: '#fafafa',
                padding: 8,
                borderRadius: 6,
                maxHeight: 240,
                overflow: 'auto',
                marginTop: 4,
              }}
            >
              {r.content}
            </pre>
          </div>
        ))
      )}
    </Spin>
  );

  const items = [
    { key: 'content', label: 'SKILL.md', children: <Spin spinning={loading}>{markdown}</Spin> },
    { key: 'resources', label: `${t('resources')} (${bundle?.resources.length ?? 0})`, children: resources },
  ];

  return (
    <Drawer
      title={release ? `${release.name} v${release.version}` : ''}
      open={open}
      onClose={onClose}
      width={860}
      destroyOnClose
      extra={
        release?.status === 'PUBLISHED' ? (
          <Tag color={STATUS_COLOR[release.status]}>{statusLabel(release.status)}</Tag>
        ) : (
          <Tag>{statusLabel(release?.status ?? '')}</Tag>
        )
      }
    >
      {release && (
        <>
          <Descriptions
            size="small"
            bordered
            column={3}
            items={[
              { key: 'name', label: t('name'), children: release.name },
              { key: 'version', label: t('version'), children: `v${release.version}` },
              {
                key: 'visibility',
                label: t('visibility'),
                children:
                  release.visibility === 'MARKET' ? (
                    <Tag color="blue">{t('visibilityMap.MARKET')}</Tag>
                  ) : (
                    <Tag>{t('visibilityMap.PRIVATE')}</Tag>
                  ),
              },
              { key: 'owner', label: t('ownerUserId'), children: release.ownerUserId ?? '-' },
              { key: 'createdAt', label: t('createdAt'), children: release.createdAt || '-' },
              { key: 'sourceDraft', label: '来源草稿', children: release.sourceDraftId ? `#${release.sourceDraftId}` : '-' },
              {
                key: 'hash',
                label: t('contentHash'),
                span: 3,
                children: (
                  <Typography.Text code style={{ fontSize: 12 }} copyable>
                    {release.contentHash || '-'}
                  </Typography.Text>
                ),
              },
              { key: 'desc', label: t('description'), span: 3, children: release.description || '-' },
            ]}
          />
          <div style={{ marginTop: 16 }}>
            <Tabs items={items} />
          </div>
        </>
      )}
    </Drawer>
  );
};

export default SkillReleaseDetailDrawer;
