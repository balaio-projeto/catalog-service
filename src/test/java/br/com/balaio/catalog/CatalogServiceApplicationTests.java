package br.com.balaio.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CatalogServiceApplicationTests extends CatalogJwtTestSupport {
    @Test
    void contextLoads() {
    }
}
