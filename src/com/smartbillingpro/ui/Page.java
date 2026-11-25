package com.smartbillingpro.ui;

import com.smartbillingpro.app.AppContext;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/** A main-navigation screen: header (title, subtitle, 1-2 actions) and a body. */
public abstract class Page extends JPanel {

    protected final AppContext ctx;
    protected final MainFrame frame;
    private final JLabel subtitle = Ui.muted(" ");
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private final JPanel body = new JPanel(new BorderLayout());

    protected Page(MainFrame frame, String title, String subtitleText) {
        super(new BorderLayout(0, 14));
        this.frame = frame;
        this.ctx = frame.context();
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JPanel head = new JPanel(new BorderLayout(12, 0));
        head.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(Ui.title(title), BorderLayout.NORTH);
        subtitle.setText(subtitleText);
        subtitle.setFont(Theme.font(13));
        titles.add(subtitle, BorderLayout.CENTER);
        head.add(titles, BorderLayout.CENTER);
        actions.setOpaque(false);
        head.add(actions, BorderLayout.EAST);
        body.setOpaque(false);
        add(head, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    /** Short windows: drop the subtitle and tighten the padding so the content gets the room. */
    public void setCompactHeader(boolean compact) {
        subtitle.setVisible(!compact);
        setBorder(BorderFactory.createEmptyBorder(compact ? 10 : 18, 22, compact ? 10 : 18, 22));
        ((BorderLayout) getLayout()).setVgap(compact ? 8 : 14);
        revalidate();
    }

    protected void setSubtitle(String text) {
        subtitle.setText(text);
    }

    protected void addAction(JComponent c) {
        actions.add(c);
    }

    protected void setBody(JComponent c) {
        body.removeAll();
        body.add(c, BorderLayout.CENTER);
        body.revalidate();
    }

    /** Reload data from the database. Called every time the page is shown. */
    public abstract void refresh();

    /** Called after the page becomes visible (e.g. to focus the main field). */
    public void focusDefault() {
    }

    /** Called before switching to another page. Return false to stay. */
    public boolean canLeave() {
        return true;
    }

    /** Called before the application closes. Return false to keep it open. */
    public boolean canExit() {
        return true;
    }

    /** Runs an action and shows any error instead of crashing. */
    protected void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Msg.error(this, e);
        }
    }
}
