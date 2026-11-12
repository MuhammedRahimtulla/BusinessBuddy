package com.businessbuddy.service;

import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.Enums.NotificationNature;
import com.businessbuddy.entity.Enums.NotificationStatus;
import com.businessbuddy.entity.Enums.NotificationType;
import com.businessbuddy.entity.Enums.Role;
import com.businessbuddy.entity.Notification;
import com.businessbuddy.entity.StockItem;
import com.businessbuddy.exception.NotFoundException;
import com.businessbuddy.repository.NotificationRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.util.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * In-app notifications (spec 8.19, pseudocode RaiseNotification and CheckLowStock in 4.6.10).
 * Only one unresolved notification exists per condition; it is resolved automatically when
 * the condition clears.
 */
@Service
public class NotificationService {

    /** Employees see stock alerts only (Table 4.37). */
    public static final Set<NotificationType> STOCK_TYPES = EnumSet.of(NotificationType.LOW_STOCK,
            NotificationType.EXPIRING_SOON, NotificationType.EXPIRED, NotificationType.PREDICTED_STOCKOUT);

    private final NotificationRepository notifications;
    private final BusinessContext context;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, BusinessContext context, Clock clock) {
        this.notifications = notifications;
        this.context = context;
        this.clock = clock;
    }

    @Transactional
    public void raise(Business business, NotificationType type, NotificationNature nature,
                      String relatedType, Long relatedId, String message) {
        if (notifications.findUnresolved(business.getId(), type, relatedType, relatedId).isPresent()) {
            return;
        }
        Notification n = new Notification();
        n.setBusiness(business);
        n.setType(type);
        n.setNature(nature);
        n.setRelatedType(relatedType);
        n.setRelatedId(relatedId);
        n.setMessage(message.length() > 300 ? message.substring(0, 297) + "..." : message);
        notifications.save(n);
    }

    @Transactional
    public void resolve(Long businessId, NotificationType type, String relatedType, Long relatedId) {
        notifications.findUnresolved(businessId, type, relatedType, relatedId).ifPresent(n -> {
            n.setStatus(NotificationStatus.RESOLVED);
            n.setResolvedAt(Instant.now(clock));
        });
    }

    /** Raises or resolves the observed low-stock alert for an item. */
    @Transactional
    public void checkLowStock(StockItem item) {
        if (item.isActive() && item.isBelowMinimum()) {
            raise(item.getBusiness(), NotificationType.LOW_STOCK, NotificationNature.OBSERVED,
                    item.stockItemType(), item.getId(),
                    "Current stock of " + item.getName() + " is " + Money.quantity(item.getCurrentQuantity())
                            .stripTrailingZeros().toPlainString() + " " + item.getStockUnit().getCode()
                            + ", below the minimum of " + item.getMinStockLevel().stripTrailingZeros().toPlainString());
        } else {
            resolve(item.getBusiness().getId(), NotificationType.LOW_STOCK, item.stockItemType(), item.getId());
        }
    }

    public Set<NotificationType> visibleTypes(CurrentUser user) {
        return user.getRole() == Role.EMPLOYEE ? STOCK_TYPES : EnumSet.allOf(NotificationType.class);
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForCurrentUser(Pageable pageable) {
        CurrentUser user = context.currentUser();
        return notifications.list(context.currentBusinessId(), visibleTypes(user), pageable);
    }

    @Transactional(readOnly = true)
    public long openCountForCurrentUser() {
        CurrentUser user = context.currentUser();
        if (user.getBusinessId() == null) {
            return 0;
        }
        return notifications.countByBusinessIdAndStatusAndTypeIn(user.getBusinessId(), NotificationStatus.OPEN,
                visibleTypes(user));
    }

    @Transactional
    public void markRead(Long id) {
        Notification n = notifications.findByIdAndBusinessId(id, context.currentBusinessId())
                .orElseThrow(() -> new NotFoundException("Notification"));
        if (!visibleTypes(context.currentUser()).contains(n.getType())) {
            throw new NotFoundException("Notification");
        }
        if (n.getStatus() == NotificationStatus.OPEN) {
            n.setStatus(NotificationStatus.READ);
        }
    }
}
