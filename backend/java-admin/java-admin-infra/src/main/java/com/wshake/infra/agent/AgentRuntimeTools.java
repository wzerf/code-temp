package com.wshake.infra.agent;

import com.wshake.common.time.TimeZones;
import io.agentscope.core.tool.Tool;
import org.springframework.stereotype.Component;

/** 当前窄链路唯一允许的可信 Java Tool。 */
@Component
public class AgentRuntimeTools {

    @Tool(name = "get_platform_time", description = "返回平台当前 UTC 时间；仅在用户明确询问当前时间时调用。", readOnly = true)
    public String getPlatformTime() {
        return TimeZones.instant().toString();
    }
}
