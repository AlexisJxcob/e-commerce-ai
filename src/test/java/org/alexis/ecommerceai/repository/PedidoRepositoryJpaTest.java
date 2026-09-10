package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.EstadoPedido;
import org.alexis.ecommerceai.model.ItemPedido;
import org.alexis.ecommerceai.model.Pedido;
import org.alexis.ecommerceai.model.Producto;
import org.alexis.ecommerceai.model.Rol;
import org.alexis.ecommerceai.model.Usuario;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de repositorio de pedidos con conteo de sentencias SQL.
 *
 * <p>Objetivo: impedir que las consultas de lectura degeneren en N+1. Cada
 * aserción compara el número de sentencias preparadas con el esperado, de modo
 * que un futuro refactor que elimine el {@code EntityGraph} rompa el test en
 * lugar de degradar silenciosamente el rendimiento.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class PedidoRepositoryJpaTest extends AbstractJpaSliceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ecommerce_pedido_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("pgvector-init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registrarDatasource(registry, POSTGRES);
    }

    private static final int PEDIDOS = 3;
    private static final int ITEMS_POR_PEDIDO = 2;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario juan;

    @BeforeEach
    void setUp() {
        juan = new Usuario();
        juan.setUsername("juan");
        juan.setPassword("hash");
        juan.setRol(Rol.CLIENTE);
        em.persist(juan);

        for (int i = 0; i < PEDIDOS; i++) {
            Pedido pedido = new Pedido();
            pedido.setUsuario(juan);
            pedido.setFechaCreacion(LocalDateTime.now());
            pedido.setEstado(EstadoPedido.PENDIENTE);
            pedido.setTotal(new BigDecimal("30.00"));
            List<ItemPedido> items = new ArrayList<>();
            for (int j = 0; j < ITEMS_POR_PEDIDO; j++) {
                var item = new ItemPedido();
                item.setPedido(pedido);
                item.setProductoId((long) (i * ITEMS_POR_PEDIDO + j + 1));
                item.setCantidad(1);
                item.setPrecioUnitario(new BigDecimal("15.00"));
                items.add(item);
            }
            pedido.setItems(items);
            em.persist(pedido);
        }
        limpiarSesion();
    }

    @Test
    void listarPorUsuario_resuelveTodosLosItemsEnUnaSolaConsulta() {
        Statistics stats = estadisticas();
        stats.clear();

        List<Pedido> pedidos = pedidoRepository.findByUsuarioUsernameOrderByIdDesc("juan");
        long trasLaConsulta = stats.getPrepareStatementCount();

        // Acceder a los items no debe disparar ninguna consulta adicional
        int itemsVistos = pedidos.stream().mapToInt(p -> p.getItems().size()).sum();
        long total = stats.getPrepareStatementCount();

        assertThat(pedidos).hasSize(PEDIDOS);
        assertThat(itemsVistos).isEqualTo(PEDIDOS * ITEMS_POR_PEDIDO);
        assertThat(trasLaConsulta).isEqualTo(1);
        assertThat(total).isEqualTo(trasLaConsulta);
    }

    @Test
    void sinEntityGraph_laColeccionLazyDegeneraEnNPlus1() {
        Statistics stats = estadisticas();
        stats.clear();

        // Control: la consulta derivada SIN fetch join sí hace N+1
        List<Pedido> pedidos = pedidoRepository.findAll();
        long trasFindAll = stats.getPrepareStatementCount();
        pedidos.forEach(p -> p.getItems().size());
        long total = stats.getPrepareStatementCount();

        assertThat(trasFindAll).isEqualTo(1);
        assertThat(total).isEqualTo(1 + PEDIDOS);
    }

    @Test
    void findConItemsById_resuelveItemsYUsuarioEnUnaSolaConsulta() {
        Long id = pedidoRepository.findByUsuarioUsernameOrderByIdDesc("juan").get(0).getId();
        limpiarSesion();

        Statistics stats = estadisticas();
        stats.clear();
        Pedido pedido = pedidoRepository.findConItemsById(id).orElseThrow();
        // Ni los items ni el usuario provocan consultas extra
        String username = pedido.getUsuario().getUsername();
        int items = pedido.getItems().size();

        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(username).isEqualTo("juan");
        assertThat(items).isEqualTo(ITEMS_POR_PEDIDO);
    }

    @Test
    void findByUsuarioUsernameOrderByIdDesc_soloDevuelveLosDelUsuario() {
        Usuario pedro = new Usuario();
        pedro.setUsername("pedro");
        pedro.setPassword("hash");
        pedro.setRol(Rol.CLIENTE);
        usuarioRepository.save(pedro);
        Pedido ajeno = new Pedido();
        ajeno.setUsuario(pedro);
        ajeno.setFechaCreacion(LocalDateTime.now());
        ajeno.setEstado(EstadoPedido.PENDIENTE);
        ajeno.setTotal(BigDecimal.TEN);
        pedidoRepository.save(ajeno);
        limpiarSesion();

        assertThat(pedidoRepository.findByUsuarioUsernameOrderByIdDesc("juan")).hasSize(PEDIDOS);
        assertThat(pedidoRepository.findByUsuarioUsernameOrderByIdDesc("pedro")).hasSize(1);
    }
}
