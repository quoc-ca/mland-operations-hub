package edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.dao;

import edu.fpt.sep490_g22.ppswbs_backend.features.productmanagement.entity.CatalogueMediaObject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * JPQL adapter of the public catalogue (T068). Returns only small scalar rows (sort keys and the facts
 * needed for "can buy"); details are batch-loaded afterwards for the products of one page.
 *
 * <p>Publicly eligible (FR-031): published, not deleted, active category, at least one READY image and an
 * applied price for every size. The query text is fixed; every user value is a bound parameter.
 */
@Repository
public class PublicCatalogueQueryRepository {

    /** One matching size of an eligible product, with the product's sort keys. */
    public record CandidateRow(Long productId, String name, Instant createdAt, Long variantId,
                               BigInteger appliedPrice, Boolean saleEnabled, BigInteger remainingUnsold) {
    }

    /** {@code namePattern} is a lower-case LIKE pattern escaped with {@code !}, or null for no keyword. */
    public record Criteria(String namePattern, Long categoryId, BigInteger minimumPrice, BigInteger maximumPrice,
                           boolean onlySaleable) {
    }

    private static final String ELIGIBLE = """
            from Product p, ProductCategory c, ProductVariant v
            where c.id = p.categoryId and c.active = true
              and p.published = true and p.deletedAt is null
              and v.productId = p.id
              and exists (select i.id from ProductImage i, CatalogueMediaObject m
                          where i.productId = p.id and m.id = i.mediaObjectId and m.state = :ready)
              and not exists (select v2.id from ProductVariant v2
                              where v2.productId = p.id and v2.appliedPrice is null)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** Every matching size of every eligible product (no paging: the caller sorts and pages the keys). */
    public List<CandidateRow> findCandidates(Criteria criteria) {
        // Hibernate instantiates the record from the select list (matching constructor).
        StringBuilder jpql = new StringBuilder(
                "select p.id, p.name, p.createdAt, v.id, v.appliedPrice, v.saleEnabled, v.remainingUnsold ")
                .append(ELIGIBLE);
        if (criteria.namePattern() != null) {
            jpql.append(" and (lower(p.name) like :pattern escape '!' or lower(p.description) like :pattern escape '!')");
        }
        if (criteria.categoryId() != null) {
            jpql.append(" and p.categoryId = :categoryId");
        }
        if (criteria.minimumPrice() != null) {
            jpql.append(" and v.appliedPrice >= :minimumPrice");
        }
        if (criteria.maximumPrice() != null) {
            jpql.append(" and v.appliedPrice <= :maximumPrice");
        }
        if (criteria.onlySaleable()) {
            jpql.append(" and v.saleEnabled = true and v.remainingUnsold > 0");
        }
        jpql.append(" order by p.id, v.ringSize");
        TypedQuery<CandidateRow> query = entityManager.createQuery(jpql.toString(), CandidateRow.class)
                .setParameter("ready", CatalogueMediaObject.State.READY);
        if (criteria.namePattern() != null) {
            query.setParameter("pattern", criteria.namePattern());
        }
        if (criteria.categoryId() != null) {
            query.setParameter("categoryId", criteria.categoryId());
        }
        if (criteria.minimumPrice() != null) {
            query.setParameter("minimumPrice", criteria.minimumPrice());
        }
        if (criteria.maximumPrice() != null) {
            query.setParameter("maximumPrice", criteria.maximumPrice());
        }
        return query.getResultList();
    }

    /** IDs among the given ones that are publicly eligible right now (re-checked on every read). */
    public List<Long> findEligibleIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("select distinct p.id " + ELIGIBLE + " and p.id in :ids", Long.class)
                .setParameter("ready", CatalogueMediaObject.State.READY)
                .setParameter("ids", productIds)
                .getResultList();
    }
}
