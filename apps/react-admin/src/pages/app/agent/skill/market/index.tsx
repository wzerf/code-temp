import { useRef } from 'react';
import { Button, Popconfirm, Tag, message } from 'antd';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { ProTable } from '@ant-design/pro-components';
import { deprecateSkillReleaseApi, getSkillReleaseApi, installSkillApi, listSkillMarketApi, unlistSkillMarketApi } from '@/api/rest/agent';
import type { SkillMarket } from '@/api/rest/types';
import ContentContainer from '@/layouts/components/PageContainer/ContentContainer';

export default function SkillMarketPage() {
  const actionRef = useRef<ActionType | undefined>(undefined);
  const reload = () => actionRef.current?.reload();
  const action = async (work: () => Promise<unknown>, text: string) => {
 try {
 await work(); message.success(text); reload(); 
} catch (error) {
 message.error((error as Error).message); 
} 
};
  const columns: ProColumns<SkillMarket>[] = [
    { title: '名称', dataIndex: 'name', width: 200 },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    { title: '来源', dataIndex: 'source', width: 120, render: (_, row) => <Tag>{row.source}</Tag> },
    { title: '内容哈希', dataIndex: 'contentHash', width: 180, search: false, ellipsis: true },
    { title: '当前 Release', dataIndex: 'currentReleaseId', width: 130, search: false },
    { title: '操作', valueType: 'option', width: 220, fixed: 'right', render: (_, row) => [
      <a key="detail" onClick={() => action(async () => {
 const release = await getSkillReleaseApi(row.currentReleaseId); message.info(`${release.name} v${release.version}：${release.description}`); 
}, '已读取 Release')}>详情</a>,
      <a key="install" onClick={() => action(() => installSkillApi(row.name), '已安装；请在 Agent 草稿中显式绑定')}>安装</a>,
      <a key="deprecate" onClick={() => action(() => deprecateSkillReleaseApi(row.currentReleaseId), '已弃用当前 Release')}>弃用</a>,
      <Popconfirm key="unlist" title="下架市场 Skill" description="下架不会影响已发布 Agent Revision。" onConfirm={() => action(() => unlistSkillMarketApi(row.name), '已下架')}><a style={{ color: '#ff4d4f' }}>下架</a></Popconfirm>,
    ] },
  ];
  return <ContentContainer scrollable><ProTable<SkillMarket> rowKey="id" actionRef={actionRef} headerTitle="Skill 市场" columns={columns}
    request={async () => ({ data: await listSkillMarketApi(), success: true })} search={{ labelWidth: 'auto' }} pagination={{ defaultPageSize: 20 }} scroll={{ x: 1050 }}
    toolBarRender={() => [<Button key="refresh" onClick={reload}>刷新</Button>]} /></ContentContainer>;
}
