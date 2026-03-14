package com.yas.backofficebff.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticatedUserTest {

    @Test
    void constructorAndAccessor_ShouldStoreUsername() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("manager");

        assertThat(authenticatedUser.username()).isEqualTo("manager");
    }
}
