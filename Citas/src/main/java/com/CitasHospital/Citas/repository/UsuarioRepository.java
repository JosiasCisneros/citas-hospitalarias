package com.CitasHospital.Citas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.CitasHospital.Citas.model.Usuario;

import jakarta.persistence.LockModeType;

/**
 * Proporciona acceso a los datos de los usuarios.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);

    /**
     * Obtiene el usuario aplicando un bloqueo pesimista durante la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.correo = :correo")
    Optional<Usuario> buscarPorCorreoConBloqueo(
            @Param("correo") String correo
    );

    boolean existsByCorreo(String correo);

    boolean existsByCedula(String cedula);

    // Compatibilidad con datos anteriores sin reescribirlos. Los nuevos se guardan sin guiones.
    @Query("select (count(u) > 0) from Usuario u where (trim(u.cedula) = :normalizada or trim(u.cedula) = :conGuiones) and (:id is null or u.id <> :id)")
    boolean existeCedulaEquivalente(@Param("normalizada") String normalizada,
            @Param("conGuiones") String conGuiones, @Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> buscarPorIdConBloqueo(@Param("id") Long id);

    // Único escritor del sello: una entidad antigua no puede restaurar una versión anterior al hacer merge.
    @Modifying(flushAutomatically = true)
    @Query("update Usuario u set u.versionRecuperacion = u.versionRecuperacion + 1 where u.id = :id")
    void incrementarVersionRecuperacion(@Param("id") Long id);

    // Una cuenta inactiva tampoco puede mantener una sesión con un sello coincidente.
    @Query("select u.versionRecuperacion from Usuario u where u.id = :id and u.activo = true")
    Optional<Long> versionRecuperacion(@Param("id") Long id);

    boolean existsByCorreoAndIdNot(String correo, Long id);

    boolean existsByCedulaAndIdNot(String cedula, Long id);

    List<Usuario> findByActivoTrue();
}
