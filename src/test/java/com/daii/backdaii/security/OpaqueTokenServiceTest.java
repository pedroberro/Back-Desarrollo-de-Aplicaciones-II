package com.daii.backdaii.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokenServiceTest {
    private final OpaqueTokenService tokens = new OpaqueTokenService();

    @Test
    void generatesUniqueOpaqueTokensAndStableHashes() {
        String first = tokens.generate();
        String second = tokens.generate();

        assertThat(first).isNotEqualTo(second);
        assertThat(tokens.hash(first)).hasSize(64).isEqualTo(tokens.hash(first));
        assertThat(tokens.hash(first)).isNotEqualTo(tokens.hash(second));
    }
}
