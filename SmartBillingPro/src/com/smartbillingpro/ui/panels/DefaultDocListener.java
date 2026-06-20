package com.smartbillingpro.ui.panels;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Convenience listener that fires a single callback for any text change. */
public abstract class DefaultDocListener implements DocumentListener {

    public abstract void changed();

    @Override
    public void insertUpdate(DocumentEvent e) {
        changed();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        changed();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        changed();
    }
}
