package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shows the terms of use the owner accepted in the installer. */
public final class TermsDialog extends FormDialog {

    public TermsDialog(Component parent) {
        super(parent, "Terms of use", "Close");
        cancelButton.setVisible(false);
        setSubtitle("The agreement accepted when Smart Billing Pro was installed.");
        JTextArea text = new JTextArea(terms());
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Theme.font(13));
        text.setForeground(Theme.TEXT);
        text.setBackground(Theme.SURFACE);
        text.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        text.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(text);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new Dimension(560, 420));
        setBody(sp);
    }

    /** The terms text bundled with the app (also used as the installer's licence page). */
    public static String terms() {
        try (InputStream in = TermsDialog.class.getResourceAsStream("/legal/terms.txt")) {
            return in == null ? "The terms of use could not be found." : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "The terms of use could not be read: " + e.getMessage();
        }
    }

    @Override
    protected boolean onSave() {
        return true;
    }
}
