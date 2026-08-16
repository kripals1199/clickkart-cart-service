// src/main/java/com/clickkart/cart/repository/CartRepository.java
package com.clickkart.cart.repository;

import com.clickkart.cart.entity.CartEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<CartEntity, Long> {

    Optional<CartEntity> findByUserPublicId(String userPublicId);

    /**
     * Carts nobody has touched in a long time.
     *
     * <p>Oldest first, so a backlog drains in the order it accumulated. Ordered on the same column
     * the index covers, so the pruning job stays a range scan rather than a full sort of a table
     * that only ever grows.
     */
    @Query("select c from CartEntity c where c.lastActivityAt < :cutoff order by c.lastActivityAt asc")
    List<CartEntity> findAbandoned(@Param("cutoff") Instant cutoff, Pageable pageable);
}
