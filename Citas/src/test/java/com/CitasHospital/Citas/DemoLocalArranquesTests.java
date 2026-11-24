package com.CitasHospital.Citas;

import java.util.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.Banner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import com.CitasHospital.Citas.model.Rol;
import com.CitasHospital.Citas.repository.UsuarioRepository;
import com.CitasHospital.Citas.service.CorreoRecuperacion;
import static org.assertj.core.api.Assertions.*;

/** Arranques reales de Spring/Tomcat. Cada escenario usa una H2 exclusiva retenida solo en esta JVM. */
class DemoLocalArranquesTests {
    Map<String,Object> propiedades(String db,String password) {
        Map<String,Object> p=new HashMap<>();
        p.put("spring.datasource.url",db); p.put("spring.jpa.hibernate.ddl-auto","update");
        p.put("spring.jpa.show-sql","false"); p.put("server.port","0");
        p.put("logging.level.root","ERROR"); p.put("logging.level.org.hibernate.SQL","OFF");
        p.put("DEMO_ADMIN_NOMBRE","Administrador ficticio"); p.put("DEMO_ADMIN_CEDULA","DEMO-ARRANQUE");
        p.put("DEMO_ADMIN_CORREO","admin-arranque@example.test"); p.put("DEMO_ADMIN_PASSWORD",password);
        return p;
    }
    ConfigurableApplicationContext arrancar(Map<String,Object> propiedades) {
        var app=new SpringApplication(CitasApplication.class);
        app.setBannerMode(Banner.Mode.OFF); app.setLogStartupInfo(false);
        app.setAdditionalProfiles("demo-local","recuperacion-local");
        app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture02B",propiedades)));
        return app.run();
    }
    @Test void reinicioRealConservaAdminYCombinaRecuperacionSinConsola() {
        String db="jdbc:h2:mem:reinicio02B"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1";
        String password=UUID.randomUUID().toString(); String hash;
        try(var primero=arrancar(propiedades(db,password))) {
            var admin=primero.getBean(UsuarioRepository.class).findByCorreo("admin-arranque@example.test").orElseThrow();
            assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
            assertThat(primero.getBean(PasswordEncoder.class).matches(password,admin.getPassword())).isTrue();
            hash=admin.getPassword();
            assertThat(primero.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(primero.getEnvironment().getProperty("spring.h2.console.enabled",Boolean.class)).isFalse();
            assertThat(primero.getBean(CorreoRecuperacion.class).disponible()).isTrue();
            assertThat(primero.getBeansOfType(CorreoRecuperacion.class)).hasSize(1);
            var registros=((WebServerApplicationContext)primero).getWebServer();
            assertThat(registros.getPort()).isPositive();
            var servlet=((org.springframework.web.context.WebApplicationContext)primero).getServletContext();
            assertThat(servlet.getServletRegistrations().values()).noneMatch(s->s.getClassName().startsWith("org.h2.server.web"));
        }
        try(var segundo=arrancar(propiedades(db,UUID.randomUUID().toString()))) {
            var repo=segundo.getBean(UsuarioRepository.class);
            var admin=repo.findByCorreo("admin-arranque@example.test").orElseThrow();
            assertThat(repo.count()).isEqualTo(1);
            assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
            assertThat(admin.getPassword().equals(hash)).isTrue();
            assertThat(segundo.getBean(PasswordEncoder.class).matches(password,admin.getPassword())).isTrue();
        }
    }
    @Test void arranqueSolicitadoSinPasswordFallaSinCuentaParcial() throws Exception {
        String db="jdbc:h2:mem:fallo02B"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1";
        assertThatThrownBy(()->arrancar(propiedades(db,""))).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contrasena es obligatoria.");
        try(var conexion=DriverManager.getConnection(db,"sa","");var stmt=conexion.createStatement();var rs=stmt.executeQuery("select count(*) from usuario")) {
            assertThat(rs.next()).isTrue(); assertThat(rs.getLong(1)).isZero();
        }
    }
}
