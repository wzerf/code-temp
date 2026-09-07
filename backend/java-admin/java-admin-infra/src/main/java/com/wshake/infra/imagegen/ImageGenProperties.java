package com.wshake.infra.imagegen;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.image")
public class ImageGenProperties {

    private int maxPromptChars = 4000;

    private int maxImagesPerCall = 4;

    private Duration rateWindow = Duration.ofMinutes(1);

    private int rateBurst = 5;

    private long maxImageBytes = 8 * 1024 * 1024L;

    private boolean uploadToStorage = false;
}
