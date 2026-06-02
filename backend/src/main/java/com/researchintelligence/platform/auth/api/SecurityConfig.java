package com.researchintelligence.platform.auth.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/publications/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/venues/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/publishers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/scientific-events/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/event-participations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/master-data/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/researchers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/research-units/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/portal/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/portal/context-assistant/ask").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/portal/publications/*/explain").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/strategic-map/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/opportunities/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/copilot/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/expert-finder/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/graph/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/report-templates/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/report-templates/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/report-templates/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/reports/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/conversational-search").hasAnyRole("ADMIN", "VALIDATOR")
                .requestMatchers("/api/admin/news/**").hasRole("ADMIN")
                .requestMatchers("/api/me/**").hasAnyRole("RESEARCHER", "ADMIN")
                .requestMatchers("/api/audit/**").hasAnyRole("RESEARCHER", "ADMIN", "VALIDATOR")
                .requestMatchers("/api/ai-suggestions/**").hasAnyRole("RESEARCHER", "ADMIN", "VALIDATOR")
                .requestMatchers("/api/data-quality/**").hasRole("ADMIN")
                .requestMatchers("/api/topics/**").hasRole("ADMIN")
                .requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "VALIDATOR")
                .requestMatchers("/api/ingestion/**").hasAnyRole("ADMIN", "VALIDATOR")
                .requestMatchers("/api/validation/**").hasAnyRole("ADMIN", "VALIDATOR")
                .requestMatchers(HttpMethod.POST, "/api/ai/topics/recommend").hasAnyRole("ADMIN", "VALIDATOR", "RESEARCHER")
                .requestMatchers(HttpMethod.POST, "/api/ai/data-quality/suggest-fixes").hasAnyRole("ADMIN", "VALIDATOR", "RESEARCHER")
                .requestMatchers(HttpMethod.POST, "/api/ai/public-summary/generate").hasAnyRole("ADMIN", "RESEARCHER")
                .requestMatchers(HttpMethod.POST, "/api/ai/news/generate-draft").hasRole("ADMIN")
                .requestMatchers("/api/ai/**").hasAnyRole("ADMIN", "VALIDATOR")
                .requestMatchers(HttpMethod.POST, "/api/research-units/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/research-units/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/research-units/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/researchers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/researchers/**").hasAnyRole("ADMIN", "RESEARCHER")
                .requestMatchers(HttpMethod.DELETE, "/api/researchers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/publications/*/submit").hasAnyRole("ADMIN", "RESEARCHER")
                .requestMatchers(HttpMethod.POST, "/api/publications/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/publications/**").hasAnyRole("ADMIN", "RESEARCHER")
                .requestMatchers(HttpMethod.DELETE, "/api/publications/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/venues/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/venues/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/publishers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/publishers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/scientific-events/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/scientific-events/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/event-participations/**").hasAnyRole("ADMIN", "RESEARCHER")
                .requestMatchers(HttpMethod.PUT, "/api/event-participations/**").hasAnyRole("ADMIN", "RESEARCHER")
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
