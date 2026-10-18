package com.cafeteria.api.service;

import com.cafeteria.api.dto.CreateOrderRequest;
import com.cafeteria.api.dto.OrderItemRequest;
import com.cafeteria.api.entity.Order;
import com.cafeteria.api.entity.OrderItem;
import com.cafeteria.api.entity.Product;
import com.cafeteria.api.repository.OrderRepository;
import com.cafeteria.api.repository.ProductRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MeterRegistry meterRegistry;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream()
                .map(this::createOrderItem)
                .toList();

        Order order = new Order(items);

        Order savedOrder = orderRepository.save(order);

        // Una unidad de contador por cada línea de producto
        // incluida en el pedido, no por cada unidad comprada.
        items.forEach(item -> {
            Counter counter = meterRegistry.counter(
                    "cafeteria.product.orders",
                    "product_id", item.getProduct().getId().toString(),
                    "product_name", item.getProduct().getName()
            );

            counter.increment();
        });

        return savedOrder;
    }

    private OrderItem createOrderItem(OrderItemRequest request) {
        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Producto no encontrado: "
                                        + request.productId()));

        if (!product.isActive()) {
            throw new RuntimeException(
                    "El producto está inactivo: " + product.getId());
        }

        return new OrderItem(
                product,
                request.quantity(),
                product.getPrice()
        );
    }

    public List<Order> findAll() {
        return orderRepository.findAllWithItems();
}

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Pedido no encontrado: " + id));
    }
}
