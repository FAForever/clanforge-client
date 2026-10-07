package com.faforever.clan_management.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Regions {

    private final List<String> names;

    public Regions(@Value("${clan.regions:EU/NA,EU,NA,Asia,Oceania,Global}") String csv) {
        names = new ArrayList<>();
        for (String n : csv.split(",")) {
            if (!n.isBlank()) names.add(n.trim()); // the regions a clan can pick, a property so another game lists its own
        }
    }

    public List<String> getNames() {
        return names;
    }

    public boolean accepts(String region) {
        return region == null || region.isBlank() || names.contains(region.trim());
    }
}
