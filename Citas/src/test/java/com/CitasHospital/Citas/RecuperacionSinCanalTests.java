package com.CitasHospital.Citas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.test.web.servlet.MockMvc;
import com.CitasHospital.Citas.repository.TokenRecuperacionRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:02ASinCanal", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class RecuperacionSinCanalTests {
    @Autowired MockMvc mvc;
    @Autowired TokenRecuperacionRepository tokens;
    @Autowired com.CitasHospital.Citas.repository.UsuarioRepository usuarios;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Test void sinCanalNoSimulaEntregaNiGeneraToken() throws Exception {
        mvc.perform(get("/recuperar-password")).andExpect(status().isOk())
                .andExpect(model().attribute("mensaje", "La recuperacion no esta disponible en este entorno. No se ha enviado ningun correo."));
        mvc.perform(post("/recuperar-password").with(csrf()).param("correo", "ausente@example.test"))
                .andExpect(status().isOk()).andExpect(model().attribute("mensaje", "La recuperacion no esta disponible en este entorno. No se ha enviado ningun correo."));
        var cuenta = usuarios.save(new com.CitasHospital.Citas.model.Usuario("Paciente sin canal", "SIN-CANAL",
                "sin-canal@example.test", "Contacto ficticio", encoder.encode(java.util.UUID.randomUUID().toString()),
                com.CitasHospital.Citas.model.Rol.USUARIO));
        mvc.perform(post("/recuperar-password").with(csrf()).param("correo", cuenta.getCorreo()))
                .andExpect(status().isOk()).andExpect(model().attribute("mensaje", "La recuperacion no esta disponible en este entorno. No se ha enviado ningun correo."));
        assertThat(tokens.count()).isZero();
    }
}
