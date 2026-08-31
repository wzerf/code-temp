package com.wshake.api.vo;

import java.util.List;

public record AgentSessionHistoryVO(AgentSessionVO session, List<MessageVO> messages) {

    public record MessageVO(String id, String role, String content, String thinking, String createdAt) {}
}
