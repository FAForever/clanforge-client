package com.faforever.clan_management.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagRuleTest {

    //the faforever rule as shipped in application.properties
    private final TagRule faf = new TagRule("^[A-Za-z0-9]{1,3}$", "1 to 3 letters or numbers");

    @Test
    @DisplayName("one to three letters or digits pass, anything else fails")
    void fafRule() {
        assertThat(faf.accepts("ABC")).isTrue();
        assertThat(faf.accepts("a1")).isTrue();
        assertThat(faf.accepts("7")).isTrue();
        assertThat(faf.accepts("ABCD")).isFalse();
        assertThat(faf.accepts("A-B")).isFalse();
        assertThat(faf.accepts("[†]")).isFalse();
        assertThat(faf.accepts("")).isFalse();
        assertThat(faf.accepts(null)).isFalse();
    }

    @Test
    @DisplayName("another deployment changes the pattern by property, not by code")
    void customRule() {
        TagRule wide = new TagRule("^[A-Z]{2,5}$","2 to 5 capital letters");
        assertThat(wide.accepts("ABCDE")).isTrue();
        assertThat(wide.accepts("abc")).isFalse();
        assertThat(wide.getHint()).isEqualTo("2 to 5 capital letters");
    }
}
