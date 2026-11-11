package io.github.soham023.spottracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI spotTrackerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Commodity Spot Tracker API")
                .version("v1")
                .description("Daily turmeric (हळद) prices from NCDEX spot, NCDEX futures and APMC mandis, "
                        + "with basis / spread analysis and alerts. Prices are ₹ per quintal.")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
