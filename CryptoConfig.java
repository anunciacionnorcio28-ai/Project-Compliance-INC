package app.privmicro.config;

import app.privmicro.crypto.FieldCrypto;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {
    private final AppProperties props;

    public CryptoConfig(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (props.jwtSecret() == null || props.jwtSecret().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        FieldCrypto.init(props.dataEncryptionKey());
    }
}
