package com.CitasHospital.Citas;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.boot.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.CitasHospital.Citas.form.*;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.*;
import com.CitasHospital.Citas.service.*;
import static org.assertj.core.api.Assertions.*;

/** Solo fixture de test, no se empaqueta en el JAR. Cada invocacion es una JVM independiente. */
public class PersistenciaReinicioProceso {
    public static void main(String[] args) throws Exception {
        boolean crear = args[0].equals("crear");
        Path datos = Path.of(args[1]).toAbsolutePath();
        String password = System.getenv("PRUEBA_PASSWORD_ORIGINAL");
        String alternativa = System.getenv("PRUEBA_PASSWORD_NUEVA");
        var app = new SpringApplication(CitasApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAdditionalProfiles(crear ? new String[]{"persistencia-local","demo-local"}
                : new String[]{"persistencia-local","demo-local","recuperacion-local"});
        Map<String,Object> p = new HashMap<>();
        p.put("citas.datos",datos.toString().replace('\\','/'));
        p.put("server.port","0");p.put("logging.level.root","ERROR");
        p.put("logging.level.org.hibernate.SQL","OFF");
        p.put("DEMO_ADMIN_NOMBRE","Administrador reinicio ficticio");
        p.put("DEMO_ADMIN_CEDULA","REINICIO-ADMIN");
        p.put("DEMO_ADMIN_CORREO","admin-reinicio@example.test");
        p.put("DEMO_ADMIN_PASSWORD",crear ? password : alternativa);
        app.addInitializers(c -> c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture04",p)));
        try (var ctx = app.run()) {
            var usuarios = ctx.getBean(UsuarioRepository.class);
            var medicos = ctx.getBean(MedicoRepository.class);
            var disponibles = ctx.getBean(DisponibilidadMedicoRepository.class);
            var citas = ctx.getBean(CitaRepository.class);
            var service = ctx.getBean(UsuarioService.class);
            var agenda = ctx.getBean(CitaService.class);
            var jdbc = ctx.getBean(JdbcTemplate.class);
            if (crear) {
                var formulario = new RegistroFormulario();
                formulario.setNombre("Paciente reinicio ficticio");formulario.setCedula("REINICIO-PACIENTE");
                formulario.setCorreo("paciente-reinicio@example.test");formulario.setContacto("Contacto ficticio");
                formulario.setPassword(password);
                var paciente = service.registrarUsuario(formulario);
                // El sello no cero debe sobrevivir tambien al cierre de la JVM.
                service.actualizarUsuario(paciente.getId(),DatosUsuarioFormulario.desde(paciente),password);
                var medico = ctx.getBean(MedicoService.class).guardarMedico(new Medico("Medico reinicio ficticio","General"));
                var fecha = ctx.getBean(TiempoAgenda.class).ahora().toLocalDate().plusDays(2);
                var d = ctx.getBean(DisponibilidadMedicoService.class).crearDisponibilidad(medico.getId(),fecha,LocalTime.of(9,10),LocalTime.of(10,10));
                var c = agenda.reservarCita(paciente.getCorreo(),d.getId(),LocalTime.of(9,10));
                agenda.confirmarCita(c.getId());
            }
            assertThat(usuarios.count()).isEqualTo(2);
            assertThat(medicos.count()).isEqualTo(1);assertThat(disponibles.count()).isEqualTo(1);assertThat(citas.count()).isEqualTo(1);
            var paciente = usuarios.findByCorreo("paciente-reinicio@example.test").orElseThrow();
            var admin = usuarios.findByCorreo("admin-reinicio@example.test").orElseThrow();
            assertThat(paciente.getVersionRecuperacion()).isEqualTo(1);
            assertThat(paciente.getRol()).isEqualTo(Rol.USUARIO);assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
            var encoder = ctx.getBean(PasswordEncoder.class);
            assertThat(encoder.matches(password,admin.getPassword())).isTrue();
            assertThat(encoder.matches(alternativa,admin.getPassword())).isFalse();
            var c = citas.findAll().getFirst();var d = disponibles.findAll().getFirst();
            assertThat(c.getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
            assertThat(c.getUsuario().getId()).isEqualTo(paciente.getId());
            assertThat(c.getMedico().getId()).isEqualTo(medicos.findAll().getFirst().getId());
            assertThat(c.getDisponibilidad().getId()).isEqualTo(d.getId());
            assertThat(c.getFecha()).isEqualTo(d.getFecha());
            assertThat(c.getHoraInicio()).isEqualTo(LocalTime.of(9,10));assertThat(c.getHoraFin()).isEqualTo(LocalTime.of(9,40));
            assertThat(jdbc.queryForObject("select count(*) from \"flyway_schema_history\" where \"version\"='1' and \"success\"=true",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from \"flyway_schema_history\" where \"type\"='SQL'",Long.class)).isEqualTo(1);
            assertThat(ctx.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(ctx.getEnvironment().getProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(ctx.getEnvironment().getProperty("spring.h2.console.enabled",Boolean.class)).isFalse();
            assertThat(ctx.getBean(CorreoRecuperacion.class).disponible()).isEqualTo(!crear);
            int puerto = ((WebServerApplicationContext)ctx).getWebServer().getPort();
            login(puerto,paciente.getCorreo(),password,"/citas/mis-citas");
            login(puerto,admin.getCorreo(),password,"/admin/citas");
            Files.writeString(Path.of(args[2]),"{\"fase\":\""+args[0]+"\",\"pid\":"+ProcessHandle.current().pid()
                +",\"usuarios\":2,\"medicos\":1,\"disponibilidades\":1,\"citas\":1,\"estado\":\"CONFIRMADA\",\"sello\":1,\"migraciones_sql\":1,\"login_http_paciente_admin\":true,\"relaciones_verificadas\":true,\"admin_no_reinicializado\":true,\"recuperacion_disponible\":"+!crear+"}");
        }
    }
    static void login(int puerto,String correo,String password,String ruta) throws Exception {
        String base="http://127.0.0.1:"+puerto;
        try(var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build()) {
            var html=client.send(HttpRequest.newBuilder(URI.create(base+"/login")).GET().build(),HttpResponse.BodyHandlers.ofString());
            var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html.body());
            assertThat(html.statusCode()).isEqualTo(200);assertThat(token.find()).isTrue();
            String body="username="+URLEncoder.encode(correo,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8)+"&_csrf="+URLEncoder.encode(token.group(1),StandardCharsets.UTF_8);
            var login=client.send(HttpRequest.newBuilder(URI.create(base+"/login")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding());
            assertThat(login.statusCode()).isEqualTo(302);
            assertThat(login.headers().firstValue("location").orElse("")).endsWith("/inicio");
            var pagina=client.send(HttpRequest.newBuilder(URI.create(base+ruta)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(pagina.statusCode()).isEqualTo(200);assertThat(pagina.body()).contains("Medico reinicio ficticio");
        }
    }
}
