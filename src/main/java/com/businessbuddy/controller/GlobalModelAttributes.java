package com.businessbuddy.controller;

import com.businessbuddy.entity.Business;
import com.businessbuddy.repository.BusinessRepository;
import com.businessbuddy.security.BusinessContext;
import com.businessbuddy.security.CurrentUser;
import com.businessbuddy.service.NotificationService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

/**
 * Values every page needs: the application name, the logged-in user, the business name,
 * the currency, the "Sample data" flag (4.7.3) and the notification bell count.
 */
@ControllerAdvice(basePackages = "com.businessbuddy.controller")
public class GlobalModelAttributes {

    public static final String APP_NAME = "BusinessBuddy";

    private final BusinessContext context;
    private final BusinessRepository businesses;
    private final NotificationService notifications;

    public GlobalModelAttributes(BusinessContext context, BusinessRepository businesses,
                                 NotificationService notifications) {
        this.context = context;
        this.businesses = businesses;
        this.notifications = notifications;
    }

    @ModelAttribute
    public void addCommon(Model model) {
        model.addAttribute("appName", APP_NAME);
        Optional<CurrentUser> user = context.currentUserIfAny();
        if (user.isEmpty()) {
            return;
        }
        CurrentUser cu = user.get();
        model.addAttribute("me", cu);
        model.addAttribute("canSeeMoney", cu.canSeeMoney());
        if (cu.getBusinessId() != null) {
            Business business = businesses.findById(cu.getBusinessId()).orElse(null);
            if (business != null) {
                model.addAttribute("businessName", business.getName());
                model.addAttribute("currency", business.getCurrencyCode());
                model.addAttribute("zone", business.getTimeZone());
                model.addAttribute("sampleData", business.isDemoData());
            }
            model.addAttribute("notificationCount", notifications.openCountForCurrentUser());
        } else {
            model.addAttribute("zone", "UTC");
        }
    }
}
