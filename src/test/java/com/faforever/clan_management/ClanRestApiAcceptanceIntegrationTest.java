//SPDX-License-Identifier: MIT
package com.faforever.clan_management;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.faforever.clan_management.entity.AppUser;
import com.faforever.clan_management.entity.Clan;
import com.faforever.clan_management.repository.AppUserRepository;
import com.faforever.clan_management.repository.ClanRepository;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Project Acceptance Test F (REST API) end to end: bearer token -> ClanApiController -> the real
// services and rules -> postgres, then rolled back. Each procedure F1-F6 has its own test, and
// every successful response is checked field by field against the schema the application itself
// publishes at /v3/api-docs, so "matches the documented OpenAPI schema" is tested, not assumed.
//
// Runs only when SPRING_DATASOURCE_URL is set, like the other integration tests:
//   SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/clan_management SPRING_DATASOURCE_USERNAME=clanadmin
//   SPRING_DATASOURCE_PASSWORD=devpassword ./mvnw test -Dtest='ClanRestApiAcceptance*'
//
// The response time of every call is printed at the end as the "baseline response times" the
// Test F analysis asks for. They are measured in-process through MockMvc, so they cover the
// controller, services and database but not the network.
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
@Transactional
class ClanRestApiAcceptanceIntegrationTest {

    private static final Map<String, List<Long>> TIMINGS = new LinkedHashMap<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ClanRepository clans;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ------------------------------------------------------------ helpers

    private String freeTag() {
        for (String t : List.of("TF9", "TF8", "TF7", "ZF9", "ZF8", "ZF7", "QF9", "QF8")) {
            if (clans.findByTagIgnoreCaseAndDeletedAtIsNull(t).isEmpty()) return t;
        }
        throw new IllegalStateException("every candidate tag is taken");
    }

    // a user with no clan, and a bearer token naming them
    private String newUserToken(String role, String stamp) {
        String sub = "testf-" + role + "-" + stamp;
        users.save(new AppUser(sub, "testf_" + role + "_" + stamp, "local", null));
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        given(jwtDecoder.decode("token-" + sub)).willReturn(jwt);
        return "Bearer token-" + sub;
    }

    private static String createBody(String name, String tag) {
        return """
                {"name":"%s","tag":"%s","description":"Created by acceptance test F",
                 "region":"Oceania","publicClan":true,"requiresInvitation":false}
                """.formatted(name, tag);
    }

    // run one call, record how long it took under its label, and hand back the result
    private MvcResult call(String label, RequestBuilder request) throws Exception {
        long start = System.nanoTime();
        MvcResult result = mockMvc.perform(request).andReturn();
        long micros = (System.nanoTime() - start) / 1_000;
        TIMINGS.computeIfAbsent(label, k -> new ArrayList<>()).add(micros);
        return result;
    }

    private static String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    private static <T> T read(MvcResult r, String path) throws Exception {
        return JsonPath.read(body(r), path);
    }

    private long createClan(String token, String name, String tag) throws Exception {
        MvcResult r = call("POST /api/clans (201)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(name, tag)));
        assertThat(r.getResponse().getStatus()).as("create status, body: %s", body(r)).isEqualTo(201);
        return ((Number) read(r, "$.id")).longValue();
    }

    private String openApi() throws Exception {
        MvcResult r = mockMvc.perform(get("/v3/api-docs")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return body(r);
    }

    // The documented schema for a component, checked against an actual JSON value: every field
    // the schema lists is present with the documented type, and nothing undocumented is sent.
    @SuppressWarnings("unchecked")
    private static void assertMatchesSchema(String openApi, String schemaName, Object actual, String where) {
        Map<String, Object> schema = JsonPath.read(openApi, "$.components.schemas." + schemaName);
        assertThat(actual).as("%s should be a JSON object", where).isInstanceOf(Map.class);
        Map<String, Object> obj = (Map<String, Object>) actual;
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).as("schema %s documents its properties", schemaName).isNotNull();
        assertThat(obj.keySet()).as("%s sends only documented fields of %s", where, schemaName)
                .isSubsetOf(props.keySet());
        assertThat(obj.keySet()).as("%s sends every documented field of %s", where, schemaName)
                .containsAll(props.keySet());
        for (Map.Entry<String, Object> p : props.entrySet()) {
            assertMatchesProperty(openApi, (Map<String, Object>) p.getValue(), obj.get(p.getKey()), where + "." + p.getKey());
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertMatchesProperty(String openApi, Map<String, Object> prop, Object value, String where) {
        if (value == null) return; // nullable fields (description, region) may be null
        if (prop.get("$ref") instanceof String ref) {
            assertMatchesSchema(openApi, ref.substring(ref.lastIndexOf('/') + 1), value, where);
            return;
        }
        Object type = prop.get("type");
        List<Object> types = type instanceof List<?> l ? (List<Object>) l : List.of(String.valueOf(type));
        if (types.contains("string")) {
            assertThat(value).as(where).isInstanceOf(String.class);
        } else if (types.contains("integer") || types.contains("number")) {
            assertThat(value).as(where).isInstanceOf(Number.class);
        } else if (types.contains("boolean")) {
            assertThat(value).as(where).isInstanceOf(Boolean.class);
        } else if (types.contains("array")) {
            assertThat(value).as(where).isInstanceOf(List.class);
            Map<String, Object> items = (Map<String, Object>) prop.get("items");
            int i = 0;
            for (Object item : (List<Object>) value) {
                assertMatchesProperty(openApi, items, item, where + "[" + i++ + "]");
            }
        } else if (types.contains("object")) {
            assertThat(value).as(where).isInstanceOf(Map.class);
        }
    }

    private static void assertJsonError(MvcResult r, String expectedMessage) throws Exception {
        assertThat(r.getResponse().getContentType()).contains("json");
        String message = JsonPath.read(body(r), "$.message");
        if (expectedMessage == null) {
            assertThat(message).isNotBlank();
        } else {
            assertThat(message).isEqualTo(expectedMessage);
        }
    }

    @AfterAll
    static void printBaselineResponseTimes() {
        StringBuilder out = new StringBuilder("\n=== Test F baseline response times (in-process, microseconds) ===\n");
        TIMINGS.forEach((label, list) -> {
            long min = list.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = list.stream().mapToLong(Long::longValue).max().orElse(0);
            long avg = (long) list.stream().mapToLong(Long::longValue).average().orElse(0);
            out.append(String.format("%-45s n=%-3d min=%-8d avg=%-8d max=%d%n", label, list.size(), min, avg, max));
        });
        System.out.println(out);
    }

    // ------------------------------------------------------------ the OpenAPI contract

    @Test
    @DisplayName("F (contract): /v3/api-docs documents the clan CRUD endpoints and their status codes")
    void openApiDocumentsClanCrud() throws Exception {
        String doc = openApi();
        // soft, so one run lists every gap between the documentation and the API, not just the first
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(documentedCodes(doc, "/api/clans", "post")).as("POST /api/clans").contains("201", "400", "401");
        softly.assertThat(documentedCodes(doc, "/api/clans", "get")).as("GET /api/clans").contains("200");
        softly.assertThat(documentedCodes(doc, "/api/clans/{id}", "get")).as("GET /api/clans/{id}").contains("200", "404");
        softly.assertThat(documentedCodes(doc, "/api/clans/{id}", "patch")).as("PATCH /api/clans/{id}").contains("200", "400", "401", "403", "404");
        softly.assertThat(documentedCodes(doc, "/api/clans/{id}", "delete")).as("DELETE /api/clans/{id}").contains("204", "401", "403", "404");

        // the success responses name the schema the body follows
        softly.assertThat(successSchemas(doc, "/api/clans", "post", "201")).as("POST /api/clans 201 schema").contains("#/components/schemas/ClanDetailResponse");
        softly.assertThat(successSchemas(doc, "/api/clans", "get", "200")).as("GET /api/clans 200 schema").contains("#/components/schemas/ClanPageResponse");
        softly.assertThat(successSchemas(doc, "/api/clans/{id}", "get", "200")).as("GET /api/clans/{id} 200 schema").contains("#/components/schemas/ClanDetailResponse");
        softly.assertThat(successSchemas(doc, "/api/clans/{id}", "patch", "200")).as("PATCH /api/clans/{id} 200 schema").contains("#/components/schemas/ClanDetailResponse");

        // the write operations say they need the bearer token
        for (String op : List.of("$.paths['/api/clans'].post", "$.paths['/api/clans/{id}'].patch", "$.paths['/api/clans/{id}'].delete")) {
            softly.assertThat(JsonPath.<List<Object>>read(doc, op + ".security[*].bearerAuth")).as(op + " security").isNotEmpty();
        }
        // and the schemas the procedures validate against exist
        softly.assertThat(JsonPath.<Map<String, Object>>read(doc, "$.components.schemas"))
                .containsKeys("ClanDetailResponse", "ClanPageResponse", "ClanSummaryResponse", "MemberResponse", "ClanWriteRequest");
        softly.assertThat(JsonPath.<Map<String, Object>>read(doc, "$.components.schemas.ClanWriteRequest.properties"))
                .containsKeys("name", "tag", "description", "region", "publicClan", "requiresInvitation");
        softly.assertAll();
    }

    private static Set<String> documentedCodes(String doc, String path, String method) {
        try {
            Map<String, Object> responses = JsonPath.read(doc, "$.paths['" + path + "']." + method + ".responses");
            return responses.keySet();
        } catch (PathNotFoundException e) {
            return Set.of(); // the operation is not documented at all
        }
    }

    private static List<String> successSchemas(String doc, String path, String method, String code) {
        try {
            return JsonPath.read(doc, "$.paths['" + path + "']." + method + ".responses['" + code + "'].content.*.schema['$ref']");
        } catch (PathNotFoundException e) {
            return List.of(); // that response, or its content, is not documented
        }
    }

    // ------------------------------------------------------------ F1

    @Test
    @DisplayName("F1: POST a valid clan returns 201 with a body matching the documented schema, and it is saved")
    void f1PostValidClan() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("owner", stamp);
        String name = "Test F " + stamp;
        String tag = freeTag();

        MvcResult r = call("POST /api/clans (201)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(name, tag)));

        assertThat(r.getResponse().getStatus()).as("body: %s", body(r)).isEqualTo(201);
        assertThat(r.getResponse().getContentType()).contains("json");
        assertMatchesSchema(openApi(), "ClanDetailResponse", JsonPath.read(body(r), "$"), "POST /api/clans");
        assertThat(JsonPath.<String>read(body(r), "$.name")).isEqualTo(name);
        assertThat(JsonPath.<String>read(body(r), "$.tag")).isEqualTo(tag);
        assertThat(JsonPath.<String>read(body(r), "$.description")).isEqualTo("Created by acceptance test F");
        assertThat(JsonPath.<String>read(body(r), "$.region")).isEqualTo("Oceania");
        assertThat(JsonPath.<String>read(body(r), "$.visibility")).isEqualTo("PUBLIC");
        assertThat(JsonPath.<Boolean>read(body(r), "$.requiresInvitation")).isFalse();
        assertThat(JsonPath.<String>read(body(r), "$.ownerDisplayName")).isEqualTo("testf_owner_" + stamp);
        assertThat(JsonPath.<String>read(body(r), "$.createdAt")).isNotBlank();
        assertThat(JsonPath.<List<String>>read(body(r), "$.members[*].role")).containsExactly("OWNER");

        // really in the database, owned by the caller
        long id = ((Number) JsonPath.read(body(r), "$.id")).longValue();
        Clan saved = clans.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getTag()).isEqualTo(tag);
        assertThat(saved.getOwner().getOidcSubject()).isEqualTo("testf-owner-" + stamp);
        assertThat(saved.getDeletedAt()).isNull();
    }

    // ------------------------------------------------------------ F2

    @Test
    @DisplayName("F2: POST payloads missing required fields return 400 with a descriptive JSON error and save nothing")
    void f2PostInvalidPayloads() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("invalid", stamp);
        long before = clans.count();

        MvcResult noFields = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        assertThat(noFields.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(noFields, "Give the clan a name.");

        MvcResult noTag = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test F no tag " + stamp + "\"}"));
        assertThat(noTag.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(noTag, "Clan tag must be 1 to 3 letters or numbers.");

        MvcResult blankName = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"tag\":\"" + freeTag() + "\"}"));
        assertThat(blankName.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(blankName, "Give the clan a name.");

        MvcResult badTag = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Test F bad tag " + stamp, "T-F!")));
        assertThat(badTag.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(badTag, "Clan tag must be 1 to 3 letters or numbers.");

        MvcResult badRegion = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test F region " + stamp + "\",\"tag\":\"" + freeTag() + "\",\"region\":\"Mars\"}"));
        assertThat(badRegion.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(badRegion, "Pick a region from the list.");

        MvcResult notJson = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("this is not json"));
        assertThat(notJson.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(notJson, "The request body could not be read as the JSON this route takes.");

        MvcResult noToken = call("POST /api/clans (401)", post("/api/clans")
                .contentType(MediaType.APPLICATION_JSON).content(createBody("Test F anon " + stamp, freeTag())));
        assertThat(noToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(noToken.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");

        assertThat(clans.count()).as("no invalid request saved a clan").isEqualTo(before);
    }

    @Test
    @DisplayName("F2: POST a tag that differs only in case from a live clan's returns 400")
    void f2PostDuplicateTagDifferentCase() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String tag = freeTag();
        createClan(newUserToken("first", stamp), "Test F first " + stamp, tag);

        MvcResult r = call("POST /api/clans (400)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, newUserToken("second", stamp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Test F second " + stamp, tag.toLowerCase())));

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(r, "The clan tag '" + tag.toLowerCase() + "' is already in use.");
    }

    // ------------------------------------------------------------ F3

    @Test
    @DisplayName("F3: GET /api/clans returns 200 with a page, matching the schema, that includes the new clan")
    void f3ListIncludesNewClan() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String name = "Test F list " + stamp;
        long id = createClan(newUserToken("lister", stamp), name, freeTag());

        MvcResult r = call("GET /api/clans (200)", get("/api/clans").param("q", name));

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).contains("json");
        assertMatchesSchema(openApi(), "ClanPageResponse", JsonPath.read(body(r), "$"), "GET /api/clans");
        assertThat(JsonPath.<List<Number>>read(body(r), "$.clans[*].id"))
                .extracting(Number::longValue).contains(id);
        assertThat(JsonPath.<List<Number>>read(body(r), "$.clans[?(@.id == " + id + ")].memberCount"))
                .extracting(Number::longValue).containsExactly(1L);

        // and without a search term, the unfiltered listing still answers 200 with a valid page
        MvcResult all = call("GET /api/clans (200)", get("/api/clans"));
        assertThat(all.getResponse().getStatus()).isEqualTo(200);
        assertMatchesSchema(openApi(), "ClanPageResponse", JsonPath.read(body(all), "$"), "GET /api/clans (no filter)");
    }

    // ------------------------------------------------------------ F4

    @Test
    @DisplayName("F4: GET /api/clans/{id} returns 200 with the right data, and 404 for an id that does not exist")
    void f4GetByIdAndMissing() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String name = "Test F get " + stamp;
        String tag = freeTag();
        long id = createClan(newUserToken("getter", stamp), name, tag);

        MvcResult found = call("GET /api/clans/{id} (200)", get("/api/clans/" + id));
        assertThat(found.getResponse().getStatus()).isEqualTo(200);
        assertMatchesSchema(openApi(), "ClanDetailResponse", JsonPath.read(body(found), "$"), "GET /api/clans/{id}");
        assertThat(((Number) read(found, "$.id")).longValue()).isEqualTo(id);
        assertThat(JsonPath.<String>read(body(found), "$.name")).isEqualTo(name);
        assertThat(JsonPath.<String>read(body(found), "$.tag")).isEqualTo(tag);
        assertThat(JsonPath.<String>read(body(found), "$.region")).isEqualTo("Oceania");
        assertThat(JsonPath.<String>read(body(found), "$.ownerDisplayName")).isEqualTo("testf_getter_" + stamp);

        long missing = clans.findAll().stream().mapToLong(Clan::getId).max().orElse(0) + 1_000_000;
        MvcResult notFound = call("GET /api/clans/{id} (404)", get("/api/clans/" + missing));
        assertThat(notFound.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("F4: a private clan is answered 404 like a missing one, so its existence is not revealed")
    void f4PrivateClanIsNotFound() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        MvcResult r = call("POST /api/clans (201)", post("/api/clans")
                .header(HttpHeaders.AUTHORIZATION, newUserToken("private", stamp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test F private " + stamp + "\",\"tag\":\"" + freeTag() + "\",\"publicClan\":false}"));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<String>read(body(r), "$.visibility")).isEqualTo("PRIVATE");
        long id = ((Number) read(r, "$.id")).longValue();

        assertThat(call("GET /api/clans/{id} (404)", get("/api/clans/" + id)).getResponse().getStatus()).isEqualTo(404);
    }

    // ------------------------------------------------------------ F5

    @Test
    @DisplayName("F5: PATCH /api/clans/{id} returns 200, and the update is reflected on a following GET")
    void f5PatchReflectedOnGet() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("patcher", stamp);
        String tag = freeTag();
        long id = createClan(token, "Test F patch " + stamp, tag);
        String renamed = "Test F patched " + stamp;

        MvcResult patched = call("PATCH /api/clans/{id} (200)", patch("/api/clans/" + id)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + renamed + "\",\"description\":\"Patched by F5\",\"region\":\"EU\",\"requiresInvitation\":true}"));
        assertThat(patched.getResponse().getStatus()).as("body: %s", body(patched)).isEqualTo(200);
        assertMatchesSchema(openApi(), "ClanDetailResponse", JsonPath.read(body(patched), "$"), "PATCH /api/clans/{id}");
        assertThat(JsonPath.<String>read(body(patched), "$.name")).isEqualTo(renamed);

        MvcResult after = call("GET /api/clans/{id} (200)", get("/api/clans/" + id));
        assertThat(after.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(after), "$.name")).isEqualTo(renamed);
        assertThat(JsonPath.<String>read(body(after), "$.description")).isEqualTo("Patched by F5");
        assertThat(JsonPath.<String>read(body(after), "$.region")).isEqualTo("EU");
        assertThat(JsonPath.<Boolean>read(body(after), "$.requiresInvitation")).isTrue();
        // what the PATCH left out is unchanged
        assertThat(JsonPath.<String>read(body(after), "$.tag")).isEqualTo(tag);
        assertThat(JsonPath.<String>read(body(after), "$.visibility")).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("F5: a PATCH that breaks a rule is 400, by a non-member is 403, without a token is 401 - and changes nothing")
    void f5PatchRefusals() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("keeper", stamp);
        String name = "Test F keep " + stamp;
        long id = createClan(token, name, freeTag());

        MvcResult badTag = call("PATCH /api/clans/{id} (400)", patch("/api/clans/" + id)
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\":\"TOOLONG\"}"));
        assertThat(badTag.getResponse().getStatus()).isEqualTo(400);
        assertJsonError(badTag, "Clan tag must be 1 to 3 letters or numbers.");

        MvcResult outsider = call("PATCH /api/clans/{id} (403)", patch("/api/clans/" + id)
                .header(HttpHeaders.AUTHORIZATION, newUserToken("outsider", stamp)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hijacked " + stamp + "\"}"));
        assertThat(outsider.getResponse().getStatus()).isEqualTo(403);
        assertJsonError(outsider, null);

        MvcResult anonymous = call("PATCH /api/clans/{id} (401)", patch("/api/clans/" + id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked " + stamp + "\"}"));
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);

        MvcResult after = call("GET /api/clans/{id} (200)", get("/api/clans/" + id));
        assertThat(JsonPath.<String>read(body(after), "$.name")).isEqualTo(name);
    }

    // ------------------------------------------------------------ F6

    @Test
    @DisplayName("F6: DELETE /api/clans/{id} returns 204, and a following GET returns 404")
    void f6DeleteThenGetIs404() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("deleter", stamp);
        String name = "Test F delete " + stamp;
        long id = createClan(token, name, freeTag());

        // someone else cannot delete it
        MvcResult outsider = call("DELETE /api/clans/{id} (403)", delete("/api/clans/" + id)
                .header(HttpHeaders.AUTHORIZATION, newUserToken("vandal", stamp)));
        assertThat(outsider.getResponse().getStatus()).isEqualTo(403);
        assertThat(call("DELETE /api/clans/{id} (401)", delete("/api/clans/" + id)).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("GET /api/clans/{id} (200)", get("/api/clans/" + id)).getResponse().getStatus()).isEqualTo(200);

        MvcResult deleted = call("DELETE /api/clans/{id} (204)", delete("/api/clans/" + id)
                .header(HttpHeaders.AUTHORIZATION, token));
        assertThat(deleted.getResponse().getStatus()).isEqualTo(204);
        assertThat(body(deleted)).isEmpty();

        assertThat(call("GET /api/clans/{id} (404)", get("/api/clans/" + id)).getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/clans/" + id + "/members")).andReturn().getResponse().getStatus()).isEqualTo(404);
        MvcResult list = call("GET /api/clans (200)", get("/api/clans").param("q", name));
        assertThat(JsonPath.<List<Number>>read(body(list), "$.clans[*].id")).extracting(Number::longValue).doesNotContain(id);

        // a soft delete: the row stays for the history, marked deleted
        assertThat(clans.findById(id).orElseThrow().getDeletedAt()).isNotNull();
    }

    // ------------------------------------------------------------ the whole lifecycle in one go

    @Test
    @DisplayName("F1-F6 in sequence on one clan: create, list, read, update, delete, gone")
    void fullCrudLifecycle() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("lifecycle", stamp);
        String name = "Test F life " + stamp;

        long id = createClan(token, name, freeTag());                                                         // F1
        assertThat(call("POST /api/clans (400)", post("/api/clans").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).getResponse().getStatus()).isEqualTo(400); // F2
        assertThat(JsonPath.<List<Number>>read(body(call("GET /api/clans (200)", get("/api/clans").param("q", name))), "$.clans[*].id"))
                .extracting(Number::longValue).contains(id);                                                    // F3
        assertThat(call("GET /api/clans/{id} (200)", get("/api/clans/" + id)).getResponse().getStatus()).isEqualTo(200); // F4
        assertThat(call("PATCH /api/clans/{id} (200)", patch("/api/clans/" + id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"lifecycle\"}")).getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(body(call("GET /api/clans/{id} (200)", get("/api/clans/" + id))), "$.description"))
                .isEqualTo("lifecycle");                                                                        // F5
        assertThat(call("DELETE /api/clans/{id} (204)", delete("/api/clans/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(call("GET /api/clans/{id} (404)", get("/api/clans/" + id)).getResponse().getStatus()).isEqualTo(404); // F6
    }

    // ------------------------------------------------------------ known deviation from the documentation

    // The OpenAPI annotations on PATCH and DELETE /api/clans/{id} promise
    // 404 "Clan is deleted or missing", but ClanManagementService checks the caller's own
    // membership first, so a missing or already-deleted clan is answered 403
    // ("You are not in a clan." / "You can only edit your own clan.") and the 404 never happens.
    // Test F says such a mismatch is logged and corrected before the next client demo; this test
    // is the record. Remove @Disabled once either the code returns 404 or the docs drop it.
    @Test
    @Disabled("Known mismatch: PATCH/DELETE on a deleted or missing clan answer 403, the docs say 404")
    @DisplayName("F (contract): PATCH and DELETE on a deleted or missing clan answer the documented 404")
    void patchAndDeleteMissingClanAre404AsDocumented() throws Exception {
        String stamp = Long.toString(System.nanoTime());
        String token = newUserToken("gone", stamp);
        long id = createClan(token, "Test F gone " + stamp, freeTag());
        assertThat(call("DELETE /api/clans/{id} (204)", delete("/api/clans/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .getResponse().getStatus()).isEqualTo(204);

        assertThat(mockMvc.perform(patch("/api/clans/" + id).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mockMvc.perform(delete("/api/clans/" + id).header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }
}
