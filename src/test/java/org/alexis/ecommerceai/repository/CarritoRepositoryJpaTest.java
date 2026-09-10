package org.alexis.ecommerceai.repository;

import org.alexis.ecommerceai.model.Carrito;
import org.alexis.ecommerceai.model.CarritoItem;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de repositorio del carrito con conteo de sentencias.
 *
 * <p>El carrito es el caso con más riesgo de N+1 del dominio: cada línea
 * apunta a un producto, así que serializar el carrito sin fetch join genera
 * 1 consulta por línea.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class CarritoRepositoryJpaTest extends AbstractJpaSliceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ecommerce_carrito_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("pgvector-init.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registrarDatasource(registry, POSTGRES);
    }

    private static final int LINEAS = 3;

    @Autowired
    private CarritoRepository carritoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    private Usuario juan;

    @BeforeEach
    void setUp() {
        juan = new Usuario();
        juan.setUsername("juan");
        juan.setPassword("hash");
        juan.setRol(Rol.CLIENTE);
        em.persist(juan);

        Carrito carrito = new Carrito();
        carrito.setUsuario(juan);
        em.persist(carrito);

        for (int i = 1; i <= LINEAS; i++) {
            Producto producto = new Producto();
            producto.setSku("SKU-" + i);
            producto.setNombre("Producto " + i);
            producto.setPrecio(new BigDecimal("10.00"));
            producto.setStock(10);
            em.persist(producto);

            CarritoItem item = new CarritoItem();
            item.setProducto(producto);
            item.setCantidad(i);
            carrito.agregarItem(item);
        }
        limpiarSesion();
    }

    @Test
    void carritoConLineasYProductos_seResuelveEnUnaSolaConsulta() {
        Statistics stats = estadisticas();
        stats.clear();

        Optional<Carrito> carrito = carritoRepository.findByUsuarioUsername("juan");
        long trasLaConsulta = stats.getPrepareStatementCount();
        long subtotales = carrito.orElseThrow().getItems().stream()
                .map(item -> item.getProducto().getPrecio().multiply(BigDecimal.valueOf(item.getCantidad())))
                .count();
        long total = stats.getPrepareStatementCount();

        assertThat(carrito).isPresent();
        assertThat(subtotales).isEqualTo(LINEAS);
        assertThat(trasLaConsulta).isEqualTo(1);
        assertThat(total).isEqualTo(trasLaConsulta);
    }

    @Test
    void sinEntityGraph_lasLineasYProductosDegeneranEnNPlus1() {
        Statistics stats = estadisticas();
        stats.clear();

        Optional<Carrito> carrito = carritoRepository.findByUsuarioId(juan.getId());
        long trasLaConsulta = stats.getPrepareStatementCount();
        carrito.orElseThrow().getItems().forEach(item -> item.getProducto().getNombre());
        long total = stats.getPrepareStatementCount();

        assertThat(trasLaConsulta).isEqualTo(1);
        // 1 consulta de líneas + 1 por producto: el contraste que justifica el fetch
        assertThat(total).isEqualTo(1 + 1 + LINEAS);
    }

    @Test
    void findConItemsByUsuarioId_traeLineasYProductosParaMutarElCarrito() {
        limpiarSesion();
        Statistics stats = estadisticas();
        stats.clear();

        Carrito carrito = carritoRepository.findConItemsByUsuarioId(juan.getId()).orElseThrow();
        int lineas = carrito.getItems().size();
        String nombre = carrito.getItems().get(0).getProducto().getNombre();

        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(lineas).isEqualTo(LINEAS);
        assertThat(nombre).isNotBlank();
    }

    @Test
    void vaciarCarrito_borraLasLineasPorOrphanRemoval() {
        Carrito carrito = carritoRepository.findConItemsByUsuarioId(juan.getId()).orElseThrow();

        carrito.vaciar();
        carritoRepository.saveAndFlush(carrito);
        limpiarSesion();

        Carrito recargado = carritoRepository.findByUsuarioUsername("juan").orElseThrow();
        assertThat(recargado.getItems()).isEmpty();
        assertThat(productoRepository.count()).isEqualTo(LINEAS);
    }
}
