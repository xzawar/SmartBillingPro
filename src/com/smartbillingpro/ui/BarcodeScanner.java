package com.smartbillingpro.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Detects USB barcode scanners anywhere in the window, without the cashier
 * clicking the search box first.
 *
 * <p>USB scanners behave like a keyboard that types very fast and ends with
 * Enter. Keystrokes arriving less than {@value #MAX_GAP_MS} ms apart are
 * collected; when Enter follows at least {@value #MIN_LENGTH} such characters
 * the code is delivered to the handler. If the scan landed in some other text
 * field (for example "Amount received"), that field's text is restored so the
 * barcode does not end up in the wrong place. When focus is not in any text
 * field, the typed characters are simply sent to the scan box.</p>
 */
public final class BarcodeScanner implements KeyEventDispatcher {

    private static final long MAX_GAP_MS = 45;
    private static final int MIN_LENGTH = 4;

    private final JComponent owner;
    private final JTextComponent scanField;
    private final BooleanSupplier active;
    private final Consumer<String> onScan;

    private final StringBuilder burst = new StringBuilder();
    private long lastKeyAt;
    private JTextComponent burstTarget;
    private String targetTextBefore;
    private int targetCaretBefore;

    public BarcodeScanner(JComponent owner, JTextComponent scanField, BooleanSupplier active, Consumer<String> onScan) {
        this.owner = owner;
        this.scanField = scanField;
        this.active = active;
        this.onScan = onScan;
    }

    public void install() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (!active.getAsBoolean() || !owner.isShowing()) {
            return false;
        }
        Component focus = e.getComponent();
        if (focus == null || SwingUtilities.getWindowAncestor(focus) != SwingUtilities.getWindowAncestor(owner)) {
            return false; // a dialog is open: leave its keys alone
        }
        if (focus == scanField) {
            return false; // the scan box handles its own Enter
        }
        long now = e.getWhen() > 0 ? e.getWhen() : System.currentTimeMillis();
        if (e.getID() == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            if (c == KeyEvent.CHAR_UNDEFINED || Character.isISOControl(c) || e.isControlDown() || e.isAltDown()) {
                return false;
            }
            if (now - lastKeyAt > MAX_GAP_MS) {
                burst.setLength(0);
                burstTarget = focus instanceof JTextComponent t && t.isEditable() ? t : null;
                targetTextBefore = burstTarget == null ? null : burstTarget.getText();
                targetCaretBefore = burstTarget == null ? 0 : burstTarget.getCaretPosition();
            }
            lastKeyAt = now;
            burst.append(c);
            if (!(focus instanceof JTextComponent tc) || !tc.isEditable()) {
                // Not typing into a field: send everything to the scan box.
                scanField.requestFocusInWindow();
                scanField.setText(scanField.getText() + c);
                return true;
            }
            return false;
        }
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (burst.length() > 0 && burstTarget == null && !scanField.getText().isBlank()) {
                // Characters were redirected into the scan box but focus has not moved yet.
                String code = scanField.getText().trim();
                burst.setLength(0);
                scanField.setText("");
                onScan.accept(code);
                return true;
            }
            boolean fast = now - lastKeyAt <= MAX_GAP_MS * 3;
            if (fast && burst.length() >= MIN_LENGTH && burstTarget != null && burstTarget != scanField) {
                String code = burst.toString();
                burstTarget.setText(targetTextBefore);
                burstTarget.setCaretPosition(Math.min(targetCaretBefore, burstTarget.getText().length()));
                burst.setLength(0);
                lastKeyAt = 0;
                onScan.accept(code);
                return true;
            }
            burst.setLength(0);
        }
        return false;
    }
}
