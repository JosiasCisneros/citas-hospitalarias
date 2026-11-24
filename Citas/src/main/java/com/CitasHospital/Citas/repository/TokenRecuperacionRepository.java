package com.CitasHospital.Citas.repository;

import java.util.Optional;
import com.CitasHospital.Citas.model.TokenRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {
    Optional<TokenRecuperacion> findByUsuarioId(Long usuarioId);
    // Proyección: evita cargar un token obsoleto antes de adquirir el bloqueo del usuario.
    @Query("select t.usuario.id from TokenRecuperacion t where t.hash = :hash")
    Optional<Long> usuarioPorHash(@Param("hash") String hash);
}
