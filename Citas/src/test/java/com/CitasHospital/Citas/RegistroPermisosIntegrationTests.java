package com.CitasHospital.Citas;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Peticiones MVC con filtros reales y comprobaciones posteriores contra H2 aislada.
 * Sin transacción de test: cada petición confirma sus propios cambios. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:entrega01;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RegistroPermisosIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired MedicoRepository medicos;
    @Autowired DisponibilidadMedicoRepository disponibilidades;
    @Autowired CitaRepository citas;
    @Autowired PasswordEncoder encoder;

    Usuario paciente, ajeno, admin;
    Medico medico;
    DisponibilidadMedico disponibilidad, libre;
    Cita cita;
    String password;
    LocalDate fecha = LocalDate.now().plusDays(10);

    @BeforeEach
    void preparar() {
        citas.deleteAll();
        disponibilidades.deleteAll();
        medicos.deleteAll();
        usuarios.deleteAll();
        password = UUID.randomUUID().toString();
        paciente = crearUsuario("paciente", Rol.USUARIO);
        ajeno = crearUsuario("ajeno", Rol.USUARIO);
        admin = crearUsuario("admin-prueba", Rol.ADMIN);
        medico = medicos.save(new Medico("Medico ficticio", "General"));
        disponibilidad = disponibilidades.save(new DisponibilidadMedico(medico, fecha,
                LocalTime.of(9, 0), LocalTime.of(10, 0)));
        libre = disponibilidades.save(new DisponibilidadMedico(medico, fecha,
                LocalTime.of(11, 0), LocalTime.of(12, 0)));
        cita = citas.save(new Cita(paciente, medico, disponibilidad, fecha,
                LocalTime.of(9, 0), LocalTime.of(9, 30), EstadoCita.PENDIENTE));
    }

    private Usuario crearUsuario(String nombre, Rol rol) {
        return usuarios.save(new Usuario(nombre, nombre.equals("paciente") ? "000000002" : "ID-" + nombre, nombre + "@example.test",
                "Contacto ficticio", encoder.encode(password), rol));
    }

    private MultiValueMap<String, String> datos(String nombre) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("nombre", nombre);
        params.add("cedula", "000000001");
        params.add("correo", nombre + "@example.test");
        params.add("contacto", "Contacto ficticio");
        return params;
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder request) {
        return request.with(user(admin.getCorreo()).roles("ADMIN"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"000000001", "0-0000-0001", "  0-0000-0001  "})
    void cedulaNuevaSeNormalizaSinPerderCeros(String cedula) throws Exception {
        var p = datos("nacional"); p.set("cedula", cedula); p.add("password", password);
        mvc.perform(post("/registro").with(csrf()).params(p)).andExpect(redirectedUrl("/login?registroExitoso"));
        assertThat(usuarios.findByCorreo("nacional@example.test").orElseThrow().getCedula()).isEqualTo("000000001");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ABCDEFGHI", "12345678", "1234567890", "1-234-56789", "1 2345 6789", "１２３４５６７８９", "1234a6789", "1–2345–6789"})
    void formatoIncorrectoSeRechazaEnRegistroYAmbasEdiciones(String cedula) throws Exception {
        var p = datos("invalido-nacional"); p.set("cedula", cedula); p.add("password", password);
        long total = usuarios.count();
        mvc.perform(post("/registro").with(csrf()).params(p)).andExpect(view().name("registro"))
                .andExpect(model().attribute("error", "Introduce una cédula de 9 dígitos."));
        for (boolean porAdmin : new boolean[]{false, true}) {
            var peticion = post(porAdmin ? "/usuarios/editar/" + paciente.getId() : "/perfil").with(csrf()).params(p);
            mvc.perform(porAdmin ? comoAdmin(peticion) : comoPaciente(peticion))
                    .andExpect(model().attribute("error", "Introduce una cédula de 9 dígitos."));
            assertThat(usuarios.findById(paciente.getId()).orElseThrow().getCedula()).isEqualTo(paciente.getCedula());
        }
        assertThat(usuarios.count()).isEqualTo(total);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"000000001", "0-0000-0001"})
    void duplicadoEquivalenteSeRechazaEnTresFlujos(String anterior) throws Exception {
        ajeno.setCedula(anterior); usuarios.saveAndFlush(ajeno);
        var p = datos("duplicado-nacional"); p.set("cedula", anterior.contains("-") ? "000000001" : "0-0000-0001");
        p.add("password", password);
        mvc.perform(post("/registro").with(csrf()).params(p)).andExpect(view().name("registro"))
                .andExpect(model().attribute("error", "Ya existe un usuario con esa cedula."));
        for (boolean porAdmin : new boolean[]{false, true}) {
            var peticion = post(porAdmin ? "/usuarios/editar/" + paciente.getId() : "/perfil").with(csrf()).params(p);
            mvc.perform(porAdmin ? comoAdmin(peticion) : comoPaciente(peticion))
                    .andExpect(model().attribute("error", "Ya existe otro usuario con esa cedula."));
        }
        assertThat(usuarios.count()).isEqualTo(3);
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().getCedula()).isEqualTo(paciente.getCedula());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false, true})
    void edicionExcluyeCuentaActualYNormalizaCambio(boolean porAdmin) throws Exception {
        var p = datos("paciente"); p.set("cedula", " 0-0000-0002 ");
        var peticion = post(porAdmin ? "/usuarios/editar/" + paciente.getId() : "/perfil").with(csrf()).params(p);
        var resultado = mvc.perform(porAdmin ? comoAdmin(peticion) : comoPaciente(peticion));
        if (porAdmin) resultado.andExpect(redirectedUrl("/usuarios?actualizado"));
        else resultado.andExpect(model().attributeExists("mensaje")).andExpect(model().attributeDoesNotExist("error"));
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().getCedula()).isEqualTo("000000002");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false, true})
    void identificadorAntiguoSeConservaExactoPeroNoPermiteOtroArbitrario(boolean porAdmin) throws Exception {
        Usuario objetivo = porAdmin ? admin : paciente;
        objetivo.setCedula(" DEMO-ANTIGUO "); usuarios.saveAndFlush(objetivo);
        String ruta = porAdmin ? "/usuarios/editar/" + objetivo.getId() : "/perfil";
        var p = datos("otro-nombre"); p.set("correo", objetivo.getCorreo()); p.set("cedula", objetivo.getCedula());
        var peticion = post(ruta).with(csrf()).params(p);
        mvc.perform(porAdmin ? comoAdmin(peticion) : comoPaciente(peticion)).andExpect(status().is(porAdmin ? 302 : 200));
        assertThat(usuarios.findById(objetivo.getId()).orElseThrow().getCedula()).isEqualTo(" DEMO-ANTIGUO ");
        assertThat(usuarios.findById(objetivo.getId()).orElseThrow().getNombre()).isEqualTo("otro-nombre");
        p.set("cedula", "DEMO-ANTIGUO"); p.add("conservarCedula", "true");
        peticion = post(ruta).with(csrf()).params(p);
        mvc.perform(porAdmin ? comoAdmin(peticion) : comoPaciente(peticion)).andExpect(model().attributeExists("error"));
        assertThat(usuarios.findById(objetivo.getId()).orElseThrow().getCedula()).isEqualTo(" DEMO-ANTIGUO ");
        p.set("cedula", "0-0000-0001"); peticion = post(ruta).with(csrf()).params(p);
        mvc.perform(porAdmin ? comoAdmin(peticion) : comoPaciente(peticion)).andExpect(status().is(porAdmin ? 302 : 200));
        assertThat(usuarios.findById(objetivo.getId()).orElseThrow().getCedula()).isEqualTo("000000001");
    }
    private MockHttpServletRequestBuilder comoPaciente(MockHttpServletRequestBuilder request) {
        return request.with(user(paciente.getCorreo()).roles("USUARIO"));
    }

    @Test
    void registroManipuladoCreaUsuarioNuevoYLoginNoObtieneAdministracion() throws Exception {
        var params = datos("nuevo");
        params.add("password", password);
        params.add("id", ajeno.getId().toString());
        params.add("rol", "ADMIN");
        params.add("activo", "false");
        params.add("tokenRecuperacion", "token-manipulado");
        mvc.perform(post("/registro").with(csrf()).params(params))
                .andExpect(redirectedUrl("/login?registroExitoso"));
        Usuario nuevo = usuarios.findByCorreo("nuevo@example.test").orElseThrow();
        assertThat(nuevo.getId()).isNotEqualTo(ajeno.getId());
        assertThat(nuevo.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(nuevo.isActivo()).isTrue();
        assertThat(nuevo.getTokenRecuperacion()).isNull();
        assertThat(nuevo.getPassword()).isNotEqualTo(password);
        assertThat(encoder.matches(password, nuevo.getPassword())).isTrue();
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(ajeno);
        assertThat(usuarios.count()).isEqualTo(4);
        var result = mvc.perform(post("/login").with(csrf()).param("username", nuevo.getCorreo())
                        .param("password", password))
                .andExpect(authenticated().withRoles("USUARIO"))
                .andExpect(redirectedUrl("/inicio")).andReturn();
        mvc.perform(get("/usuarios").session((MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void registroNormalYLoginFuncionan() throws Exception {
        var params = datos("normal"); params.add("password", password);
        mvc.perform(post("/registro").with(csrf()).params(params))
                .andExpect(redirectedUrl("/login?registroExitoso"));
        assertThat(usuarios.findByCorreo("normal@example.test").orElseThrow().getRol()).isEqualTo(Rol.USUARIO);
        mvc.perform(post("/login").with(csrf()).param("username", "normal@example.test")
                .param("password", password)).andExpect(authenticated().withRoles("USUARIO"));
    }

    static Stream<Arguments> datosInvalidos() {
        return Stream.of(Arguments.of("nombre", " "), Arguments.of("cedula", ""),
                Arguments.of("correo", "sin-correo"), Arguments.of("password", " "),
                Arguments.of("nombre", "x".repeat(256)), Arguments.of("cedula", "x".repeat(256)),
                Arguments.of("correo", "x".repeat(245) + "@example.test"),
                Arguments.of("contacto", "x".repeat(256)), Arguments.of("password", "x".repeat(73)),
                Arguments.of("password", "é".repeat(37)));
    }

    @ParameterizedTest @MethodSource("datosInvalidos")
    void registroInvalidoNoPersiste(String campo, String valor) throws Exception {
        var params = datos("invalido"); params.add("password", password); params.set(campo, valor);
        mvc.perform(post("/registro").with(csrf()).params(params)).andExpect(view().name("registro"))
                .andExpect(model().attributeExists("error"));
        assertThat(usuarios.count()).isEqualTo(3);
    }

    @Test
    void limitesDeColumnasYDeBCryptSonAceptadosConCedulaNacional() throws Exception {
        var params = datos("limites");
        params.set("nombre", "n".repeat(255)); params.set("cedula", "000000001");
        params.set("contacto", "c".repeat(255));
        params.set("correo", "e".repeat(242) + "@example.test");
        params.add("password", "é".repeat(36));
        mvc.perform(post("/registro").with(csrf()).params(params))
                .andExpect(redirectedUrl("/login?registroExitoso"));
        Usuario nuevo = usuarios.findByCorreo(params.getFirst("correo")).orElseThrow();
        assertThat(nuevo.getCedula()).isEqualTo("000000001");
        assertThat(encoder.matches("é".repeat(36), nuevo.getPassword())).isTrue();
    }

    @Test
    void duplicadosYRegistroSinCsrfNoCambianCuentas() throws Exception {
        var params = datos("duplicado"); params.add("password", password);
        mvc.perform(post("/registro").params(params)).andExpect(status().isForbidden());
        params.set("correo", paciente.getCorreo());
        mvc.perform(post("/registro").with(csrf()).params(params)).andExpect(view().name("registro"));
        params.set("correo", "duplicado@example.test"); params.set("cedula", paciente.getCedula());
        mvc.perform(post("/registro").with(csrf()).params(params)).andExpect(view().name("registro"));
        assertThat(usuarios.count()).isEqualTo(3);
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().getNombre()).isEqualTo("paciente");
    }

    static Stream<Arguments> rutasLectura() {
        return Stream.of(
                Arguments.of("/usuarios", true), Arguments.of("/usuarios/editar/{usuario}", true),
                Arguments.of("/medicos/nuevo", true), Arguments.of("/medicos/editar/{medico}", true),
                Arguments.of("/disponibilidades/nueva", true), Arguments.of("/disponibilidades/editar/{libre}", true),
                Arguments.of("/admin/citas", true), Arguments.of("/admin/reportes", true),
                Arguments.of("/admin/reportes/csv", true), Arguments.of("/medicos", false),
                Arguments.of("/disponibilidades", false), Arguments.of("/perfil", false),
                Arguments.of("/citas/disponibles", false), Arguments.of("/citas/mis-citas", false));
    }

    String ruta(String plantilla) {
        return plantilla.replace("{usuario}", ajeno.getId().toString())
                .replace("{medico}", medico.getId().toString()).replace("{libre}", libre.getId().toString())
                .replace("{cita}", cita.getId().toString());
    }

    @ParameterizedTest @MethodSource("rutasLectura")
    void matrizDeLectura(String plantilla, boolean administrativa) throws Exception {
        String url = ruta(plantilla);
        mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(comoPaciente(get(url))).andExpect(status().is(administrativa ? 403 : 200));
        mvc.perform(comoAdmin(get(url))).andExpect(status().isOk());
    }

    static Stream<String> bajas() {
        return Stream.of("/usuarios/desactivar/{usuario}", "/medicos/desactivar/{medico}",
                "/disponibilidades/eliminar/{libre}");
    }

    void sinBajas() {
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(medicos.findById(medico.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(disponibilidades.existsById(libre.getId())).isTrue();
    }

    @ParameterizedTest @MethodSource("bajas")
    void bajasExigenPostAdminYCsrf(String plantilla) throws Exception {
        String url = ruta(plantilla);
        mvc.perform(comoAdmin(get(url))).andExpect(status().isMethodNotAllowed()); sinBajas();
        mvc.perform(comoAdmin(head(url))).andExpect(status().isMethodNotAllowed()); sinBajas();
        mvc.perform(comoAdmin(post(url))).andExpect(status().isForbidden()); sinBajas();
        mvc.perform(comoAdmin(post(url).with(csrf().useInvalidToken()))).andExpect(status().isForbidden()); sinBajas();
        mvc.perform(comoPaciente(post(url).with(csrf()))).andExpect(status().isForbidden()); sinBajas();
        mvc.perform(post(url).with(csrf())).andExpect(status().is3xxRedirection()); sinBajas();
        mvc.perform(comoAdmin(post(url).with(csrf()))).andExpect(status().is3xxRedirection());
        if (url.startsWith("/usuarios")) assertThat(usuarios.findById(ajeno.getId()).orElseThrow().isActivo()).isFalse();
        if (url.startsWith("/medicos")) assertThat(medicos.findById(medico.getId()).orElseThrow().isActivo()).isFalse();
        if (url.startsWith("/disponibilidades")) assertThat(disponibilidades.existsById(libre.getId())).isFalse();
    }

    static Stream<String> escriturasAdmin() {
        return Stream.of("/usuarios/editar/{usuario}", "/medicos/guardar", "/medicos/editar/{medico}",
                "/disponibilidades/guardar", "/disponibilidades/editar/{libre}",
                "/admin/citas/confirmar/{cita}", "/admin/citas/cancelar/{cita}");
    }

    @ParameterizedTest @MethodSource("escriturasAdmin")
    void escriturasAdminDenegadasAntesDeModificar(String plantilla) throws Exception {
        String url = ruta(plantilla);
        // Sin parámetros de negocio: la seguridad debe rechazar antes del controlador.
        mvc.perform(post(url).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(comoPaciente(post(url).with(csrf()))).andExpect(status().isForbidden());
        mvc.perform(comoAdmin(post(url))).andExpect(status().isForbidden());
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow().getNombre()).isEqualTo("ajeno");
        assertThat(medicos.count()).isEqualTo(1);
        assertThat(medicos.findById(medico.getId()).orElseThrow().getNombre()).isEqualTo("Medico ficticio");
        assertThat(disponibilidades.count()).isEqualTo(2);
        assertThat(disponibilidades.findById(libre.getId()).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.of(11,0));
        assertThat(citas.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void perfilRechazaIdAjenoYCamposInternosNoSeGuardan() throws Exception {
        var params = datos("editado"); params.set("correo", paciente.getCorreo());
        params.add("id", ajeno.getId().toString()); params.add("rol", "ADMIN");
        params.add("activo", "false"); params.add("tokenRecuperacion", "manipulado");
        params.add("password", "no-debe-guardarse");
        mvc.perform(comoPaciente(post("/perfil").with(csrf()).params(params))).andExpect(status().isForbidden());
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow().getNombre()).isEqualTo("ajeno");
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().getNombre()).isEqualTo("paciente");
        params.remove("id");
        mvc.perform(comoPaciente(post("/perfil").params(params))).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(post("/perfil").with(csrf()).params(params))).andExpect(status().isOk());
        Usuario actualizado = usuarios.findById(paciente.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("editado");
        assertThat(actualizado.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(actualizado.isActivo()).isTrue(); assertThat(actualizado.getTokenRecuperacion()).isNull();
        assertThat(encoder.matches(password, actualizado.getPassword())).isTrue();
    }

    @Test
    void edicionAdminUsaIdDeRutaYConservaEstadoInterno() throws Exception {
        var params = datos("editado-admin"); params.add("id", paciente.getId().toString());
        params.add("rol", "ADMIN"); params.add("activo", "false"); params.add("tokenRecuperacion", "manipulado");
        mvc.perform(comoAdmin(post("/usuarios/editar/" + ajeno.getId()).with(csrf()).params(params)))
                .andExpect(redirectedUrl("/usuarios?actualizado"));
        Usuario actualizado = usuarios.findById(ajeno.getId()).orElseThrow();
        assertThat(actualizado.getNombre()).isEqualTo("editado-admin");
        assertThat(actualizado.getRol()).isEqualTo(Rol.USUARIO); assertThat(actualizado.isActivo()).isTrue();
        assertThat(actualizado.getTokenRecuperacion()).isNull();
        assertThat(usuarios.findById(paciente.getId()).orElseThrow().getNombre()).isEqualTo("paciente");
    }

    @Test
    void bindingMedicoNoSobrescribeIdNiActivoYAdminPuedeEditar() throws Exception {
        mvc.perform(comoAdmin(post("/medicos/guardar").with(csrf()).param("id", medico.getId().toString())
                .param("nombre", "Otro medico").param("especialidad", "General").param("activo", "false")))
                .andExpect(redirectedUrl("/medicos?guardado"));
        assertThat(medicos.count()).isEqualTo(2);
        assertThat(medicos.findById(medico.getId()).orElseThrow().getNombre()).isEqualTo("Medico ficticio");
        Medico nuevo = medicos.findAll().stream().filter(m -> !m.getId().equals(medico.getId())).findFirst().orElseThrow();
        assertThat(nuevo.isActivo()).isTrue();
        mvc.perform(comoAdmin(post("/medicos/editar/" + medico.getId()).with(csrf())
                .param("id", nuevo.getId().toString()).param("nombre", "Medico editado")
                .param("especialidad", "Pediatria").param("activo", "false")))
                .andExpect(redirectedUrl("/medicos?actualizado"));
        assertThat(medicos.findById(medico.getId()).orElseThrow().getNombre()).isEqualTo("Medico editado");
        assertThat(medicos.findById(medico.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(medicos.findById(nuevo.getId()).orElseThrow().getNombre()).isEqualTo("Otro medico");
    }

    @Test
    void disponibilidadUsaParametrosPermitidosYRutaParaEditar() throws Exception {
        mvc.perform(comoAdmin(post("/disponibilidades/guardar").with(csrf())
                .param("id", libre.getId().toString()).param("medicoId", medico.getId().toString())
                .param("fecha", fecha.toString()).param("horaInicio", "13:00").param("horaFin", "14:00")))
                .andExpect(redirectedUrl("/disponibilidades?guardado"));
        assertThat(disponibilidades.count()).isEqualTo(3);
        mvc.perform(comoAdmin(post("/disponibilidades/editar/" + libre.getId()).with(csrf())
                .param("id", disponibilidad.getId().toString()).param("medicoId", "999999")
                .param("fecha", fecha.toString()).param("horaInicio", "15:00").param("horaFin", "16:00")))
                .andExpect(redirectedUrl("/disponibilidades?actualizado"));
        DisponibilidadMedico editada = disponibilidades.findById(libre.getId()).orElseThrow();
        assertThat(editada.getHoraInicio()).isEqualTo(LocalTime.of(15,0));
        assertThat(editada.getMedico().getId()).isEqualTo(medico.getId());
        assertThat(disponibilidades.findById(disponibilidad.getId()).orElseThrow().getHoraInicio()).isEqualTo(LocalTime.of(9,0));
    }

    @Test
    void citasUsanPrincipalYRechazanCancelacionAjena() throws Exception {
        mvc.perform(post("/citas/reservar/" + libre.getId()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(comoPaciente(post("/citas/reservar/" + libre.getId()).param("horaInicio", "11:00")))
                .andExpect(status().isForbidden());
        mvc.perform(comoPaciente(post("/citas/reservar/" + libre.getId()).with(csrf()).param("horaInicio", "11:00")
                .param("usuarioId", ajeno.getId().toString()).param("estado", "CONFIRMADA")))
                .andExpect(redirectedUrl("/citas/mis-citas"));
        Cita creada = citas.findAll().stream().filter(c -> !c.getId().equals(cita.getId())).findFirst().orElseThrow();
        assertThat(creada.getUsuario().getId()).isEqualTo(paciente.getId());
        assertThat(creada.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        mvc.perform(post("/citas/cancelar/" + cita.getId()).with(user(ajeno.getCorreo()).roles("USUARIO"))
                .with(csrf()).param("correoUsuario", paciente.getCorreo()))
                .andExpect(flash().attributeExists("error"));
        assertThat(citas.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        mvc.perform(comoPaciente(post("/citas/cancelar/" + cita.getId()))).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(post("/citas/cancelar/" + cita.getId()).with(csrf())))
                .andExpect(redirectedUrl("/citas/mis-citas"));
        assertThat(citas.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }

    @Test
    void lecturasDePerfilEHistorialNoUsanIdDelCliente() throws Exception {
        Cita otra = citas.save(new Cita(ajeno, medico, libre, fecha,
                LocalTime.of(11, 0), LocalTime.of(11, 30), EstadoCita.PENDIENTE));
        mvc.perform(comoPaciente(get("/perfil").param("id", ajeno.getId().toString())))
                .andExpect(model().attribute("usuario", org.hamcrest.Matchers.hasProperty("nombre",
                        org.hamcrest.Matchers.is("paciente"))));
        var result = mvc.perform(comoPaciente(get("/citas/mis-citas").param("usuarioId", ajeno.getId().toString())))
                .andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        var historial = (java.util.List<Cita>) result.getModelAndView().getModel().get("citas");
        assertThat(historial).extracting(Cita::getId).containsExactly(cita.getId()).doesNotContain(otra.getId());
    }

    @Test
    void erroresDeEdicionConservanRutaYNoPersisten() throws Exception {
        var params = datos("error-edicion"); params.set("nombre", " ");
        mvc.perform(comoAdmin(post("/usuarios/editar/" + ajeno.getId()).with(csrf()).params(params)))
                .andExpect(view().name("usuario-formulario"))
                .andExpect(model().attribute("usuarioId", ajeno.getId()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/usuarios/editar/" + ajeno.getId())));
        mvc.perform(comoAdmin(post("/medicos/editar/" + medico.getId()).with(csrf())
                .param("nombre", " ").param("especialidad", "General")))
                .andExpect(view().name("medico-formulario"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/medicos/editar/" + medico.getId())));
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow().getNombre()).isEqualTo("ajeno");
        assertThat(medicos.findById(medico.getId()).orElseThrow().getNombre()).isEqualTo("Medico ficticio");
    }

    @Test
    void controlesDeBajaRenderizanPostConCsrf() throws Exception {
        String[][] controles = {
            {"/usuarios", "/usuarios/desactivar/" + ajeno.getId()},
            {"/medicos", "/medicos/desactivar/" + medico.getId()},
            {"/disponibilidades", "/disponibilidades/eliminar/" + libre.getId()}
        };
        for (String[] control : controles) {
            String html = mvc.perform(comoAdmin(get(control[0]))).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            var matcher = java.util.regex.Pattern.compile("<form[^>]*>.*?</form>",
                    java.util.regex.Pattern.DOTALL).matcher(html);
            String formulario = null;
            String comilla = Character.toString(34);
            while (matcher.find()) {
                if (matcher.group().contains("action=" + comilla + control[1] + comilla)) {
                    formulario = matcher.group();
                    break;
                }
            }
            assertThat(formulario).as("Formulario de baja en %s", control[0]).isNotNull()
                    .contains("method=" + comilla + "post" + comilla,
                            "name=" + comilla + "_csrf" + comilla,
                            "type=" + comilla + "submit" + comilla);
            assertThat(html).doesNotContain("href=" + comilla + control[1] + comilla);
        }
    }

    @Test
    void adminConfirmaYCancelaCitaLegitima() throws Exception {
        mvc.perform(comoAdmin(post("/admin/citas/confirmar/" + cita.getId()).with(csrf())))
                .andExpect(redirectedUrl("/admin/citas"));
        assertThat(citas.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
        mvc.perform(comoAdmin(post("/admin/citas/cancelar/" + cita.getId()).with(csrf())))
                .andExpect(redirectedUrl("/admin/citas"));
        assertThat(citas.findById(cita.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }

    @Test
    void inicioAdminTrasLoginTerminaEnCitasYAmbosMenusSonAdministrativos() throws Exception {
        var login = mvc.perform(post("/login").with(csrf()).param("username", admin.getCorreo())
                        .param("password", password))
                .andExpect(authenticated().withRoles("ADMIN"))
                .andExpect(redirectedUrl("/inicio")).andReturn();
        var sesion = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/inicio").session(sesion))
                .andExpect(redirectedUrl("/admin/citas"));
        String html = mvc.perform(get("/admin/citas").session(sesion))
                .andExpect(status().isOk()).andExpect(view().name("citas-admin"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String menu : menusPrincipales(html)) {
            assertThat(menu).doesNotContain("Mi atención");
            var enlaces = Pattern.compile("href=\"([^\"]+)\"").matcher(menu).results().map(m -> m.group(1)).toList();
            assertThat(enlaces).containsExactly("/admin/citas", "/medicos", "/disponibilidades", "/usuarios", "/admin/reportes", "/perfil");
            assertThat(menu).contains("action=\"/logout\"", "method=\"post\"", "name=\"_csrf\"");
        }
        // Ocultar Mi atencion no revoca las rutas de paciente al administrador.
        mvc.perform(get("/citas/disponibles").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/citas/mis-citas").session(sesion)).andExpect(status().isOk());
    }

    @Test
    void inicioPacienteTrasLoginConservaVistaYMenuPersonal() throws Exception {
        var login = mvc.perform(post("/login").with(csrf()).param("username", paciente.getCorreo())
                        .param("password", password))
                .andExpect(authenticated().withRoles("USUARIO"))
                .andExpect(redirectedUrl("/inicio")).andReturn();
        var sesion = (MockHttpSession) login.getRequest().getSession(false);
        String html = mvc.perform(get("/inicio").session(sesion))
                .andExpect(status().isOk()).andExpect(view().name("inicio"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String menu : menusPrincipales(html)) {
            assertThat(menu).contains("Mi atención").doesNotContain("Administración");
            var enlaces = Pattern.compile("href=\"([^\"]+)\"").matcher(menu).results().map(m -> m.group(1)).toList();
            assertThat(enlaces).containsExactly("/inicio", "/citas/disponibles", "/citas/mis-citas", "/medicos", "/perfil");
            assertThat(menu).contains("aria-current=\"page\"");
        }
        mvc.perform(get("/admin/citas").session(sesion)).andExpect(status().isForbidden());
    }

    // El renderizador produce HTML5, no XML: se delimitan solo las dos regiones nav conocidas.
    private List<String> menusPrincipales(String html) {
        var menus = Pattern.compile("<nav[^>]*aria-label=\"Principal\"[^>]*>(.*?)</nav>", Pattern.DOTALL)
                .matcher(html).results().map(m -> m.group(1)).toList();
        assertThat(menus).hasSize(2);
        return menus;
    }

    @Test
    void inicioAnonimoContinuaProtegido() throws Exception {
        mvc.perform(get("/inicio")).andExpect(redirectedUrl("/login")).andExpect(unauthenticated());
        mvc.perform(get("/admin/citas")).andExpect(redirectedUrl("/login"));
    }

}
