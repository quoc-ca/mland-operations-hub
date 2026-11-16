package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CategoryDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FR-062: the category management list filters by activation state before paging; no filter lists every category. */
class ProductCategoryListTests {

    private final ProductCategoryRepository repository = mock(ProductCategoryRepository.class);
    private ProductCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ProductCategoryService(repository, null, null, null, null);
    }

    @Test
    void withoutFilterListsEveryCategory() {
        PageRequest firstPage = PageRequest.of(0, ManagementProductDtos.PAGE_SIZE);
        when(repository.findAllByOrderByNameAscIdAsc(firstPage))
                .thenReturn(new PageImpl<>(List.of(category("Nhẫn"), category("Dây chuyền")), firstPage, 2));

        CategoryDtos.CategoryPage page = service.list(null, null);

        assertThat(page.totalElements()).isEqualTo("2");
        verify(repository, never()).findAllByActiveOrderByNameAscIdAsc(anyBoolean(), any());
    }

    @Test
    void activeFilterIsAppliedBeforePaging() {
        PageRequest secondPage = PageRequest.of(1, ManagementProductDtos.PAGE_SIZE);
        when(repository.findAllByActiveOrderByNameAscIdAsc(true, secondPage))
                .thenReturn(new PageImpl<>(List.of(category("Nhẫn")), secondPage, 21));

        CategoryDtos.CategoryPage page = service.list(1, true);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo("21");
        assertThat(page.items()).hasSize(1);
        verify(repository, never()).findAllByOrderByNameAscIdAsc(any());
    }

    @Test
    void inactiveFilterAsksForInactiveCategories() {
        PageRequest firstPage = PageRequest.of(0, ManagementProductDtos.PAGE_SIZE);
        when(repository.findAllByActiveOrderByNameAscIdAsc(false, firstPage))
                .thenReturn(new PageImpl<>(List.of(), firstPage, 0));

        CategoryDtos.CategoryPage page = service.list(0, false);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo("0");
        verify(repository).findAllByActiveOrderByNameAscIdAsc(false, firstPage);
    }

    @Test
    void oneArgumentListKeepsListingEveryCategory() {
        PageRequest firstPage = PageRequest.of(0, ManagementProductDtos.PAGE_SIZE);
        when(repository.findAllByOrderByNameAscIdAsc(firstPage)).thenReturn(new PageImpl<>(List.of(), firstPage, 0));

        service.list(null);

        verify(repository).findAllByOrderByNameAscIdAsc(firstPage);
    }

    @Test
    void nameSearchIsTrimmedAndCombinesWithTheStatusFilter() {
        PageRequest firstPage = PageRequest.of(0, ManagementProductDtos.PAGE_SIZE);
        when(repository.findAllByNameContainingIgnoreCaseOrderByNameAscIdAsc("nhẫn", firstPage))
                .thenReturn(new PageImpl<>(List.of(category("Nhẫn")), firstPage, 1));
        when(repository.findAllByActiveAndNameContainingIgnoreCaseOrderByNameAscIdAsc(false, "nhẫn", firstPage))
                .thenReturn(new PageImpl<>(List.of(), firstPage, 0));

        assertThat(service.list(null, null, "  nhẫn ").totalElements()).isEqualTo("1");
        assertThat(service.list(null, false, "nhẫn").totalElements()).isEqualTo("0");
    }

    @Test
    void blankNameSearchMeansNoNameFilter() {
        PageRequest firstPage = PageRequest.of(0, ManagementProductDtos.PAGE_SIZE);
        when(repository.findAllByActiveOrderByNameAscIdAsc(true, firstPage)).thenReturn(new PageImpl<>(List.of(), firstPage, 0));

        service.list(null, true, "   ");

        verify(repository).findAllByActiveOrderByNameAscIdAsc(true, firstPage);
        verify(repository, never()).findAllByActiveAndNameContainingIgnoreCaseOrderByNameAscIdAsc(anyBoolean(), any(), any());
    }

    @Test
    void tooLongNameSearchIsRejected() {
        assertThatThrownBy(() -> service.list(null, null, "a".repeat(201)))
                .isInstanceOf(CatalogueException.class);
    }

    private static ProductCategory category(String name) {
        ProductCategory category = ProductCategory.create(name, null);
        ReflectionTestUtils.setField(category, "id", (long) name.length());
        return category;
    }
}
