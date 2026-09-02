import { useMemo, useRef, useState } from 'react';
import { Button, Col, Modal, Popconfirm, Row, Space, Tag, message } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { PlusOutlined } from '@ant-design/icons';
import {
  approveSkillDraftApi,
  deleteSkillDraftResourceApi,
  listSkillDraftResourcesApi,
  listSkillDraftsApi,
  rejectSkillDraftApi,
  submitSkillDraftApi,
  withdrawSkillDraftApi,
} from '@/api/rest/agent';
import type { SkillDraft, SkillResource } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';
import SkillDraftDrawer from './modules/skill-draft-drawer';
import SkillResourceDrawer from './modules/skill-resource-drawer';

function statusTag(status: SkillDraft['status']) {
  const color: Record<SkillDraft['status'], string> = {
    DRAFT: 'default',
    PENDING_REVIEW: 'processing',
    REJECTED: 'error',
    CONSUMED: 'success',
  };
  return <Tag color={color[status]}>{status}</Tag>;
}

function isResourceEditable(draft: SkillDraft | null) {
  return !!draft && (draft.status === 'DRAFT' || draft.status === 'REJECTED');
}

/** 估算表体可用高度；仅当行数超出时才启用 scroll.y，避免内容不满也出现滚动条 */
const TABLE_ROW_HEIGHT = 55;
const TABLE_BODY_OFFSET = 400;

function resolveTableScrollY(rowCount: number): number | undefined {
  if (typeof window === 'undefined' || rowCount <= 0) return undefined;
  const maxBody = Math.max(240, window.innerHeight - TABLE_BODY_OFFSET);
  return rowCount * TABLE_ROW_HEIGHT > maxBody ? maxBody : undefined;
}

export default function SkillDraftsPage() {
  const draftActionRef = useRef<ActionType | undefined>(undefined);
  const resourceActionRef = useRef<ActionType | undefined>(undefined);

  const [selectedDraftId, setSelectedDraftId] = useState<number | null>(null);
  const [selectedDraft, setSelectedDraft] = useState<SkillDraft | null>(null);
  const selectedDraftIdRef = useRef<number | null>(null);
  const [draftRowCount, setDraftRowCount] = useState(0);
  const [resourceRowCount, setResourceRowCount] = useState(0);

  const [draftDrawerOpen, setDraftDrawerOpen] = useState(false);
  const [editingDraft, setEditingDraft] = useState<SkillDraft | null>(null);

  const [resourceDrawerOpen, setResourceDrawerOpen] = useState(false);
  const [editingResource, setEditingResource] = useState<SkillResource | null>(null);

  const reloadDrafts = () => draftActionRef.current?.reload();
  const reloadResources = () => resourceActionRef.current?.reload();
  const draftScrollY = resolveTableScrollY(draftRowCount);
  const resourceScrollY = resolveTableScrollY(resourceRowCount);

  const clearSelection = () => {
    selectedDraftIdRef.current = null;
    setSelectedDraftId(null);
    setSelectedDraft(null);
    reloadResources();
  };

  const selectDraft = (row: SkillDraft) => {
    selectedDraftIdRef.current = row.id;
    setSelectedDraftId(row.id);
    setSelectedDraft(row);
    reloadResources();
  };

  const action = async (work: () => Promise<unknown>, text: string) => {
    try {
      await work();
      message.success(text);
      reloadDrafts();
      if (selectedDraftIdRef.current != null) {
        reloadResources();
      }
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const draftColumns: ProColumns<SkillDraft>[] = useMemo(
    () => [
      { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
      {
        title: '范围',
        dataIndex: 'visibility',
        width: 100,
        valueEnum: { MARKET: { text: 'MARKET' }, PRIVATE: { text: 'PRIVATE' } },
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 130,
        render: (_, row) => statusTag(row.status),
      },
      {
        title: '资源数',
        dataIndex: 'resources',
        width: 90,
        search: false,
        render: (_, row) => row.resources?.length ?? 0,
      },
      {
        title: '内容哈希',
        dataIndex: 'contentHash',
        search: false,
        ellipsis: true,
        width: 150,
      },
      {
        title: '审核意见',
        dataIndex: 'reviewComment',
        search: false,
        ellipsis: true,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 220,
        fixed: 'right',
        render: (_, row) => [
          (row.status === 'DRAFT' || row.status === 'REJECTED') && (
            <a
              key="edit"
              onClick={(e) => {
                e.stopPropagation();
                setEditingDraft(row);
                setDraftDrawerOpen(true);
              }}
            >
              编辑
            </a>
          ),
          (row.status === 'DRAFT' || row.status === 'REJECTED') && (
            <a
              key="submit"
              onClick={(e) => {
                e.stopPropagation();
                void action(() => submitSkillDraftApi(row.id), '已提交审核');
              }}
            >
              提交
            </a>
          ),
          row.status === 'PENDING_REVIEW' && (
            <a
              key="withdraw"
              onClick={(e) => {
                e.stopPropagation();
                void action(() => withdrawSkillDraftApi(row.id), '已撤回');
              }}
            >
              撤回
            </a>
          ),
          row.status === 'PENDING_REVIEW' && (
            <a
              key="approve"
              onClick={(e) => {
                e.stopPropagation();
                void action(() => approveSkillDraftApi(row.id), '已发布 Release');
              }}
            >
              通过
            </a>
          ),
          row.status === 'PENDING_REVIEW' && (
            <Popconfirm
              key="reject"
              title="驳回草稿"
              description="确定驳回此草稿？"
              onConfirm={() => action(() => rejectSkillDraftApi(row.id), '已驳回')}
            >
              <a
                onClick={(e) => {
                  e.stopPropagation();
                }}
              >
                驳回
              </a>
            </Popconfirm>
          ),
        ],
      },
    ],
    // action 闭包依赖 selectedDraftIdRef，无需列入 deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const resourceEditable = isResourceEditable(selectedDraft);

  const resourceColumns: ProColumns<SkillResource>[] = useMemo(
    () => [
      { title: '路径', dataIndex: 'path', ellipsis: true },
      {
        title: '内容哈希',
        dataIndex: 'contentHash',
        search: false,
        ellipsis: true,
        width: 160,
      },
      {
        title: '内容预览',
        dataIndex: 'content',
        search: false,
        ellipsis: true,
        render: (_, row) => row.content?.slice(0, 80) || <span style={{ color: '#999' }}>-</span>,
      },
      {
        title: '操作',
        valueType: 'option',
        width: 120,
        fixed: 'right',
        render: (_, row) => [
          <a
            key="edit"
            onClick={() => {
              setEditingResource(row);
              setResourceDrawerOpen(true);
            }}
          >
            {resourceEditable ? '编辑' : '查看'}
          </a>,
          resourceEditable && (
            <a
              key="delete"
              style={{ color: '#ff4d4f' }}
              onClick={() => {
                Modal.confirm({
                  title: '确认删除该资源文件？',
                  okText: '删除',
                  cancelText: '取消',
                  okButtonProps: { danger: true },
                  onOk: async () => {
                    if (selectedDraftIdRef.current == null || row.id == null) return;
                    try {
                      await deleteSkillDraftResourceApi(selectedDraftIdRef.current, row.id);
                      message.success('已删除资源');
                      reloadDrafts();
                      reloadResources();
                    } catch (error) {
                      message.error((error as Error).message);
                    }
                  },
                });
              }}
            >
              删除
            </a>
          ),
        ],
      },
    ],
    [resourceEditable],
  );

  return (
    <ContentContainer heightMode="auto" scrollable padding="16px">
      <Row gutter={16}>
        <Col xs={24} md={12}>
          <ProTable<SkillDraft>
            headerTitle="Skill 草稿"
            cardBordered
            rowKey="id"
            actionRef={draftActionRef}
            columns={draftColumns}
            search={{ labelWidth: 'auto' }}
            request={async () => {
              const data = await listSkillDraftsApi();
              setDraftRowCount(data.length);
              if (selectedDraftIdRef.current != null) {
                const latest = data.find((item) => item.id === selectedDraftIdRef.current);
                if (latest) {
                  setSelectedDraft(latest);
                } else {
                  clearSelection();
                }
              }
              return { data, success: true };
            }}
            pagination={{ defaultPageSize: 20, showSizeChanger: true }}
            scroll={{ x: 900, ...(draftScrollY ? { y: draftScrollY } : {}) }}
            toolBarRender={() => [
              <Button
                key="create"
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => {
                  setEditingDraft(null);
                  setDraftDrawerOpen(true);
                }}
              >
                新建 Skill
              </Button>,
            ]}
            onRow={(record) => ({
              onClick: () => selectDraft(record),
              style: {
                cursor: 'pointer',
                background:
                  selectedDraftId === record.id ? 'rgba(59,130,246,0.08)' : undefined,
              },
            })}
          />
        </Col>

        <Col xs={24} md={12}>
          <ProTable<SkillResource>
            headerTitle={
              <Space size={8} align="center" wrap>
                <span>资源文件</span>
                {selectedDraft && (
                  <Tag
                    closable
                    onClose={(e) => {
                      e.preventDefault();
                      clearSelection();
                    }}
                    style={{ margin: 0 }}
                  >
                    {selectedDraft.name}
                  </Tag>
                )}
              </Space>
            }
            cardBordered
            rowKey={(row) => String(row.id ?? row.path)}
            actionRef={resourceActionRef}
            columns={resourceColumns}
            search={false}
            request={async () => {
              const draftId = selectedDraftIdRef.current;
              if (draftId == null) {
                setResourceRowCount(0);
                return { data: [], success: true };
              }
              const data = await listSkillDraftResourcesApi(draftId);
              setResourceRowCount(data.length);
              return { data, success: true };
            }}
            pagination={{ defaultPageSize: 20, showSizeChanger: true }}
            scroll={{ x: 'max-content', ...(resourceScrollY ? { y: resourceScrollY } : {}) }}
            toolBarRender={() => [
              <Button
                key="create"
                type="primary"
                icon={<PlusOutlined />}
                disabled={!resourceEditable}
                onClick={() => {
                  setEditingResource(null);
                  setResourceDrawerOpen(true);
                }}
              >
                新建资源
              </Button>,
            ]}
            locale={{
              emptyText: selectedDraft ? '暂无资源文件' : '请先选择左侧 Skill 草稿',
            }}
          />
        </Col>
      </Row>

      <SkillDraftDrawer
        open={draftDrawerOpen}
        row={editingDraft}
        onClose={() => setDraftDrawerOpen(false)}
        onSaved={(saved) => {
          reloadDrafts();
          selectDraft(saved);
        }}
      />
      <SkillResourceDrawer
        open={resourceDrawerOpen}
        draftId={selectedDraftId}
        row={editingResource}
        editable={resourceEditable}
        onClose={() => setResourceDrawerOpen(false)}
        onSaved={() => {
          reloadDrafts();
          reloadResources();
        }}
      />
    </ContentContainer>
  );
}
