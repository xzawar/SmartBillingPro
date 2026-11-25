package com.smartbillingpro.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A calm two-line list: avatar, title, subtitle and a value on the right.
 * Shows a friendly empty state when there is nothing to list.
 */
public final class RowList<T> extends JPanel {

    /** How one item is shown. */
    public record Row(String initials, Image image, String icon, String title, String subtitle, String trailing,
                      String trailingSub, Ui.Tone subTone, boolean dim) {

        public static Row of(String title, String subtitle, String trailing) {
            return new Row(initialsOf(title), null, null, title, subtitle, trailing, null, null, false);
        }

        public Row withStatus(String text, Ui.Tone tone) {
            return new Row(initials, image, icon, title, subtitle, trailing, text, tone, dim);
        }

        public Row withImage(Image img) {
            return new Row(initials, img, icon, title, subtitle, trailing, trailingSub, subTone, dim);
        }

        public Row withIcon(String iconName) {
            return new Row(initials, image, iconName, title, subtitle, trailing, trailingSub, subTone, dim);
        }

        public Row dimmed(boolean d) {
            return new Row(initials, image, icon, title, subtitle, trailing, trailingSub, subTone, d);
        }
    }

    public static String initialsOf(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : (name == null ? "" : name).trim().split("\\s+")) {
            if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(0)) && sb.length() < 2) {
                sb.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    private final Function<T, Row> mapper;
    private DefaultListModel<T> model = new DefaultListModel<>();
    private final JList<T> list = new JList<>(model);
    private java.util.function.BiPredicate<T, String> matcher;
    private Consumer<String> onNoResults;
    private final List<T> all = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel holder = new JPanel(cards);
    private final JPanel emptyHolder = new JPanel(new BorderLayout());
    private EmptyState empty = new EmptyState("info", "Nothing here yet", "");
    private final EmptyState noResults = new EmptyState("search", "No matches", "Try a different word.", true);
    private final int rowHeight;
    private String filter = "";
    private String filterRaw = "";
    private int hover = -1;

    public RowList(Function<T, Row> mapper) {
        this(mapper, 62);
    }

    public RowList(Function<T, Row> mapper, int rowHeight) {
        super(new BorderLayout());
        this.mapper = mapper;
        this.rowHeight = rowHeight;
        setOpaque(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(rowHeight);
        list.setCellRenderer(new Renderer());
        list.setBackground(Theme.SURFACE);
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && !list.getCellBounds(i, i).contains(e.getPoint())) {
                    i = -1;
                }
                if (i != hover) {
                    hover = i;
                    list.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                list.repaint();
            }
        };
        list.addMouseListener(m);
        list.addMouseMotionListener(m);
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Theme.SURFACE);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        emptyHolder.setOpaque(false);
        emptyHolder.add(empty, BorderLayout.CENTER);
        holder.setOpaque(false);
        holder.add(sp, "list");
        holder.add(emptyHolder, "empty");
        holder.add(noResults, "none");
        add(holder, BorderLayout.CENTER);
    }

    // ------------------------------------------------------------ data

    public void setItems(List<T> items) {
        T keep = selected();
        all.clear();
        all.addAll(items);
        apply(keep);
    }

    public List<T> items() {
        return all;
    }

    public void filter(String text) {
        filterRaw = text == null ? "" : text.trim();
        filter = filterRaw.toLowerCase(Locale.ROOT);
        apply(selected());
    }

    /** Custom search (receives the lower-case search text). By default the visible texts are searched. */
    public void setMatcher(java.util.function.BiPredicate<T, String> m) {
        this.matcher = m;
    }

    public String filterText() {
        return filter;
    }

    private void apply(T keep) {
        DefaultListModel<T> next = new DefaultListModel<>();
        List<T> shown = new ArrayList<>();
        for (T t : all) {
            if (filter.isEmpty() || matches(t)) {
                shown.add(t);
            }
        }
        next.addAll(shown);
        model = next;
        list.setModel(next);
        hover = -1;
        if (keep != null) {
            int idx = model.indexOf(keep);
            if (idx >= 0) {
                list.setSelectedIndex(idx);
            }
        }
        if (all.isEmpty()) {
            cards.show(holder, "empty");
        } else if (model.isEmpty()) {
            noResults.setText("Nothing matches “" + filterRaw + "”. Check the spelling or try a shorter word.");
            if (onNoResults != null) {
                onNoResults.accept(filterRaw);
            }
            cards.show(holder, "none");
        } else {
            cards.show(holder, "list");
        }
    }

    private boolean matches(T t) {
        if (matcher != null) {
            return matcher.test(t, filter);
        }
        Row r = mapper.apply(t);
        String hay = (r.title() + " " + nz(r.subtitle()) + " " + nz(r.trailing()) + " " + nz(r.trailingSub()))
                .toLowerCase(Locale.ROOT);
        return hay.contains(filter);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public void setEmptyState(EmptyState state) {
        empty = state;
        emptyHolder.removeAll();
        emptyHolder.add(state, BorderLayout.CENTER);
        emptyHolder.revalidate();
    }

    /** Lets the page adjust the "no matches" screen (e.g. offer to add the missing item). */
    public void onNoResults(Consumer<String> c) {
        this.onNoResults = c;
    }

    public EmptyState noResultsState() {
        return noResults;
    }

    public T selected() {
        return list.getSelectedValue();
    }

    public void select(T item) {
        int idx = model.indexOf(item);
        if (idx >= 0) {
            list.setSelectedIndex(idx);
            list.ensureIndexIsVisible(idx);
        }
    }

    public void selectFirst() {
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    public void clearSelection() {
        list.clearSelection();
    }

    public int visibleCount() {
        return model.size();
    }

    public T visibleAt(int i) {
        return model.get(i);
    }

    public void onSelect(Consumer<T> action) {
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                action.accept(list.getSelectedValue());
            }
        });
    }

    /** Double-click, single-click (when {@code singleClick}) or Enter. */
    public void onOpen(Consumer<T> action, boolean singleClick) {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i < 0 || !list.getCellBounds(i, i).contains(e.getPoint()) || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (singleClick || e.getClickCount() == 2) {
                    action.accept(model.get(i));
                }
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-item");
        list.getActionMap().put("open-item", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                T t = list.getSelectedValue();
                if (t != null) {
                    action.accept(t);
                }
            }
        });
    }

    public JList<T> jlist() {
        return list;
    }

    // ------------------------------------------------------------ painting

    private final class Renderer extends JComponent implements ListCellRenderer<T> {
        private Row row;
        private boolean selected;
        private boolean hovered;

        @Override
        public Component getListCellRendererComponent(JList<? extends T> l, T value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            row = mapper.apply(value);
            selected = isSelected;
            hovered = index == hover;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(Theme.SURFACE);
            g2.fillRect(0, 0, w, h);
            if (selected || hovered) {
                g2.setColor(selected ? Theme.ACCENT_SOFT : Theme.SURFACE_ALT);
                g2.fillRoundRect(4, 2, w - 8, h - 4, 24, 24);
            }
            int av = 40;
            int ax = 14;
            int ay = (h - av) / 2;
            if (row.dim()) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
            }
            if (row.image() != null) {
                Graphics2D gi = (Graphics2D) g2.create();
                gi.setClip(new Ellipse2D.Double(ax, ay, av, av));
                gi.drawImage(row.image(), ax, ay, av, av, null);
                gi.dispose();
            } else {
                g2.setColor(selected ? Theme.ACCENT : Theme.NEUTRAL_SOFT);
                g2.fillOval(ax, ay, av, av);
                if (row.icon() != null) {
                    Icons.get(row.icon(), 20, Theme.ACCENT_INK).paintIcon(this, g2, ax + 10, ay + 10);
                } else {
                    g2.setFont(Theme.bold(14));
                    g2.setColor(Theme.ACCENT_INK);
                    FontMetrics fm = g2.getFontMetrics();
                    String in = row.initials() == null ? "" : row.initials();
                    g2.drawString(in, ax + (av - fm.stringWidth(in)) / 2, ay + (av + fm.getAscent()) / 2 - 2);
                }
            }
            g2.setComposite(AlphaComposite.SrcOver);
            int right = w - 16;
            g2.setFont(Theme.bold(14));
            FontMetrics bold = g2.getFontMetrics();
            int trailingW = row.trailing() == null ? 0 : bold.stringWidth(row.trailing());
            g2.setFont(Theme.font(12));
            int subW = row.trailingSub() == null ? 0 : g2.getFontMetrics().stringWidth(row.trailingSub());
            int textX = ax + av + 14;
            int textW = Math.max(40, right - textX - Math.max(trailingW, subW) - 18);
            boolean twoLines = row.subtitle() != null && !row.subtitle().isBlank();
            int titleY = twoLines ? h / 2 - 2 : h / 2 + 5;
            g2.setFont(Theme.bold(14));
            g2.setColor(row.dim() ? Theme.MUTED : Theme.TEXT);
            g2.drawString(fit(g2, row.title(), textW), textX, titleY);
            if (twoLines) {
                g2.setFont(Theme.font(12));
                g2.setColor(Theme.MUTED);
                g2.drawString(fit(g2, row.subtitle(), textW), textX, h / 2 + 16);
            }
            boolean twoRight = row.trailingSub() != null && !row.trailingSub().isBlank();
            if (row.trailing() != null) {
                g2.setFont(Theme.bold(14));
                g2.setColor(row.dim() ? Theme.MUTED : Theme.TEXT);
                g2.drawString(row.trailing(), right - trailingW, twoRight ? h / 2 - 2 : h / 2 + 5);
            }
            if (twoRight) {
                g2.setFont(Theme.bold(12));
                Color c = row.subTone() == null || row.subTone() == Ui.Tone.NEUTRAL ? Theme.MUTED : row.subTone().fg;
                g2.setColor(c);
                int sw = g2.getFontMetrics().stringWidth(row.trailingSub());
                g2.drawString(row.trailingSub(), right - sw, h / 2 + 16);
            }
            g2.dispose();
        }

        private String fit(Graphics2D g2, String s, int width) {
            if (s == null) {
                return "";
            }
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(s) <= width) {
                return s;
            }
            String t = s;
            while (t.length() > 1 && fm.stringWidth(t + "…") > width) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "…";
        }
    }

    public int rowHeight() {
        return rowHeight;
    }
}
