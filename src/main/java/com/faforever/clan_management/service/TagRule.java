package com.faforever.clan_management.service;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TagRule {

    private final Pattern pattern;
    private final String hint;

    public TagRule(@Value("${clan.tag.pattern:^[A-Za-z0-9]{1,3}$}") String pattern, @Value("${clan.tag.hint:1 to 3 letters or numbers}") String hint) {
        this.pattern = Pattern.compile(pattern); // the rule is a property so another game can loosen or tighten it without a code change
        this.hint = hint;
    }

    public boolean accepts(String tag) {
        return tag != null && pattern.matcher(tag).matches();
    }

    public String getHint() {
        return hint;
    }
}
