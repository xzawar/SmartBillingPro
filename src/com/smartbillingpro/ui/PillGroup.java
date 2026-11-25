package com.smartbillingpro.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** One-tap choice between a few options, shown as pills (e.g. Cash / Card / Bank). */
public final class PillGroup<T> extends JPanel {

    private final List<T> items = new ArrayList<>();
    private final List<JToggleButton> buttons = new ArrayList<>();
    private ButtonGroup group = new ButtonGroup();
    private Consumer<T> onChange = t -> { };
    private boolean adjusting;

    public PillGroup() {
        super(new WrapLayout(FlowLayout.LEFT, 6, 3));
        setOpaque(false);
    }

    public void setItems(List<T> values, Function<T, String> label) {
        T keep = selected();
        removeAll();
        items.clear();
        buttons.clear();
        group = new ButtonGroup();
        for (T v : values) {
            JToggleButton b = new JToggleButton(label.apply(v));
            b.putClientProperty(FlatClientProperties.STYLE, "arc:999;selectedBackground:" + Theme.hex(Theme.ACCENT_INK)
                    + ";selectedForeground:#ffffff;background:#ffffff;borderColor:" + Theme.hex(Theme.BORDER_STRONG)
                    + ";hoverBackground:" + Theme.hex(Theme.NAV_HOVER) + ";focusWidth:0;margin:6,14,6,14");
            b.setFont(Theme.bold(13));
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.setFocusable(false);
            b.addActionListener(e -> {
                if (!adjusting) {
                    onChange.accept(v);
                }
            });
            group.add(b);
            buttons.add(b);
            items.add(v);
            add(b);
        }
        if (keep != null) {
            select(keep);
        }
        if (selected() == null && !buttons.isEmpty()) {
            adjusting = true;
            buttons.get(0).setSelected(true);
            adjusting = false;
        }
        revalidate();
        repaint();
    }

    public T selected() {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).isSelected()) {
                return items.get(i);
            }
        }
        return null;
    }

    public void select(T value) {
        adjusting = true;
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(items.get(i), value)) {
                buttons.get(i).setSelected(true);
            }
        }
        adjusting = false;
    }

    /** Select by predicate (e.g. matching id). */
    public void selectWhere(java.util.function.Predicate<T> p) {
        for (T t : items) {
            if (p.test(t)) {
                select(t);
                return;
            }
        }
    }

    public void onChange(Consumer<T> c) {
        this.onChange = c;
    }

    public int count() {
        return items.size();
    }
}
