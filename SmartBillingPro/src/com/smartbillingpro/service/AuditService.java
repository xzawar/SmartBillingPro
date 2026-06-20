package com.smartbillingpro.service;

import com.smartbillingpro.model.AuditEntry;
import com.smartbillingpro.model.User;
import com.smartbillingpro.repository.AuditDao;

import java.util.List;

/** Records who did what, using the currently logged-in user. */
public class AuditService {

    private final AuditDao auditDao;
    private final AppContext context;

    public AuditService(AuditDao auditDao, AppContext context) {
        this.auditDao = auditDao;
        this.context = context;
    }

    public void log(String action, String details) {
        User user = context.getCurrentUser();
        String username = user != null ? user.getUsername() : "system";
        auditDao.log(username, action, details);
    }

    public List<AuditEntry> recent(int limit) {
        return auditDao.findRecent(limit);
    }
}
