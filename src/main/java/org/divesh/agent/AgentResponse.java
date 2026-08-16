package org.divesh.agent;

public record AgentResponse(
        String command,
        String explanation,
        boolean dangerous
) {}