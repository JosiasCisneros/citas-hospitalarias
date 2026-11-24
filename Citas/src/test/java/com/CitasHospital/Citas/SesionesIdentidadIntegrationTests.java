package com.CitasHospital.Citas;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.CitasHospital.Citas.form.DatosUsuarioFormulario;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.*;
import com.CitasHospital.Citas.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sesiones establecidas por /login real, filtros y repositorios reales; sin SMTP. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:sesiones02C;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class SesionesIdentidadIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TokenRecuperacionRepository tokens;
    @Autowired UsuarioService servicio;
    @Autowired RecuperacionService recuperacion;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;
    Usuario paciente,admin,ajeno;
    String password;
    @BeforeEach void preparar() {
        tokens.deleteAll(); usuarios.deleteAll(); password=UUID.randomUUID().toString();
        paciente=crear("paciente",Rol.USUARIO); admin=crear("admin",Rol.ADMIN); ajeno=crear("ajeno",Rol.USUARIO);
    }
    Usuario crear(String nombre,Rol rol) {
        return usuarios.save(new Usuario(nombre,"TEST-"+nombre,nombre+"@example.test","Contacto ficticio",encoder.encode(password),rol));
    }
    MockHttpSession login(String correo,String clave) throws Exception {
        var r=mvc.perform(post("/login").with(csrf()).param("username",correo).param("password",clave))
                .andExpect(authenticated()).andExpect(redirectedUrl("/inicio")).andReturn();
        return (MockHttpSession)r.getRequest().getSession(false);
    }
    MockHttpSession login(Usuario u) throws Exception { return login(u.getCorreo(),password); }
    void loginRechazado(String correo,String clave) throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username",correo).param("password",clave))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }
    org.springframework.util.MultiValueMap<String,String> datos(Usuario u) {
        var p=new org.springframework.util.LinkedMultiValueMap<String,String>();
        p.add("nombre",u.getNombre());p.add("cedula",u.getCedula());p.add("correo",u.getCorreo());p.add("contacto",u.getContacto());
        return p;
    }
    String emitir(Usuario u) {
        String token=TokensRecuperacion.nuevo();
        tokens.saveAndFlush(new TokenRecuperacion(u,TokensRecuperacion.hash(token),Instant.now().plus(Duration.ofMinutes(15))));
        return token;
    }
    void tokenAnulado(Usuario u,String token) {
        assertThat(tokens.findByUsuarioId(u.getId()).orElseThrow().getConsumidoEn()).isNotNull();
        assertThat(recuperacion.consumir(token,UUID.randomUUID().toString())).isFalse();
    }
    void pendiente(Usuario u) { assertThat(tokens.findByUsuarioId(u.getId()).orElseThrow().getConsumidoEn()).isNull(); }
    void sesionRevocada(MockHttpSession sesion) throws Exception {
        mvc.perform(get("/perfil").session(sesion)).andExpect(redirectedUrl("/login?sesionRestablecida"));
        assertThat(sesion.isInvalid()).isTrue();
    }
    @Test void correoPropioCierraTodasLasSesionesSinBuscarElAnterior() throws Exception {
        var actual=login(paciente);var otra=login(paciente);String token=emitir(paciente);
        var p=datos(paciente);p.set("correo","actualizado@example.test");
        mvc.perform(post("/perfil").session(actual).with(csrf()).params(p))
                .andExpect(redirectedUrl("/login?sesionRestablecida")).andExpect(unauthenticated());
        assertThat(actual.isInvalid()).isTrue();sesionRevocada(otra);
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().getCorreo()).isEqualTo("actualizado@example.test");
        tokenAnulado(paciente,token);loginRechazado(paciente.getCorreo(),password);
        var nueva=login("actualizado@example.test",password);
        mvc.perform(get("/perfil").session(nueva)).andExpect(status().isOk());
    }
    @Test void passwordPropiaCierraSesionesYAnulaTokenAnterior() throws Exception {
        var actual=login(paciente);var otra=login(paciente);String token=emitir(paciente),nueva=UUID.randomUUID().toString();
        var p=datos(paciente);p.add("nuevaPassword",nueva);
        mvc.perform(post("/perfil").session(actual).with(csrf()).params(p)).andExpect(redirectedUrl("/login?sesionRestablecida"));
        assertThat(actual.isInvalid()).isTrue();sesionRevocada(otra);tokenAnulado(paciente,token);
        loginRechazado(paciente.getCorreo(),password);login(paciente.getCorreo(),nueva);
    }
    static Stream<Arguments> noSensibles() {
        return Stream.of(Arguments.of("nombre","Nombre actualizado",""),Arguments.of("cedula","000000004"," "),Arguments.of("contacto","Otro contacto ficticio",""));
    }
    @ParameterizedTest @MethodSource("noSensibles")
    void cambiosNoSensiblesConservanSesionYToken(String campo,String valor,String claveBlanca) throws Exception {
        var sesion=login(paciente);String token=emitir(paciente);var p=datos(paciente);p.set(campo,valor);p.add("nuevaPassword",claveBlanca);
        mvc.perform(post("/perfil").session(sesion).with(csrf()).params(p)).andExpect(status().isOk()).andExpect(model().attributeExists("mensaje"));
        mvc.perform(get("/perfil").session(sesion)).andExpect(status().isOk());
        assertThat(sesion.isInvalid()).isFalse();var actual=usuarios.findById(paciente.getId()).orElseThrow();
        assertThat(actual.getVersionRecuperacion()).isZero();assertThat(encoder.matches(password,actual.getPassword())).isTrue();pendiente(paciente);
        assertThat(tokens.findByUsuarioId(paciente.getId()).orElseThrow().getHash().equals(TokensRecuperacion.hash(token))).isTrue();
    }
    static Stream<Arguments> invalidos() {
        return Stream.of(Arguments.of("correo","ajeno@example.test"),Arguments.of("nombre"," "),Arguments.of("nuevaPassword","é".repeat(37)));
    }
    @ParameterizedTest @MethodSource("invalidos")
    void validacionFallidaConservaDatosSesionYToken(String campo,String valor) throws Exception {
        var sesion=login(paciente);emitir(paciente);var antes=usuarios.findById(paciente.getId()).orElseThrow();
        var p=datos(paciente);p.set(campo,valor);
        mvc.perform(post("/perfil").session(sesion).with(csrf()).params(p)).andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        assertThat(usuarios.findById(paciente.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(antes);pendiente(paciente);
        mvc.perform(get("/perfil").session(sesion)).andExpect(status().isOk());assertThat(sesion.isInvalid()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"correo","password"})
    void adminEditaSoloLaCuentaObjetivo(String cambio) throws Exception {
        var actor=login(admin);var victima=login(paciente);String token=emitir(paciente);emitir(admin);
        var p=datos(paciente);String nueva=UUID.randomUUID().toString();
        if(cambio.equals("correo"))p.set("correo","administrado@example.test");else p.add("nuevaPassword",nueva);
        mvc.perform(post("/usuarios/editar/"+paciente.getId()).session(actor).with(csrf()).params(p)).andExpect(redirectedUrl("/usuarios?actualizado"));
        sesionRevocada(victima);tokenAnulado(paciente,token);pendiente(admin);
        mvc.perform(get("/usuarios").session(actor)).andExpect(status().isOk());assertThat(actor.isInvalid()).isFalse();
        assertThat(usuarios.findById(admin.getId()).orElseThrow().getVersionRecuperacion()).isZero();
        login(cambio.equals("correo")?"administrado@example.test":paciente.getCorreo(),cambio.equals("correo")?password:nueva);
        loginRechazado(paciente.getCorreo(),password);
    }
    @Test void desactivarRechazaSesionYLoginSinCerrarAlAdmin() throws Exception {
        var actor=login(admin);var victima=login(paciente);String token=emitir(paciente);emitir(admin);
        mvc.perform(post("/usuarios/desactivar/"+paciente.getId()).session(actor).with(csrf())).andExpect(redirectedUrl("/usuarios?desactivado"));
        sesionRevocada(victima);loginRechazado(paciente.getCorreo(),password);tokenAnulado(paciente,token);pendiente(admin);
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().isActivo()).isFalse();
        mvc.perform(get("/usuarios").session(actor)).andExpect(status().isOk());assertThat(actor.isInvalid()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"correo","password"})
    void adminSeEditaConLaMismaPolitica(String cambio) throws Exception {
        var sesion=login(admin);String token=emitir(admin),nueva=UUID.randomUUID().toString();var p=datos(admin);
        if(cambio.equals("correo"))p.set("correo","admin-nuevo@example.test");else p.add("nuevaPassword",nueva);
        mvc.perform(post("/usuarios/editar/"+admin.getId()).session(sesion).with(csrf()).params(p)).andExpect(redirectedUrl("/login?sesionRestablecida"));
        assertThat(sesion.isInvalid()).isTrue();tokenAnulado(admin,token);
        login(cambio.equals("correo")?"admin-nuevo@example.test":admin.getCorreo(),cambio.equals("correo")?password:nueva);
    }
    @Test void adminSeDesactivaYCierraSuSesion() throws Exception {
        var sesion=login(admin);String token=emitir(admin);
        mvc.perform(post("/usuarios/desactivar/"+admin.getId()).session(sesion).with(csrf())).andExpect(redirectedUrl("/login?sesionRestablecida"));
        assertThat(sesion.isInvalid()).isTrue();loginRechazado(admin.getCorreo(),password);tokenAnulado(admin,token);
    }
    @Test void mismaPasswordInformadaCuentaComoOperacionDeCredenciales() throws Exception {
        var sesion=login(paciente);String token=emitir(paciente);var p=datos(paciente);p.add("nuevaPassword",password);
        mvc.perform(post("/perfil").session(sesion).with(csrf()).params(p)).andExpect(redirectedUrl("/login?sesionRestablecida"));
        assertThat(sesion.isInvalid()).isTrue();tokenAnulado(paciente,token);login(paciente);
    }
    @Test void rollbackNoConfirmaCredencialesVersionNiAnulacion() throws Exception {
        var sesion=login(paciente);emitir(paciente);var antes=usuarios.findById(paciente.getId()).orElseThrow();
        var cambios=DatosUsuarioFormulario.desde(paciente);cambios.setCorreo("no-confirmado@example.test");
        assertThatThrownBy(()->new TransactionTemplate(txManager).executeWithoutResult(status->{
            servicio.actualizarUsuario(paciente.getId(),cambios,UUID.randomUUID().toString());
            throw new IllegalStateException("Rollback deliberado de prueba");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Rollback deliberado de prueba");
        assertThat(usuarios.findById(paciente.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(antes);pendiente(paciente);
        mvc.perform(get("/perfil").session(sesion)).andExpect(status().isOk());
    }
    @Test void edicionNoRestauraPasswordObsoletaTrasRecuperacionConfirmada() {
        String token=emitir(paciente),nueva=UUID.randomUUID().toString();Set<Long> conexiones=new HashSet<>();
        new TransactionTemplate(txManager).executeWithoutResult(status->{
            em.find(Usuario.class,paciente.getId());conexiones.add(jdbc.queryForObject("select session_id()",Long.class));
            var independiente=new TransactionTemplate(txManager);independiente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independiente.executeWithoutResult(otra->{
                conexiones.add(jdbc.queryForObject("select session_id()",Long.class));
                assertThat(recuperacion.consumir(token,nueva)).isTrue();
            });
            var datos=DatosUsuarioFormulario.desde(paciente);datos.setContacto("Contacto actualizado");
            servicio.actualizarUsuario(paciente.getId(),datos,null);
        });
        assertThat(conexiones).hasSize(2);
        var actual=usuarios.findById(paciente.getId()).orElseThrow();
        assertThat(encoder.matches(nueva,actual.getPassword())).isTrue();assertThat(actual.getVersionRecuperacion()).isEqualTo(1);
    }
}
