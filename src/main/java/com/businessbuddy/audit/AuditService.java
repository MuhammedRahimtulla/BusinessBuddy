package com.businessbuddy.audit;

import com.businessbuddy.entity.AuditLog;
import com.businessbuddy.entity.Business;
import com.businessbuddy.entity.User;
import com.businessbuddy.repository.AuditLogRepository;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.repository.UserRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Writes the append-only audit trail (4.8.6). Details hold changed values only and must
 * never contain passwords, session identifiers or other secrets.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final BusinessContext context;
    private final ObjectMapper json;

    public AuditService(AuditLogRepository auditLogs, BusinessRepository businesses, UserRepository users,
                        BusinessContext context, ObjectMapper json) {
        this.auditLogs = auditLogs;
        this.businesses = businesses;
        this.users = users;
        this.context = context;
        this.json = json;
    }

    /** Records an action by the logged-in user. */
    @Transactional
    public void record(String action, String entityType, Long entityId, Map<String, ?> details) {
        CurrentUser current = context.currentUserIfAny().orElse(null);
        Long userId = current == null ? null : current.getUserId();
        Long businessId = current == null ? null : current.getBusinessId();
        write(action, entityType, entityId, details, userId, businessId);
    }

    public void record(String action, String entityType, Long entityId) {
        record(action, entityType, entityId, null);
    }

    /** Records an action where the user is known but not logged in (login events, jobs). */
    @Transactional
    public void recordFor(String action, Long userId, Long businessId, String entityType, Long entityId,
                          Map<String, ?> details) {
        write(action, entityType, entityId, details, userId, businessId);
    }

    private void write(String action, String entityType, Long entityId, Map<String, ?> details,
                       Long userId, Long businessId) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setIpAddress(BusinessContext.clientIp());
        if (userId != null) {
            User user = users.getReferenceById(userId);
            entry.setUser(user);
        }
        if (businessId != null) {
            Business business = businesses.getReferenceById(businessId);
            entry.setBusiness(business);
        }
        if (details != null && !details.isEmpty()) {
            try {
                entry.setDetails(json.writeValueAsString(details));
            } catch (JsonProcessingException e) {
                entry.setDetails(null);
            }
        }
        auditLogs.save(entry);
    }
}
