package com.smartbillingpro.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.smartbillingpro.db.AppException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks one thing at a time. Each {@link Step} is a single question with a big,
 * obvious answer area; mistakes are explained right under the answer instead of
 * in pop-ups. An optional review step summarises everything with "Change" links
 * that jump straight to one question and come back.
 */
public class Wizard extends JDialog {

    /** One question. */
    public abstract static class Step {
        protected Wizard wizard;
        private JComponent body;

        /** The question, e.g. "What is the product called?" */
        public abstract String title();

        /** One short helpful line under the question (optional). */
        public String help() {
            return null;
        }

        protected abstract JComponent build();

        final JComponent body() {
            if (body == null || dynamic()) {
                body = build();
            }
            return body;
        }

        /** Rebuild the answer area every time the step is shown (e.g. one row per size). */
        public boolean dynamic() {
            return false;
        }

        /** Part of the normal forward flow? (Detail questions are only reached from the review.) */
        public boolean visible() {
            return true;
        }

        /** Label of the skip button, or null when the question must be answered. */
        public String skipLabel() {
            return null;
        }

        /** Called when the user skips: store a sensible default. */
        public void skip() {
        }

        /**
         * Called after the step is shown: put the cursor in the right place. By default the main button gets
         * the focus, so Enter continues on steps with nothing to type (such as a summary).
         */
        public void shown() {
            wizard.focusNext();
        }

        /** Check and store the answer. Throw {@link AppException} with a friendly message when it is not valid. */
        public void commit() {
        }

        /** When reached from the review screen: a follow-up question to ask before going back (or null). */
        public Step then() {
            return null;
        }

        /** Custom text for the main button (e.g. "Save product"). */
        public String nextLabel() {
            return null;
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private final JLabel flowName = new JLabel();
    private final Progress progress = new Progress();
    private final JLabel title = new JLabel();
    private final JLabel help = new JLabel();
    private final JPanel bodyHolder = new JPanel(new BorderLayout());
    private final JLabel error = new JLabel(" ");
    private final JButton back = Ui.ghost("Back", null);
    private final JButton skip = Ui.secondary("Skip", null);
    private final JButton next = Ui.primary("Continue", null);
    private final JScrollPane scroll;
    private Step current;
    private Step review;
    private boolean returnToReview;
    private boolean finished;
    private boolean touched;

    public Wizard(Component parent, String name) {
        super(parent == null ? null : parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent), name,
                ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                requestClose();
            }
        });
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.SURFACE);

        JPanel head = new JPanel(new BorderLayout(0, 10));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(16, 28, 0, 20));
        flowName.setText(name);
        flowName.setFont(Theme.bold(13));
        flowName.setForeground(Theme.MUTED);
        JButton close = Ui.icon("close", "Close");
        close.setFocusable(false);
        close.addActionListener(e -> requestClose());
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(flowName, BorderLayout.WEST);
        top.add(close, BorderLayout.EAST);
        head.add(top, BorderLayout.NORTH);
        head.add(progress, BorderLayout.CENTER);
        root.add(head, BorderLayout.NORTH);

        title.setFont(Theme.bold(24));
        title.setForeground(Theme.TEXT);
        help.setFont(Theme.font(14));
        help.setForeground(Theme.MUTED);
        error.setFont(Theme.bold(13));
        error.setForeground(Theme.BAD);
        bodyHolder.setOpaque(false);
        ScrollablePanel content = new ScrollablePanel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(22, 28, 12, 28));
        for (JComponent c : new JComponent[] {title, help, bodyHolder, error}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(help);
        content.add(Box.createVerticalStrut(20));
        content.add(bodyHolder);
        content.add(Box.createVerticalStrut(10));
        content.add(error);
        scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        root.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.SURFACE);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(14, 20, 14, 24)));
        back.setIcon(Icons.get("chevron-left", 16, Theme.TEXT_2));
        back.addActionListener(e -> back());
        skip.addActionListener(e -> skipStep());
        next.addActionListener(e -> next());
        footer.add(back, BorderLayout.WEST);
        footer.add(Ui.flowRight(10, skip, next), BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(next);
        Ui.key(getRootPane(), "ESCAPE", this::requestClose);
        Ui.key(getRootPane(), "alt LEFT", this::back);
    }

    // ------------------------------------------------------------ setup

    public Wizard add(Step s) {
        s.wizard = this;
        steps.add(s);
        return this;
    }

    /** The summary step shown last; "Change" links on it use {@link #jumpTo(Step)}. */
    public Wizard review(Step s) {
        add(s);
        review = s;
        return this;
    }

    /** Big text box for one-question steps. */
    public static JTextField bigField(String placeholder) {
        JTextField f = new JTextField(20);
        f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        f.putClientProperty(FlatClientProperties.STYLE, "font:+7;margin:10,14,10,14");
        f.setFont(Theme.font(20));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        return f;
    }

    /** Big amount box with the currency symbol. */
    public static JTextField bigMoney(String placeholder) {
        JTextField f = Ui.money(placeholder);
        f.putClientProperty(FlatClientProperties.STYLE, "font:+7;margin:10,14,10,14");
        f.setFont(Theme.font(20));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, f.getPreferredSize().height));
        return f;
    }

    // ------------------------------------------------------------ navigation

    private List<Step> linear() {
        List<Step> out = new ArrayList<>();
        for (Step s : steps) {
            if (s.visible() || s == review) {
                out.add(s);
            }
        }
        return out;
    }

    private void show(Step s) {
        current = s;
        String t = s.title();
        title.setText("<html><div style='width:520px'>" + esc(t) + "</div></html>");
        String h = s.help();
        help.setText(h == null || h.isBlank() ? " " : "<html><div style='width:520px'>" + esc(h) + "</div></html>");
        help.setVisible(h != null && !h.isBlank());
        bodyHolder.removeAll();
        bodyHolder.add(s.body(), BorderLayout.NORTH);
        clearError();
        boolean onReviewDetour = returnToReview && s != review;
        List<Step> flow = linear();
        int idx = flow.indexOf(s);
        back.setVisible(onReviewDetour || (s != review && idx > 0) || (s == review && !returnToReview && idx > 0));
        back.setText(onReviewDetour ? "Back to summary" : "Back");
        String skipLabel = s.skipLabel();
        skip.setVisible(skipLabel != null);
        skip.setText(skipLabel == null ? "" : skipLabel);
        String nl = s.nextLabel();
        next.setText(nl != null ? nl : onReviewDetour ? "Done" : "Continue");
        progress.set(idx < 0 ? 1.0 : (idx + 1) / (double) Math.max(1, flow.size()));
        bodyHolder.revalidate();
        bodyHolder.repaint();
        SwingUtilities.invokeLater(() -> {
            scroll.getVerticalScrollBar().setValue(0);
            scroll.getViewport().scrollRectToVisible(new Rectangle(0, 0, 1, 1));
            s.shown();
        });
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;");
    }

    /** Validate the current answer and go forward. */
    public void next() {
        if (current == null) {
            return;
        }
        try {
            current.commit();
        } catch (AppException | IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        } catch (RuntimeException e) {
            Msg.error(this, e);
            return;
        }
        touched = true;
        if (finished) {
            return;
        }
        advance();
    }

    private void advance() {
        Step follow = returnToReview ? current.then() : null;
        if (follow != null) {
            show(follow);
            return;
        }
        if (returnToReview && current != review) {
            show(review);
            return;
        }
        List<Step> flow = linear();
        int idx = flow.indexOf(current);
        if (idx >= 0 && idx < flow.size() - 1) {
            show(flow.get(idx + 1));
        } else {
            finish();
        }
    }

    private void skipStep() {
        if (current == null) {
            return;
        }
        try {
            current.skip();
        } catch (AppException | IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        } catch (RuntimeException e) {
            Msg.error(this, e);
            return;
        }
        touched = true;
        if (finished) {
            return;
        }
        advance();
    }

    public void back() {
        if (current == null || !back.isVisible()) {
            return;
        }
        if (returnToReview && current != review) {
            show(review);
            return;
        }
        List<Step> flow = linear();
        int idx = flow.indexOf(current);
        if (idx > 0) {
            show(flow.get(idx - 1));
        }
    }

    /** Open one question from the summary; "Done" comes back to the summary. */
    public void jumpTo(Step s) {
        returnToReview = true;
        show(s);
    }

    /** Give the keyboard focus to the main ("Continue" / "Save") button. */
    public void focusNext() {
        next.requestFocusInWindow();
    }

    /** True once the user is editing from the review screen. */
    public boolean reviewing() {
        return returnToReview;
    }

    /** Re-render the current step (e.g. after data changed). */
    public void refreshCurrent() {
        if (current != null) {
            show(current);
        }
    }

    public void showError(String message) {
        error.setText("<html><div style='width:520px'>⚠  " + esc(message) + "</div></html>");
        error.setVisible(true);
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    public void clearError() {
        error.setText(" ");
    }

    public void finish() {
        finished = true;
        dispose();
    }

    public boolean finished() {
        return finished;
    }

    /** Mark that the user changed something (so closing asks before throwing it away). */
    public void touch() {
        touched = true;
    }

    private void requestClose() {
        if (touched && !finished && !Msg.confirm(this, "Leave without saving?",
                "What you have entered here will not be saved.", "Leave")) {
            return;
        }
        dispose();
    }

    /** Show the wizard from its first step (or the review step when {@code atReview}). Blocks until closed. */
    public boolean open(boolean atReview) {
        if (atReview && review != null) {
            returnToReview = true;
            show(review);
        } else {
            show(linear().get(0));
        }
        setSize(new Dimension(680, 640));
        Window owner = getOwner();
        Rectangle screen = (owner != null ? owner.getGraphicsConfiguration() : getGraphicsConfiguration()).getBounds();
        setSize(Math.min(680, screen.width - 40), Math.min(640, screen.height - 60));
        setMinimumSize(new Dimension(Math.min(520, screen.width - 40), Math.min(460, screen.height - 60)));
        setLocationRelativeTo(owner);
        setVisible(true);
        return finished;
    }

    /** Thin progress line under the header. */
    private static final class Progress extends JComponent {
        private double value;

        Progress() {
            setPreferredSize(new Dimension(10, 4));
        }

        void set(double v) {
            value = Math.max(0, Math.min(1, v));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.NEUTRAL_SOFT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
            g2.setColor(Theme.ACCENT_INK);
            g2.fillRoundRect(0, 0, (int) (getWidth() * value), getHeight(), 4, 4);
            g2.dispose();
        }
    }

    /** Label + value row with a "Change" link, for review screens. */
    public static JPanel summaryRow(String label, String value, Runnable change) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(10, 2, 10, 0)));
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(14));
        l.setForeground(Theme.MUTED);
        l.setPreferredSize(new Dimension(150, l.getPreferredSize().height));
        JLabel v = new JLabel("<html>" + esc(value == null || value.isBlank() ? "—" : value).replace("\n", "<br>") + "</html>");
        v.setFont(Theme.bold(14));
        v.setForeground(Theme.TEXT);
        v.setVerticalAlignment(SwingConstants.TOP);
        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.CENTER);
        if (change != null) {
            JButton c = Ui.ghost("Change", null);
            c.putClientProperty(FlatClientProperties.STYLE, "arc:999;foreground:" + Theme.hex(Theme.ACCENT_INK)
                    + ";hoverBackground:" + Theme.hex(Theme.NAV_HOVER) + ";margin:4,10,4,10");
            c.addActionListener(e -> change.run());
            row.add(c, BorderLayout.EAST);
        }
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
        return row;
    }
}
