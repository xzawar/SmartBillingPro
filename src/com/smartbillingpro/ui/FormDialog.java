package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Window;

/**
 * Standard modal form: title, scrollable body, Cancel + one primary button.
 * Enter saves, Escape cancels. Errors from {@link #onSave()} are shown and the
 * dialog stays open so nothing typed is lost.
 */
public abstract class FormDialog extends JDialog {

    protected final JButton primaryButton;
    protected final JButton cancelButton;
    private final JPanel footerLeft = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    private final JLabel subtitle = Ui.muted("");
    private final JLabel error = new JLabel();
    private boolean saved;

    protected FormDialog(Component parent, String title, String primaryText) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.SURFACE);
        JPanel head = new JPanel(new BorderLayout(0, 3));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(18, 22, 6, 22));
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(18));
        t.setForeground(Theme.TEXT);
        head.add(t, BorderLayout.NORTH);
        head.add(subtitle, BorderLayout.CENTER);
        subtitle.setVisible(false);
        root.add(head, BorderLayout.NORTH);

        primaryButton = Ui.primary(primaryText, null);
        cancelButton = Ui.secondary("Cancel", null);
        cancelButton.addActionListener(e -> dispose());
        primaryButton.addActionListener(e -> trySave());
        error.setFont(Theme.bold(13));
        error.setForeground(Theme.BAD);
        error.setBorder(BorderFactory.createEmptyBorder(0, 22, 10, 22));
        error.setVisible(false);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.SURFACE_ALT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(12, 22, 12, 22)));
        footerLeft.setOpaque(false);
        footer.add(footerLeft, BorderLayout.WEST);
        footer.add(Ui.flowRight(8, cancelButton, primaryButton), BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(error, BorderLayout.NORTH);
        bottom.add(footer, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(primaryButton);
        Ui.key(getRootPane(), "ESCAPE", this::dispose);
        Ui.key(getRootPane(), "ctrl ENTER", this::trySave);
    }

    protected void setSubtitle(String text) {
        subtitle.setText(text);
        subtitle.setVisible(text != null && !text.isBlank());
    }

    protected void setBody(JComponent body) {
        body.setBorder(BorderFactory.createEmptyBorder(10, 22, 18, 22));
        body.setOpaque(false);
        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        getContentPane().add(sp, BorderLayout.CENTER);
    }

    protected void addFooterLeft(JComponent c) {
        footerLeft.add(c);
    }

    /** Validate and save. Return true to close. Throwing shows the message and keeps the dialog open. */
    protected abstract boolean onSave();

    private void trySave() {
        if (!primaryButton.isEnabled()) {
            return;
        }
        primaryButton.setEnabled(false);
        try {
            if (onSave()) {
                saved = true;
                dispose();
            } else {
                showError(null);
            }
        } catch (com.smartbillingpro.db.AppException | IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(null);
            Msg.error(this, e);
        } finally {
            primaryButton.setEnabled(true);
        }
    }

    /** Show a problem right above the buttons (null hides it). The dialog stays open. */
    protected void showError(String message) {
        boolean show = message != null && !message.isBlank();
        error.setText(show ? "<html><div style='width:" + Math.max(260, getWidth() - 80) + "px'>\u26A0  "
                + message.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>" : "");
        error.setVisible(show);
        if (show) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
        revalidate();
        repaint();
    }

    protected void markSaved() {
        saved = true;
    }

    /** Show the dialog (blocking). Returns true if it was saved. */
    public boolean open(int width) {
        pack();
        Window owner = getOwner();
        GraphicsConfiguration gc = owner != null ? owner.getGraphicsConfiguration() : getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        int maxH = (int) (screen.height * 0.9);
        int w = Math.min(width, screen.width - 40);
        int h = Math.min(Math.max(getHeight(), 200), maxH);
        setSize(new Dimension(w, h));
        setMinimumSize(new Dimension(Math.min(420, w), Math.min(260, h)));
        setLocationRelativeTo(owner);
        setVisible(true);
        return saved;
    }
}
