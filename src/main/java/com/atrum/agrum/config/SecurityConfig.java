package com.atrum.agrum.config;

import com.atrum.agrum.security.DynamicPermissionSetManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final DynamicPermissionSetManager dynamicPermissionSetManager;

    public SecurityConfig(DynamicPermissionSetManager dynamicPermissionSetManager) {
        this.dynamicPermissionSetManager = dynamicPermissionSetManager;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz
                        // Public authentication/system endpoints
                        .requestMatchers("/api/public/**", "/error").permitAll()
                        // Route all business endpoints through dynamic manager
                        .anyRequest().access(dynamicPermissionSetManager)
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}