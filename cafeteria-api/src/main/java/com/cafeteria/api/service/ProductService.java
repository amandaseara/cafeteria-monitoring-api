package com.cafeteria.api.service;

import com.cafeteria.api.entity.Product;
import com.cafeteria.api.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Producto no encontrado: " + id));
    }

    public Product create(Product product) {
        product.setActive(true);
        return productRepository.save(product);
    }

    public Product update(Long id, Product data) {
        Product product = findById(id);

        product.setName(data.getName());
        product.setDescription(data.getDescription());
        product.setPrice(data.getPrice());

        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = findById(id);
        product.setActive(false);
        productRepository.save(product);
    }
}
