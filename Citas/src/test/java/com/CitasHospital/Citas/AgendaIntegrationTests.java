package com.CitasHospital.Citas;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.CitasHospital.Citas.model.*;
import com.CitasHospital.Citas.repository.*;
import com.CitasHospital.Citas.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:agenda03;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=3000",
        "spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=OFF"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(AgendaIntegrationTests.RelojConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgendaIntegrationTests {
    static final LocalDate HOY=LocalDate.of(2030,4,10), FECHA=HOY.plusDays(1);
    static final LocalTime INICIO=LocalTime.of(9,10), FIN=LocalTime.of(10,10);
    @TestConfiguration static class RelojConfig {
        @Bean @Primary Clock relojFijo() { return Clock.fixed(Instant.parse("2030-04-10T15:00:00Z"),ZoneOffset.UTC); }
    }
    @Autowired UsuarioRepository usuarios;
    @Autowired MedicoRepository medicos;
    @Autowired DisponibilidadMedicoRepository disponibilidades;
    @Autowired CitaRepository citas;
    @Autowired TokenRecuperacionRepository tokens;
    @Autowired CitaService citaService;
    @Autowired DisponibilidadMedicoService disponibilidadService;
    @Autowired MedicoService medicoService;
    @Autowired UsuarioService usuarioService;
    @Autowired BloqueosAgenda bloqueos;
    @Autowired TiempoAgenda tiempo;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    Usuario p1,p2;
    Medico m1,m2;
    DisponibilidadMedico d1,d2;
    final List<String> evidencia=new ArrayList<>();
    @BeforeAll void comprobarEsquemaMigrado() {
        assertThat(jdbc.queryForObject("select count(*) from \"flyway_schema_history\" where \"version\"='1' and \"success\"=true",Long.class)).isEqualTo(1L);
    }
    @BeforeEach void preparar() {
        citas.deleteAll();disponibilidades.deleteAll();medicos.deleteAll();tokens.deleteAll();usuarios.deleteAll();
        String hash=encoder.encode(UUID.randomUUID().toString());
        p1=usuarios.save(new Usuario("Paciente ficticio 1","TEST-P1","p1@example.test","Contacto ficticio",hash,Rol.USUARIO));
        p2=usuarios.save(new Usuario("Paciente ficticio 2","TEST-P2","p2@example.test","Contacto ficticio",hash,Rol.USUARIO));
        m1=medicos.save(new Medico("Medico ficticio 1","General"));m2=medicos.save(new Medico("Medico ficticio 2","General"));
        d1=disponibilidades.save(new DisponibilidadMedico(m1,FECHA,INICIO,FIN));
        d2=disponibilidades.save(new DisponibilidadMedico(m2,FECHA,INICIO,FIN));
    }
    Cita reservar(Usuario p,DisponibilidadMedico d,LocalTime h) { return citaService.reservarCita(p.getCorreo(),d.getId(),h); }
    Cita estado(Long id) { return citas.findById(id).orElseThrow(); }
    DisponibilidadMedico actual() { return disponibilidades.findById(d1.getId()).orElseThrow(); }
    long activas() { return citas.findAll().stream().filter(c->c.getEstado()!=EstadoCita.CANCELADA).count(); }
    void coherencia() {
        for(var c:citas.findAll()) {
            assertThat(disponibilidades.existsById(c.getDisponibilidad().getId())).isTrue();
            var d=disponibilidades.findById(c.getDisponibilidad().getId()).orElseThrow();
            assertThat(c.getFecha()).isEqualTo(d.getFecha());
            assertThat(c.getHoraInicio().isBefore(d.getHoraInicio())).isFalse();
            assertThat(c.getHoraFin().isAfter(d.getHoraFin())).isFalse();
            assertThat(Duration.between(c.getHoraInicio(),c.getHoraFin())).isEqualTo(Duration.ofMinutes(30));
        }
    }
    @Test void cancelacionVisibleUsaHoraDeNegocioYEstadoSinRelojDelSistema() throws Exception {
        var hoy = disponibilidades.save(new DisponibilidadMedico(m1,HOY,LocalTime.of(8,30),LocalTime.of(11,0)));
        var pasada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(8,30),LocalTime.of(9,0),EstadoCita.PENDIENTE));
        var iniciada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(9,0),LocalTime.of(9,30),EstadoCita.CONFIRMADA));
        var pendiente = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(9,30),LocalTime.of(10,0),EstadoCita.PENDIENTE));
        var confirmada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(10,0),LocalTime.of(10,30),EstadoCita.CONFIRMADA));
        var cancelada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(10,30),LocalTime.of(11,0),EstadoCita.CANCELADA));
        String html = mvc.perform(get("/citas/mis-citas").with(user(p1.getCorreo()).roles("USUARIO")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("citasCancelables",Set.of(pendiente.getId(),confirmada.getId())))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("action=\"/citas/cancelar/"+pendiente.getId()+"\"", "action=\"/citas/cancelar/"+confirmada.getId()+"\"");
        for (var noCancelable : List.of(pasada,iniciada,cancelada)) {
            assertThat(html).doesNotContain("action=\"/citas/cancelar/"+noCancelable.getId()+"\"");
        }
        assertThat(html).contains("Pendiente", "Confirmada", "Cancelada").doesNotContain("Hora de Costa Rica");
    }

    @Test void adminConfirmaSoloPendientesFuturasPeroPuedeCancelarPasadas() throws Exception {
        var hoy = disponibilidades.save(new DisponibilidadMedico(m1,HOY,LocalTime.of(8,30),LocalTime.of(11,0)));
        var pasada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(8,30),LocalTime.of(9,0),EstadoCita.PENDIENTE));
        var iniciada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(9,0),LocalTime.of(9,30),EstadoCita.PENDIENTE));
        var futura = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(9,30),LocalTime.of(10,0),EstadoCita.PENDIENTE));
        var confirmada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(10,0),LocalTime.of(10,30),EstadoCita.CONFIRMADA));
        var cancelada = citas.save(new Cita(p1,m1,hoy,HOY,LocalTime.of(10,30),LocalTime.of(11,0),EstadoCita.CANCELADA));
        String html = mvc.perform(get("/admin/citas").with(user(p2.getCorreo()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("citasConfirmables",Set.of(futura.getId())))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("action=\"/admin/citas/confirmar/"+futura.getId()+"\"");
        for (var noConfirmable : List.of(pasada,iniciada,confirmada,cancelada)) {
            assertThat(html).doesNotContain("action=\"/admin/citas/confirmar/"+noConfirmable.getId()+"\"");
        }
        assertThat(html).contains("action=\"/admin/citas/cancelar/"+pasada.getId()+"\"",
                "action=\"/admin/citas/cancelar/"+iniciada.getId()+"\"",
                "action=\"/admin/citas/cancelar/"+confirmada.getId()+"\"",
                "La cita quedará cancelada y se conservará su historial.",
                "data-estilo=\"primario\"", "data-titulo=\"¿Quieres confirmar esta cita?\"",
                "data-mensaje=\"La cita pasará al estado Confirmada.\"", "data-accion=\"Confirmar cita\"");
        assertThat(html).doesNotContain("action=\"/admin/citas/cancelar/"+cancelada.getId()+"\"",
                "El horario volverá a quedar disponible.", "Un horario pasado", "Los horarios pasados", "Hora de Costa Rica");
        mvc.perform(post("/admin/citas/confirmar/"+futura.getId()).with(user(p2.getCorreo()).roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/citas"));
        assertThat(citas.findById(futura.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
        mvc.perform(post("/admin/citas/cancelar/"+pasada.getId()).with(user(p2.getCorreo()).roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/citas"));
        assertThat(citas.findById(pasada.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }

    @Test void zonaCuadriculaRelativaContiguidadYReutilizacion() {
        assertThat(tiempo.ahora()).isEqualTo(LocalDateTime.of(HOY,LocalTime.of(9,0)));
        Cita primera=reservar(p1,d1,INICIO),segunda=reservar(p1,d1,INICIO.plusMinutes(30));
        assertThat(activas()).isEqualTo(2);coherencia();
        citaService.confirmarCita(primera.getId());
        citaService.cancelarCitaUsuario(primera.getId(),p1.getCorreo());
        assertThatThrownBy(()->citaService.confirmarCita(primera.getId())).isInstanceOf(IllegalArgumentException.class);
        reservar(p2,d1,INICIO);
        assertThat(citas.count()).isEqualTo(3);assertThat(activas()).isEqualTo(2);
        assertThat(estado(primera.getId()).getEstado()).isEqualTo(EstadoCita.CANCELADA);
        assertThat(estado(segunda.getId()).getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }
    @ParameterizedTest @ValueSource(strings={"09:10:01","09:10:00.000000001","09:15","08:40","10:10","23:45"})
    void reservaRechazaPrecisionCuadriculaYLimites(String valor) {
        assertThatThrownBy(()->reservar(p1,d1,LocalTime.parse(valor))).isInstanceOf(IllegalArgumentException.class);
        assertThat(citas.count()).isZero();
    }
    @ParameterizedTest @CsvSource({"09:10:01,10:10","09:10,10:10:00.000000001","09:10,09:10","23:30,00:00"})
    void limitesInvalidosDeDisponibilidadNoSeGuardan(String inicio,String fin) {
        var a=LocalTime.parse(inicio);var b=LocalTime.parse(fin);
        assertThatThrownBy(()->disponibilidadService.crearDisponibilidad(m1.getId(),FECHA.plusDays(1),a,b)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->disponibilidadService.actualizarDisponibilidad(d1.getId(),FECHA,a,b)).isInstanceOf(IllegalArgumentException.class);
        assertThat(disponibilidades.count()).isEqualTo(2);assertThat(actual().getHoraInicio()).isEqualTo(INICIO);
    }
    @Test void finDelDiaYDisponibilidadesContiguas() {
        var noche=disponibilidadService.crearDisponibilidad(m1.getId(),FECHA,LocalTime.of(23,0),LocalTime.of(23,59));
        reservar(p1,noche,LocalTime.of(23,0));
        assertThatThrownBy(()->reservar(p2,noche,LocalTime.of(23,30))).isInstanceOf(IllegalArgumentException.class);
        disponibilidadService.crearDisponibilidad(m1.getId(),FECHA,FIN,LocalTime.of(11,10));
        assertThat(citas.count()).isEqualTo(1);coherencia();
    }
    DisponibilidadMedico historica() {
        return disponibilidades.save(new DisponibilidadMedico(m1,HOY,LocalTime.of(8,0),LocalTime.of(11,0)));
    }
    @ParameterizedTest @CsvSource({"08:30,false","09:00,false","09:30,true"})
    void reservaEnPasadoInicioExactoYFuturo(String h,boolean futuro) {
        var d=historica();
        if(futuro) reservar(p1,d,LocalTime.parse(h));
        else assertThatThrownBy(()->reservar(p1,d,LocalTime.parse(h))).isInstanceOf(IllegalArgumentException.class);
        assertThat(citas.count()).isEqualTo(futuro?1:0);
        assertThatThrownBy(()->disponibilidadService.crearDisponibilidad(m2.getId(),HOY,LocalTime.of(9,0),FIN)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @CsvSource({"08:30,false","09:00,false","09:30,true"})
    void confirmarSoloFuturoYEstadosSinResurreccion(String h,boolean futuro) {
        var d=historica();var inicio=LocalTime.parse(h);
        var c=citas.save(new Cita(p1,m1,d,HOY,inicio,inicio.plusMinutes(30),EstadoCita.PENDIENTE));
        if(futuro) {
            citaService.confirmarCita(c.getId());assertThat(estado(c.getId()).getEstado()).isEqualTo(EstadoCita.CONFIRMADA);
            assertThatThrownBy(()->citaService.confirmarCita(c.getId())).isInstanceOf(IllegalArgumentException.class);
        } else {
            assertThatThrownBy(()->citaService.confirmarCita(c.getId())).isInstanceOf(IllegalArgumentException.class);
            assertThat(estado(c.getId()).getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        }
        citaService.cancelarCitaAdministrador(c.getId());
        assertThatThrownBy(()->citaService.confirmarCita(c.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(estado(c.getId()).getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }
    @ParameterizedTest @CsvSource({"08:30,false","09:00,false","09:30,true"})
    void cancelacionPacienteSoloAntesDelInicio(String h,boolean futuro) {
        var d=historica();var inicio=LocalTime.parse(h);
        var c=citas.save(new Cita(p1,m1,d,HOY,inicio,inicio.plusMinutes(30),EstadoCita.PENDIENTE));
        if(futuro)citaService.cancelarCitaUsuario(c.getId(),p1.getCorreo());
        else {
            assertThatThrownBy(()->citaService.cancelarCitaUsuario(c.getId(),p1.getCorreo())).isInstanceOf(IllegalArgumentException.class);
            citaService.cancelarCitaAdministrador(c.getId());
        }
        assertThat(estado(c.getId()).getEstado()).isEqualTo(EstadoCita.CANCELADA);
    }
    @ParameterizedTest @EnumSource(EstadoCita.class)
    void historialImpideEditarYEliminar(EstadoCita estado) {
        var c=citas.save(new Cita(p1,m1,d1,FECHA,INICIO,INICIO.plusMinutes(30),estado));
        assertThatThrownBy(()->disponibilidadService.actualizarDisponibilidad(d1.getId(),FECHA,LocalTime.of(11,10),LocalTime.of(12,10))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->disponibilidadService.eliminarDisponibilidad(d1.getId())).isInstanceOf(IllegalArgumentException.class);
        assertThat(actual().getHoraInicio()).isEqualTo(INICIO);assertThat(estado(c.getId()).getEstado()).isEqualTo(estado);coherencia();
    }
    @Test void medicoOcupadoEnOtraDisponibilidadHeredadaNoAdmiteDobleReserva() {
        var heredada=disponibilidades.save(new DisponibilidadMedico(m1,FECHA,LocalTime.of(9,40),LocalTime.of(10,40)));
        reservar(p1,d1,LocalTime.of(9,40));
        assertThatThrownBy(()->reservar(p2,heredada,LocalTime.of(9,40))).isInstanceOf(IllegalArgumentException.class);
        assertThat(citaService.listarEspaciosDisponibles()).noneMatch(e->e.getMedico().getId().equals(m1.getId())
                &&e.getHoraInicio().isBefore(LocalTime.of(10,10))&&e.getHoraFin().isAfter(LocalTime.of(9,40)));
        assertThat(activas()).isEqualTo(1);
    }
    @ParameterizedTest @CsvSource({"usuario,true","usuario,false","medico,true","medico,false"})
    void ambosOrdenesDeReservaYDesactivacion(String recurso,boolean primeroReserva) {
        if(primeroReserva) reservar(p1,d1,INICIO);
        if(recurso.equals("usuario"))usuarioService.desactivarUsuario(p1.getId());else medicoService.desactivarMedico(m1.getId());
        assertThatThrownBy(()->reservar(p1,d1,INICIO.plusMinutes(30))).isInstanceOf(IllegalArgumentException.class);
        assertThat(citas.count()).isEqualTo(primeroReserva?1:0);
        assertThat(activas()).isEqualTo(primeroReserva?1:0);
    }
    @Test void medicoPreleidoNoSeReactivaAlEditar() {
        Set<Long> ids=new HashSet<>();
        new TransactionTemplate(txManager).executeWithoutResult(s->{
            em.find(Medico.class,m1.getId());ids.add(jdbc.queryForObject("select session_id()",Long.class));
            var otra=new TransactionTemplate(txManager);otra.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            otra.executeWithoutResult(o->{ids.add(jdbc.queryForObject("select session_id()",Long.class));medicoService.desactivarMedico(m1.getId());});
            medicoService.actualizarMedico(m1.getId(),new Medico("Nombre editado","General"));
        });
        assertThat(ids).hasSize(2);assertThat(medicos.findById(m1.getId()).orElseThrow().isActivo()).isFalse();
    }
    @Test void disponibilidadPreleidaNoOcultaCruceNuevo() {
        Set<Long> ids=new HashSet<>();
        assertThatThrownBy(()->new TransactionTemplate(txManager).executeWithoutResult(s->{
            em.find(DisponibilidadMedico.class,d1.getId());ids.add(jdbc.queryForObject("select session_id()",Long.class));
            var otra=new TransactionTemplate(txManager);otra.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            otra.executeWithoutResult(o->{
                ids.add(jdbc.queryForObject("select session_id()",Long.class));
                disponibilidadService.actualizarDisponibilidad(d1.getId(),FECHA,LocalTime.of(11,10),LocalTime.of(12,10));
            });
            disponibilidadService.crearDisponibilidad(m1.getId(),FECHA,LocalTime.of(11,40),LocalTime.of(12,40));
        })).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("se cruza");
        assertThat(ids).hasSize(2);assertThat(disponibilidades.count()).isEqualTo(2);
        assertThat(actual().getHoraInicio()).isEqualTo(LocalTime.of(11,10));
    }
    enum Carrera {MISMO_BLOQUE,PACIENTE_CRUZADO,DISPONIBILIDADES_CRUZADAS,RESERVA_EDICION,RESERVA_ELIMINACION,CONFIRMACION_CANCELACION,RESERVA_DESACTIVAR_USUARIO,RESERVA_DESACTIVAR_MEDICO}
    Future<Boolean> competir(ExecutorService pool,int indice,Runnable operacion,CyclicBarrier inicio,AtomicLongArray ids,Set<Integer> aislamientos) {
        return pool.submit(()->{
            try { return new TransactionTemplate(txManager).execute(s->{
                ids.set(indice,jdbc.queryForObject("select session_id()",Long.class));
                aislamientos.add(jdbc.execute((ConnectionCallback<Integer>)java.sql.Connection::getTransactionIsolation));
                try { inicio.await(10,TimeUnit.SECONDS); } catch(Exception e) { throw new IllegalStateException("Coordinacion de prueba fallida",e); }
                operacion.run();return true;
            }); } catch(IllegalArgumentException regla) { return false; }
        });
    }
    @ParameterizedTest @EnumSource(Carrera.class)
    void carrerasConDosTransacciones(Carrera caso) throws Exception {
        Runnable a,b;Long citaId=null;
        switch(caso) {
            case MISMO_BLOQUE -> {a=()->reservar(p1,d1,INICIO);b=()->reservar(p2,d1,INICIO);}
            case PACIENTE_CRUZADO -> {a=()->reservar(p1,d1,INICIO);b=()->reservar(p1,d2,INICIO);}
            case DISPONIBILIDADES_CRUZADAS -> {
                a=()->disponibilidadService.crearDisponibilidad(m1.getId(),FECHA.plusDays(1),INICIO,FIN);
                b=()->disponibilidadService.crearDisponibilidad(m1.getId(),FECHA.plusDays(1),INICIO.plusMinutes(30),FIN.plusMinutes(30));
            }
            case RESERVA_EDICION -> {a=()->reservar(p1,d1,INICIO);b=()->disponibilidadService.actualizarDisponibilidad(d1.getId(),FECHA,LocalTime.of(11,10),LocalTime.of(12,10));}
            case RESERVA_ELIMINACION -> {a=()->reservar(p1,d1,INICIO);b=()->disponibilidadService.eliminarDisponibilidad(d1.getId());}
            case CONFIRMACION_CANCELACION -> {Long id=reservar(p1,d1,INICIO).getId();citaId=id;a=()->citaService.confirmarCita(id);b=()->citaService.cancelarCitaAdministrador(id);}
            case RESERVA_DESACTIVAR_USUARIO -> {a=()->reservar(p1,d1,INICIO);b=()->usuarioService.desactivarUsuario(p1.getId());}
            case RESERVA_DESACTIVAR_MEDICO -> {a=()->reservar(p1,d1,INICIO);b=()->medicoService.desactivarMedico(m1.getId());}
            default -> throw new IllegalStateException();
        }
        var ids=new AtomicLongArray(2);Set<Integer> aislamientos=ConcurrentHashMap.newKeySet();var inicio=new CyclicBarrier(2);
        boolean ra,rb;
        try(var pool=Executors.newFixedThreadPool(2)) {
            var fa=competir(pool,0,a,inicio,ids,aislamientos);var fb=competir(pool,1,b,inicio,ids,aislamientos);
            ra=fa.get(20,TimeUnit.SECONDS);rb=fb.get(20,TimeUnit.SECONDS);
        }
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));assertThat(aislamientos).containsExactly(java.sql.Connection.TRANSACTION_READ_COMMITTED);
        switch(caso) {
            case MISMO_BLOQUE,PACIENTE_CRUZADO -> {assertThat(ra^rb).isTrue();assertThat(activas()).isEqualTo(1);}
            case DISPONIBILIDADES_CRUZADAS -> {assertThat(ra^rb).isTrue();assertThat(disponibilidades.findByMedicoIdAndFecha(m1.getId(),FECHA.plusDays(1))).hasSize(1);}
            case RESERVA_EDICION -> {assertThat(ra^rb).isTrue();assertThat(activas()).isEqualTo(ra?1:0);assertThat(actual().getHoraInicio()).isEqualTo(ra?INICIO:LocalTime.of(11,10));}
            case RESERVA_ELIMINACION -> {assertThat(ra^rb).isTrue();assertThat(activas()).isEqualTo(ra?1:0);assertThat(disponibilidades.existsById(d1.getId())).isEqualTo(ra);}
            case CONFIRMACION_CANCELACION -> {assertThat(rb).isTrue();assertThat(estado(citaId).getEstado()).isEqualTo(EstadoCita.CANCELADA);}
            case RESERVA_DESACTIVAR_USUARIO -> {assertThat(rb).isTrue();assertThat(activas()).isEqualTo(ra?1:0);assertThat(usuarios.findById(p1.getId()).orElseThrow().isActivo()).isFalse();}
            case RESERVA_DESACTIVAR_MEDICO -> {assertThat(rb).isTrue();assertThat(activas()).isEqualTo(ra?1:0);assertThat(medicos.findById(m1.getId()).orElseThrow().isActivo()).isFalse();}
        }
        coherencia();
        evidencia.add(String.format(Locale.ROOT,"{\"caso\":\"%s\",\"ejecuciones\":1,\"conexion_1\":%d,\"conexion_2\":%d,\"aislamiento\":\"READ_COMMITTED\",\"timeout_configurado_ms\":3000,\"operacion_1_aceptada\":%s,\"operacion_2_aceptada\":%s,\"estado_final_verificado\":true}",caso,ids.get(0),ids.get(1),ra,rb));
    }
    @Test void conflictosDeHistorialYRecursoEliminadoSePresentanEnMvc() throws Exception {
        var c=reservar(p1,d1,INICIO);citaService.cancelarCitaAdministrador(c.getId());
        mvc.perform(post("/disponibilidades/eliminar/"+d1.getId()).with(user("admin@example.test").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/disponibilidades")).andExpect(flash().attributeExists("error"));
        disponibilidadService.eliminarDisponibilidad(d2.getId());
        mvc.perform(post("/disponibilidades/editar/"+d2.getId()).with(user("admin@example.test").roles("ADMIN")).with(csrf())
                .param("fecha",FECHA.toString()).param("horaInicio","11:10").param("horaFin","12:10"))
                .andExpect(redirectedUrl("/disponibilidades")).andExpect(flash().attributeExists("error"));
        assertThat(disponibilidades.existsById(d1.getId())).isTrue();assertThat(citas.count()).isEqualTo(1);
    }
    @Test void timeoutRealDeBloqueoEsConflictoMvcControlado() throws Exception {
        var tomado=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var titular=pool.submit(()->new TransactionTemplate(txManager).executeWithoutResult(s->{
                bloqueos.medico(m1.getId());tomado.countDown();
                try { if(!liberar.await(15,TimeUnit.SECONDS))throw new IllegalStateException("No se libero el titular de prueba"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
            }));
            try {
                assertThat(tomado.await(10,TimeUnit.SECONDS)).isTrue();
                mvc.perform(post("/disponibilidades/guardar").with(user("admin@example.test").roles("ADMIN")).with(csrf())
                        .param("medicoId",m1.getId().toString()).param("fecha",FECHA.plusDays(1).toString()).param("horaInicio","11:10").param("horaFin","12:10"))
                        .andExpect(redirectedUrl("/disponibilidades")).andExpect(flash().attribute("error","Otro proceso esta modificando estos datos. Vuelve a intentarlo."));
            } finally {liberar.countDown();titular.get(10,TimeUnit.SECONDS);}
        }
        assertThat(disponibilidades.count()).isEqualTo(2);assertThat(citas.count()).isZero();
    }
    @AfterAll void guardarEvidencia() throws Exception {
        Path destino=Path.of("target","agenda-03-carreras.json");Files.createDirectories(destino.getParent());
        Files.writeString(destino,"["+String.join(",",evidencia)+"]");
    }
}
