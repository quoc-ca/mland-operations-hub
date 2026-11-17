package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** T018: the catalogue database profile starts on the external MySQL 8.4 test database, never H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
class CatalogueMySqlEnvironmentIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void usesMySql84() {
        assertThat(CatalogueTestSupport.requireMySql84(dataSource)).startsWith("8.4");
    }
}
