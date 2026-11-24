package com.CitasHospital.Citas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.CitasHospital.Citas.service.CustomUserDetailsService;

/**
 * Configura la seguridad y el control de acceso del sistema.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    /**
     * Constructor de SecurityConfig.
     */
    public SecurityConfig(CustomUserDetailsService customUserDetailsService) {
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
     * Configura el codificador de contraseñas del sistema.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configura las reglas de acceso, inicio de sesión y cierre de sesión.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            com.CitasHospital.Citas.repository.UsuarioRepository usuarios) throws Exception {
        http
                .addFilterAfter(new com.CitasHospital.Citas.security.SesionRecuperacionFilter(usuarios),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/registro",
                                "/recuperar-password",
                                "/cambiar-password",
                                "/css/**",
                                "/js/recuperacion.js")
                        .permitAll()
                        .requestMatchers(
                                "/usuarios/**",
                                "/medicos/nuevo",
                                "/medicos/guardar",
                                "/medicos/editar/**",
                                "/medicos/desactivar/**",
                                "/disponibilidades/nueva",
                                "/disponibilidades/guardar",
                                "/disponibilidades/editar/**",
                                "/disponibilidades/eliminar/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/admin/citas/**").hasRole("ADMIN")
                        .requestMatchers("/citas/**").hasAnyRole("USUARIO", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/inicio", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .userDetailsService(customUserDetailsService);

        return http.build();
    }
}
