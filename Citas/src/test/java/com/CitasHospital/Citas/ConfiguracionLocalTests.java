package com.CitasHospital.Citas;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import com.CitasHospital.Citas.config.DataInitializer;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.UsuarioRepository;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:config02B;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConfiguracionLocalTests {
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationContext contexto;
    @Autowired MockMvc mvc;
    String password;
    @BeforeAll void estadoInicialSinSemilla() {
        assertThat(usuarios.count()).isZero();
        assertThat(contexto.getBeansOfType(DataInitializer.class)).isEmpty();
    }
    @BeforeEach void preparar() { usuarios.deleteAll(); password=UUID.randomUUID().toString(); }
    MockEnvironment datos() {
        return new MockEnvironment().withProperty("DEMO_ADMIN_NOMBRE", "Administrador ficticio")
                .withProperty("DEMO_ADMIN_CEDULA", "DEMO-02B").withProperty("DEMO_ADMIN_CORREO", "admin-prueba@example.test")
                .withProperty("DEMO_ADMIN_PASSWORD", password);
    }
    void inicializar(MockEnvironment entorno) throws Exception {
        new DataInitializer().crearAdministradorInicial(usuarios,encoder,entorno).run();
    }
    @Test void normalNoCreaAdminNiExponeConsola() throws Exception {
        assertThat(contexto.getBeansOfType(DataInitializer.class)).isEmpty();
        assertThat(usuarios.count()).isZero();
        assertThat(contexto.getEnvironment().getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
        mvc.perform(get("/h2-console/").with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/h2-console/").with(user("admin@example.test").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/h2-console/").with(user("admin@example.test").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
    }
    @Test void altaExplicitaTieneHashYLoginValido() throws Exception {
        inicializar(datos());
        var admin=usuarios.findByCorreo("admin-prueba@example.test").orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(encoder.matches(password,admin.getPassword())).isTrue();
        assertThat(usuarios.count()).isEqualTo(1);
        mvc.perform(post("/login").with(csrf()).param("username",admin.getCorreo()).param("password",password))
                .andExpect(authenticated().withRoles("ADMIN")).andExpect(redirectedUrl("/inicio"));
    }
    @Test void segundoArranqueNoCambiaCuentaAdminNiReactiva() throws Exception {
        inicializar(datos());
        var antes=usuarios.findByCorreo("admin-prueba@example.test").orElseThrow();
        antes.setActivo(false); usuarios.saveAndFlush(antes);
        inicializar(datos().withProperty("DEMO_ADMIN_PASSWORD", UUID.randomUUID().toString()).withProperty("DEMO_ADMIN_NOMBRE","Otro nombre"));
        assertThat(usuarios.findById(antes.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(antes);
        assertThat(usuarios.count()).isEqualTo(1);
    }
    @Test void pacientePreexistenteNoSeElevaNiSeReescribe() {
        var antes=usuarios.save(new Usuario("Paciente ficticio","PACIENTE-DEMO","admin-prueba@example.test","Contacto ficticio",encoder.encode(password),Rol.USUARIO));
        assertThatThrownBy(()->inicializar(datos())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no administradora");
        assertThat(usuarios.findById(antes.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(antes);
        assertThat(usuarios.count()).isEqualTo(1);
    }
    @Test void identificacionAjenaNoCreaCuentaParcial() {
        usuarios.save(new Usuario("Paciente ficticio","DEMO-02B","otro@example.test","Contacto ficticio",encoder.encode(password),Rol.USUARIO));
        assertThatThrownBy(()->inicializar(datos())).isInstanceOf(IllegalStateException.class).hasMessageContaining("identificacion");
        assertThat(usuarios.count()).isEqualTo(1);
        assertThat(usuarios.findByCorreo("admin-prueba@example.test")).isEmpty();
    }
    static Stream<Arguments> invalidos() {
        return Stream.of(Arguments.of("DEMO_ADMIN_NOMBRE",""),Arguments.of("DEMO_ADMIN_CEDULA"," "),
                Arguments.of("DEMO_ADMIN_CORREO",""),Arguments.of("DEMO_ADMIN_PASSWORD",""),
                Arguments.of("DEMO_ADMIN_CORREO","no-es-correo"),Arguments.of("DEMO_ADMIN_CORREO","persona@dominio.invalid"),
                Arguments.of("DEMO_ADMIN_NOMBRE","x".repeat(256)),Arguments.of("DEMO_ADMIN_PASSWORD","x".repeat(11)),
                Arguments.of("DEMO_ADMIN_PASSWORD","x".repeat(73)),Arguments.of("DEMO_ADMIN_PASSWORD","é".repeat(37)));
    }
    @ParameterizedTest @MethodSource("invalidos")
    void configuracionInvalidaNoDejaCuentaParcial(String clave,String valor) {
        assertThatThrownBy(()->inicializar(datos().withProperty(clave,valor))).isInstanceOf(IllegalArgumentException.class);
        assertThat(usuarios.count()).isZero();
    }
}
