/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.web.mvc.spring.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.BeanIds;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.header.writers.frameoptions.AllowFromStrategy;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.module.AuthenticationDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.web.mvc.spring.MangoRestSpringConfiguration;
import com.serotonin.m2m2.web.mvc.spring.security.authentication.MangoPasswordAuthenticationProvider;
import com.serotonin.m2m2.web.mvc.spring.security.authentication.MangoTokenAuthenticationProvider;
import com.serotonin.m2m2.web.mvc.spring.security.authentication.MangoUserDetailsService;

/**
 * @author Jared Wiltshire
 */
@Configuration
@EnableWebSecurity
@ComponentScan(basePackages = {"com.serotonin.m2m2.web.mvc.spring.security"})
public class MangoSecurityConfiguration {

    public static final String BASIC_AUTHENTICATION_REALM = "Mango";

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    //Share between all Configurations
    final static RequestMatcher browserHtmlRequestMatcher = createBrowserHtmlRequestMatcher();

    @Autowired
    public void configureAuthenticationManager(AuthenticationManagerBuilder auth,
            MangoUserDetailsService userDetails,
            MangoPasswordAuthenticationProvider passwordAuthenticationProvider,
            MangoTokenAuthenticationProvider tokenAuthProvider
            ) throws Exception {

        auth.userDetailsService(userDetails);

        for (AuthenticationDefinition def : ModuleRegistry.getDefinitions(AuthenticationDefinition.class)) {
            auth.authenticationProvider(def.authenticationProvider());
        }

        auth.authenticationProvider(passwordAuthenticationProvider)
        .authenticationProvider(tokenAuthProvider);
    }

    @Bean
    public UserDetailsChecker userDetailsChecker() {
        return new AccountStatusUserDetailsChecker();
    }

    @Bean
    public UserDetailsService userDetailsService(MangoUserDetailsService userDetails) {
        return userDetails;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(MangoAccessDeniedHandler handler) {
        return handler;
    }

    @Bean(name = "restAccessDeniedHandler")
    public AccessDeniedHandler restAccessDeniedHandler(MangoRestAccessDeniedHandler handler) {
        return new MangoRestAccessDeniedHandler();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(MangoAuthenticationEntryPoint authenticationEntryPoint) {
        return authenticationEntryPoint;
    }

    @Bean
    public AuthenticationSuccessHandler mangoAuthenticationSuccessHandler() {
        return new MangoAuthenticationSuccessHandler(requestCache(), browserHtmlRequestMatcher());
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler(MangoAuthenticationFailureHandler authenticationFailureHandler) {
        return authenticationFailureHandler;
    }

    @Bean
    public LogoutHandler logoutHandler(MangoLogoutHandler logoutHandler) {
        return logoutHandler;
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler(MangoLogoutSuccessHandler logoutSuccessHandler) {
        return logoutSuccessHandler;
    }

    @Bean
    public static ContentNegotiationStrategy contentNegotiationStrategy() {
        return new HeaderContentNegotiationStrategy();
    }

    @Bean
    public RequestCache requestCache() {
        return new NullRequestCache();
    }

    // used to dectect if we should do redirects on login/authentication failure/logout etc
    @Bean(name="browserHtmlRequestMatcher")
    public static RequestMatcher browserHtmlRequestMatcher() {
        return browserHtmlRequestMatcher;
    }

    @Bean
    public SessionInformationExpiredStrategy sessionInformationExpiredStrategy(@Qualifier("browserHtmlRequestMatcher") RequestMatcher matcher) {
        return new MangoExpiredSessionStrategy(matcher);
    }

    /**
     * Internal method to create a static matcher
     * @return
     */
    private static RequestMatcher createBrowserHtmlRequestMatcher(){
        ContentNegotiationStrategy contentNegotiationStrategy = contentNegotiationStrategy();

        MediaTypeRequestMatcher mediaMatcher = new MediaTypeRequestMatcher(
                contentNegotiationStrategy, MediaType.APPLICATION_XHTML_XML, MediaType.TEXT_HTML);
        mediaMatcher.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));

        RequestMatcher notXRequestedWith = new NegatedRequestMatcher(
                new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"));

        return new AndRequestMatcher(Arrays.asList(notXRequestedWith, mediaMatcher));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if(Common.envProps.getBoolean("rest.cors.enabled", false)) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(Arrays.asList(Common.envProps.getStringArray("rest.cors.allowedOrigins", ",", new String[0])));
            configuration.setAllowedMethods(Arrays.asList(Common.envProps.getStringArray("rest.cors.allowedMethods", ",", new String[0])));
            configuration.setAllowedHeaders(Arrays.asList(Common.envProps.getStringArray("rest.cors.allowedHeaders", ",", new String[0])));
            configuration.setExposedHeaders(Arrays.asList(Common.envProps.getStringArray("rest.cors.exposedHeaders", ",", new String[0])));
            configuration.setAllowCredentials(Common.envProps.getBoolean("rest.cors.allowCredentials", false));
            configuration.setMaxAge(Common.envProps.getLong("rest.cors.maxAge", 0));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.setAlwaysUseFullPath(true); //Don't chop off the starting /rest stuff
            source.registerCorsConfiguration("/rest/**", configuration);
            return source;
        } else {
            return null;
        }
    }

    @Bean
    public PermissionExceptionFilter permissionExceptionFilter(){
        return new PermissionExceptionFilter();
    }

    @Primary
    @Bean("restObjectMapper")
    public ObjectMapper objectMapper() {
        return MangoRestSpringConfiguration.getObjectMapper();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return beanFactory.createBean(MangoSessionRegistry.class);
    }

    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        DefaultHttpFirewall firewall = new DefaultHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }

    @SuppressWarnings("deprecation")
    @Bean("ipRateLimiter")
    public RateLimiter<String> ipRateLimiter() {
        if (!SystemSettingsDao.instance.getBooleanValue("rateLimit.rest.anonymous.enabled", true)) {
            return null;
        }

        return new RateLimiter<>(
                Common.envProps.getLong("rateLimit.rest.anonymous.burstCapacity", 10),
                Common.envProps.getLong("rateLimit.rest.anonymous.refillQuanitity", 2),
                Common.envProps.getLong("rateLimit.rest.anonymous.refillPeriod", 1),
                Common.envProps.getTimeUnitValue("rateLimit.rest.anonymous.refillPeriodUnit", TimeUnit.SECONDS));
    }

    @SuppressWarnings("deprecation")
    @Bean("userRateLimiter")
    public RateLimiter<Integer> userRateLimiter() {
        if (!SystemSettingsDao.instance.getBooleanValue("rateLimit.rest.user.enabled", false)) {
            return null;
        }

        return new RateLimiter<>(
                Common.envProps.getLong("rateLimit.rest.user.burstCapacity", 20),
                Common.envProps.getLong("rateLimit.rest.user.refillQuanitity", 10),
                Common.envProps.getLong("rateLimit.rest.user.refillPeriod", 1),
                Common.envProps.getTimeUnitValue("rateLimit.rest.user.refillPeriodUnit", TimeUnit.SECONDS));
    }

    // Configure a separate WebSecurityConfigurerAdapter for REST requests which have an Authorization header.
    // We use a stateless session creation policy and disable CSRF for these requests so that the Authentication is not
    // persisted in the session inside the SecurityContext. This security configuration allows the JWT token authentication
    // and also basic authentication.
    @Configuration
    @Order(1)
    public static class TokenAuthenticatedRestSecurityConfiguration extends WebSecurityConfigurerAdapter {
        @Autowired
        AccessDeniedHandler accessDeniedHandler;
        @Autowired
        AuthenticationEntryPoint authenticationEntryPoint = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        @Autowired
        CorsConfigurationSource corsConfigurationSource;
        @Autowired
        HttpFirewall httpFirewall;

        @Autowired
        @Qualifier("ipRateLimiter")
        RateLimiter<String> ipRateLimiter;

        @Autowired
        @Qualifier("userRateLimiter")
        RateLimiter<Integer> userRateLimiter;

        @Bean(name=BeanIds.AUTHENTICATION_MANAGER)
        @Override
        public AuthenticationManager authenticationManagerBean() throws Exception {
            return super.authenticationManagerBean();
        }

        @Override
        public void configure(WebSecurity web) throws Exception {
            web.httpFirewall(this.httpFirewall);
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.requestMatcher(new RequestMatcher() {
                AntPathRequestMatcher pathMatcher = new AntPathRequestMatcher("/rest/**");
                @Override
                public boolean matches(HttpServletRequest request) {
                    String header = request.getHeader("Authorization");
                    return header != null && pathMatcher.matches(request);
                }
            })
            .sessionManagement()
            // stops the SessionManagementConfigurer from using a HttpSessionSecurityContextRepository to
            // store the SecurityContext, instead it creates a NullSecurityContextRepository which does
            // result in session creation
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            .antMatchers("/rest/*/login/**").denyAll()
            .antMatchers("/rest/*/logout/**").denyAll()
            .antMatchers(HttpMethod.POST, "/rest/*/login/su").denyAll()
            .antMatchers(HttpMethod.POST, "/rest/*/login/exit-su").denyAll()
            .antMatchers(HttpMethod.GET, "/rest/*/translations/public/**").permitAll() //For public translations
            .antMatchers(HttpMethod.GET, "/rest/*/json-data/public/**").permitAll() //For public json-data
            .antMatchers(HttpMethod.GET, "/rest/*/modules/angularjs-modules/public/**").permitAll() //For public angularjs modules
            .antMatchers(HttpMethod.GET, "/rest/*/file-stores/public/**").permitAll() //For public file store
            .antMatchers("/rest/*/password-reset/**").permitAll() // password reset must be public
            .antMatchers("/rest/*/auth-tokens/**").permitAll() // should be able to get public key and verify tokens
            .antMatchers(HttpMethod.OPTIONS).permitAll()
            .anyRequest().authenticated()
            .and()
            // do not need CSRF protection when we are using a JWT token
            .csrf().disable()
            .rememberMe().disable()
            .logout().disable()
            .formLogin().disable()
            .requestCache().disable()
            .httpBasic()
            .realmName(BASIC_AUTHENTICATION_REALM)
            .authenticationEntryPoint(authenticationEntryPoint)
            .and()
            .exceptionHandling()
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
            .and()
            .addFilterBefore(new BearerAuthenticationFilter(authenticationManagerBean(), authenticationEntryPoint), BasicAuthenticationFilter.class)
            .addFilterAfter(new RateLimitingFilter(ipRateLimiter, userRateLimiter), ExceptionTranslationFilter.class);

            //Configure the headers
            configureHeaders(http);
            configureHSTS(http, false);

            // Use the MVC Cors Configuration
            if (Common.envProps.getBoolean("rest.cors.enabled", false))
                http.cors().configurationSource(corsConfigurationSource);
        }
    }

    @Configuration
    @Order(2)
    public static class RestSecurityConfiguration extends WebSecurityConfigurerAdapter {
        @Autowired
        AuthenticationSuccessHandler authenticationSuccessHandler;
        @Autowired
        AuthenticationFailureHandler authenticationFailureHandler;
        @Autowired
        AccessDeniedHandler accessDeniedHandler;
        @Autowired
        CorsConfigurationSource corsConfigurationSource;
        @Autowired
        JsonLoginConfigurer jsonLoginConfigurer;
        @Autowired
        LogoutHandler logoutHandler;
        @Autowired
        LogoutSuccessHandler logoutSuccessHandler;
        @Autowired
        PermissionExceptionFilter permissionExceptionFilter;
        @Autowired
        SessionRegistry sessionRegistry;
        @Autowired
        SessionInformationExpiredStrategy sessionInformationExpiredStrategy;
        @Autowired
        HttpFirewall httpFirewall;

        @Autowired
        @Qualifier("ipRateLimiter")
        RateLimiter<String> ipRateLimiter;

        @Autowired
        @Qualifier("userRateLimiter")
        RateLimiter<Integer> userRateLimiter;

        @Override
        public void configure(WebSecurity web) throws Exception {
            web.httpFirewall(this.httpFirewall);
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.antMatcher("/rest/**")
            .sessionManagement()
            // dont actually want an invalid session strategy, just treat them as having no session
            //.invalidSessionStrategy(invalidSessionStrategy)
            .maximumSessions(10)
            .maxSessionsPreventsLogin(false)
            .sessionRegistry(sessionRegistry)
            .expiredSessionStrategy(sessionInformationExpiredStrategy)
            .and()
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation()
            .newSession()
            .and()
            .authorizeRequests()
            .antMatchers("/rest/*/login").permitAll()
            .antMatchers("/rest/*/exception/**").permitAll() //For exception info for a user's session...
            .antMatchers(HttpMethod.POST, "/rest/*/login/su").hasRole("ADMIN")
            .antMatchers(HttpMethod.GET, "/rest/*/translations/public/**").permitAll() //For public translations
            .antMatchers(HttpMethod.GET, "/rest/*/json-data/public/**").permitAll() //For public json-data
            .antMatchers(HttpMethod.GET, "/rest/*/modules/angularjs-modules/public/**").permitAll() //For public angularjs modules
            .antMatchers(HttpMethod.GET, "/rest/*/file-stores/public/**").permitAll() //For public file store
            .antMatchers("/rest/*/password-reset/**").permitAll() // password reset must be public
            .antMatchers("/rest/*/auth-tokens/**").permitAll() // should be able to get public key and verify tokens
            .antMatchers(HttpMethod.OPTIONS).permitAll()
            .anyRequest().authenticated()
            .and()
            .apply(jsonLoginConfigurer)
            .successHandler(authenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .and()
            .logout()
            .logoutRequestMatcher(new AntPathRequestMatcher("/rest/*/logout", "POST"))
            .addLogoutHandler(logoutHandler)
            .invalidateHttpSession(true)
            // XSRF token is deleted but its own logout handler, session cookie doesn't really need to be deleted as its invalidated
            // but why not for the sake of cleanliness
            .deleteCookies(Common.getCookieName())
            .logoutSuccessHandler(logoutSuccessHandler)
            .and()
            .csrf()
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and()
            .rememberMe().disable()
            .formLogin().disable()
            .requestCache().disable()
            .exceptionHandling()
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            .accessDeniedHandler(accessDeniedHandler)
            .and()
            .addFilterAfter(switchUserFilter(), FilterSecurityInterceptor.class)
            .addFilterAfter(permissionExceptionFilter, ExceptionTranslationFilter.class)
            .addFilterAfter(new RateLimitingFilter(ipRateLimiter, userRateLimiter), ExceptionTranslationFilter.class);

            //Configure headers
            configureHeaders(http);
            configureHSTS(http, false);

            // Use the MVC Cors Configuration
            if (Common.envProps.getBoolean("rest.cors.enabled", false))
                http.cors().configurationSource(corsConfigurationSource);
        }

        @Bean
        public SwitchUserFilter switchUserFilter() {
            SwitchUserFilter filter = new MangoSwitchUserFilter();
            filter.setUserDetailsService(userDetailsService());
            filter.setSuccessHandler(authenticationSuccessHandler);
            filter.setUsernameParameter("username");
            return filter;
        }

        private static class MangoSwitchUserFilter extends SwitchUserFilter {
            RequestMatcher suMatcher = new AntPathRequestMatcher("/rest/*/login/su", HttpMethod.POST.name());
            RequestMatcher exitSuMatcher = new AntPathRequestMatcher("/rest/*/login/exit-su", HttpMethod.POST.name());

            @Override
            protected boolean requiresSwitchUser(HttpServletRequest request) {
                return suMatcher.matches(request);
            }

            @Override
            protected boolean requiresExitUser(HttpServletRequest request) {
                return exitSuMatcher.matches(request);
            }
        }
    }

    @Configuration
    @Order(3)
    public static class DefaultSecurityConfiguration extends WebSecurityConfigurerAdapter {
        @Autowired
        AccessDeniedHandler accessDeniedHandler;
        @Autowired
        AuthenticationEntryPoint authenticationEntryPoint;
        @Autowired
        AuthenticationSuccessHandler authenticationSuccessHandler;
        @Autowired
        AuthenticationFailureHandler authenticationFailureHandler;
        @Autowired
        LogoutHandler logoutHandler;
        @Autowired
        LogoutSuccessHandler logoutSuccessHandler;
        @Autowired
        RequestCache requestCache;
        @Autowired
        PermissionExceptionFilter permissionExceptionFilter;
        @Autowired
        SessionRegistry sessionRegistry;
        @Autowired
        SessionInformationExpiredStrategy sessionInformationExpiredStrategy;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http
            .sessionManagement()
            // dont actually want an invalid session strategy, just treat them as having no session
            //.invalidSessionStrategy(invalidSessionStrategy)
            .maximumSessions(10)
            .maxSessionsPreventsLogin(false)
            .sessionRegistry(sessionRegistry)
            .expiredSessionStrategy(sessionInformationExpiredStrategy)
            .and()
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            .sessionFixation()
            .newSession()
            .and()
            .formLogin()
            // setting this prevents FormLoginConfigurer from adding the login page generating filter
            // this adds an authentication entry point but it wont be used as we have already specified one below in exceptionHandling()
            .loginPage("/login-xyz.htm")
            .loginProcessingUrl("/login")
            .successHandler(authenticationSuccessHandler)
            .failureHandler(authenticationFailureHandler)
            .permitAll()
            .and()
            .logout()
            .logoutUrl("/logout")
            .addLogoutHandler(logoutHandler)
            .invalidateHttpSession(true)
            // XSRF token is deleted but its own logout handler, session cookie doesn't really need to be deleted as its invalidated
            // but why not for the sake of cleanliness
            .deleteCookies(Common.getCookieName())
            .logoutSuccessHandler(logoutSuccessHandler)
            .and()
            .rememberMe()
            .disable()
            .authorizeRequests()
            // dont allow access to any modules folders other than web
            .antMatchers(HttpMethod.GET, "/modules/*/web/**").permitAll()
            .antMatchers("/modules/**").denyAll()
            // Access to *.shtm files must be authenticated
            .antMatchers("/**/*.shtm").authenticated()
            //Access to protected folder
            .antMatchers("/protected/**").authenticated()
            // Default to permit all
            .anyRequest().permitAll()
            .and()
            .csrf()
            // DWR handles its own CRSF protection (It is set to look at the same cookie in Lifecyle)
            .ignoringAntMatchers("/dwr/**", "/httpds")
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .and()
            .requestCache()
            .requestCache(requestCache)
            .and()
            .exceptionHandling()
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
            .and()
            .addFilterAfter(permissionExceptionFilter, ExceptionTranslationFilter.class);

            //Customize the headers here
            configureHeaders(http);
            configureHSTS(http, true);
        }
    }

    static void configureHSTS(HttpSecurity http, boolean requiresSecure) throws Exception {
        // If using SSL then enable the hsts and secure forwarding
        if (Common.envProps.getBoolean("ssl.on", false) && Common.envProps.getBoolean("ssl.hsts.enabled", true)) {
            // dont enable "requiresSecure" for REST calls
            // this options sets the REQUIRES_SECURE_CHANNEL attribute and causes ChannelProcessingFilter
            // to perform a 302 redirect to https://
            if (requiresSecure) {
                http.requiresChannel()
                .anyRequest()
                .requiresSecure();
            }
            http.headers()
            .httpStrictTransportSecurity()
            .maxAgeInSeconds(Common.envProps.getLong("ssl.hsts.maxAge", 31536000))
            .includeSubDomains(Common.envProps.getBoolean("ssl.hsts.includeSubDomains", false));
        } else {
            http.headers()
            .httpStrictTransportSecurity()
            .disable();
        }
    }

    /**
     * Ensure the headers are properly configured
     * @param http
     * @throws Exception
     */
    static void configureHeaders(HttpSecurity http) throws Exception{
        String iFrameControl = Common.envProps.getString("web.security.iFrameAccess", "SAMEORIGIN");
        if(StringUtils.equals(iFrameControl, "SAMEORIGIN")){
            http.headers()
            .frameOptions().sameOrigin()
            .cacheControl().disable();
        }else if(StringUtils.equals(iFrameControl, "DENY")){
            http.headers()
            .frameOptions().deny()
            .cacheControl().disable();
        }else if(StringUtils.equals(iFrameControl, "ANY")){
            http.headers()
            .frameOptions().disable()
            .cacheControl().disable();
        }else{
            //TODO Ensure these are valid Domains?
            XFrameOptionsHeaderWriter headerWriter = new XFrameOptionsHeaderWriter(new MangoAllowFromStrategy(iFrameControl));
            http.headers().addHeaderWriter(headerWriter)
            .frameOptions().disable()
            .cacheControl().disable();

        }

        String contentSecurityPolicy = Common.envProps.getString("web.security.contentSecurityPolicy", "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; connect-src ws: wss: 'self'");
        if (!contentSecurityPolicy.isEmpty()) {
            HeadersConfigurer<HttpSecurity>.ContentSecurityPolicyConfig cspConfig = http.headers().contentSecurityPolicy(contentSecurityPolicy);
            boolean contentSecurityPolicyReportOnly = Common.envProps.getBoolean("web.security.contentSecurityPolicy.reportOnly", false);
            if (contentSecurityPolicyReportOnly) {
                cspConfig.reportOnly();
            }
        }
    }

    /**
     * Get the
     *
     * @author Terry Packer
     */
    static class MangoAllowFromStrategy implements AllowFromStrategy{

        String allowedDomain;

        public MangoAllowFromStrategy(String allowed){
            this.allowedDomain = allowed;
        }
        /* (non-Javadoc)
         * @see org.springframework.security.web.header.writers.frameoptions.AllowFromStrategy#getAllowFromValue(javax.servlet.http.HttpServletRequest)
         */
        @Override
        public String getAllowFromValue(HttpServletRequest request) {
            return allowedDomain;
        }

    }
}