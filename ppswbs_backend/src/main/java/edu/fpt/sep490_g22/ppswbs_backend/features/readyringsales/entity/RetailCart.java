package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "retail_carts")
@Getter
@NoArgsConstructor
public class RetailCart {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "member_account_id", nullable = false, unique = true)
    private Long memberAccountId;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private final List<CartLine> lines = new ArrayList<>();

    public RetailCart(Long memberAccountId) {
        this.memberAccountId = memberAccountId;
    }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public void addLine(Long variantId, Long locationId, int quantity) {
        lines.add(new CartLine(this, variantId, locationId, quantity));
        touch();
    }

    public void removeLine(CartLine line) {
        lines.remove(line);
        touch();
    }

    public void touch() {
        updatedAt = Instant.now();
    }
}
