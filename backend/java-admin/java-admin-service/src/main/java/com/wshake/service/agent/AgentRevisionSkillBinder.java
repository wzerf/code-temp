package com.wshake.service.agent;

import com.wshake.common.constant.StatusFlags;
import com.wshake.common.exception.BizException;
import com.wshake.common.result.ResultCode;
import com.wshake.service.agent.AgentControlModels.SkillBindingCommand;
import com.wshake.service.agent.AgentControlModels.SkillBindingView;
import com.wshake.service.agent.AgentControlModels.SkillSnapshot;
import com.wshake.service.entity.AgentRevisionSkillBinding;
import com.wshake.service.entity.AgentSkillRelease;
import com.wshake.service.repository.AgentRevisionSkillBindingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRevisionSkillBinder {

    private final AgentRevisionSkillBindingRepository bindingRepository;
    private final SkillControlService skillControlService;

    public void replaceDraftBindings(Long revisionId, List<SkillBindingCommand> commands, Long ownerUserId) {
        List<AgentRevisionSkillBinding> rows = resolve(commands, ownerUserId);
        rows.forEach(row -> row.setAgentRevisionId(revisionId));
        bindingRepository.replace(revisionId, rows);
    }

    public void copyToPublished(Long draftRevisionId, Long publishedRevisionId, Long ownerUserId) {
        List<SkillBindingCommand> commands = bindingRepository.listByRevisionId(draftRevisionId).stream()
                .map(row -> new SkillBindingCommand(row.getSkillReleaseId(), isWinner(row)))
                .toList();
        List<AgentRevisionSkillBinding> copied = resolve(commands, ownerUserId);
        copied.forEach(row -> row.setAgentRevisionId(publishedRevisionId));
        bindingRepository.insertAll(copied);
    }

    public List<SkillBindingView> list(Long revisionId) {
        return bindingRepository.listByRevisionId(revisionId).stream()
                .map(row -> new SkillBindingView(
                        row.getSkillReleaseId(), row.getSkillName(), row.getContentHash(), isWinner(row)))
                .toList();
    }

    public List<SkillSnapshot> snapshotsForRun(Long revisionId) {
        List<SkillSnapshot> snapshots = new ArrayList<>();
        for (AgentRevisionSkillBinding binding : bindingRepository.listByRevisionId(revisionId)) {
            AgentSkillRelease release = skillControlService.requireReleaseForRun(binding.getSkillReleaseId());
            if (!binding.getContentHash().equals(release.getContentHash())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "skill binding content hash has drifted");
            }
            snapshots.add(new SkillSnapshot(
                    release.getName(),
                    release.getDescription(),
                    release.getSkillContent(),
                    release.getSource(),
                    release.getContentHash(),
                    skillControlService.resourcesOf(release.getId())));
        }
        return snapshots;
    }

    private List<AgentRevisionSkillBinding> resolve(List<SkillBindingCommand> commands, Long ownerUserId) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Map<String, List<ResolvedBinding>> byName = new LinkedHashMap<>();
        for (SkillBindingCommand command : commands) {
            if (command == null || command.skillReleaseId() == null) {
                throw BizException.of(ResultCode.PARAM_INVALID, "skillReleaseId is required");
            }
            AgentSkillRelease release = skillControlService.requirePublishedRelease(command.skillReleaseId());
            if (!skillControlService.canBind(ownerUserId, release)) {
                throw BizException.of(ResultCode.AUTH_FORBIDDEN, "skill is not installed or owned by current user");
            }
            byName.computeIfAbsent(release.getName(), ignored -> new ArrayList<>())
                    .add(new ResolvedBinding(release, command.overrideWinner()));
        }
        List<AgentRevisionSkillBinding> rows = new ArrayList<>();
        for (Map.Entry<String, List<ResolvedBinding>> entry : byName.entrySet()) {
            List<ResolvedBinding> candidates = entry.getValue();
            ResolvedBinding selected = selectWinner(entry.getKey(), candidates);
            AgentRevisionSkillBinding row = new AgentRevisionSkillBinding();
            row.setSkillReleaseId(selected.release().getId());
            row.setSkillName(selected.release().getName());
            row.setContentHash(selected.release().getContentHash());
            row.setOverrideWinner(
                    selected.overrideWinner() || candidates.size() > 1 ? StatusFlags.ENABLED : StatusFlags.DISABLED);
            rows.add(row);
        }
        return rows;
    }

    private static ResolvedBinding selectWinner(String skillName, List<ResolvedBinding> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        List<ResolvedBinding> winners =
                candidates.stream().filter(ResolvedBinding::overrideWinner).toList();
        if (winners.size() != 1) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "skill name conflict must declare exactly one overrideWinner: " + skillName);
        }
        return winners.get(0);
    }

    private static boolean isWinner(AgentRevisionSkillBinding row) {
        return row.getOverrideWinner() != null && row.getOverrideWinner() != StatusFlags.DISABLED;
    }

    private record ResolvedBinding(AgentSkillRelease release, boolean overrideWinner) {}
}
