//SPDX-License-Identifier: MIT
package com.faforever.clan_management.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

// a cap on the body of an api request, a json body has none of its own the way a multipart upload does
public class ApiBodyLimit extends OncePerRequestFilter {

    private final long max;

    public ApiBodyLimit(long max) {
        this.max = max;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        long declared = request.getContentLengthLong();
        //System.out.println("body " + declared + " of " + max);
        if (declared > max) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Request bodies are " + max + " bytes at most.\"}");
            return;
        }
        chain.doFilter(declared >= 0 ? request : new Capped(request, max), response);
    }

    //what the capped stream throws, so the api error handler can answer 413 with the same sentence a declared length gets
    public static final class TooLarge extends IOException {

        public TooLarge(long max) {
            super("Request bodies are " + max + " bytes at most.");
        }
    }

    //a body sent in chunks names no length up front, so the stream itself stops at the cap
    private static final class Capped extends HttpServletRequestWrapper {

        private final long max;

        Capped(HttpServletRequest request,long max) {
            super(request);
            this.max = max;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long seen;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0) count(1);
                    return b;
                }

                @Override
                public int read(byte[] buf, int off, int len) throws IOException {
                    int n = in.read(buf, off, len);
                    if (n > 0) count(n);
                    return n;
                }

                private void count(long n) throws IOException {
                    seen += n;
                    if (seen > max) throw new TooLarge(max);
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }
    }
}
