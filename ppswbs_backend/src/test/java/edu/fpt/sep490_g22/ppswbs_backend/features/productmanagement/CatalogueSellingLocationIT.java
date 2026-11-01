package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocation;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.facade.SellingLocationPort;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.support.CatalogueTestSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Decision A (2026-11-01) over HTTP on MySQL 8.4: {@code GET /api/v1/product-selling-locations} is public, no-store and
 * returns the active branches of {@link SellingLocationPort} sorted by name then id; a missing, failing or
 * unverifiable branch source is 503 {@code LOCATION_DEPENDENCY_UNAVAILABLE}, never an empty list. The port is
 * implemented by 003.2; until then the "no source" case is what develop answers.
 */
class CatalogueSellingLocationIT {

    private static JsonNode call(MockMvc mvc, ObjectMapper json, int status) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/product-selling-locations")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
    class WithABranchSource {

        @Autowired private MockMvc mvc;
        @Autowired private ObjectMapper json;
        @MockitoBean private SellingLocationPort port;

        @Test
        void activeBranchesAreListedWithoutABearerSortedByNameThenId() throws Exception {
            when(port.activeSellingLocations()).thenReturn(List.of(
                    new SellingLocation(9, "MLAND Thủ Đức", "1 Võ Văn Ngân"),
                    new SellingLocation(3, "MLAND Quận 1", "12 Lê Lợi"),
                    new SellingLocation(2, "MLAND Quận 1", "Chi nhánh 2")));

            JsonNode body = call(mvc, json, 200);

            assertThat(body.get("items")).hasSize(3);
            assertThat(body.get("items").get(0).get("id").asText()).isEqualTo("2");
            assertThat(body.get("items").get(1).get("id").asText()).isEqualTo("3");
            assertThat(body.get("items").get(2).get("name").asText()).isEqualTo("MLAND Thủ Đức");
            assertThat(body.get("items").get(0).get("address").asText()).isEqualTo("Chi nhánh 2");
        }

        @Test
        void aFailingOrUnverifiableSourceIs503NeverAnEmptyList() throws Exception {
            doThrow(new IllegalStateException("branch store down")).when(port).activeSellingLocations();
            assertThat(call(mvc, json, 503).get("code").asText()).isEqualTo("LOCATION_DEPENDENCY_UNAVAILABLE");

            doReturn(null).when(port).activeSellingLocations();
            assertThat(call(mvc, json, 503).get("code").asText()).isEqualTo("LOCATION_DEPENDENCY_UNAVAILABLE");

            doReturn(List.of(new SellingLocation(3, "A", ""), new SellingLocation(3, "B", ""))).when(port).activeSellingLocations();
            assertThat(call(mvc, json, 503).get("code").asText()).as("duplicate branch").isEqualTo("LOCATION_DEPENDENCY_UNAVAILABLE");

            doReturn(List.of()).when(port).activeSellingLocations();
            assertThat(call(mvc, json, 200).get("items")).as("no active branch is a real, empty answer").isEmpty();
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles(CatalogueTestSupport.MYSQL_PROFILE)
    class WithoutABranchSource {

        @Autowired private MockMvc mvc;
        @Autowired private ObjectMapper json;

        @Test
        void withoutAnImplementationTheReadIs503() throws Exception {
            JsonNode body = call(mvc, json, 503);
            assertThat(body.get("code").asText()).isEqualTo("LOCATION_DEPENDENCY_UNAVAILABLE");
            assertThat(body.get("correlationId").asText()).isNotBlank();
        }
    }
}
