package com.cafeteria.api.service;

import com.cafeteria.api.dto.CreateOrderRequest;
import com.cafeteria.api.dto.OrderItemRequest;
import com.cafeteria.api.entity.Product;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final OrderService orderService;

    private final Map<String, Long> productIds = new HashMap<>();
    private final List<JsonNode> orderCases = new ArrayList<>();

    private int currentOrderIndex = 0;

    public DemoDataLoader(
            ObjectMapper objectMapper,
            ProductService productService,
            OrderService orderService) {
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.orderService = orderService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        try (InputStream input = getClass()
                .getResourceAsStream("/demo-data.json")) {

            if (input == null) {
                throw new IllegalStateException(
                        "No se encontró demo-data.json");
            }

            JsonNode root = objectMapper.readTree(input);

            // 1. Crear los productos definidos en el JSON.
            for (JsonNode node : root.path("products")) {

                Product product = new Product();

                product.setName(node.path("name").asText());
                product.setDescription(
                        node.path("description").asText(""));
                product.setPrice(
                        node.path("price").decimalValue());

                Product savedProduct =
                        productService.create(product);

                productIds.put(
                        savedProduct.getName(),
                        savedProduct.getId());

                System.out.println(
                        "[DEMO] Producto creado: "
                                + savedProduct.getName()
                                + " | ID: "
                                + savedProduct.getId());
            }

            // 2. Guardar los casos de pedidos para ejecutarlos
            // de manera escalonada.
            root.path("orders").forEach(orderCases::add);

            System.out.println(
                    "[DEMO] Datos cargados. Pedidos preparados: "
                            + orderCases.size());
        }
    }

    // 3. Crear un pedido cada 15 segundos.
    // Esto permite observar el crecimiento de las métricas.
    @Scheduled(initialDelay = 15000, fixedDelay = 15000)
    public void executeNextOrder() {

        if (currentOrderIndex >= orderCases.size()) {
            return;
        }

        JsonNode testCase = orderCases.get(currentOrderIndex);

        String productName = testCase.path("product").asText();
        int quantity = testCase.path("quantity").asInt();

        Long productId = productIds.get(productName);

        if (productId == null) {
            throw new IllegalStateException(
                    "Producto no encontrado en los datos demo: "
                            + productName);
        }

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(
                        new OrderItemRequest(productId, quantity)
                )
        );

        var savedOrder = orderService.create(request);

        System.out.println(
                "[DEMO] Pedido creado: "
                        + savedOrder.getId()
                        + " | Producto: "
                        + productName
                        + " | Cantidad: "
                        + quantity
                        + " | Caso: "
                        + (currentOrderIndex + 1)
                        + "/" + orderCases.size());

        currentOrderIndex++;
    }
}
