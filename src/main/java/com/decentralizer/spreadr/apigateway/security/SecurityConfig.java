package com.decentralizer.spreadr.apigateway.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
class SecurityConfig extends WebSecurityConfigurerAdapter {

    public static final String WEBSOCKET = "/messages/**";
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String LOGOUT = "/users/logout";
    private static final String EXCEPTION = "/error";
    private static final String POST_USERS_INTERNAL = "/application/users";
    private static final String USERS_INTERNAL = "/application/user/**";
    private static final String CONTROLLERS_INTERNAL = "/application/controllers/";
    private static final String SWAGGER = "/swagger-ui.http";
    private static final String FILES_ONE = "/files/one/**";
    private static final String H2 = "/h2/**";
    private static final String USERS_WHOAMI = "/users/whoami";
    private static final String[] PUBLIC_PLACES = {WEBSOCKET, POST_USERS_INTERNAL, CONTROLLERS_INTERNAL, USERS_INTERNAL, SWAGGER, LOGOUT, FILES_ONE, USERS_WHOAMI, H2, EXCEPTION};

    private final SpringControllersForSecurity springControllersDiscovery;
    private final AuthenticationProviderImpl authenticationProvider;

    public static String stringifyController(String getClassLevelAnnotation, String getMethodLevelAnnotation, String httpMethod) {
        String stringified = getClassLevelAnnotation.concat((getMethodLevelAnnotation.length() > 0 ? getMethodLevelAnnotation : ""));
        stringified = stringified.concat("$" + httpMethod);
        logger.info("stringified controller: [{}]", stringified);
        return stringified;
    }

    @Override
    public void configure(HttpSecurity httpSecurity) throws Exception {
        logger.info("configure(HttpSecurity [{}])", httpSecurity);
        final List<String> publicPlacesAsList = Arrays.asList(PUBLIC_PLACES);
        List<String> nonPublicControllers = getNonPublicControllers(publicPlacesAsList);
        var expr = init(httpSecurity);
        setHttpSecurityForPublicPlaces(expr);
        setHttpSecurityForAuthorities(expr, nonPublicControllers);
        options(expr);
        csrf(httpSecurity);
        finish(expr);
    }

    private ArrayList<String> getNonPublicControllers(List<String> publicPlacesAsList) {
        logger.info("getNonPublicControllers(List<String>  [{}]))", publicPlacesAsList);
        var result = springControllersDiscovery.getControllers().stream()
                .map(c -> stringifyController(c.getClassLevelAnnotation(), c.getMethodLevelAnnotation(), c.getHttpMethod()))
                .filter(c -> !publicPlacesAsList.contains(c)).distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        publicPlacesAsList.forEach(c -> logger.info("publicPlace [{}]", c));
        result.forEach(c -> logger.info("nonPublicController [{}]", c));
        return result;
    }

    private ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry
    init(HttpSecurity httpSecurity) throws Exception {
        logger.info("init(HttpSecurity  [{}]))", httpSecurity);
        return httpSecurity
                .headers().frameOptions().sameOrigin().and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .and()
                .authorizeRequests();
    }

    private ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry
    setHttpSecurityForPublicPlaces(
            ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry expr) {
        logger.info("setHttpSecurityForPublicPlaces");
        return expr.antMatchers(SecurityConfig.PUBLIC_PLACES).permitAll();
    }

    private ExpressionUrlAuthorizationConfigurer<?>.ExpressionInterceptUrlRegistry
    options(ExpressionUrlAuthorizationConfigurer<?>.ExpressionInterceptUrlRegistry expr) {
        logger.info("options");
        return expr.antMatchers(HttpMethod.OPTIONS).permitAll();
    }

    private void finish(ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry expr) throws Exception {
        expr.anyRequest().authenticated()
                .and().httpBasic()
                .and().logout().invalidateHttpSession(true);
    }

    private void csrf(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.csrf().ignoringAntMatchers(PUBLIC_PLACES)
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    private ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry
    setHttpSecurityForAuthorities(final ExpressionUrlAuthorizationConfigurer<HttpSecurity>
            .ExpressionInterceptUrlRegistry expr, List<String> nonPublicControllers) {
        nonPublicControllers.forEach(auth -> nonPublicController(expr, auth));
        return expr;
    }

    private void nonPublicController(
            ExpressionUrlAuthorizationConfigurer<HttpSecurity>
                    .ExpressionInterceptUrlRegistry expr, String auth) {
        HttpMethod httpMethod = getHttpMethodFromAuthority(auth);
        String local = auth.substring(0, auth.lastIndexOf("$"));
        logger.info("setHttpSecurityForAuthorities httpMethod: [{}] local: [{}]", httpMethod, local);
        if (httpMethod != null)
            expr.antMatchers(httpMethod, local).hasAuthority(auth);
        else
            logger.error("auth: [{}]", auth);
    }

    private HttpMethod getHttpMethodFromAuthority(String auth) {
        int index = auth.lastIndexOf("$");
        HttpMethod r = null;
        if (index > 0) {
            String method = auth.substring(index + 1);
            logger.info("auth [{}], HttpMethod [{}]", auth, r);
            r = HttpMethod.valueOf(method);
        }
        logger.info("auth [{}], HttpMethod [{}]", auth, r);
        return r;
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) {
        auth.authenticationProvider(authenticationProvider);
    }

}
