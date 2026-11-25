package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Keyboard-friendly searchable chooser: type to filter, arrows to move, Enter to
 * pick. Optionally offers "Add new" for creating the item on the spot.
 */
public final class PickerDialog<T> extends FormDialog {

    private final JTextField search = Ui.search("Type to search…");
    private final DefaultListModel<T> model = new DefaultListModel<>();
    private final JList<T> list = new JList<>(model);
    private final List<T> all;
    private final Function<T, String> text;
    private final Function<T, String> detail;
    private T result;

    private PickerDialog(Component parent, String title, List<T> items, Function<T, String> text,
                         Function<T, String> detail, String addLabel, Supplier<T> onAdd) {
        super(parent, title, "Select");
        this.all = items;
        this.text = text;
        this.detail = detail;
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(detail == null ? 36 : 46);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                @SuppressWarnings("unchecked")
                T item = (T) v;
                String d = detail == null ? null : detail.apply(item);
                String html = "<html><b>" + esc(text.apply(item)) + "</b>"
                        + (d == null || d.isBlank() ? "" : "<br><span style='color:#6b7480'>" + esc(d) + "</span>") + "</html>";
                super.getListCellRendererComponent(l, html, i, sel, false);
                setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    primaryButton.doClick();
                }
            }
        });
        Ui.onChange(search, this::refilter);
        for (int key : new int[] {KeyEvent.VK_DOWN, KeyEvent.VK_UP}) {
            search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), "move" + key);
            final int k = key;
            search.getActionMap().put("move" + key, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int i = list.getSelectedIndex() + (k == KeyEvent.VK_DOWN ? 1 : -1);
                    if (i >= 0 && i < model.size()) {
                        list.setSelectedIndex(i);
                        list.ensureIndexIsVisible(i);
                    }
                }
            });
        }
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.add(search, BorderLayout.NORTH);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(list);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setPreferredSize(new java.awt.Dimension(420, 340));
        body.add(sp, BorderLayout.CENTER);
        setBody(body);
        if (onAdd != null) {
            JButton add = Ui.ghost(addLabel, "plus");
            add.addActionListener(e -> {
                T created = onAdd.get();
                if (created != null) {
                    result = created;
                    markSaved();
                    dispose();
                }
            });
            addFooterLeft(add);
        }
        refilter();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;");
    }

    private void refilter() {
        String q = search.getText().trim().toLowerCase();
        model.clear();
        for (T t : all) {
            String hay = (text.apply(t) + " " + (detail == null ? "" : detail.apply(t))).toLowerCase();
            if (q.isEmpty() || hay.contains(q)) {
                model.addElement(t);
            }
            if (model.size() >= 300) {
                break;
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    @Override
    protected boolean onSave() {
        result = list.getSelectedValue();
        return result != null;
    }

    /** Show the picker; returns the chosen (or newly added) item, or null. */
    public static <T> T pick(Component parent, String title, List<T> items, Function<T, String> text,
                             Function<T, String> detail, String addLabel, Supplier<T> onAdd) {
        PickerDialog<T> d = new PickerDialog<>(parent, title, items, text, detail, addLabel, onAdd);
        d.open(520);
        return d.result;
    }
}
