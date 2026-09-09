package com.wshake.infra.videogen;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.video")
public class VideoGenProperties {

    private int maxPromptChars = 4000;

    private int minDurationSeconds = 1;

    private int maxDurationSeconds = 15;

    private int defaultDurationSeconds = 5;

    private Duration rateWindow = Duration.ofMinutes(1);

    private int rateBurst = 3;

    private Duration pollInterval = Duration.ofSeconds(5);

    private Duration pollTimeout = Duration.ofMinutes(8);

    private long maxVideoBytes = 50 * 1024 * 1024L;

    private boolean uploadToStorage = false;
}
