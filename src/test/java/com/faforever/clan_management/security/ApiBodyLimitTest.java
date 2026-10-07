//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.faforever.clan_management.api.ApiError;
import com.faforever.clan_management.api.ApiErrorHandler;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

//the size cap on api bodies, driven directly so a body with no declared length can be tried too
class ApiBodyLimitTest {

    private final ApiBodyLimit limit = new ApiBodyLimit(10);

    @Test
    @DisplayName("a declared length over the cap is 413 and the rest of the chain never runs")
    void declaredLengthOverTheCap() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reports");
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        limit.doFilter(request,response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("10 bytes at most");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("a body with no declared length reads up to the cap and fails past it")
    void undeclaredLengthStopsAtTheCap() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reports") {
            @Override
            public long getContentLengthLong() {
                return -1; //sent in chunks
            }
        };
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        limit.doFilter(request, response, chain);
        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        InputStream in = passed.getInputStream();
        assertThat(in.readNBytes(new byte[10], 0, 10)).isEqualTo(10);
        assertThatThrownBy(in::read).isInstanceOf(ApiBodyLimit.TooLarge.class).hasMessage("Request bodies are 10 bytes at most.");
    }

    @Test
    @DisplayName("a chunked body past the cap comes out of the json reader as 413 with the sentence a declared length gets, not a bare 400")
    void undeclaredLengthIs413ThroughTheReader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reports") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContentType("application/json");
        request.setContent("{\"reason\":\"far more than ten bytes\"}".getBytes(StandardCharsets.UTF_8));
        MockFilterChain chain = new MockFilterChain();
        limit.doFilter(request, new MockHttpServletResponse(), chain);
        ServletServerHttpRequest passed = new ServletServerHttpRequest((HttpServletRequest) chain.getRequest());
        HttpMessageNotReadableException thrown = catchThrowableOfType(HttpMessageNotReadableException.class, () -> new JacksonJsonHttpMessageConverter().read(Map.class,passed));
        ResponseEntity<ApiError> answer = new ApiErrorHandler().unreadable(thrown);
        assertThat(answer.getStatusCode().value()).isEqualTo(413);
        assertThat(answer.getBody().message()).isEqualTo("Request bodies are 10 bytes at most.");
        ResponseEntity<ApiError> broken = new ApiErrorHandler().unreadable(new HttpMessageNotReadableException("JSON parse error",passed));
        assertThat(broken.getStatusCode().value()).isEqualTo(400);
        assertThat(broken.getBody().message()).isEqualTo("The request body could not be read as the JSON this route takes.");
    }

    @Test
    @DisplayName("a body at the cap passes untouched")
    void bodyAtTheCapPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reports");
        request.setContent(new byte[10]);
        MockFilterChain chain = new MockFilterChain();
        limit.doFilter(request, new MockHttpServletResponse(),chain);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
