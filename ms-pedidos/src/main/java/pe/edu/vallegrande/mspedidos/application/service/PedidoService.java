package pe.edu.vallegrande.mspedidos.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.vallegrande.mspedidos.application.port.in.IPedidoServicePort;
import pe.edu.vallegrande.mspedidos.application.port.out.IPedidoRepositoryPort;
import pe.edu.vallegrande.mspedidos.application.port.out.IProductoClientPort;
import pe.edu.vallegrande.mspedidos.domain.model.Pedido;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PedidoService implements IPedidoServicePort {
    private final IPedidoRepositoryPort repositoryPort;
    private final IProductoClientPort productoClientPort;

    @Override
    public Flux<Pedido> findALl() {
        return repositoryPort.findAll();
    }

    @Override
    public Mono<Pedido> findById(Long id) {
        return repositoryPort.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Override
    public Mono<Pedido> create(Pedido order) {
        Long productIdLong = Long.valueOf(order.getProductId());
        return productoClientPort.findById(productIdLong)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado")))
                .flatMap(product -> {
                    if (product.getStock() < order.getQuantity()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock insuficiente"));
                    }

                    return productoClientPort.decreaseStock(productIdLong, order.getQuantity())
                            .flatMap(updated -> {
                                BigDecimal total = product.getPrice()
                                        .multiply(BigDecimal.valueOf(order.getQuantity()));
                                order.setPrice(product.getPrice());
                                order.setTotal(total);
                                order.setStatus("CONFIRMADO");
                                order.setFecha(OffsetDateTime.now());
                                order.setCreatedAt(OffsetDateTime.now());
                                order.setUpdatedAt(OffsetDateTime.now());
                                return repositoryPort.save(order);
                            });
                });
    }

    @Override
    public Mono<Pedido> cancel(Long id) {
        return findById(id)
                .flatMap(order -> {
                    order.setStatus("CANCELADO");
                    order.setUpdatedAt(OffsetDateTime.now());
                    return repositoryPort.save(order);
                });
    }
}
