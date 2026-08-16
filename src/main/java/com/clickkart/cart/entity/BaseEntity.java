// clickkart-cart-service/src/main/java/com/clickkart/cart/entity/BaseEntity.java
package com.clickkart.cart.entity;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Compliance fields required on every entity (Rule 3). Populated by {@code
 * com.clickkart.cart.config.SecurityConfig}'s {@code auditorAware} bean - the authenticated
 * principal's publicId, or "system" for the rare unauthenticated path. {@code id}/{@code version}
 * are getter-only: they're strictly framework-managed (generated PK, optimistic-lock counter) and
 * never set by application code.
 *
 * <p>Unlike Auth Service's copy this uses a plain sequence generator rather than that service's
 * {@code AssignedOrSequenceIdGenerator}, which exists there only to support a fixed-id singleton
 * row (the audit chain head). This service has no such entity, so the extra generator would be
 * unused indirection.
 *
 * <p>{@code @Version} is not decoration here. An order is written by several actors who never
 * coordinate - the customer cancelling, Payment reporting an outcome, a seller advancing a line,
 * and the expiry sweeper - so two of them landing at once is routine rather than exotic. The
 * optimistic lock makes the loser fail instead of overwriting a status it never saw, which is how a
 * cancellation could otherwise erase a payment that had just been captured.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "clickkart_order_seq_gen")
    @SequenceGenerator(name = "clickkart_order_seq_gen", sequenceName = "clickkart_order_seq", allocationSize = 1)
    private Long id;

    @Setter
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Setter
    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Setter
    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Setter
    @LastModifiedDate
    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
