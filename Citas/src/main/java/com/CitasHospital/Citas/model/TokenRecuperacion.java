package com.CitasHospital.Citas.model;

import java.time.Instant;
import jakarta.persistence.*;

/** Una credencial temporal por cuenta; nunca almacena el token en claro. */
@Entity
@Table(name = "token_recuperacion")
public class TokenRecuperacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;
    @Column(nullable = false, length = 64, unique = true)
    private String hash;
    @Column(nullable = false)
    private Instant venceEn;
    private Instant consumidoEn;

    protected TokenRecuperacion() { }
    public TokenRecuperacion(Usuario usuario, String hash, Instant venceEn) {
        this.usuario = usuario;
        reemplazar(hash, venceEn);
    }
    public void reemplazar(String hash, Instant venceEn) {
        this.hash = hash;
        this.venceEn = venceEn;
        this.consumidoEn = null;
    }
    public boolean vigente(Instant ahora) { return consumidoEn == null && ahora.isBefore(venceEn); }
    public void consumir(Instant ahora) { consumidoEn = ahora; }
    public String getHash() { return hash; }
    public Instant getVenceEn() { return venceEn; }
    public Instant getConsumidoEn() { return consumidoEn; }
}
