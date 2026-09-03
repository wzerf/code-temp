import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import {
  createGitSourceApi,
  deleteGitSourceApi,
  listGitSourceApi,
  previewGitSourceApi,
  syncGitSourceApi,
  updateGitSourceApi,
} from '@/api/rest/skill-git';
import type { GitPreviewResult, GitSkillPackage, GitSource, GitSyncItemResult, GitSyncResult } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

const RESULT_COLOR: Record<string, string> = {
  CREATED: 'green',
  UPDATED: 'blue',
  UNCHANGED: 'default',
  CONFLICT: 'orange',
  FAILED: 'red',
};

const { TextArea } = Input;

interface FormValues {
  scope: 'MARKET' | 'PRIVATE';
  url: string;
  ref?: string;
  subdirectory?: string;
  plainSecret?: string;
  remark?: string;
}

/** Skill Git 受控导入来源面板。 */
const GitSourcePanel = () => {
  const { t } = useTranslation('skill');
  const [rows, setRows] = useState<GitSource[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<GitSource | null>(null);
  const [preview, setPreview] = useState<GitPreviewResult | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [syncResult, setSyncResult] = useState<GitSyncResult | null>(null);
  const [syncingId, setSyncingId] = useState<number | null>(null);
  const [form] = Form.useForm<FormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listGitSourceApi({}));
    } catch (err) {
      message.error(`加载失败：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    const timer = setTimeout(() => void load(), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ scope: 'PRIVATE', ref: 'main' });
    setDrawerOpen(true);
  };

  const openEdit = (row: GitSource) => {
    setEditing(row);
    form.setFieldsValue({
      scope: row.scope as 'MARKET' | 'PRIVATE',
      url: row.url,
      ref: row.ref,
      subdirectory: row.subdirectory,
      remark: row.remark,
    });
    setDrawerOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await updateGitSourceApi({
          id: editing.id,
          ref: values.ref,
          subdirectory: values.subdirectory,
          plainSecret: values.plainSecret || undefined,
          remark: values.remark,
        });
        message.success(t('gitUpdateSuccess'));
      } else {
        await createGitSourceApi({
          scope: values.scope,
          url: values.url,
          ref: values.ref || 'HEAD',
          subdirectory: values.subdirectory,
          plainSecret: values.plainSecret || undefined,
          remark: values.remark,
        });
        message.success(t('gitCreateSuccess'));
      }
      setDrawerOpen(false);
      await load();
    } catch (err) {
      message.error(`${t('createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    }
  };

  const handlePreview = async (id: number) => {
    setPreviewLoading(true);
    setPreview(null);
    try {
      const result = await previewGitSourceApi(id);
      setPreview(result);
      message.success(t('gitPreviewSuccess', { count: result.packages.length }));
    } catch (err) {
      message.error(`${t('gitPreviewTitle')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleSync = async (row: GitSource) => {
    // 有 lastCommitSha 时以预览重新解析更准确;直接调后端 sync 用该值
    Modal.confirm({
      title: t('gitConfirmSync'),
      okText: t('gitSync'),
      cancelText: t('cancel'),
      onOk: async () => {
        setSyncingId(row.id);
        try {
          // 先预览拿最新 commitSha(不写),再按该 sha 同步
          const pv = await previewGitSourceApi(row.id);
          const result = await syncGitSourceApi(row.id, pv.commitSha);
          setSyncResult(result);
          message.success(t('gitSyncSuccess'));
          await load();
        } catch (err) {
          message.error(`${t('gitSync')}：${getApiErrorMessage(err, t('unknownError'))}`);
        } finally {
          setSyncingId(null);
        }
      },
    });
  };

  const resultLabel = (r: GitSyncItemResult) => t(`gitResultMap.${r}`, { defaultValue: r });

  const columns = [
    { title: t('id'), dataIndex: 'id', width: 70 },
    { title: t('gitUrl'), dataIndex: 'url', ellipsis: true },
    { title: t('gitRef'), dataIndex: 'ref', width: 120 },
    { title: t('gitSubdirectory'), dataIndex: 'subdirectory', width: 140, render: (v: string) => v || '—' },
    {
      title: t('gitScope'),
      dataIndex: 'scope',
      width: 100,
      render: (v: string) =>
        v === 'MARKET' ? <Tag color="blue">MARKET</Tag> : <Tag>PRIVATE</Tag>,
    },
    { title: t('gitLastCommitSha'), dataIndex: 'lastCommitSha', width: 130, ellipsis: true, render: (v: string) => (v ? <Typography.Text code>{v.slice(0, 10)}</Typography.Text> : '—') },
    {
      title: t('gitLastError'),
      dataIndex: 'lastError',
      ellipsis: true,
      render: (_: unknown, r: GitSource) =>
        r.status === 'FAILED' ? <Tag color="error">{r.lastError || t('gitStatusMap.FAILED')}</Tag> : <Tag color="success">{t('gitStatusMap.READY')}</Tag>,
    },
    {
      title: t('action'),
      key: 'action',
      width: 220,
      render: (_: unknown, r: GitSource) => (
        <Space size={4}>
          <a onClick={() => handlePreview(r.id)}>{t('gitPreview')}</a>
          {syncingId === r.id ? (
            <Typography.Text type="secondary">同步中…</Typography.Text>
          ) : (
            <a onClick={() => handleSync(r)}>{t('gitSync')}</a>
          )}
          <a onClick={() => openEdit(r)}>{t('edit')}</a>
          <Popconfirm
            title={t('gitConfirmDelete')}
            onConfirm={async () => {
              try {
                await deleteGitSourceApi(r.id);
                message.success(t('gitDeleteSuccess'));
                await load();
              } catch (err) {
                message.error(`${t('deleteFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
              }
            }}
          >
            <a style={{ color: '#ff4d4f' }}>{t('gitDelete')}</a>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <ContentContainer scrollable>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Space style={{ alignSelf: 'flex-end' }}>
          <Button icon={<ReloadOutlined />} onClick={() => void load()} />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('gitCreate')}
          </Button>
        </Space>
        <Table<GitSource>
          rowKey="id"
          loading={loading}
          columns={columns as never}
          dataSource={rows}
          pagination={false}
          scroll={{ x: 1100 }}
          size="middle"
        />
      </Space>

      <Drawer
        title={editing ? t('gitEditTitle') : t('gitCreate')}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={600}
        destroyOnClose
        footer={
          <Space style={{ float: 'right' }}>
            <Button onClick={() => setDrawerOpen(false)}>{t('cancel')}</Button>
            <Button type="primary" onClick={handleSave}>
              {t('save')}
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          {!editing && (
            <Form.Item name="scope" label={t('gitScope')} rules={[{ required: true }]}>
              <Select
                options={[
                  { value: 'MARKET', label: 'MARKET（进市场）' },
                  { value: 'PRIVATE', label: 'PRIVATE（私有）' },
                ]}
              />
            </Form.Item>
          )}
          {!editing && (
            <Form.Item name="url" label={t('gitUrl')} rules={[{ required: true, message: t('gitUrlRequired') }]}>
              <Input placeholder={t('gitUrlPlaceholder')} />
            </Form.Item>
          )}
          <Form.Item name="ref" label={t('gitRef')}>
            <Input placeholder={t('gitRefPlaceholder')} />
          </Form.Item>
          <Form.Item name="subdirectory" label={t('gitSubdirectory')}>
            <Input placeholder={t('gitSubdirectoryPlaceholder')} />
          </Form.Item>
          <Form.Item name="plainSecret" label={t('gitSecret')} extra={t('secretHint', { defaultValue: '' })}>
            <Input.Password placeholder={t('gitSecretPlaceholder')} autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea rows={2} maxLength={512} />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={t('gitPreviewTitle')}
        open={!!preview || previewLoading}
        onCancel={() => setPreview(null)}
        footer={<Button onClick={() => setPreview(null)}>{t('confirm')}</Button>}
        width={860}
      >
        {previewLoading && !preview ? (
          <Typography.Text type="secondary">解析中…</Typography.Text>
        ) : preview ? (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text>
              <Tag color="blue">{preview.commitSha.slice(0, 12)}</Tag>
              {preview.packages.length} 个包
            </Typography.Text>
            {preview.packages.length === 0 ? (
              <Typography.Text type="secondary">{t('gitPreviewEmpty')}</Typography.Text>
            ) : (
              <Table
                rowKey="skillPath"
                size="small"
                dataSource={preview.packages}
                pagination={false}
                expandable={{
                  expandedRowRender: (record: GitSkillPackage) => (
                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                      {record.resourcePaths.length === 0 ? (
                        <li>（无资源文件）</li>
                      ) : (
                        record.resourcePaths.map((p) => <li key={p}>{p}</li>)
                      )}
                    </ul>
                  ),
                }}
                columns={[
                  { title: 'skillPath', dataIndex: 'skillPath', width: 200 },
                  { title: 'name', dataIndex: 'name', width: 170 },
                  {
                    title: '资源文件',
                    dataIndex: 'resourceCount',
                    width: 110,
                    render: (_: unknown, r: GitSkillPackage) => <Tag>{r.resourceCount ?? 0} 个</Tag>,
                  },
                  { title: t('contentHash', { defaultValue: 'contentHash' }), dataIndex: 'contentHash', ellipsis: true },
                ]}
              />
            )}
          </Space>
        ) : null}
      </Modal>

      <Modal
        title={t('gitSyncTitle')}
        open={!!syncResult}
        onCancel={() => setSyncResult(null)}
        footer={<Button onClick={() => setSyncResult(null)}>{t('confirm')}</Button>}
        width={720}
      >
        {syncResult && (
          <Table
            rowKey="skillPath"
            size="small"
            dataSource={syncResult.items}
            pagination={false}
            columns={[
              { title: 'skillPath', dataIndex: 'skillPath', width: 260 },
              {
                title: 'result',
                dataIndex: 'result',
                width: 120,
                render: (v: GitSyncItemResult) => <Tag color={RESULT_COLOR[v]}>{resultLabel(v)}</Tag>,
              },
              { title: 'message', dataIndex: 'message', ellipsis: true },
            ]}
          />
        )}
      </Modal>
    </ContentContainer>
  );
};

export default GitSourcePanel;
