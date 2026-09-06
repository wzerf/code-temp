import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
  theme as antdTheme,
} from 'antd';
import {
  useBatchUpsertI18nTranslationByKey,
  useGetI18nTranslationByKey,
  useListAllI18nLocale,
  useListI18nTranslationByLocaleCode,
} from '@/api/hooks/i18n';
import type {
  I18nLocale,
  I18nTranslation,
  I18nTranslationBatchUpsertByKeyItem,
} from '@/api/rest/types';

interface Props {
  open: boolean;
  /** 编辑入口：传入原 row 时进入编辑模式；新建时传 null */
  sourceRow: I18nTranslation | null;
  /** 默认语言编码（来自双表选中态或 i18n default）；用于拉取 keys 与标星 */
  defaultLocaleCode?: string;
  /** 新建场景预填的 translation_key */
  initialKey?: string;
  onClose: () => void;
  onSaved: () => void;
}

interface BaseRow {
  localeId: number;
  localeCode?: string;
  localeName: string;
  isDefault: boolean;
  serverValue: string;
  serverRemark: string;
  serverEnabled: boolean;
  existingId?: number;
}

interface FormValues {
  translationKey: string;
  globalEnabled: boolean;
  [k: `value_${number}`]: string;
  [k: `remark_${number}`]: string;
  [k: `enabled_${number}`]: boolean;
}

const KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9._-]{0,254}$/;

const I18nTranslationKeyDrawer = ({
  open,
  sourceRow,
  defaultLocaleCode,
  initialKey,
  onClose,
  onSaved,
}: Props) => {
  const [form] = Form.useForm<FormValues>();
  const { token } = antdTheme.useToken();
  const isEdit = !!sourceRow;
  const initKey = sourceRow?.translationKey ?? initialKey ?? '';

  // 拉全语种（作为列头）
  const localesQuery = useListAllI18nLocale(
    { status: 1 },
    { enabled: open },
  );
  const locales = useMemo(
    () => (localesQuery.data ?? []) as I18nLocale[],
    [localesQuery.data],
  );

  // 编辑模式：按 translation_key 拉 values。
  // 新建模式传 null: hook 内部 enabled 检查会让请求不发起,
  // byKeyQuery.data 保持 undefined,避免空 key 误请求导致 data.values 缺失崩。
  const editingKey = isEdit ? sourceRow?.translationKey : null;
  const byKeyQuery = useGetI18nTranslationByKey(editingKey, {
    enabled: isEdit && open,
  });

  // 新建模式：按 default locale 拉 keys，做实时去重提示
  const byLocaleQuery = useListI18nTranslationByLocaleCode(
    defaultLocaleCode,
    { enabled: open && !isEdit },
  );
  const existingKeys = useMemo(
    () => new Set((byLocaleQuery.data ?? []).map((r) => r.translationKey)),
    [byLocaleQuery.data],
  );

  // 服务端原始 rows（不放在 state，用 useMemo 派生）
  const baseRows: BaseRow[] = useMemo(() => {
    if (locales.length === 0) return [];
    if (isEdit && byKeyQuery.isLoading) return [];
    return locales.map((l) => {
      const found = (byKeyQuery.data?.values ?? []).find(
        (v) => v.localeId === l.id,
      );
      return {
        localeId: l.id,
        localeCode: l.code,
        localeName: l.name,
        isDefault: l.isDefault === 1,
        serverValue: found?.value ?? '',
        serverRemark: found?.remark ?? '',
        serverEnabled: found?.isEnabled === 1,
        existingId: found?.id,
      };
    });
  }, [locales, isEdit, byKeyQuery.data, byKeyQuery.isLoading]);

  // 用户操作层（删除 / enabled 覆盖）
  const [deletedIds, setDeletedIds] = useState<Set<number>>(new Set());
  const [enabledOverrides, setEnabledOverrides] = useState<
    Record<number, boolean>
  >({});
  const [errors, setErrors] = useState<string[]>([]);
  const [translationKeyInput, setTranslationKeyInput] = useState(initKey);

  // 渲染期同步初始化 form：每次 baseRows 变化即重置
  // 用 form 的 initialValues + destroyOnHidden 保证打开即挂载时拿到最新值
  const initialFormValues: FormValues = useMemo(() => {
    const formValues: FormValues = {
      translationKey: initKey,
      globalEnabled: baseRows.some(
        (r) => r.serverEnabled && (!r.existingId || !deletedIds.has(r.existingId)),
      ),
    } as FormValues;
    for (const r of baseRows) {
      (formValues as Record<string, unknown>)[`value_${r.localeId}`] =
        r.serverValue;
      (formValues as Record<string, unknown>)[`remark_${r.localeId}`] =
        r.serverRemark;
      (formValues as Record<string, unknown>)[`enabled_${r.localeId}`] =
        r.serverEnabled;
    }
    return formValues;
    // deletedIds 是初值参考之一；其余依赖为服务端数据
  }, [baseRows, initKey, deletedIds]);

  // 抽屉首次打开或数据首次就绪时,把表单重置为初值
  useEffect(() => {
    if (!open) return;
    if (initialFormValues.translationKey === undefined && baseRows.length === 0) {
      return;
    }
    form.setFieldsValue(initialFormValues);
  }, [open, initialFormValues, baseRows.length, form]);

  // 合成最终 rows（不含已删除的）
  const rows = useMemo(
    () =>
      baseRows
        .filter((r) => !r.existingId || !deletedIds.has(r.existingId))
        .map((r) => ({
          ...r,
          enabled:
            r.localeId in enabledOverrides
              ? enabledOverrides[r.localeId]
              : r.serverEnabled,
        })),
    [baseRows, deletedIds, enabledOverrides],
  );

  const handleGlobalEnabledChange = (next: boolean) => {
    const nextOverrides: Record<number, boolean> = {};
    const fieldUpdates: Partial<FormValues> = {};
    for (const r of baseRows) {
      if (!r.existingId || !deletedIds.has(r.existingId)) {
        nextOverrides[r.localeId] = next;
        fieldUpdates[`enabled_${r.localeId}`] = next;
      }
    }
    setEnabledOverrides(nextOverrides);
    form.setFieldsValue(fieldUpdates);
  };

  const handleRowEnabledChange = (localeId: number, next: boolean) => {
    setEnabledOverrides((prev) => ({ ...prev, [localeId]: next }));
    form.setFieldValue(`enabled_${localeId}`, next);
  };

  const handleRowDelete = (localeId: number, existingId?: number) => {
    if (existingId !== undefined) {
      setDeletedIds((prev) => {
        const next = new Set(prev);
        next.add(existingId);
        return next;
      });
    }
    form.setFieldValue(`value_${localeId}`, '');
    form.setFieldValue(`remark_${localeId}`, '');
    form.setFieldValue(`enabled_${localeId}`, false);
  };

  // 翻译键始终可编辑（包括编辑模式）：修改 key 会通过后端 batch-upsert
  // 的 rename 阶段同步影响所有语言版本的同一 key 行。
  // 「启用 X 种」基于可见行 (排除被删除), 与总开关 + 各行联动一致。
  const enabledCount = rows.filter((r) => r.enabled).length;
  const totalLocales = rows.length;

  const batchUpsertMut = useBatchUpsertI18nTranslationByKey({
    onSuccess: (res) => {
      if (!res.ok) {
        const msgs = (res.errors ?? []).map(
          (e) => `[${e.code}] ${e.message}`,
        );
        setErrors(msgs);
        message.error(`保存失败：${msgs.join('；') || '未知错误'}`);
        return;
      }
      setErrors([]);
      const a = res.affected ?? {
        renamed: 0,
        created: 0,
        updated: 0,
        deleted: 0,
      };
      message.success(
        `已保存（新增 ${a.created} / 更新 ${a.updated} / 删除 ${a.deleted}${a.renamed ? ' / 改名' : ''}）`,
      );
      onSaved();
      onClose();
    },
    onError: (err) => {
      setErrors([(err as Error).message ?? '未知错误']);
      message.error(`保存失败：${(err as Error).message ?? '未知错误'}`);
    },
  });

  const submitting = batchUpsertMut.isPending;

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setErrors([]);

      const finalKey = (values.translationKey ?? '').trim();
      if (!finalKey) {
        setErrors(['请输入翻译键']);
        return;
      }
      if (!KEY_PATTERN.test(finalKey)) {
        setErrors(['翻译键需字母开头，仅含字母数字 . _ -']);
        return;
      }

      const deletedIdList = Array.from(deletedIds);

      const items: I18nTranslationBatchUpsertByKeyItem[] = baseRows
        .filter(
          (r) =>
            (!r.existingId || !deletedIds.has(r.existingId)) &&
            String(values[`value_${r.localeId}`] ?? '').trim() !== '',
        )
        .map((r) => {
          const enabled =
            r.localeId in enabledOverrides
              ? enabledOverrides[r.localeId]
              : r.serverEnabled;
          return {
            localeId: r.localeId,
            value: String(values[`value_${r.localeId}`]).trim(),
            remark: String(values[`remark_${r.localeId}`] ?? '').trim() || undefined,
            isEnabled: enabled ? 1 : 0,
          };
        });

      const defaultRow = baseRows.find(
        (r) => r.isDefault && (!r.existingId || !deletedIds.has(r.existingId)),
      );
      if (defaultRow) {
        const defaultValue = String(
          values[`value_${defaultRow.localeId}`] ?? '',
        ).trim();
        if (!defaultValue) {
          setErrors(['默认语言（★）必须填写']);
          return;
        }
      }
      if (
        items.length === 0 &&
        deletedIdList.length === 0 &&
        isEdit &&
        finalKey === sourceRow.translationKey
      ) {
        message.warning('没有需要保存的修改');
        return;
      }

      const newTranslationKey =
        isEdit && finalKey !== sourceRow.translationKey
          ? finalKey
          : undefined;

      batchUpsertMut.mutate({
        translationKey: sourceRow?.translationKey ?? finalKey,
        newTranslationKey,
        items,
        deletedIds: deletedIdList,
      });
    } catch {
      // antd 校验失败：不提交
    }
  };

  const keyDuplicate =
    !isEdit &&
    translationKeyInput.length > 0 &&
    existingKeys.has(translationKeyInput);

  return (
    <Drawer
      title={isEdit ? '编辑翻译' : '新建翻译'}
      open={open}
      onClose={onClose}
      size={800}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
          <Button onClick={onClose} disabled={submitting}>
            取消
          </Button>
          <Button type="primary" onClick={handleOk} loading={submitting}>
            保存
          </Button>
        </Space>
      }
    >
      {errors.length > 0 && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message={errors.join('；')}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={initialFormValues}
        onValuesChange={(changed) => {
          if (typeof changed.translationKey === 'string') {
            setTranslationKeyInput(changed.translationKey);
          }
        }}
      >
        <Form.Item
          label={
            <Space size={6}>
              <span>翻译键</span>
              {!isEdit && keyDuplicate && (
                <Tag color="warning">该 key 在默认语言已存在</Tag>
              )}
            </Space>
          }
          name="translationKey"
          rules={[
            { required: true, message: '请输入翻译键' },
            { max: 255 },
            {
              pattern: KEY_PATTERN,
              message: '字母开头，仅含字母数字 . _ -',
            },
          ]}
        >
          <Input placeholder="例如 menu.user.create" />
        </Form.Item>
        <Typography.Paragraph
          type="secondary"
          style={{ fontSize: 12, marginTop: -16, marginBottom: 16 }}
        >
          建议使用点分命名空间（如 menu.user.create），修改 key 会
          同步影响所有语言版本的同一 key 行
        </Typography.Paragraph>

        <Form.Item label="状态">
          <Space size={16} align="center" wrap>
            <Form.Item
              name="globalEnabled"
              valuePropName="checked"
              noStyle
              getValueFromEvent={(v) => !!v}
              getValueProps={(v) => ({ checked: v !== false })}
            >
              <Switch
                checkedChildren="启用"
                unCheckedChildren="禁用"
                onChange={handleGlobalEnabledChange}
              />
            </Form.Item>
            <Tag
              color={
                enabledCount === totalLocales && totalLocales > 0
                  ? 'success'
                  : 'default'
              }
            >
              {enabledCount} / {totalLocales} 启用
            </Tag>
          </Space>
        </Form.Item>
        <Typography.Paragraph
          type="secondary"
          style={{ fontSize: 12, marginTop: -16, marginBottom: 16 }}
        >
          总开关会联动所有可见语言行；混合态保持原值
        </Typography.Paragraph>

        <Form.Item label="各语言值">
          <Table
            dataSource={rows}
            rowKey="localeId"
            size="small"
            pagination={false}
          >
            <Table.Column
              title="语言"
              width={180}
              render={(_, r: BaseRow) => (
                <Space size={6} align="center">
                  <Tag
                    color={r.isDefault ? 'blue' : 'default'}
                    style={{
                      margin: 0,
                      fontFamily:
                        'ui-monospace, SFMono-Regular, Menlo, monospace',
                      fontSize: 12,
                    }}
                  >
                    {r.localeCode}
                  </Tag>
                  <Typography.Text
                    ellipsis
                    style={{
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      maxWidth: 60,
                    }}
                  >
                    {r.localeName}
                  </Typography.Text>
                  {r.isDefault && <Tag color="blue">★</Tag>}
                </Space>
              )}
            />
            <Table.Column
              title="翻译值"
              minWidth={160}
              render={(_, r: BaseRow) => (
                <Form.Item
                  name={`value_${r.localeId}`}
                  noStyle
                  rules={
                    r.isDefault
                      ? [{ required: true, message: '默认语言必须填写' }]
                      : undefined
                  }
                >
                  <Input
                    placeholder={
                      r.isDefault ? '默认语言必填' : '翻译值（留空跳过）'
                    }
                  />
                </Form.Item>
              )}
            />
            <Table.Column
              title="备注"
              width={140}
              render={(_, r: BaseRow) => (
                <Form.Item name={`remark_${r.localeId}`} noStyle>
                  <Input placeholder="选填" />
                </Form.Item>
              )}
            />
            <Table.Column
              title="启用"
              width={72}
              align="center"
              render={(_, r: BaseRow) => (
                <Form.Item
                  name={`enabled_${r.localeId}`}
                  noStyle
                  valuePropName="checked"
                  getValueFromEvent={(v) => !!v}
                  getValueProps={(v) => ({ checked: v !== false })}
                >
                  <Switch
                    size="small"
                    onChange={(next) =>
                      handleRowEnabledChange(r.localeId, next)
                    }
                  />
                </Form.Item>
              )}
            />
            <Table.Column
              title="操作"
              width={52}
              align="center"
              render={(_, r: BaseRow) => (
                <Tooltip title="删除该语言行">
                  <Button
                    danger
                    type="text"
                    shape="circle"
                    size="small"
                    onClick={() =>
                      handleRowDelete(r.localeId, r.existingId)
                    }
                  >
                    ×
                  </Button>
                </Tooltip>
              )}
            />
          </Table>
        </Form.Item>
      </Form>

      <div
        style={{
          padding: '10px 12px',
          marginTop: 16,
          fontSize: 12,
          color: token.colorTextSecondary,
          background: token.colorPrimaryBg,
          borderLeft: `3px solid ${token.colorPrimary}`,
          borderRadius: 4,
        }}
      >
        填了的语言会写入对应行；留空则跳过。默认语言（★）必须。
      </div>
    </Drawer>
  );
};

export default I18nTranslationKeyDrawer;
