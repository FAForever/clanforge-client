//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        //System.out.println("ten read, the eleventh fails");
        assertThatThrownBy(in::read).isInstanceOf(IOException.class).hasMessageContaining("over 10 bytes");
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
