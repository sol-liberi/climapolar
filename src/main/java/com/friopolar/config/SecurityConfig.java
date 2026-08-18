package com.friopolar.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import static jakarta.servlet.DispatcherType.ERROR;

@Configuration
@EnableConfigurationProperties({AdminProperties.class, MediaStorageProperties.class})
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            LoginAttemptLimiter loginAttemptLimiter,
                                            ClientAddressResolver clientAddressResolver) throws Exception {
        var successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setDefaultTargetUrl("/admin/trabajos");
        successHandler.setAlwaysUseDefaultTargetUrl(true);
        var failureHandler = new SimpleUrlAuthenticationFailureHandler("/admin/login?error");

        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/admin/login").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/admin/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/admin/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/", "/trabajos/**", "/api/trabajos/**", "/css/**", "/js/**", "/img/**",
                                "/media/**", "/webjars/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers(HttpMethod.HEAD,
                                "/", "/trabajos/**", "/api/trabajos/**", "/css/**", "/js/**", "/img/**",
                                "/media/**", "/webjars/**", "/favicon.ico", "/error").permitAll()
                        // Los GET/HEAD desconocidos deben terminar como 404 público,
                        // no como una redirección al inicio de sesión.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**").permitAll()
                        .anyRequest().denyAll())
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler((request, response, authentication) -> {
                            loginAttemptLimiter.recordSuccess(
                                    clientAddressResolver.resolve(request), request.getParameter("username"));
                            successHandler.onAuthenticationSuccess(request, response, authentication);
                        })
                        .failureHandler((request, response, exception) -> {
                            loginAttemptLimiter.recordFailure(
                                    clientAddressResolver.resolve(request), request.getParameter("username"));
                            failureHandler.onAuthenticationFailure(request, response, exception);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "base-uri 'self'; "
                                        + "connect-src 'self'; "
                                        + "font-src 'self'; "
                                        + "form-action 'self'; "
                                        + "frame-ancestors 'none'; "
                                        + "img-src 'self' data: blob:; "
                                        + "media-src 'self' blob:; "
                                        + "object-src 'none'; "
                                        + "script-src 'self'; "
                                        + "style-src 'self'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), geolocation=(), microphone=(), payment=(), usb=()"))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .requestMatcher(request -> request.isSecure())
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000)))
                .addFilterBefore(new AdminRequestGuardFilter(), CsrfFilter.class)
                .addFilterBefore(
                        new LoginRateLimitFilter(loginAttemptLimiter, clientAddressResolver),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    LoginAttemptLimiter loginAttemptLimiter() {
        return new LoginAttemptLimiter();
    }

    @Bean
    ClientAddressResolver clientAddressResolver(
            @Value("${climapolar.security.trusted-proxies:127.0.0.1,::1}") String trustedProxies) {
        return new ClientAddressResolver(trustedProxies);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AdminProperties properties, PasswordEncoder encoder) {
        var admin = User.withUsername(properties.getUsername())
                .password(encoder.encode(properties.getPassword()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }
}
