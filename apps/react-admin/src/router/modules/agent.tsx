import type { AppRouteObject } from '@/core/router/types';
import AgentManagePage from '@/pages/app/agent/manage';
import SkillDraftsPage from '@/pages/app/agent/skill/drafts';
import SkillGitPage from '@/pages/app/agent/skill/git';
import SkillMarketPage from '@/pages/app/agent/skill/market';

const agentRoutes: AppRouteObject[] = [
  {
    name: 'Agent',
    path: 'agent',
    meta: { title: 'Agent', icon: 'lucide:bot', order: 2002 },
    children: [
      { name: 'AgentManage', path: 'manage', element: <AgentManagePage />, meta: { title: 'Agent 管理', icon: 'lucide:bot', order: 2 } },
      { name: 'AgentSkillDrafts', path: 'skill/drafts', element: <SkillDraftsPage />, meta: { title: 'Skill 草稿', icon: 'lucide:package', order: 3 } },
      { name: 'AgentSkillMarket', path: 'skill/market', element: <SkillMarketPage />, meta: { title: 'Skill 市场', icon: 'lucide:store', order: 4 } },
      { name: 'AgentSkillGit', path: 'skill/git', element: <SkillGitPage />, meta: { title: 'Git Skill 来源', icon: 'lucide:git-branch', order: 5 } },
    ],
  },
];

export default agentRoutes;
