package com.trademaster.brokerauth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultToken;

import java.net.URI;

/**
 * Test Configuration for Vault KV v2 Engine
 *
 * MANDATORY: Proper Vault configuration for integration tests - Rule #23
 * MANDATORY: Virtual Thread compatible configuration - Rule #12
 */
@TestConfiguration
public class TestVaultConfig {

    @Value("${spring.cloud.vault.uri}")
    private String vaultUri;

    @Value("${spring.cloud.vault.token}")
    private String vaultToken;

    /**
     * Configure VaultTemplate for KV v2 engine support
     * MANDATORY: KV v2 requires proper path transformation - Rule #23
     */
    @Bean
    public VaultTemplate vaultTemplate() {
        try {
            VaultEndpoint endpoint = VaultEndpoint.from(URI.create(vaultUri));
            VaultTemplate template = new VaultTemplate(
                endpoint,
                new TokenAuthentication(VaultToken.of(vaultToken))
            );
            return template;
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure VaultTemplate", e);
        }
    }
}
