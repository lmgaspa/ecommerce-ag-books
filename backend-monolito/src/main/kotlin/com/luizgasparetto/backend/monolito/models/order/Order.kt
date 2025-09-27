package com.luizgasparetto.backend.monolito.models.order

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(
    name = "orders",
    indexes = [Index(name = "uk_orders_txid", columnList = "txid", unique = true)]
)
data class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // Dados do cliente
    @Column(nullable = false) val firstName: String,
    @Column(nullable = false) val lastName: String,
    @Column(nullable = false) val email: String,

    @Column(nullable = false) var cpf: String,
    @Column(nullable = false) var number: String,
    @Column(nullable = true)  var complement: String? = null,
    @Column(nullable = false) var district: String,

    @Column(nullable = false) val address: String,
    @Column(nullable = false) val city: String,
    @Column(nullable = false) val state: String,
    @Column(nullable = false) val cep: String,
    @Column(nullable = false) val phone: String,

    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    // Cartão
    @Column(nullable = true)
    var installments: Int? = 1,

    // Método de pagamento: "card" ou "pix"
    @Column(nullable = false, length = 8)
    var paymentMethod: String = "card",

    // Cartão: chargeId da Efí (PIX normalmente não usa)
    @Column(name = "charge_id", unique = true)
    var chargeId: String? = null,

    // Totais
    @Column(nullable = false, precision = 10, scale = 2) val total: BigDecimal,
    @Column(nullable = false, precision = 10, scale = 2) val shipping: BigDecimal,

    // Pagamento
    @Column(nullable = false)
    var paid: Boolean = false,

    // PIX: txid opcional (para cartão fica null)
    @Column(nullable = true, unique = true, length = 35)
    var txid: String? = null,

    @Column(name = "mailed_at")
    var mailedAt: OffsetDateTime? = null,

    // PIX
    @Column(columnDefinition = "TEXT")
    var qrCode: String? = null,

    @Column(columnDefinition = "TEXT")
    var qrCodeBase64: String? = null,

    // Status interno da ordem
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: OrderStatus = OrderStatus.NEW,

    @Column(name = "reserve_expires_at")
    var reserveExpiresAt: OffsetDateTime? = null,

    @Column(name = "paid_at")
    var paidAt: OffsetDateTime? = null,

    @OneToMany(
        mappedBy = "order",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var items: MutableList<OrderItem> = mutableListOf()
)
