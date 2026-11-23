package app.privmicro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String jwtSecret,
                            String dataEncryptionKey,
                            boolean cookieSecure,
                            int sessionMinutes,
                            long cleanupIntervalMs) {
}
