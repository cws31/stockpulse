package com.stockpulse.inventory.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI stockPulseInventoryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("StockPulse Inventory Service API")
                .version("v1")
                .description("""
                        APIs for product inventory, stock adjustments, and order reservations.\n\n\
                        **Inventory rules**\n\
                        - `totalStock = availableStock + reservedStock`\n\
                        - Reservations reduce available stock and increase reserved stock.\n\
                        - Confirmation consumes reserved and total stock.\n\
                        - Release or expiry returns reserved stock to available stock.\n\
                        - Reservation requests require a unique `idempotencyKey`.\n\
                        - Stock-changing operations are serialized with PostgreSQL row locks.\n\n\
                        Reservation lifetime is configurable with `RESERVATION_TTL_SECONDS` (default: 300 seconds).
                        """)
                .contact(new Contact().name("StockPulse Engineering"))
                .license(new License().name("Internal project API")));
    }
}
