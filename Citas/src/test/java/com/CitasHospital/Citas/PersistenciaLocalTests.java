package com.CitasHospital.Citas;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.springframework.mock.env.MockEnvironment;
import com.CitasHospital.Citas.config.PersistenciaLocalConfiguracion;
import static org.assertj.core.api.Assertions.*;

class PersistenciaLocalTests {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path temporal;
    @Test void dosJVMConservanReservaLoginSelloYAdmin() throws Exception {
        String original=UUID.randomUUID().toString(),nueva=UUID.randomUUID().toString();
        Path datos=temporal.resolve("datos con espacios");
        Path primera=ejecutar("crear",datos,original,nueva);
        assertThat(Files.isRegularFile(datos.resolve("hospital.mv.db"))).isTrue();
        Path segunda=ejecutar("verificar",datos,original,nueva);
        String a=Files.readString(primera),b=Files.readString(segunda);
        assertThat(a).contains("\"fase\":\"crear\"");assertThat(b).contains("\"fase\":\"verificar\"");
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/persistencia-04-reinicio.json"),"{\"procesos\":["+a+","+b+"],\"directorio_temporal_exclusivo\":true,\"jvm_finalizadas\":true}\n");
    }
    Path ejecutar(String fase,Path datos,String original,String nueva) throws Exception {
        Path evidencia=temporal.resolve(fase+".json"),log=temporal.resolve(fase+".log");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dspring.devtools.restart.enabled=false","-cp",
            System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
            PersistenciaReinicioProceso.class.getName(),fase,datos.toString(),evidencia.toString());
        builder.environment().put("PRUEBA_PASSWORD_ORIGINAL",original);builder.environment().put("PRUEBA_PASSWORD_NUEVA",nueva);
        // Aislar de configuracion personal de Spring, salvo entorno de SO/JDK.
        builder.environment().keySet().removeIf(k->k.startsWith("SPRING_")||k.startsWith("DEMO_ADMIN_")||k.equals("CITAS_DATOS"));
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process proceso=builder.start();
        try {
            assertThat(proceso.waitFor(60,TimeUnit.SECONDS)).as("JVM %s termina en 60s; revisar fixture si falla",fase).isTrue();
            assertThat(proceso.exitValue()).withFailMessage("JVM %s fallo. Log aislado: %s",fase,log).isZero();
            assertThat(evidencia).isRegularFile();return evidencia;
        } finally { if(proceso.isAlive()) { proceso.destroyForcibly();proceso.waitFor(); } }
    }
    @Test void esquemaAjenoSeRechazaSinAdoptarNiBorrarDatos() throws Exception {
        String url="jdbc:h2:mem:ajena04"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.execute("create table legado(id integer primary key, dato varchar(50))");s.execute("insert into legado values(1,'sintetico')");
            var flyway=Flyway.configure().dataSource(url,"sa","").baselineOnMigrate(false).cleanDisabled(true).load();
            var estrategia=new PersistenciaLocalConfiguracion().migrarBaseLocal(new MockEnvironment().withProperty("citas.datos",temporal.toString()));
            assertThatThrownBy(()->estrategia.migrate(flyway)).isInstanceOf(IllegalStateException.class).hasMessageContaining("No habilites baseline");
            try(var rs=s.executeQuery("select dato from legado where id=1")) { assertThat(rs.next()).isTrue();assertThat(rs.getString(1)).isEqualTo("sintetico"); }
            try(var rs=s.executeQuery("select count(*) from information_schema.tables where table_name='flyway_schema_history'")) { rs.next();assertThat(rs.getInt(1)).isZero(); }
        }
    }
    @Test void migracionMantieneUnicidadEnumsForaneasYPrecision() throws Exception {
        String url="jdbc:h2:mem:esquema04"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.execute("insert into usuario(activo,cedula,correo,rol) values(true,'TEST-04','esquema@example.test','USUARIO')");
            assertThatThrownBy(()->s.execute("insert into usuario(activo,correo) values(true,'esquema@example.test')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into usuario(activo,cedula) values(true,'TEST-04')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into usuario(activo,rol) values(true,'OTRO')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("insert into token_recuperacion(hash,vence_en,usuario_id) values('ficticio',current_timestamp,999)" )).isInstanceOf(SQLException.class);
            try(var rs=s.executeQuery("select version_recuperacion from usuario")) { rs.next();assertThat(rs.getLong(1)).isZero(); }
            try(var rs=s.executeQuery("select datetime_precision from information_schema.columns where table_name='CITA' and column_name='HORA_INICIO'")) { rs.next();assertThat(rs.getInt(1)).isZero(); }
            try(var rs=s.executeQuery("select data_type,datetime_precision from information_schema.columns where table_name='TOKEN_RECUPERACION' and column_name='VENCE_EN'")) { rs.next();assertThat(rs.getString(1)).isEqualTo("TIMESTAMP WITH TIME ZONE");assertThat(rs.getInt(2)).isEqualTo(6); }
        }
    }
}
