package com.smartbillingpro.service;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.User;
import com.smartbillingpro.repository.UserDao;
import com.smartbillingpro.util.PasswordUtil;

import java.util.List;

/** Handles login and staff account management. */
public class AuthService {

    private final UserDao userDao;
    private final AuditService auditService;

    public AuthService(UserDao userDao, AuditService auditService) {
        this.userDao = userDao;
        this.auditService = auditService;
    }

    /** Returns the matching active user, or null when credentials are wrong. */
    public User authenticate(String username, String password) {
        User user = userDao.findByUsername(username == null ? "" : username.trim());
        if (user == null || !user.isActive()) {
            return null;
        }
        return PasswordUtil.verify(password, user.getPasswordHash()) ? user : null;
    }

    /**
     * Finds an active user by username WITHOUT checking a password. Used only by
     * the "Remember me" auto-login, where the user already authenticated in a
     * previous session and chose to stay signed in until they log out.
     */
    public User findActiveByUsername(String username) {
        User user = userDao.findByUsername(username == null ? "" : username.trim());
        return (user != null && user.isActive()) ? user : null;
    }

    public List<User> listUsers() {
        return userDao.findAll();
    }

    /** Staff accounts that belong to one store (for the owner's Staff screen and admin drill-down). */
    public List<User> listUsersForStore(int storeId) {
        return userDao.findByStore(storeId);
    }

    public boolean usernameExists(String username) {
        return userDao.findByUsername(username == null ? "" : username.trim()) != null;
    }

    public int userCount() {
        return userDao.count();
    }

    public User createUser(String username, String password, Role role, String fullName, int storeId) {
        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(PasswordUtil.hash(password));
        user.setRole(role);
        user.setFullName(fullName);
        user.setStoreId(storeId);
        user.setActive(true);
        userDao.insert(user);
        auditService.log("USER_CREATE", user.getUsername());
        return user;
    }

    public void updateUser(User user) {
        userDao.update(user);
        auditService.log("USER_UPDATE", user.getUsername());
    }

    public void resetPassword(User user, String newPassword) {
        userDao.updatePassword(user.getId(), PasswordUtil.hash(newPassword));
        auditService.log("USER_PASSWORD", user.getUsername());
    }

    /** Cloud uuid for an account (null when it has not synced yet). */
    public String remoteUuid(int id) {
        return userDao.remoteUuid(id);
    }
}
