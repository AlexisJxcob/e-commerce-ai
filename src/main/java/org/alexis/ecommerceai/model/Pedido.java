package org.alexis.ecommerceai.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** Suma de las líneas, sin costo de entrega. */
    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    /** Costo de entrega aplicado por {@code EnvioService} al crear el pedido. */
    @Column(name = "costo_despacho", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoDespacho = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_entrega", length = 20)
    private MetodoEntrega metodoEntrega;

    // --- Datos del comprador (recolectados en el checkout) ---

    @Column(name = "comprador_nombre", length = 70)
    private String compradorNombre;

    @Column(name = "comprador_apellidos", length = 70)
    private String compradorApellidos;

    @Column(name = "comprador_rut", length = 20)
    private String compradorRut;

    @Column(name = "comprador_email", length = 120)
    private String compradorEmail;

    @Column(name = "comprador_telefono", length = 25)
    private String compradorTelefono;

    // --- Datos de entrega ---

    @Column(name = "despacho_region", length = 100)
    private String despachoRegion;

    @Column(name = "despacho_comuna", length = 100)
    private String despachoComuna;

    @Column(name = "despacho_direccion", length = 200)
    private String despachoDireccion;

    @Column(name = "despacho_depto", length = 100)
    private String despachoDepto;

    @Column(name = "despacho_referencias", length = 300)
    private String despachoReferencias;

    @Column(name = "retira_tercero", nullable = false)
    private Boolean retiraTercero = false;

    @Column(name = "tercero_nombre", length = 150)
    private String terceroNombre;

    @Column(name = "tercero_rut", length = 20)
    private String terceroRut;

    @Column(name = "webpay_token", unique = true)
    private String webpayToken;

    @Column(name = "webpay_authorization_code", length = 50)
    private String webpayAuthorizationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", nullable = false, length = 20)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> items = new ArrayList<>();
}
