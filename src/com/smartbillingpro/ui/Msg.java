package com.smartbillingpro.ui;

import com.smartbillingpro.db.AppException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Short, friendly messages in the app's own style. Confirmation is only used
 * for important or destructive actions; destructive buttons are red.
 */
public final class Msg {

    private Msg() {
    }

    public static void error(Component parent, Throwable e) {
        String text;
        if (e instanceof AppException || e instanceof IllegalArgumentException) {
            text = e.getMessage();
        } else {
            text = "Something went wrong: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + "\n\nYour data is safe - the last action was not saved.";
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println(sw);
        }
        show(parent, "That didn't work", text, Ui.Tone.BAD, null, "OK", null, false, null);
    }

    public static void error(Component parent, String text) {
        show(parent, "That didn't work", text, Ui.Tone.BAD, null, "OK", null, false, null);
    }

    public static void error(Component parent, String title, String text) {
        show(parent, title, text, Ui.Tone.BAD, null, "OK", null, false, null);
    }

    public static void info(Component parent, String title, String text) {
        show(parent, title, text, Ui.Tone.INFO, null, "OK", null, false, null);
    }

    /** Ask before an important action. Red button for destructive words (Delete, Remove, Clear, ...). */
    public static boolean confirm(Component parent, String title, String text, String yesLabel) {
        boolean danger = yesLabel != null && yesLabel.matches("(?i).*(delete|remove|clear|discard|leave|reverse|void|restore|hide).*");
        return show(parent, title, text, danger ? Ui.Tone.BAD : Ui.Tone.INFO, "Cancel", yesLabel, null, danger, null) == 1;
    }

    /** Several choices; returns the index of the chosen option or -1 when closed. */
    public static int choose(Component parent, String title, String text, String... options) {
        Result r = new Result();
        JDialog d = dialog(parent, title, text, Ui.Tone.INFO);
        JPanel buttons = Ui.flowRight(8);
        for (int i = options.length - 1; i >= 0; i--) {
            final int idx = i;
            JButton b = i == 0 ? Ui.primary(options[i], null) : Ui.secondary(options[i], null);
            b.addActionListener(e -> {
                r.value = idx;
                d.dispose();
            });
            buttons.add(b, 0);
            if (i == 0) {
                d.getRootPane().setDefaultButton(b);
                d.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowOpened(java.awt.event.WindowEvent e) {
                        b.requestFocusInWindow();
                    }
                });
            }
        }
        finish(d, buttons, null);
        d.setVisible(true);
        return r.value;
    }

    /** Ask for a required line of text (e.g. a reason). Returns null when cancelled. */
    public static String ask(Component parent, String title, String prompt, String initial) {
        JTextField field = Ui.field();
        field.setText(initial == null ? "" : initial);
        field.setFont(Theme.font(15));
        field.selectAll();
        while (true) {
            int r = show(parent, title, prompt, Ui.Tone.INFO, "Cancel", "OK", field, false, field);
            if (r != 1) {
                return null;
            }
            String s = field.getText().trim();
            if (!s.isEmpty()) {
                return s;
            }
            error(parent, "Please type something first.");
        }
    }

    // ------------------------------------------------------------ implementation

    private static final class Result {
        int value = -1;
    }

    private static int show(Component parent, String title, String text, Ui.Tone tone, String cancel, String ok,
                            JComponent extra, boolean danger, JComponent focus) {
        Result r = new Result();
        JDialog d = dialog(parent, title, text, tone);
        JButton okButton = danger ? Ui.danger(ok, null) : Ui.primary(ok, null);
        okButton.addActionListener(e -> {
            r.value = 1;
            d.dispose();
        });
        JPanel buttons = Ui.flowRight(8);
        JButton cancelButton = null;
        if (cancel != null) {
            JButton c = Ui.secondary(cancel, null);
            c.addActionListener(e -> {
                r.value = 0;
                d.dispose();
            });
            buttons.add(c);
            cancelButton = c;
        }
        buttons.add(okButton);
        d.getRootPane().setDefaultButton(okButton);
        finish(d, buttons, extra);
        // Enter answers "yes" - except for destructive actions, where it safely cancels
        JComponent first = focus != null ? focus : danger && cancelButton != null ? cancelButton : okButton;
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                first.requestFocusInWindow();
            }
        });
        d.setVisible(true);
        return r.value;
    }

    private static JDialog dialog(Component parent, String title, String text, Ui.Tone tone) {
        Window owner = parent == null ? null : parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(16, 0));
        root.setBackground(Theme.SURFACE);
        root.setBorder(BorderFactory.createEmptyBorder(24, 24, 18, 24));
        String iconName = tone == Ui.Tone.BAD ? "warning" : tone == Ui.Tone.GOOD ? "check" : "info";
        JLabel icon = new JLabel(Icons.get(iconName, 22, tone.fg)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tone.bg);
                g2.fillOval(0, 0, 44, 44);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setVerticalAlignment(SwingConstants.CENTER);
        icon.setPreferredSize(new Dimension(44, 44));
        JPanel iconCol = new JPanel(new BorderLayout());
        iconCol.setOpaque(false);
        iconCol.add(icon, BorderLayout.NORTH);
        JLabel t = new JLabel(title);
        t.setFont(Theme.bold(18));
        t.setForeground(Theme.TEXT);
        JLabel body = new JLabel("<html><div style='width:360px'>" + html(text) + "</div></html>");
        body.setFont(Theme.font(14));
        body.setForeground(Theme.TEXT_2);
        JPanel textCol = new JPanel(new BorderLayout(0, 8));
        textCol.setOpaque(false);
        textCol.add(t, BorderLayout.NORTH);
        textCol.add(body, BorderLayout.CENTER);
        root.add(iconCol, BorderLayout.WEST);
        root.add(textCol, BorderLayout.CENTER);
        d.setContentPane(root);
        Ui.key(d.getRootPane(), "ESCAPE", d::dispose);
        return d;
    }

    private static void finish(JDialog d, JPanel buttons, JComponent extra) {
        JPanel root = (JPanel) d.getContentPane();
        JPanel textCol = (JPanel) ((BorderLayout) root.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 16));
        south.setOpaque(false);
        if (extra != null) {
            south.add(extra, BorderLayout.NORTH);
        }
        south.add(buttons, BorderLayout.SOUTH);
        south.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        textCol.add(south, BorderLayout.SOUTH);
        d.pack();
        d.setResizable(false);
        d.setLocationRelativeTo(d.getOwner());
    }

    private static String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>");
    }
}
