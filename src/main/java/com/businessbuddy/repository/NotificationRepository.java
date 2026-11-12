package com.businessbuddy.repository;

import com.businessbuddy.entity.Enums.NotificationStatus;
import com.businessbuddy.entity.Enums.NotificationType;
import com.businessbuddy.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdAndBusinessId(Long id, Long businessId);

    /** The unresolved notification for a condition, if one exists (no duplicates, 8.19). */
    @Query("""
            select n from Notification n where n.business.id = :businessId and n.type = :type
              and n.relatedType = :relatedType and n.relatedId = :relatedId and n.status <> 'RESOLVED'""")
    Optional<Notification> findUnresolved(@Param("businessId") Long businessId, @Param("type") NotificationType type,
                                          @Param("relatedType") String relatedType, @Param("relatedId") Long relatedId);

    @Query("""
            select n from Notification n where n.business.id = :businessId and n.type in :types
            order by case when n.status = 'RESOLVED' then 1 else 0 end, n.id desc""")
    Page<Notification> list(@Param("businessId") Long businessId,
                            @Param("types") Collection<NotificationType> types, Pageable pageable);

    long countByBusinessIdAndStatusAndTypeIn(Long businessId, NotificationStatus status,
                                             Collection<NotificationType> types);
}
