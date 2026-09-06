import { useEffect, useMemo } from 'react';
import {
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Tag,
  message,
} from 'antd';
import {
  useCreateDictData,
  useListAllDictType,
  useUpdateDictData,
} from '@/api/hooks/dict';
import type {
  CreateDictDataRequest,
  DictData,
  DictTagType,
} from '@/api/rest/types';
import {
  getCurrentPlatform,
  PLATFORM_OPTIONS,
  PLATFORM_TAG_TYPE_OPTIONS,
  TAG_TYPE_SET,
} from './shared';

interface Props {
  open: boolean;
  row: DictData | null;
  /** 抽屉打开时若指定了 typeId，则锁定 typeId 字段不可改（来自双表选中态） */
  defaultTypeId?: number;
  /** 字典类型下拉选项，由父组件传入，避免抽屉内重复请求 */
  typeOptions?: { label: string; value: number }[];
  onClose: () => void;
  onSaved: (row?: DictData) => void;
}

interface FormValues {
  typeId: number;
  value: string;
  label: string;
  sort?: number;
  isDefault?: boolean;
  platform?: string;
  /** 是否开启预设样式（默认开；编辑回显由 row.tagType 决定） */
  usePresetStyle?: boolean;
  /** 预设样式标识；关闭时强制 'default'；与 antd `<Tag color>` 签名一致 */
  tagType?: DictTagType;
  isEnabled?: boolean;
  remark?: string;
}

const DictDataDrawer = ({
  open,
  row,
  defaultTypeId,
  typeOptions = [],
  onClose,
  onSaved,
}: Props) => {
  const [form] = Form.useForm<FormValues>();
  // 抽屉内任意字段变化都触发预览（label + tagType 同时影响）
  const watchedLabel = Form.useWatch('label', form);
  const watchedTagType = Form.useWatch('tagType', form);
  const watchedUsePreset = Form.useWatch('usePresetStyle', form);
  // 监听 platform：切到 general 时强制 usePresetStyle=false，并锁住开关
  const watchedPlatform = Form.useWatch('platform', form);
  // 兜底加载：父组件 typeOptions 还没就绪就打开抽屉时，自己拉一次确保
  // Select 拿得到匹配项，否则 setFieldsValue 设的 typeId 会显示不出来
  const allTypesQuery = useListAllDictType(
    { status: 1 },
    { enabled: open && typeOptions.length === 0 },
  );
  const effectiveTypeOptions = useMemo(() => {
    if (typeOptions.length > 0) return typeOptions;
    return (allTypesQuery.data ?? []).map((t) => ({
      label: `${t.name}（${t.code}）`,
      value: t.id,
    }));
  }, [typeOptions, allTypesQuery.data]);

  const createMut = useCreateDictData({
    onSuccess: (created) => {
      message.success('创建成功');
      onSaved(created);
      onClose();
    },
    onError: (err) => {
      message.error(`创建失败：${(err as Error).message ?? '未知错误'}`);
    },
  });
  const updateMut = useUpdateDictData({
    onSuccess: (updated) => {
      message.success('保存成功');
      onSaved(updated);
      onClose();
    },
    onError: (err) => {
      message.error(`保存失败：${(err as Error).message ?? '未知错误'}`);
    },
  });
  const isEdit = !!row;
  const submitting = createMut.isPending || updateMut.isPending;

  // 根据当前模式（编辑/新建）构造 form values。
  // - 编辑：用 row 各字段回显，注意 isDefault/isEnabled 是 0/1，转成 boolean 给 Switch；
  //   hasPreset 同时校验 row.tagType ∈ TAG_TYPE_SET（16 项白名单），legacy 颜色预设
  //   （如历史数据可能出现的非法值）一律视为「关闭预设样式」
  // - 新建：用 defaultTypeId 回显所属类型，platform 默认当前前端平台，其他字段给个合理的初始值
  const buildFormValues = (
    source: DictData | null,
    initialTypeId?: number,
  ): FormValues => {
    if (source) {
      const hasPreset =
        !!source.tagType &&
        source.tagType !== 'default' &&
        TAG_TYPE_SET.has(source.tagType);
      return {
        typeId: source.typeId,
        value: source.value,
        label: source.label,
        sort: source.sort,
        isDefault: source.isDefault === 1,
        platform: source.platform,
        usePresetStyle: hasPreset,
        tagType: hasPreset ? source.tagType : 'primary',
        isEnabled: source.isEnabled === 1,
        remark: source.remark,
      };
    }
    return {
      typeId: initialTypeId as FormValues['typeId'],
      value: '',
      label: '',
      sort: 0,
      isDefault: false,
      platform: getCurrentPlatform(),
      usePresetStyle: true,
      tagType: 'primary',
      isEnabled: true,
      remark: '',
    };
  };

  // 抽屉初次挂载时通过 initialValues 注入；destroyOnHidden 会让 Form 在每次
  // 打开时重新创建，因此用 useMemo 把"当前应当填进表单的值"算成对象，
  // 配合 key 让 Form 在 row/open 真正变化时整体重建，避免出现「Select 已经
  // mount 但 setFieldsValue 还没跑」导致的下拉框不回显。
  const formInitialValues = useMemo<FormValues>(
    () => buildFormValues(row, defaultTypeId),
    // buildFormValues 是纯函数；这里只依赖真正影响回显的入参
     
    [row, defaultTypeId],
  );

  useEffect(() => {
    if (!open) return;
    // 新建模式必须等 typeOptions 就绪再回显，否则 Select 拿不到匹配项
    if (!row && effectiveTypeOptions.length === 0) return;
    // Form 跨打开不会自动重置 store（form 实例由 useForm() 持有），因此
    // 每次抽屉从关闭转为打开时重置到当前 initialValues，保证编辑切换
    // 不同 row 时所有字段（包括 Select / Switch）都正确回显。
    form.resetFields();
  }, [open, row, defaultTypeId, effectiveTypeOptions, form]);

  // 联动：platform=general 时强制 usePresetStyle=false 并锁住 tagType 选择
  // （通用平台不允许维护自己的预设样式，按 PRD §「行为契约」执行）。
  // 用 useEffect 而非在 onChange 里写：watchedPlatform 在表单刚 reset 完
  // 也会触发一次，此时 usePresetStyle 已经是正确的初始值，setFieldsValue
  // 是 no-op，所以不会破坏编辑回显。
  useEffect(() => {
    if (!open) return;
    if (watchedPlatform === 'general' && watchedUsePreset) {
      form.setFieldsValue({ usePresetStyle: false });
    }
  }, [open, watchedPlatform, watchedUsePreset, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    // 永远下发白名单内的值：usePresetStyle=false → 'default'；
    // true → 当前选中的 tagType 或 'primary' 兜底（前端白名单 ⊂ 后端 16 项白名单，
    // 不会触发后端 PUT 的 400）。也避免下发空串。
    const finalTagType = values.usePresetStyle
      ? values.tagType || 'primary'
      : 'default';
    if (isEdit) {
      updateMut.mutate({
        id: row.id,
        value: values.value,
        label: values.label,
        sort: values.sort ?? 0,
        isDefault: values.isDefault ? 1 : 0,
        platform: values.platform ?? getCurrentPlatform(),
        tagType: finalTagType,
        isEnabled: values.isEnabled ? 1 : 0,
        remark: values.remark ?? '',
      });
    } else {
      const body: CreateDictDataRequest = {
        typeId: values.typeId,
        value: values.value,
        label: values.label,
        sort: values.sort ?? 0,
        isDefault: !!values.isDefault,
        platform: values.platform ?? getCurrentPlatform(),
        tagType: finalTagType,
        isEnabled: values.isEnabled ? 1 : 0,
        remark: values.remark ?? '',
      };
      createMut.mutate(body);
    }
  };

  // 实时预览：根据当前 usePresetStyle / tagType / label 渲染。
  // - 关闭预设样式：展示纯文本，不再渲染 Tag，避免出现「无颜色边框 Tag」的歧义
  // - 开启预设样式：渲染对应颜色的 Tag
  const previewColor = watchedUsePreset ? watchedTagType || 'primary' : undefined;
  const previewText = watchedLabel || '示例标签';

  return (
    <Drawer
      title={isEdit ? '编辑字典项' : '新建字典项'}
      open={open}
      onClose={onClose}
      size={640}
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
      <Form
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={formInitialValues}
      >
        {/* ============ 基础信息 ============ */}
        <Card size="small" title="基础信息" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item
                label="所属类型"
                name="typeId"
                rules={[{ required: true, message: '请选择所属类型' }]}
              >
                <Select
                  placeholder="请选择类型"
                  options={effectiveTypeOptions}
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="字典值"
                name="value"
                rules={[
                  { required: true, message: '请输入字典值' },
                  { max: 64 },
                ]}
              >
                <Input placeholder="例如 Y / N / 0 / 1" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="字典标签"
                name="label"
                rules={[
                  { required: true, message: '请输入字典标签' },
                  { max: 128 },
                ]}
              >
                <Input placeholder="例如 是 / 否 / 启用" />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        {/* ============ 样式设置 ============ */}
        <Card size="small" title="样式设置" style={{ marginBottom: 16 }}>
          <Row gutter={16} align="middle">
            <Col span={8}>
              <Form.Item
                label="开启预设样式"
                name="usePresetStyle"
                valuePropName="checked"
                getValueFromEvent={(v) => !!v}
                style={{ marginBottom: 0 }}
              >
                {/* 通用平台：开关 disable，鼠标点击无响应（PRD 视觉约定） */}
                <Switch
                  checkedChildren="开"
                  unCheckedChildren="关"
                  disabled={watchedPlatform === 'general'}
                />
              </Form.Item>
            </Col>
            {watchedUsePreset ? (
              <Col span={10}>
                <Form.Item
                  label="预设样式"
                  name="tagType"
                  rules={[{ required: true, message: '请选择预设样式' }]}
                  style={{ marginBottom: 0 }}
                >
                  <Select
                    placeholder="请选择预设样式"
                    options={PLATFORM_TAG_TYPE_OPTIONS(watchedPlatform)}
                    showSearch
                    optionFilterProp="label"
                  />
                </Form.Item>
              </Col>
            ) : null}
            <Col span={watchedUsePreset ? 6 : 16}>
              <div
                style={{
                  fontSize: 12,
                  color: '#999',
                  marginBottom: 4,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                预览
              </div>
              {watchedUsePreset ? (
                <Tag color={previewColor} style={{ marginInlineEnd: 0 }}>
                  {previewText}
                </Tag>
              ) : (
                <span style={{ color: '#333' }}>{previewText}</span>
              )}
            </Col>
          </Row>
        </Card>

        {/* ============ 其他属性 ============ */}
        <Card size="small" title="其他属性" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="排序"
                name="sort"
                rules={[{ type: 'number', message: '排序必须为数字' }]}
              >
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="升序排序，0 排在前"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="归属平台" name="platform">
                <Select
                  options={PLATFORM_OPTIONS}
                  placeholder="请选择归属平台"
                  showSearch
                  optionFilterProp="label"
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="是否默认"
                name="isDefault"
                valuePropName="checked"
                getValueFromEvent={(v) => !!v}
                getValueProps={(v) => ({ checked: !!v })}
              >
                <Switch checkedChildren="是" unCheckedChildren="否" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="启用"
                name="isEnabled"
                valuePropName="checked"
                getValueFromEvent={(v) => !!v}
                getValueProps={(v) => ({ checked: v !== false })}
              >
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item label="备注" name="remark">
                <Input.TextArea rows={3} placeholder="选填" />
              </Form.Item>
            </Col>
          </Row>
        </Card>
      </Form>
    </Drawer>
  );
};

export default DictDataDrawer;