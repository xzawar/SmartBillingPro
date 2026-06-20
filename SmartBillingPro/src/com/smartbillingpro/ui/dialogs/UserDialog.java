package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.model.Role;
import com.smartbillingpro.model.Store;
import com.smartbillingpro.model.User;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.components.Ui;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Window;
import java.util.List;

/**
 * Add a staff account, or edit an existing one. Only the administrator uses
 * this. Every account is assigned to a store and given a role (Store Owner,
 * Manager or Cashier); staff in the same store share that store's data.
 */
public class UserDialog extends FormDialog {

    private final AppContext context;
    private final User user;
    private final boolean isNew;

    private final JTextField usernameField = new JTextField(16);
    private final JTextField fullNameField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);
    private final JComboBox<Role> roleCombo = new JComboBox<>(new Role[] {Role.OWNER, Role.MANAGER, Role.CASHIER});
    private final JComboBox<Store> storeCombo = new JComboBox<>();
    private final JCheckBox activeCheck = new JCheckBox("Account is active");

    public UserDialog(Window owner, AppContext context, User existing) {
        super(owner, existing == null ? "Add User Account" : "Edit User Account");
        this.context = context;
        this.isNew = existing == null;
        this.user = existing == null ? new User() : existing;

        Ui.styleField(usernameField);
        Ui.styleField(fullNameField);
        Ui.styleField(passwordField);
        Ui.styleComponent(roleCombo);
        Ui.styleComponent(storeCombo);
        activeCheck.setOpaque(false);
        activeCheck.setSelected(true);

        List<Store> stores = context.getStoreService().all();
        for (Store store : stores) {
            storeCombo.addItem(store);
        }

        addField("Username", usernameField);
        addField("Full Name", fullNameField);
        addField(isNew ? "Password" : "New Password (optional)", passwordField);
        addField("Role", roleCombo);
        addField("Store", storeCombo);
        addField("Status", activeCheck);

        if (!isNew) {
            usernameField.setText(user.getUsername());
            usernameField.setEditable(false);
            fullNameField.setText(user.getFullName());
            roleCombo.setSelectedItem(user.getRole());
            selectStore(user.getStoreId());
            activeCheck.setSelected(user.isActive());
        }
    }

    private void selectStore(int storeId) {
        for (int i = 0; i < storeCombo.getItemCount(); i++) {
            if (storeCombo.getItemAt(i).getId() == storeId) {
                storeCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    protected boolean onSave() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        Role role = (Role) roleCombo.getSelectedItem();
        Store store = (Store) storeCombo.getSelectedItem();

        if (store == null) {
            error("Create a store first, then assign this user to it.");
            return false;
        }

        if (isNew) {
            if (username.isEmpty()) {
                error("Username is required.");
                return false;
            }
            if (password.length() < 4) {
                error("Password must be at least 4 characters.");
                return false;
            }
            if (context.getAuthService().usernameExists(username)) {
                error("That username is already taken.");
                return false;
            }
            User created = context.getAuthService()
                    .createUser(username, password, role, fullNameField.getText().trim(), store.getId());
            created.setActive(activeCheck.isSelected());
            context.getAuthService().updateUser(created);
        } else {
            user.setFullName(fullNameField.getText().trim());
            user.setRole(role);
            user.setStoreId(store.getId());
            user.setActive(activeCheck.isSelected());
            context.getAuthService().updateUser(user);
            if (!password.isEmpty()) {
                if (password.length() < 4) {
                    error("Password must be at least 4 characters.");
                    return false;
                }
                context.getAuthService().resetPassword(user, password);
            }
        }
        return true;
    }
}
