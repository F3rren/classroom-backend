package com.classroom.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The external contract of Role.
 *
 * The test on toAuthority() is the most important in the file: the
 * @PreAuthorize("hasRole('ADMIN')") expressions are SpEL strings the compiler does not check,
 * so if somebody changed the constant's name or the prefix, authorisation would break in
 * silence, with no compilation error at all. That link is pinned down here.
 */
class RoleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authorityMatchesTheStringUsedInPreAuthorize() {
        // hasRole('ADMIN') cerca l'authority "ROLE_ADMIN"
        assertThat(Role.ADMIN.toAuthority()).isEqualTo("ROLE_ADMIN");
        assertThat(Role.USER.toAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void serializesLowercaseBecauseTheFrontendComparesWithAdmin() throws Exception {
        // a client compares: user?.role === "admin"
        assertThat(objectMapper.writeValueAsString(Role.ADMIN)).isEqualTo("\"admin\"");
        assertThat(objectMapper.writeValueAsString(Role.USER)).isEqualTo("\"user\"");
    }

    @Test
    void matchesTheDatabaseCheckConstraint() {
        // user_role_check admits exactly 'admin' and 'user'
        assertThat(java.util.Arrays.stream(Role.values()).map(Role::getValue))
                .containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertThat(Role.from("ADMIN")).isEqualTo(Role.ADMIN);
        assertThat(Role.from(" Admin ")).isEqualTo(Role.ADMIN);
        assertThat(Role.from(null)).isNull();
        assertThatThrownBy(() -> Role.from("superuser")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theJpaConverterWritesTheLowercaseValueOnDisk() {
        // this is what keeps the column inside user_role_check
        Role.JpaConverter converter = new Role.JpaConverter();

        assertThat(converter.convertToDatabaseColumn(Role.ADMIN)).isEqualTo("admin");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute("user")).isEqualTo(Role.USER);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
