package clinic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import clinic.dto.ErrorDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.time.Instant;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService users(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT id, email, password_hash, role FROM users WHERE email = ?",
                (rs, n) -> new AccountPrincipal(rs.getLong("id"), rs.getString("email"),
                        rs.getString("password_hash"), rs.getString("role")), email)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid login"));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/login", "/available", "/css/**", "/error", "/api/home", "/api/slots", "/slots").permitAll()
                    .requestMatchers("/doctor/**", "/api/doctor/**").hasRole("PROVIDER")
                    .requestMatchers("/book/**", "/confirmation/**", "/my-appointments/**", "/api/appointments/**").hasRole("CUSTOMER")
                    .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) -> {
                        if (request.getRequestURI().startsWith("/api/")) {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            mapper.writeValue(response.getWriter(), new ErrorDto(Instant.now(), 401,
                                    "Unauthorized", "Please log in", request.getRequestURI()));
                        } else { response.sendRedirect("/login"); }
                    })
                    .accessDeniedHandler((request, response, exception) -> {
                        response.setStatus(403);
                        if (request.getRequestURI().startsWith("/api/")) {
                            response.setContentType("application/json");
                            mapper.writeValue(response.getWriter(), new ErrorDto(Instant.now(), 403,
                                    "Forbidden", "This action is not allowed", request.getRequestURI()));
                        } else { response.sendError(403, "This action is not allowed"); }
                    }))
                .build(); // CSRF protection stays enabled for every form and API write.
    }
}
