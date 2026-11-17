package com.staffportal.staff_management_portal.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. ALLOW ALL NON-API REQUESTS (html, js, css, images)
                        .requestMatchers(request -> !request.getServletPath().startsWith("/api/")).permitAll()

                        // 2. PUBLIC AUTH API ENDPOINTS
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                        // 2b. ADMIN-ONLY QR SCAN ENDPOINT
                        .requestMatchers(HttpMethod.POST, "/api/attendance/scan").hasRole("ADMINISTRATOR")

                        // 2c. AI INSIGHTS / ANOMALIES
                        .requestMatchers(HttpMethod.GET, "/api/attendance/anomalies/all", "/api/leaves/insights/all")
                        .hasAnyRole("DEAN", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/anomalies/my-department", "/api/leaves/insights/my-department")
                        .hasRole("HEAD_OF_DEPARTMENT")

                        // 2d. ATTENDANCE
                        .requestMatchers(HttpMethod.POST, "/api/attendance").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.GET, "/api/attendance").hasAnyRole("DEAN", "ADMINISTRATOR")

                        // 2e. EMPLOYEE WRITES
                        .requestMatchers(HttpMethod.POST, "/api/employees").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/employees/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.POST, "/api/employees/*/enroll-face").hasRole("ADMINISTRATOR")

                        // 2f. EMPLOYEE READS
                        .requestMatchers(HttpMethod.GET, "/api/employees/me", "/api/employees/my-department",
                                "/api/employees/department/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/employees", "/api/employees/*")
                        .hasAnyRole("DEAN", "ADMINISTRATOR")

                        // 2g. LEAVE READS
                        .requestMatchers(HttpMethod.GET, "/api/leaves", "/api/leaves/employee/**")
                        .hasAnyRole("DEAN", "ADMINISTRATOR")

                        // 2h. ATTENDANCE BY EMPLOYEE ID
                        .requestMatchers(HttpMethod.GET, "/api/attendance/employee/**")
                        .hasAnyRole("DEAN", "ADMINISTRATOR")

                        // 2i. TASK READS: full list and tasks by employee ID are Dean only.
                        //     /api/tasks/me stays open (own tasks); /my-department is for HODs.
                        .requestMatchers(HttpMethod.GET, "/api/tasks", "/api/tasks/employee/**")
                        .hasRole("DEAN")
                        .requestMatchers(HttpMethod.GET, "/api/tasks/my-department")
                        .hasRole("HEAD_OF_DEPARTMENT")

                        // 3. LEAVE APPROVAL/REJECTION: DEAN ONLY
                        .requestMatchers(HttpMethod.PUT, "/api/leaves/*/status").hasRole("DEAN")

                        // 4. DELETIONS
                        .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasAnyRole("FOUNDER_CHANCELLOR", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/attendance/**").hasAnyRole("FOUNDER_CHANCELLOR", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/leaves/**").hasAnyRole("FOUNDER_CHANCELLOR", "ADMINISTRATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/payroll/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/tasks/**").hasAnyRole("DEAN","HEAD_OF_DEPARTMENT")

                        // 5. SELF-SERVICE PAYROLL
                        .requestMatchers(HttpMethod.GET, "/api/payroll/me", "/api/payroll/me/**").authenticated()

                        // 6. PAYROLL: ADMIN ONLY
                        .requestMatchers(HttpMethod.GET, "/api/payroll/**").hasRole("ADMINISTRATOR")
                        .requestMatchers(HttpMethod.POST, "/api/payroll/**").hasRole("ADMINISTRATOR")

                        // 7. TASK ASSIGNMENT
                        .requestMatchers(HttpMethod.POST, "/api/tasks/**").hasAnyRole("DEAN", "HEAD_OF_DEPARTMENT")

                        // 8. PROTECTED API ENDPOINTS
                        .requestMatchers("/api/employees/**", "/api/attendance/**", "/api/leaves/**",
                                "/api/payroll/**", "/api/tasks/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}