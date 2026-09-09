package com.wshake.infra.agent.runtime;

public final class AgentContext {

    private static final InheritableThreadLocal<AgentRunPlan> HOLDER = new InheritableThreadLocal<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, AgentRunPlan.ImageModelConfig>
            SESSION_IMAGE_MODELS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, AgentRunPlan.VideoModelConfig>
            SESSION_VIDEO_MODELS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Long, AgentRunPlan> SESSION_PLANS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private AgentContext() {}

    public static void set(AgentRunPlan plan) {
        HOLDER.set(plan);
        if (plan != null && plan.sessionId() != null) {
            SESSION_PLANS.put(plan.sessionId(), plan);
            rememberMedia(plan.sessionId(), plan);
        }
    }

    public static void setForSession(Long sessionId, AgentRunPlan plan) {
        HOLDER.set(plan);
        if (sessionId != null && plan != null) {
            SESSION_PLANS.put(sessionId, plan);
            rememberMedia(sessionId, plan);
        }
    }

    private static void rememberMedia(Long sessionId, AgentRunPlan plan) {
        if (plan.imageModel() != null) {
            SESSION_IMAGE_MODELS.put(sessionId, plan.imageModel());
        } else {
            SESSION_IMAGE_MODELS.remove(sessionId);
        }
        if (plan.videoModel() != null) {
            SESSION_VIDEO_MODELS.put(sessionId, plan.videoModel());
        } else {
            SESSION_VIDEO_MODELS.remove(sessionId);
        }
    }

    public static AgentRunPlan snapshot(Long sessionId) {
        if (sessionId == null) return HOLDER.get();
        AgentRunPlan p = SESSION_PLANS.get(sessionId);
        return p != null ? p : HOLDER.get();
    }

    public static AgentRunPlan get() {
        return HOLDER.get();
    }

    public static AgentRunPlan.ImageModelConfig imageModelOrNull() {
        AgentRunPlan plan = HOLDER.get();
        if (plan != null && plan.imageModel() != null) {
            return plan.imageModel();
        }
        if (!SESSION_PLANS.isEmpty()) {
            return SESSION_PLANS.values().iterator().next().imageModel();
        }
        return null;
    }

    public static AgentRunPlan.ImageModelConfig imageModelOrNull(io.agentscope.core.tool.ToolCallParam param) {
        if (param != null) {
            try {
                var rc = param.getRuntimeContext();
                if (rc != null) {
                    String sid = rc.getSessionId();
                    if (sid != null && !sid.isBlank()) {
                        Long id = Long.valueOf(sid.trim());
                        AgentRunPlan.ImageModelConfig cfg = SESSION_IMAGE_MODELS.get(id);
                        if (cfg != null) return cfg;
                        AgentRunPlan p = SESSION_PLANS.get(id);
                        if (p != null) return p.imageModel();
                    }
                    Object attr = rc.get("agui.threadId");
                    if (attr instanceof String s && !s.isBlank()) {
                        Long id = Long.valueOf(s.trim());
                        AgentRunPlan.ImageModelConfig cfg = SESSION_IMAGE_MODELS.get(id);
                        if (cfg != null) return cfg;
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AgentContext.class).debug("imageModel resolve", e);
            }
        }
        AgentRunPlan plan = HOLDER.get();
        if (plan != null) return plan.imageModel();
        if (!SESSION_PLANS.isEmpty())
            return SESSION_PLANS.values().iterator().next().imageModel();
        return null;
    }

    public static AgentRunPlan planFor(io.agentscope.core.tool.ToolCallParam param) {
        if (param != null && param.getRuntimeContext() != null) {
            String sid = param.getRuntimeContext().getSessionId();
            if (sid != null && !sid.isBlank()) {
                try {
                    Long id = Long.valueOf(sid.trim());
                    AgentRunPlan p = SESSION_PLANS.get(id);
                    if (p != null) {
                        return p;
                    }
                } catch (NumberFormatException e) {
                    org.slf4j.LoggerFactory.getLogger(AgentContext.class).debug("bad sessionId {}", sid, e);
                }
            }
        }
        return HOLDER.get();
    }

    public static AgentRunPlan.VideoModelConfig videoModelOrNull() {
        AgentRunPlan plan = HOLDER.get();
        if (plan != null && plan.videoModel() != null) {
            return plan.videoModel();
        }
        if (!SESSION_PLANS.isEmpty()) {
            return SESSION_PLANS.values().iterator().next().videoModel();
        }
        return null;
    }

    public static AgentRunPlan.VideoModelConfig videoModelOrNull(io.agentscope.core.tool.ToolCallParam param) {
        if (param != null) {
            try {
                var rc = param.getRuntimeContext();
                if (rc != null) {
                    String sid = rc.getSessionId();
                    if (sid != null && !sid.isBlank()) {
                        Long id = Long.valueOf(sid.trim());
                        AgentRunPlan.VideoModelConfig cfg = SESSION_VIDEO_MODELS.get(id);
                        if (cfg != null) return cfg;
                        AgentRunPlan p = SESSION_PLANS.get(id);
                        if (p != null) return p.videoModel();
                    }
                    Object attr = rc.get("agui.threadId");
                    if (attr instanceof String s && !s.isBlank()) {
                        Long id = Long.valueOf(s.trim());
                        AgentRunPlan.VideoModelConfig cfg = SESSION_VIDEO_MODELS.get(id);
                        if (cfg != null) return cfg;
                    }
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AgentContext.class).debug("videoModel resolve", e);
            }
        }
        AgentRunPlan plan = HOLDER.get();
        if (plan != null) return plan.videoModel();
        if (!SESSION_PLANS.isEmpty())
            return SESSION_PLANS.values().iterator().next().videoModel();
        return null;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static void clear(Long sessionId) {
        HOLDER.remove();
    }

    public static void evict(Long sessionId) {
        if (sessionId != null) {
            SESSION_IMAGE_MODELS.remove(sessionId);
            SESSION_VIDEO_MODELS.remove(sessionId);
            SESSION_PLANS.remove(sessionId);
        }
    }
}
