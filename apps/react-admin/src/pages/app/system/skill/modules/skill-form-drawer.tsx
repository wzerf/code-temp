import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Col,
  Descriptions,
  Divider,
  Drawer,
  Form,
  Input,
  Row,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import {
  createSkillDraftApi,
  getSkillDraftBundleApi,
  updateSkillDraftApi,
} from '@/api/rest/skill';
import type { SkillDraft, SkillResourceItem } from '@/api/rest/types';
import { getApiErrorMessage } from '../../blacklist/modules/error-message';

interface Props {
  open: boolean;
  row: SkillDraft | null;
  onClose: () => void;
  onSaved: () => void;
}

interface FormValues {
  name: string;
  visibility: 'MARKET' | 'PRIVATE';
  description?: string;
  skillContent: string;
  remark?: string;
}

const { TextArea } = Input;

const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING_REVIEW: 'gold',
  REJECTED: 'red',
  CONSUMED: 'purple',
};

const SkillFormDrawer = ({ open, row, onClose, onSaved }: Props) => {
  const { t } = useTranslation('skill');
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  // 资源用受控 state 管理(不用 Form.List,避免异步 setFieldsValue 不回显)
  const [resources, setResources] = useState<SkillResourceItem[]>([]);
  const isEdit = !!row;
  /** 可编辑:新建 或 DRAFT/REJECTED 草稿;PENDING_REVIEW/CONSUMED 只读查看 */
  const editable = !row || row.status === 'DRAFT' || row.status === 'REJECTED';

  useEffect(() => {
    if (!open) return;
    if (row) {
      form.setFieldsValue({
        name: row.name,
        visibility: row.visibility as 'MARKET' | 'PRIVATE',
        description: row.description,
        skillContent: row.skillContent,
        remark: row.remark,
      });
      const timer = setTimeout(() => {
        // 拉取完整内容包(列表接口不带全文/资源,必须异步补全)
        getSkillDraftBundleApi(row.id)
          .then((bundle) => {
            if (!bundle) return;
            form.setFieldValue('skillContent', bundle.skillContent);
            setResources(bundle.resources ?? []);
          })
          .catch(() => {
            setResources([]);
            message.warning('资源加载失败');
          });
      }, 0);
      return () => clearTimeout(timer);
    }
    const timer = setTimeout(() => {
      form.resetFields();
      form.setFieldsValue({ visibility: 'PRIVATE' });
      setResources([]);
    }, 0);
    return () => clearTimeout(timer);
  }, [open, row, form]);

  const updateResource = (index: number, patch: Partial<SkillResourceItem>) => {
    setResources((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)));
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const cleanResources = resources.filter((r) => r?.resourcePath?.trim());
      if (isEdit && row) {
        await updateSkillDraftApi({
          id: row.id,
          name: values.name,
          description: values.description,
          skillContent: values.skillContent,
          remark: values.remark,
          resources: cleanResources.length ? cleanResources : undefined,
        });
        message.success(t('updateSuccess'));
      } else {
        await createSkillDraftApi({
          name: values.name,
          visibility: values.visibility,
          description: values.description,
          skillContent: values.skillContent,
          remark: values.remark,
          resources: cleanResources,
        });
        message.success(t('createSuccess'));
      }
      onSaved();
      onClose();
    } catch (err) {
      message.error(`${t(isEdit ? 'updateFailed' : 'createFailed')}：${getApiErrorMessage(err, t('unknownError'))}`);
    } finally {
      setSaving(false);
    }
  };

  const statusLabel = (s?: string) =>
    t(`statusMap.${s ?? ''}`, { defaultValue: s ?? '-' });
  const visibilityLabel = (v: string) =>
    v === 'MARKET' ? t('visibilityMap.MARKET') : t('visibilityMap.PRIVATE');

  return (
    <Drawer
      title={!isEdit ? t('createTitle') : editable ? t('editTitle') : t('viewTitle', { defaultValue: '查看 Skill 草稿' })}
      open={open}
      onClose={onClose}
      width={820}
      destroyOnClose
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={saving}>
            {t('cancel')}
          </Button>
          {editable && (
            <Button type="primary" onClick={handleSave} loading={saving}>
              {t('save')}
            </Button>
          )}
        </Space>
      }
    >
      {/* 编辑态:只读元信息,补齐表格里看不到的关键字段 */}
      {isEdit && row && (
        <>
          <Descriptions
            size="small"
            bordered
            column={2}
            items={[
              {
                key: 'status',
                label: t('status'),
                children: <Tag color={STATUS_COLOR[row.status]}>{statusLabel(row.status)}</Tag>,
              },
              {
                key: 'visibility',
                label: t('visibility'),
                children:
                  row.visibility === 'MARKET' ? (
                    <Tag color="blue">{visibilityLabel(row.visibility)}</Tag>
                  ) : (
                    <Tag>{visibilityLabel(row.visibility)}</Tag>
                  ),
              },
              { key: 'owner', label: t('ownerUserId'), children: row.ownerUserId ?? '-' },
              {
                key: 'source',
                label: t('sectionSource', { defaultValue: '来源' }),
                children: row.groupKey ? <Tag color="geekblue">{row.groupKey}</Tag> : <Tag>手动</Tag>,
              },
              { key: 'createdAt', label: t('createdAt'), children: row.createdAt || '-' },
              { key: 'updatedAt', label: t('updatedAt'), children: row.updatedAt || '-' },
              {
                key: 'hash',
                label: t('contentHash'),
                children: (
                  <Typography.Text code style={{ fontSize: 12 }} copyable>
                    {row.contentHash || '-'}
                  </Typography.Text>
                ),
              },
              {
                key: 'count',
                label: '资源数',
                children: `${row.resourceCount ?? 0} 个文件`,
              },
            ]}
          />
          {row.status === 'REJECTED' && (
            <Alert
              type="error"
              showIcon
              style={{ marginTop: 12 }}
              message={`${t('rejectReason', { defaultValue: '驳回原因' })}: ${row.reviewComment || '-'}`}
            />
          )}
          {row.status === 'CONSUMED' && (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 12 }}
              message={t('consumedHint', { defaultValue: '该草稿已发布为 Release,不可再编辑;修改请新建草稿' })}
            />
          )}
          <Divider style={{ margin: '16px 0' }} />
        </>
      )}

      <Form form={form} layout="vertical" preserve={false} disabled={!editable}>
        <Row gutter={16}>
          <Col span={isEdit ? 24 : 14}>
            <Form.Item name="name" label={t('name')} rules={[{ required: true, message: t('requiredName') }]}>
              <Input maxLength={128} />
            </Form.Item>
          </Col>
          {!isEdit && (
            <Col span={10}>
              <Form.Item name="visibility" label={t('visibility')} rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'MARKET', label: `${t('visibilityMap.MARKET')}（进市场）` },
                    { value: 'PRIVATE', label: t('visibilityMap.PRIVATE') },
                  ]}
                />
              </Form.Item>
            </Col>
          )}
        </Row>
        <Form.Item name="description" label={t('description')}>
          <Input maxLength={512} />
        </Form.Item>
        <Form.Item
          name="skillContent"
          label={t('skillContent')}
          rules={[{ required: true, message: 'SKILL.md 不能为空' }]}
          extra={t('skillContentPlaceholder')}
        >
          <TextArea rows={14} style={{ fontFamily: 'monospace', fontSize: 12 }} />
        </Form.Item>

        <Typography.Title level={5}>{t('resources')}</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
          {resources.length > 0
            ? `共 ${resources.length} 个资源文件`
            : '暂无资源文件(SKILL.md 之外的可选文件)'}
        </Typography.Paragraph>
        {resources.map((res, index) => (
          <div
            key={`${res.resourcePath || 'new'}-${index}`}
            style={{
              border: '1px dashed #d9d9d9',
              borderRadius: 6,
              padding: '10px 12px 0',
              marginBottom: 10,
              position: 'relative',
              background: '#fafafa',
            }}
          >
            <Input
              placeholder={t('resourcePath')}
              value={res.resourcePath}
              disabled={!editable}
              onChange={(e) => updateResource(index, { resourcePath: e.target.value })}
              style={{ marginBottom: 8, width: '100%' }}
            />
            <TextArea
              placeholder="content"
              value={res.content}
              disabled={!editable}
              onChange={(e) => updateResource(index, { content: e.target.value })}
              style={{ width: '100%', fontFamily: 'monospace', fontSize: 12 }}
              autoSize={{ minRows: 2, maxRows: 12 }}
            />
            <MinusCircleOutlined
              disabled={!editable}
              onClick={() => setResources((prev) => prev.filter((_, i) => i !== index))}
              style={{ position: 'absolute', top: 10, right: 12 }}
            />
          </div>
        ))}
        <Button
          type="dashed"
          disabled={!editable}
          onClick={() => setResources((prev) => [...prev, { resourcePath: '', content: '' }])}
          block
          icon={<PlusOutlined />}
        >
          {t('addResource')}
        </Button>
        <Form.Item name="remark" label="备注" style={{ marginTop: 8 }}>
          <TextArea rows={2} maxLength={512} />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default SkillFormDrawer;
