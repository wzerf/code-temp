package com.wshake.service.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillDraftResourceRepository;
import com.wshake.service.repository.AgentSkillGitSourceRepository;
import com.wshake.service.repository.AgentSkillGitSyncRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import com.wshake.service.repository.AgentSkillReleaseResourceRepository;
import com.wshake.service.skill.SkillControlService.CreateSkillCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SkillControlService} 状态机与 Release 行为。
 */
class SkillControlServiceTest {

    private final AgentSkillDraftRepository draftRepo = mock(AgentSkillDraftRepository.class);
    private final AgentSkillDraftResourceRepository draftResourceRepo = mock(AgentSkillDraftResourceRepository.class);
    private final AgentSkillReleaseRepository releaseRepo = mock(AgentSkillReleaseRepository.class);
    private final AgentSkillReleaseResourceRepository releaseResourceRepo =
            mock(AgentSkillReleaseResourceRepository.class);
    private final AgentSkillGitSourceRepository gitSourceRepo = mock(AgentSkillGitSourceRepository.class);
    private final AgentSkillGitSyncRepository gitSyncRepo = mock(AgentSkillGitSyncRepository.class);

    private SkillControlService service;

    @BeforeEach
    void init() {
        service = new SkillControlService(
                draftRepo,
                draftResourceRepo,
                releaseRepo,
                releaseResourceRepo,
                gitSourceRepo,
                gitSyncRepo,
                new io.github.linpeilie.Converter());
        // 视图组装所需默认空返回
        when(draftResourceRepo.countByDraftIds(any())).thenReturn(Map.of());
        when(draftResourceRepo.listByDraftId(any())).thenReturn(List.of());
        when(gitSyncRepo.listByDraftIds(any())).thenReturn(List.of());
        when(releaseResourceRepo.countByReleaseIds(any())).thenReturn(Map.of());
    }

    private AgentSkillDraft draft(Long id, String status) {
        AgentSkillDraft d = new AgentSkillDraft();
        d.setId(id);
        d.setOwnerUserId(1L);
        d.setName("code-reviewer");
        d.setVisibility("PRIVATE");
        d.setStatus(status);
        d.setSkillContent("content");
        d.setIsEnabled(1);
        return d;
    }

    @Test
    void createDraft_rejectsDuplicateActiveDraft() {
        when(draftRepo.existsActiveDraft(1L, "code-reviewer", "PRIVATE", null)).thenReturn(true);
        CreateSkillCommand cmd =
                new CreateSkillCommand("code-reviewer", "desc", "content", "PRIVATE", "", 1L, List.of());
        assertThatThrownBy(() -> service.createDraft(cmd))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在活跃草稿");
    }

    @Test
    void createDraft_insertsAndComputesHash() {
        when(draftRepo.existsActiveDraft(1L, "code-reviewer", "PRIVATE", null)).thenReturn(false);
        doAnswer(inv -> {
                    inv.getArgument(0, AgentSkillDraft.class).setId(10L);
                    return 1;
                })
                .when(draftRepo)
                .insert(any(AgentSkillDraft.class));
        when(draftResourceRepo.listByDraftId(10L)).thenReturn(List.of());
        // refreshHash 内部 update
        when(draftRepo.update(any(AgentSkillDraft.class))).thenReturn(1L);
        AgentSkillDraft saved = draft(10L, "DRAFT");
        when(draftRepo.findById(10L)).thenReturn(saved);

        CreateSkillCommand cmd =
                new CreateSkillCommand("code-reviewer", "desc", "SKILL body", "PRIVATE", "", 1L, List.of());
        var view = service.createDraft(cmd);

        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.status()).isEqualTo("DRAFT");
        verify(draftRepo).insert(any(AgentSkillDraft.class));
    }

    @Test
    void submit_requiresEditableStatus() {
        AgentSkillDraft pending = draft(1L, "PENDING_REVIEW");
        when(draftRepo.findById(1L)).thenReturn(pending);
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许");
    }

    @Test
    void submit_rejectsEmptySkillContent() {
        AgentSkillDraft d = draft(1L, "DRAFT");
        d.setSkillContent("  ");
        when(draftRepo.findById(1L)).thenReturn(d);
        assertThatThrownBy(() -> service.submit(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("SKILL.md 内容为空");
        verify(draftRepo, never()).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void submit_movesToPendingReview() {
        AgentSkillDraft d = draft(1L, "DRAFT");
        d.setSkillContent("body");
        when(draftRepo.findById(1L)).thenReturn(d);
        service.submit(1L);
        verify(draftRepo).updateStatus(1L, "PENDING_REVIEW", "", null, null);
    }

    @Test
    void approve_insertsReleaseWithNextVersion_andConsumesDraft() {
        AgentSkillDraft d = draft(1L, "PENDING_REVIEW");
        d.setSkillContent("SKILL body");
        when(draftRepo.findById(1L)).thenReturn(d);
        when(draftResourceRepo.listByDraftId(1L)).thenReturn(List.of());
        when(releaseRepo.listByNameAllVersions(1L, "PRIVATE", "code-reviewer")).thenReturn(List.of(release(5L, 2)));
        doAnswer(inv -> {
                    inv.getArgument(0, AgentSkillRelease.class).setId(100L);
                    return 1;
                })
                .when(releaseRepo)
                .insert(any(AgentSkillRelease.class));
        when(releaseRepo.findById(100L)).thenReturn(release(100L, 3));
        // 提交后 approve 再次 require 草稿会找不到?draft 已在 findById(1L) 返回——updateStatus 后无妨

        var view = service.approve(1L);

        assertThat(view.version()).isEqualTo(3);
        assertThat(view.id()).isEqualTo(100L);
        verify(releaseRepo).insert(any(AgentSkillRelease.class));
        verify(draftRepo).updateStatus(1L, "CONSUMED", "", null, null);
    }

    private AgentSkillRelease release(Long id, int version) {
        AgentSkillRelease r = new AgentSkillRelease();
        r.setId(id);
        r.setOwnerUserId(1L);
        r.setName("code-reviewer");
        r.setVisibility("PRIVATE");
        r.setStatus("PUBLISHED");
        r.setVersion(version);
        return r;
    }

    @Test
    void marketList_takesLatestVersionPerName() {
        AgentSkillRelease v1 = release(1L, 1);
        v1.setName("a");
        AgentSkillRelease v2 = release(2L, 2);
        v2.setName("a");
        AgentSkillRelease other = release(3L, 1);
        other.setName("b");
        when(releaseRepo.listMarket()).thenReturn(List.of(v1, other, v2));
        var market = service.listMarket();
        assertThat(market).hasSize(2);
        assertThat(market.get(0).name()).isEqualTo("a");
        assertThat(market.get(0).version()).isEqualTo(2);
    }

    @Test
    void deprecate_releaseOnlyChangesStatus() {
        when(releaseRepo.findById(9L)).thenReturn(release(9L, 1));
        service.deprecate(9L);
        verify(releaseRepo).updateStatus(9L, "DEPRECATED");
    }

    @Test
    void reject_onlyFromPendingReview() {
        AgentSkillDraft d = draft(1L, "REJECTED");
        when(draftRepo.findById(1L)).thenReturn(d);
        assertThatThrownBy(() -> service.reject(1L, "no")).isInstanceOf(BizException.class);
    }
}
