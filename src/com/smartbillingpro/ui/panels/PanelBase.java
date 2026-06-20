package com.smartbillingpro.ui.panels;

import com.smartbillingpro.model.Settings;
import com.smartbillingpro.service.AppContext;
import com.smartbillingpro.ui.MainFrame;
import com.smartbillingpro.ui.Refreshable;
import com.smartbillingpro.ui.theme.Theme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** Base panel providing the page header (title + subtitle + actions) and body. */
public abstract class PanelBase extends JPanel implements Refreshable {

    protected final MainFrame frame;
    protected final AppContext context;

    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JPanel actions = new JPanel();
    private final JPanel body = new JPanel(new BorderLayout());

    protected PanelBase(MainFrame frame, AppContext context, String title, String subtitle) {
        this.frame = frame;
        this.context = context;
        setLayout(new BorderLayout(0, 16));
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titleLabel.setText(title);
        titleLabel.setFont(Theme.bold(24));
        titleLabel.setForeground(Theme.TEXT);
        subtitleLabel.setText(subtitle);
        subtitleLabel.setFont(Theme.regular(13));
        subtitleLabel.setForeground(Theme.MUTED);
        titles.add(titleLabel);
        titles.add(subtitleLabel);

        actions.setOpaque(false);
        header.add(titles, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder());

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    protected void addAction(JComponent component) {
        actions.add(component);
    }

    protected void setBody(JComponent component) {
        body.removeAll();
        body.add(component, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    protected void setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
    }

    protected Settings settings() {
        return context.getSettings();
    }

    protected String symbol() {
        return context.getSettings().getCurrencySymbol();
    }
}
