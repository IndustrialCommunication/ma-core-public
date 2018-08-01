/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.web.mvc.spring.security;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import com.serotonin.m2m2.module.DefaultPagesDefinition;

/**
 * @author Jared Wiltshire
 *
 */
@Component
public class MangoAuthenticationEntryPoint extends LoginUrlAuthenticationEntryPoint {
    final RequestMatcher browserHtmlRequestMatcher;

    @Autowired
    public MangoAuthenticationEntryPoint(@Qualifier("browserHtmlRequestMatcher") RequestMatcher browserHtmlRequestMatcher) {
        // this URL is not actually used
        super("/login.htm");

        this.browserHtmlRequestMatcher = browserHtmlRequestMatcher;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        if (browserHtmlRequestMatcher.matches(request)) {
            super.commence(request, response, authException);
        } else {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), authException.getMessage());
        }
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        return DefaultPagesDefinition.getLoginUri(request, response);
    }
}
