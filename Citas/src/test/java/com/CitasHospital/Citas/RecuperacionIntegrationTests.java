package com.CitasHospital.Citas;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.*;
import com.CitasHospital.Citas.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cada petición/worker usa transacción propia. No hay @Transactional de test. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:recuperacion02A;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "spring.h2.console.enabled=false", "recuperacion.url-base=http://127.0.0.1:18082",
    "logging.level.org.hibernate.SQL=OFF"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RecuperacionIntegrationTests.Config.class)
class RecuperacionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired TokenRecuperacionRepository tokens;
    @Autowired RecuperacionService servicio;
    @Autowired PasswordEncoder encoder;
    @Autowired RelojPrueba reloj;
    @Autowired BuzonPrueba buzon;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;
    static AtomicInteger dia = new AtomicInteger();
    Usuario usuario;
    String password;

    static class RelojPrueba extends Clock {
        final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return ahora.get(); }
        void avanzar(Duration duracion) { ahora.updateAndGet(t -> t.plus(duracion)); }
    }
    static class BuzonPrueba implements CorreoRecuperacion {
        String enlace;
        int enviados;
        boolean fallar;
        public boolean disponible() { return true; }
        public void enviar(String destinatario, String enlace) {
            if (fallar) throw new MailSendException("Fallo sintetico de canal");
            this.enlace = enlace; enviados++;
        }
        String token() { return enlace.substring(enlace.indexOf("#token=") + 7); }
    }
    @TestConfiguration
    static class Config {
        @Bean @Primary RelojPrueba relojPrueba() { return new RelojPrueba(); }
        @Bean @Primary BuzonPrueba buzonPrueba() { return new BuzonPrueba(); }
    }
    @BeforeEach
    void preparar() {
        tokens.deleteAll(); usuarios.deleteAll();
        reloj.ahora.set(Instant.parse("2030-01-01T00:00:00Z").plus(Duration.ofDays(dia.incrementAndGet())));
        buzon.enlace = null; buzon.enviados = 0; buzon.fallar = false;
        password = UUID.randomUUID().toString();
        usuario = usuarios.save(new Usuario("Paciente ficticio", "ID-02A", "recuperar@example.test",
                "Contacto ficticio", encoder.encode(password), Rol.USUARIO));
    }
    MvcResult pedir(String correo) throws Exception {
        return mvc.perform(post("/recuperar-password").with(csrf()).param("correo", correo))
                .andExpect(status().isOk()).andReturn();
    }
    String emitir() throws Exception { pedir(usuario.getCorreo()); return buzon.token(); }
    void sinCambio() {
        var cuenta = usuarios.findById(usuario.getId()).orElseThrow();
        assertThat(encoder.matches(password, cuenta.getPassword())).isTrue();
        assertThat(cuenta.getVersionRecuperacion()).isZero();
    }
    String cuerpoSinValores(MvcResult result) throws Exception {
        String q = Character.toString(34);
        return result.getResponse().getContentAsString().replaceAll("value="+q+"[^"+q+"]*"+q, "value="+q+q);
    }
    @Test
    void solicitudNoRevelaCuentaNiTokenYNoConfiaEnHost() throws Exception {
        var existente = pedir(usuario.getCorreo());
        String token = buzon.token();
        var inexistente = pedir("ausente@example.test");
        usuario.setActivo(false); usuarios.save(usuario);
        var inactiva = pedir(usuario.getCorreo());
        assertThat(cuerpoSinValores(existente)).isEqualTo(cuerpoSinValores(inexistente)).isEqualTo(cuerpoSinValores(inactiva));
        assertThat(existente.getResponse().getContentAsString().contains(token)).isFalse();
        assertThat(existente.getModelAndView().getModel().containsKey("token")).isFalse();
        assertThat(buzon.enviados).isEqualTo(1);
        assertThat(buzon.enlace.startsWith("http://127.0.0.1:18082/cambiar-password#token=")).isTrue();
        var registro = tokens.findByUsuarioId(usuario.getId()).orElseThrow();
        assertThat(registro.getHash().equals(token)).isFalse();
        assertThat(registro.getHash().equals(TokensRecuperacion.hash(token))).isTrue();
        assertThat(registro.getVenceEn()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(15)));
        assertThat(registro.getConsumidoEn()).isNull();
        usuario.setActivo(true); usuarios.save(usuario);
        mvc.perform(post("/recuperar-password").with(csrf()).header("Host", "ataque.invalid")
                .header("X-Forwarded-Host", "ataque.invalid").param("correo", usuario.getCorreo()))
                .andExpect(status().isOk());
        assertThat(buzon.enlace.contains("ataque.invalid")).isFalse();
    }
    @Test
    void getNoConsumeNiReflejaYPostValidoSoloSeUsaUnaVez() throws Exception {
        String token = emitir();
        var form = mvc.perform(get("/cambiar-password").param("token", token))
                .andExpect(status().isOk()).andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("Content-Security-Policy")).andReturn();
        assertThat(form.getResponse().getContentAsString().contains(token)).isFalse();
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isNull();
        sinCambio();
        String nueva = UUID.randomUUID().toString();
        mvc.perform(post("/cambiar-password").with(csrf()).param("token", token).param("nuevaPassword", nueva))
                .andExpect(redirectedUrl("/login?passwordCambiada")).andExpect(unauthenticated());
        var cuenta = usuarios.findById(usuario.getId()).orElseThrow();
        assertThat(encoder.matches(nueva, cuenta.getPassword())).isTrue();
        assertThat(cuenta.getVersionRecuperacion()).isEqualTo(1);
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isEqualTo(reloj.instant());
        assertThat(servicio.consumir(token, UUID.randomUUID().toString())).isFalse();
        assertThat(encoder.matches(nueva, usuarios.findById(usuario.getId()).orElseThrow().getPassword())).isTrue();
    }
    static Stream<String> passwordsInvalidas() {
        return Stream.of("", " ", "x".repeat(73), "é".repeat(37));
    }
    @ParameterizedTest @MethodSource("passwordsInvalidas")
    void passwordInvalidaNoConsumeNiModifica(String invalida) throws Exception {
        String token = emitir();
        var resultado = mvc.perform(post("/cambiar-password").with(csrf()).param("token", token).param("nuevaPassword", invalida))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error")).andReturn();
        assertThat(resultado.getResponse().getContentAsString().contains(token)).isFalse();
        sinCambio();
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isNull();
    }
    static Stream<String> tokensInvalidos() { return Stream.of("", "invalido", "x".repeat(200)); }
    @ParameterizedTest @MethodSource("tokensInvalidos")
    void postConTokenInvalidoEsControlado(String invalido) throws Exception {
        emitir();
        mvc.perform(post("/cambiar-password").with(csrf()).param("token", invalido)
                .param("nuevaPassword", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        sinCambio();
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isNull();
    }

    @Test
    void bcryptAceptaLimiteMultibyte() throws Exception {
        String token = emitir();
        assertThat(servicio.consumir(token, "é".repeat(36))).isTrue();
        assertThat(encoder.matches("é".repeat(36), usuarios.findById(usuario.getId()).orElseThrow().getPassword())).isTrue();
    }
    @Test
    void csrfObligatorioEnAmbosPosts() throws Exception {
        mvc.perform(post("/recuperar-password").param("correo", usuario.getCorreo())).andExpect(status().isForbidden());
        assertThat(tokens.count()).isZero(); assertThat(buzon.enviados).isZero();
        String token = emitir();
        mvc.perform(post("/cambiar-password").param("token", token).param("nuevaPassword", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/cambiar-password").with(csrf().useInvalidToken()).param("token", token)
                .param("nuevaPassword", UUID.randomUUID().toString())).andExpect(status().isForbidden());
        sinCambio(); assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isNull();
    }
    @Test
    void invalidoVencidoYSustituidoNoCambianPassword() throws Exception {
        String anterior = emitir();
        assertThat(servicio.consumir(TokensRecuperacion.nuevo(), password)).isFalse();
        assertThat(servicio.consumir("malformado", password)).isFalse();
        String vigente = emitir();
        assertThat(servicio.consumir(anterior, password)).isFalse();
        assertThat(tokens.count()).isEqualTo(1);
        reloj.avanzar(Duration.ofMinutes(15));
        assertThat(servicio.consumir(vigente, password)).isFalse();
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isNull(); sinCambio();
    }
    @Test
    void cuentaInactivaNiEmiteNiSeReactivaConTokenPrevio() throws Exception {
        String token = emitir();
        usuario.setActivo(false); usuarios.save(usuario);
        pedir(usuario.getCorreo());
        assertThat(buzon.enviados).isEqualTo(1);
        assertThat(servicio.consumir(token, password)).isFalse(); sinCambio();
        assertThat(usuarios.findById(usuario.getId()).orElseThrow().isActivo()).isFalse();
    }
    @Test
    void columnaTokenAnteriorNoEsUnaCredencialValida() throws Exception {
        String legado = TokensRecuperacion.nuevo();
        usuario.setTokenRecuperacion(legado); usuarios.save(usuario);
        assertThat(servicio.consumir(legado, password)).isFalse(); sinCambio();
        assertThat(tokens.count()).isZero();
    }
    @Test
    void falloSmtpRevierteReemplazoYRespuestaSigueGenerica() throws Exception {
        String primero = emitir();
        String hash = tokens.findByUsuarioId(usuario.getId()).orElseThrow().getHash();
        buzon.fallar = true;
        var fallo = pedir(usuario.getCorreo());
        var desconocido = pedir("desconocido@example.test");
        assertThat(cuerpoSinValores(fallo)).isEqualTo(cuerpoSinValores(desconocido));
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getHash().equals(hash)).isTrue();
        sinCambio(); assertThat(servicio.consumir(primero, UUID.randomUUID().toString())).isTrue();
    }
    @Test
    void falloPrimerEnvioNoDejaTokenPersistido() throws Exception {
        buzon.fallar = true; pedir(usuario.getCorreo());
        assertThat(tokens.count()).isZero(); sinCambio();
    }
    @Test
    void limitePorCuentaMantieneRespuestaYNoInutilizaUltimoToken() throws Exception {
        var normal = pedir(usuario.getCorreo()); pedir(usuario.getCorreo()); pedir(usuario.getCorreo());
        String ultimo = buzon.token();
        var limitado = pedir(usuario.getCorreo());
        assertThat(cuerpoSinValores(normal)).isEqualTo(cuerpoSinValores(limitado));
        assertThat(buzon.enviados).isEqualTo(3);
        assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getHash().equals(TokensRecuperacion.hash(ultimo))).isTrue();
        reloj.avanzar(Duration.ofMinutes(15)); pedir(usuario.getCorreo());
        assertThat(buzon.enviados).isEqualTo(4);
    }
    @Test
    void limiteGlobalNoRevelaCuentasYSeLiberaConReloj() throws Exception {
        for (int i=0; i<30; i++) pedir("ausente" + i + "@example.test");
        pedir(usuario.getCorreo()); assertThat(buzon.enviados).isZero(); assertThat(tokens.count()).isZero();
        reloj.avanzar(Duration.ofMinutes(15)); pedir(usuario.getCorreo());
        assertThat(buzon.enviados).isEqualTo(1);
    }
    @Test
    void sesionRealAnteriorSeInvalidaYExigeNuevoLogin() throws Exception {
        var login = mvc.perform(post("/login").with(csrf()).param("username", usuario.getCorreo()).param("password", password))
                .andExpect(authenticated()).andReturn();
        var sesion = (MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/perfil").session(sesion)).andExpect(status().isOk());
        String token = emitir(), nueva = UUID.randomUUID().toString();
        assertThat(servicio.consumir(token, nueva)).isTrue();
        mvc.perform(get("/perfil").session(sesion)).andExpect(redirectedUrl("/login?sesionRestablecida"));
        assertThat(sesion.isInvalid()).isTrue();
        mvc.perform(post("/login").with(csrf()).param("username", usuario.getCorreo()).param("password", password))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("username", usuario.getCorreo()).param("password", nueva))
                .andExpect(authenticated()).andExpect(redirectedUrl("/inicio"));
    }
    @Test
    void entidadAntiguaNoPuedeRestaurarSelloDeSesion() throws Exception {
        var anterior = usuarios.findById(usuario.getId()).orElseThrow();
        String nueva = UUID.randomUUID().toString();
        assertThat(servicio.consumir(emitir(), nueva)).isTrue();
        // Mantiene el hash actual: esta prueba aísla la protección del sello, no políticas de edición de 02C.
        anterior.setPassword(usuarios.findById(usuario.getId()).orElseThrow().getPassword());
        anterior.setContacto("Contacto editado");
        usuarios.saveAndFlush(anterior);
        assertThat(usuarios.findById(usuario.getId()).orElseThrow().getVersionRecuperacion()).isEqualTo(1);
    }

    @Test
    void dosConsumosConcurrentesSoloConfirmanUnCambio() throws Exception {
        String token = emitir();
        var inicio = new CyclicBarrier(2);
        Set<Long> conexiones = ConcurrentHashMap.newKeySet();
        Set<Integer> aislamientos = ConcurrentHashMap.newKeySet();
        String[] passwords = {UUID.randomUUID().toString(), UUID.randomUUID().toString()};
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (String nueva : passwords) resultados.add(pool.submit(() -> new TransactionTemplate(txManager).execute(status -> {
                conexiones.add(jdbc.queryForObject("select session_id()", Long.class));
                aislamientos.add(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) java.sql.Connection::getTransactionIsolation));
                try { inicio.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException("Barrera de prueba", e); }
                return servicio.consumir(token, nueva);
            })));
            boolean primero = resultados.get(0).get(15, TimeUnit.SECONDS);
            boolean segundo = resultados.get(1).get(15, TimeUnit.SECONDS);
            assertThat(primero ^ segundo).isTrue();
            assertThat(conexiones).hasSize(2);
            assertThat(aislamientos).containsExactly(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            var cuenta = usuarios.findById(usuario.getId()).orElseThrow();
            assertThat(cuenta.getVersionRecuperacion()).isEqualTo(1);
            assertThat(encoder.matches(passwords[primero ? 0 : 1], cuenta.getPassword())).isTrue();
            assertThat(encoder.matches(passwords[primero ? 1 : 0], cuenta.getPassword())).isFalse();
            assertThat(tokens.findByUsuarioId(usuario.getId()).orElseThrow().getConsumidoEn()).isNotNull();
        }
    }
}
