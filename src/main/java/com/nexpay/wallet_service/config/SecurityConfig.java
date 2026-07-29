package com.nexpay.wallet_service.config;

import com.nexpay.wallet_service.middleware.KongAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public KongAuthFilter kongAuthFilter() {
        return new KongAuthFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // disable CSRF — stateless API
             .csrf(csrf -> csrf.disable())

            // stateless — no session, Kong handles auth
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // route permissions
            .authorizeHttpRequests(auth -> auth
                // actuator health — public
                .requestMatchers("/actuator/**").permitAll()
                // all wallet routes — require authentication
                .anyRequest().authenticated()
            )

            // add Kong filter before Spring Security filter
            .addFilterBefore(
                kongAuthFilter(),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}