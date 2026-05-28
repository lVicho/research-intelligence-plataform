package com.researchintelligence.platform.auth.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchintelligence.platform.ai.api.AdminConversationalSearchController;
import com.researchintelligence.platform.ai.api.AdminConversationalSearchResponse;
import com.researchintelligence.platform.ai.application.AdminConversationalSearchService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityConfigConversationalSearchTest {

    @Test
    void conversationalSearchAllowsValidatorAndRejectsResearcher() throws Exception {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        try {
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
            AdminConversationalSearchService service = context.getBean(AdminConversationalSearchService.class);
            when(service.search(org.mockito.ArgumentMatchers.any())).thenReturn(new AdminConversationalSearchResponse(
                "Busqueda interpretada",
                Map.of(),
                "PUBLICATIONS",
                List.of(),
                List.of(),
                "Se tradujo la pregunta a filtros estructurados permitidos.",
                false,
                List.of()
            ));

            mockMvc.perform(post("/api/admin/conversational-search")
                    .header("Authorization", basic("validator", "pw"))
                    .contentType("application/json")
                    .content("{\"question\":\"publicaciones pendientes\",\"limit\":5}"))
                .andExpect(status().isOk());

            mockMvc.perform(post("/api/admin/conversational-search")
                    .header("Authorization", basic("researcher", "pw"))
                    .contentType("application/json")
                    .content("{\"question\":\"publicaciones pendientes\",\"limit\":5}"))
                .andExpect(status().isForbidden());

            verify(service, times(1)).search(org.mockito.ArgumentMatchers.any());
        } finally {
            context.close();
        }
    }

    private String basic(String username, String password) {
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {

        @Bean
        AdminConversationalSearchController adminConversationalSearchController(AdminConversationalSearchService service) {
            return new AdminConversationalSearchController(service);
        }

        @Bean
        AdminConversationalSearchService adminConversationalSearchService() {
            return mock(AdminConversationalSearchService.class);
        }

        @Bean
        UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
            return new InMemoryUserDetailsManager(
                User.withUsername("validator")
                    .password(passwordEncoder.encode("pw"))
                    .roles("VALIDATOR")
                    .build(),
                User.withUsername("researcher")
                    .password(passwordEncoder.encode("pw"))
                    .roles("RESEARCHER")
                    .build()
            );
        }
    }
}
