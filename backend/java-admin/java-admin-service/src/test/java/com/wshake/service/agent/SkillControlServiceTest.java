package com.wshake.service.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wshake.common.exception.BizException;
import com.wshake.service.agent.SkillControlModels.CreateSkillDraftCommand;
import com.wshake.service.agent.SkillControlModels.CreateSkillDraftResourceCommand;
import com.wshake.service.agent.SkillControlModels.UpdateSkillDraftResourceCommand;
import com.wshake.service.entity.AgentSkillDraft;
import com.wshake.service.entity.AgentSkillDraftResource;
import com.wshake.service.entity.AgentSkillMarket;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.repository.AgentSkillDraftRepository;
import com.wshake.service.repository.AgentSkillInstallRepository;
import com.wshake.service.repository.AgentSkillMarketRepository;
import com.wshake.service.repository.AgentSkillReleaseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillControlServiceTest {

    private static final String SKILL_MD = """
            ---
            name: code-reviewer
            description: Review pull requests
            ---
            Use the checklist.
            """;

    private final AgentSkillDraftRepository draftRepository = mock(AgentSkillDraftRepository.class);
    private final AgentSkillReleaseRepository releaseRepository = mock(AgentSkillReleaseRepository.class);
    private final AgentSkillMarketRepository marketRepository = mock(AgentSkillMarketRepository.class);
    private final AgentSkillInstallRepository installRepository = mock(AgentSkillInstallRepository.class);
    private SkillControlService service;

    @BeforeEach
    void setUp() {
        service = new SkillControlService(draftRepository, releaseRepository, marketRepository, installRepository);
    }

    @Test
    void approve_market_upsertsCurrentRow_andKeepsPreviousRelease() {
        AgentSkillDraft draft = pendingDraft(SkillControlModels.VISIBILITY_MARKET);
        when(draftRepository.findById(10L)).thenReturn(draft);
        when(draftRepository.listResources(10L)).thenReturn(List.of());
        when(releaseRepository.findLatest(0L, "MARKET", "code-reviewer")).thenReturn(null);
        AtomicLong releaseId = new AtomicLong();
        doAnswer(invocation -> {
                    AgentSkillRelease row = invocation.getArgument(0);
                    row.setId(21L);
                    releaseId.set(21L);
                    return null;
                })
                .when(releaseRepository)
                .insert(any(AgentSkillRelease.class));
        when(marketRepository.findByName("code-reviewer")).thenReturn(null);
        doAnswer(invocation -> {
                    AgentSkillMarket row = invocation.getArgument(0);
                    row.setId(3L);
                    return null;
                })
                .when(marketRepository)
                .insert(any(AgentSkillMarket.class));

        var published = service.approve(10L, 7L);

        assertThat(published.id()).isEqualTo(21L);
        assertThat(published.visibility()).isEqualTo("MARKET");
        assertThat(draft.getStatus()).isEqualTo(SkillControlModels.CONSUMED);
        verify(marketRepository).insert(any(AgentSkillMarket.class));
        verify(marketRepository).replaceResources(eq(3L), anyList());
    }

    @Test
    void approve_private_doesNotWriteMarket() {
        AgentSkillDraft draft = pendingDraft(SkillControlModels.VISIBILITY_PRIVATE);
        when(draftRepository.findById(10L)).thenReturn(draft);
        when(draftRepository.listResources(10L)).thenReturn(List.of());
        when(releaseRepository.findLatest(7L, "PRIVATE", "code-reviewer")).thenReturn(null);
        doAnswer(invocation -> {
                    AgentSkillRelease row = invocation.getArgument(0);
                    row.setId(22L);
                    return null;
                })
                .when(releaseRepository)
                .insert(any(AgentSkillRelease.class));

        var published = service.approve(10L, 7L);

        assertThat(published.visibility()).isEqualTo("PRIVATE");
        assertThat(published.ownerUserId()).isEqualTo(7L);
        verify(marketRepository, never()).insert(any());
        verify(marketRepository, never()).update(any());
    }

    @Test
    void approve_marketUpdate_replacesCurrentRow_withoutMutatingOldRelease() {
        AgentSkillDraft draft = pendingDraft(SkillControlModels.VISIBILITY_MARKET);
        when(draftRepository.findById(10L)).thenReturn(draft);
        when(draftRepository.listResources(10L)).thenReturn(List.of());
        AgentSkillRelease previous = new AgentSkillRelease();
        previous.setId(20L);
        previous.setVersion(1);
        previous.setName("code-reviewer");
        previous.setSkillContent("old");
        previous.setContentHash("old-hash");
        when(releaseRepository.findLatest(0L, "MARKET", "code-reviewer")).thenReturn(previous);
        doAnswer(invocation -> {
                    AgentSkillRelease row = invocation.getArgument(0);
                    row.setId(21L);
                    return null;
                })
                .when(releaseRepository)
                .insert(any(AgentSkillRelease.class));
        AgentSkillMarket current = new AgentSkillMarket();
        current.setId(3L);
        current.setName("code-reviewer");
        current.setCurrentReleaseId(20L);
        current.setSkillContent("old");
        when(marketRepository.findByName("code-reviewer")).thenReturn(current);

        var published = service.approve(10L, 7L);

        assertThat(published.id()).isEqualTo(21L);
        assertThat(published.version()).isEqualTo(2);
        assertThat(previous.getSkillContent()).isEqualTo("old");
        assertThat(current.getCurrentReleaseId()).isEqualTo(21L);
        verify(marketRepository).update(current);
        verify(releaseRepository, never()).update(previous);
    }

    @Test
    void unlistMarket_deletesCurrentRow() {
        AgentSkillMarket current = new AgentSkillMarket();
        current.setId(3L);
        current.setName("code-reviewer");
        current.setCurrentReleaseId(21L);
        AgentSkillRelease release = new AgentSkillRelease();
        release.setId(21L);
        release.setStatus(SkillControlModels.PUBLISHED);
        when(marketRepository.findByName("code-reviewer")).thenReturn(current);
        when(releaseRepository.findById(21L)).thenReturn(release);

        service.unlistMarket("code-reviewer", 7L);

        verify(marketRepository).deleteByName("code-reviewer");
        assertThat(release.getStatus()).isEqualTo(SkillControlModels.DEPRECATED);
    }

    @Test
    void createDraft_rejectsNameMismatch() {
        assertThatThrownBy(() -> service.createDraft(new CreateSkillDraftCommand(
                        7L,
                        "other-name",
                        "Review pull requests",
                        SKILL_MD,
                        SkillControlModels.VISIBILITY_MARKET,
                        Map.of(),
                        null,
                        "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("frontmatter");
    }

    @Test
    void createDraftResource_insertsAndRecalculatesHash() {
        AgentSkillDraft draft = editableDraft();
        when(draftRepository.findById(10L)).thenReturn(draft);
        when(draftRepository.findResourceByDraftIdAndPath(10L, "scripts/check.sh"))
                .thenReturn(null);
        List<AgentSkillDraftResource> stored = new ArrayList<>();
        doAnswer(invocation -> {
                    AgentSkillDraftResource row = invocation.getArgument(0);
                    row.setId(101L);
                    stored.clear();
                    stored.add(row);
                    return null;
                })
                .when(draftRepository)
                .insertResource(any(AgentSkillDraftResource.class));
        when(draftRepository.listResources(10L)).thenAnswer(invocation -> List.copyOf(stored));

        var created = service.createDraftResource(
                new CreateSkillDraftResourceCommand(10L, 7L, "scripts/check.sh", "echo ok"));

        assertThat(created.id()).isEqualTo(101L);
        assertThat(created.path()).isEqualTo("scripts/check.sh");
        assertThat(draft.getContentHash())
                .isEqualTo(AgentSkillContentHash.sha256(SKILL_MD, Map.of("scripts/check.sh", "echo ok")));
        verify(draftRepository).update(draft);
    }

    @Test
    void updateDraftResource_renamesPathAndRejectsDuplicate() {
        AgentSkillDraft draft = editableDraft();
        AgentSkillDraftResource existing = resource(101L, "old.txt", "v1");
        AgentSkillDraftResource conflict = resource(102L, "new.txt", "other");
        when(draftRepository.findById(10L)).thenReturn(draft);
        when(draftRepository.findResourceById(101L)).thenReturn(existing);
        when(draftRepository.findResourceByDraftIdAndPath(10L, "new.txt")).thenReturn(conflict);

        assertThatThrownBy(() -> service.updateDraftResource(
                        new UpdateSkillDraftResourceCommand(10L, 101L, 7L, "new.txt", true, null, false)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteDraftResource_removesAndRecalculatesHash() {
        AgentSkillDraft draft = editableDraft();
        draft.setContentHash(AgentSkillContentHash.sha256(SKILL_MD, Map.of("notes.md", "x")));
        AgentSkillDraftResource existing = resource(101L, "notes.md", "x");
        when(draftRepository.findById(10L)).thenReturn(draft);
        when(draftRepository.findResourceById(101L)).thenReturn(existing);
        when(draftRepository.listResources(10L)).thenReturn(List.of());

        service.deleteDraftResource(10L, 101L, 7L);

        verify(draftRepository).deleteResource(101L);
        assertThat(draft.getContentHash()).isEqualTo(AgentSkillContentHash.sha256(SKILL_MD, Map.of()));
    }

    @Test
    void createDraftResource_rejectsPendingReview() {
        AgentSkillDraft draft = pendingDraft(SkillControlModels.VISIBILITY_PRIVATE);
        when(draftRepository.findById(10L)).thenReturn(draft);

        assertThatThrownBy(
                        () -> service.createDraftResource(new CreateSkillDraftResourceCommand(10L, 7L, "a.txt", "x")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("only draft or rejected");
    }

    private static AgentSkillDraft pendingDraft(String visibility) {
        AgentSkillDraft draft = new AgentSkillDraft();
        draft.setId(10L);
        draft.setName("code-reviewer");
        draft.setDescription("Review pull requests");
        draft.setSkillContent(SKILL_MD);
        draft.setVisibility(visibility);
        draft.setStatus(SkillControlModels.PENDING_REVIEW);
        draft.setOwnerUserId(7L);
        draft.setContentHash(AgentSkillContentHash.sha256(SKILL_MD, Map.of()));
        draft.setRemark("");
        draft.setIsEnabled(1);
        return draft;
    }

    private static AgentSkillDraft editableDraft() {
        AgentSkillDraft draft = pendingDraft(SkillControlModels.VISIBILITY_PRIVATE);
        draft.setStatus(SkillControlModels.DRAFT);
        return draft;
    }

    private static AgentSkillDraftResource resource(Long id, String path, String content) {
        AgentSkillDraftResource row = new AgentSkillDraftResource();
        row.setId(id);
        row.setDraftId(10L);
        row.setResourcePath(path);
        row.setResourceContent(content);
        row.setContentHash(AgentSkillContentHash.sha256(content));
        return row;
    }
}
