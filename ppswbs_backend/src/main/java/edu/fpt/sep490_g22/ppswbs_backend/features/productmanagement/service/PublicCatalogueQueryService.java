package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.ProductCategoryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao.PublicCatalogueQueryRepository.CandidateRow;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.CategoryDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.ManagementProductDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dto.PublicCatalogueDtos;
import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.ProductCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.text.Collator;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public catalogue search, filter, sort and paging (T068, US8, FR-032/033/035). No identity is needed.
 *
 * <ul>
 *   <li>Keyword: trimmed, at most 200 code points, case-insensitive literal substring of name or description.
 *       Category must be active. Price bounds are inclusive whole VND, minimum &lt;= maximum.</li>
 *   <li>Price range and "can buy" must hold for the same size (FR-033): a disabled size's price never pairs
 *       with another size's availability.</li>
 *   <li>Sorts: name A–Z (default), price ascending/descending by the lowest matching size price, newest by
 *       creation time; ties by product ID. 20 products per page.</li>
 *   <li>The sort keys of every matching product are read first (small scalar rows), filtered and paged; then
 *       only the page's products are loaded. Pages are therefore never short because of filtering.</li>
 * </ul>
 */
@Service
public class PublicCatalogueQueryService {

    public enum Sort {
        NAME_ASC("name-asc"),
        PRICE_ASC("price-asc"),
        PRICE_DESC("price-desc"),
        NEWEST("newest");

        private final String value;

        Sort(String value) {
            this.value = value;
        }

        static Sort parse(String raw) {
            if (raw == null) {
                return NAME_ASC;
            }
            for (Sort sort : values()) {
                if (sort.value.equals(raw)) {
                    return sort;
                }
            }
            throw new CatalogueException(CatalogueErrorCode.INVALID_SORT);
        }
    }

    public record Filter(String q, String categoryId, String minimumPrice, String maximumPrice, Boolean canBuy,
                         String sort, Integer page) {
    }

    /** Vietnamese alphabetical order, ignoring case. */
    private static final Collator NAME_ORDER = vietnameseOrder();

    private record ProductKey(Long productId, String name, Instant createdAt, BigInteger lowestPrice) {
    }

    private final PublicCatalogueQueryRepository publicQueries;
    private final PublicCatalogueProjection projection;
    private final ProductCategoryRepository categories;

    public PublicCatalogueQueryService(PublicCatalogueQueryRepository publicQueries,
                                       PublicCatalogueProjection projection, ProductCategoryRepository categories) {
        this.publicQueries = publicQueries;
        this.projection = projection;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public PublicCatalogueDtos.PublicProductPage list(Filter filter) {
        String keyword = CatalogueValues.searchQuery("q", filter.q());
        Long categoryId = filter.categoryId() == null ? null : CatalogueValues.parseId("categoryId", filter.categoryId());
        BigInteger minimum = filter.minimumPrice() == null ? null
                : CatalogueValues.parseQuantity("minimumPrice", filter.minimumPrice());
        BigInteger maximum = filter.maximumPrice() == null ? null
                : CatalogueValues.parseQuantity("maximumPrice", filter.maximumPrice());
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw CatalogueException.invalidField("minimumPrice", "The minimum price must not exceed the maximum price.");
        }
        Sort sort = Sort.parse(filter.sort());
        int page = filter.page() == null ? 0 : filter.page();
        if (page < 0) {
            throw CatalogueException.invalidField("page", "Page must be zero or greater.");
        }
        if (categoryId != null && categories.findByIdAndActiveTrue(categoryId).isEmpty()) {
            throw CatalogueException.invalidField("categoryId", "The category does not exist or is not active.");
        }
        boolean onlyBuyable = Boolean.TRUE.equals(filter.canBuy());

        List<CandidateRow> rows = publicQueries.findCandidates(new PublicCatalogueQueryRepository.Criteria(
                keyword == null ? null : "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%",
                categoryId, minimum, maximum, onlyBuyable));
        if (onlyBuyable) {
            Map<Long, BigInteger> held = projection.held(rows.stream().map(CandidateRow::variantId).toList());
            rows = rows.stream()
                    .filter(row -> PublicCatalogueProjection.canBuy(row.saleEnabled(), row.remainingUnsold(),
                            held.get(row.variantId())))
                    .toList();
        }
        Map<Long, ProductKey> keys = new LinkedHashMap<>();
        for (CandidateRow row : rows) {
            keys.merge(row.productId(), new ProductKey(row.productId(), row.name(), row.createdAt(), row.appliedPrice()),
                    (a, b) -> a.lowestPrice().compareTo(b.lowestPrice()) <= 0 ? a : b);
        }
        List<Long> ordered = keys.values().stream().sorted(comparator(sort)).map(ProductKey::productId).toList();
        int from = (int) Math.min((long) page * ManagementProductDtos.PAGE_SIZE, ordered.size());
        int to = Math.min(from + ManagementProductDtos.PAGE_SIZE, ordered.size());
        return new PublicCatalogueDtos.PublicProductPage(projection.load(ordered.subList(from, to)), page,
                ManagementProductDtos.PAGE_SIZE, Integer.toString(ordered.size()));
    }

    /** A product that is hidden, deleted or otherwise ineligible is "not found", like one that never existed. */
    @Transactional(readOnly = true)
    public PublicCatalogueDtos.PublicProduct get(String productId) {
        long id = CatalogueValues.parseId("productId", productId);
        return projection.load(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new CatalogueException(CatalogueErrorCode.PRODUCT_NOT_FOUND));
    }

    /** Active categories only, for the public filter. */
    @Transactional(readOnly = true)
    public CategoryDtos.CategoryPage categories(Integer page) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw CatalogueException.invalidField("page", "Page must be zero or greater.");
        }
        Page<ProductCategory> found = categories.findAllByActiveTrueOrderByNameAscIdAsc(
                PageRequest.of(pageNumber, ManagementProductDtos.PAGE_SIZE));
        return new CategoryDtos.CategoryPage(found.getContent().stream().map(ProductCategoryService::view).toList(),
                pageNumber, ManagementProductDtos.PAGE_SIZE, Long.toString(found.getTotalElements()));
    }

    private static Comparator<ProductKey> comparator(Sort sort) {
        Comparator<ProductKey> byId = Comparator.comparing(ProductKey::productId);
        return switch (sort) {
            case NAME_ASC -> Comparator.comparing(ProductKey::name, NAME_ORDER).thenComparing(byId);
            case PRICE_ASC -> Comparator.comparing(ProductKey::lowestPrice).thenComparing(byId);
            case PRICE_DESC -> Comparator.comparing(ProductKey::lowestPrice).reversed().thenComparing(byId);
            case NEWEST -> Comparator.comparing(ProductKey::createdAt).reversed().thenComparing(byId.reversed());
        };
    }

    private static Collator vietnameseOrder() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi"));
        collator.setStrength(Collator.SECONDARY);
        return collator;
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
