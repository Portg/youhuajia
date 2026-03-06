package com.youhua.infra.health;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class DeepSeekHealthIndicator implements HealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ChatClient chatClient;

    public DeepSeekHealthIndicator(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public Health health() {
        long start = System.currentTimeMillis();
        try {
            String response = chatClient.prompt()
                    .user("1+1")
                    .call()
                    .content();
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > TIMEOUT.toMillis()) {
                throw new TimeoutException("DeepSeek response exceeded " + TIMEOUT.toSeconds() + "s timeout");
            }
            return Health.up()
                    .withDetail("latencyMs", elapsed)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
