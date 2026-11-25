package com.smartbillingpro.ui.flows;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.service.Settings;
import com.smartbillingpro.ui.OptionList;
import com.smartbillingpro.ui.Theme;
import com.smartbillingpro.ui.Wizard;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First start: asks for the few shop details that appear on receipts, one
 * question at a time. Everything else keeps its sensible default and can be
 * changed later in Settings.
 */
public final class ShopSetupFlow {

    private final AppContext ctx;
    private final Wizard wizard;
    private String name;
    private String phone;
    private String address;
    private boolean tax;
    private String rate;

    private ShopSetupFlow(Component parent, AppContext ctx) {
        this.ctx = ctx;
        Settings st = ctx.settings().get();
        name = "My Store".equals(st.storeName()) ? "" : st.storeName();
        phone = st.storePhone();
        address = st.storeAddress();
        tax = st.taxEnabled();
        rate = st.taxPercent().signum() == 0 ? "" : st.taxPercent().stripTrailingZeros().toPlainString();
        wizard = new Wizard(parent, "Welcome to SmartBillingPro");
        wizard.add(new NameStep()).add(new PhoneStep()).add(new AddressStep()).add(new TaxStep()).add(new RateStep());
    }

    /** True when the shop has not been set up yet (a fresh install). */
    public static boolean needed(AppContext ctx) {
        Settings st = ctx.settings().get();
        return !st.setupDone() && "My Store".equals(st.storeName()) && ctx.inventory().products(true).isEmpty();
    }

    /** Runs the setup. Closing it early still marks it as seen, so it is not asked again. Returns true when saved. */
    public static boolean open(Component parent, AppContext ctx) {
        ShopSetupFlow f = new ShopSetupFlow(parent, ctx);
        boolean done = f.wizard.open(false);
        if (!done) {
            ctx.settings().save(Map.of(Settings.SETUP_DONE, "true"));
        }
        return done;
    }

    private void save() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Settings.STORE_NAME, name);
        m.put(Settings.STORE_PHONE, phone == null ? "" : phone);
        m.put(Settings.STORE_ADDRESS, address == null ? "" : address);
        m.put(Settings.TAX_ENABLED, String.valueOf(tax));
        if (tax) {
            m.put(Settings.TAX_PERCENT, rate);
        }
        m.put(Settings.SETUP_DONE, "true");
        ctx.settings().save(m);
    }

    private JTextField field(String placeholder, String value) {
        JTextField f = Wizard.bigField(placeholder);
        f.setText(value == null ? "" : value);
        f.addActionListener(e -> wizard.next());
        return f;
    }

    private final class NameStep extends Wizard.Step {
        private JTextField f;

        @Override
        public String title() {
            return "What is your shop called?";
        }

        @Override
        public String help() {
            return "It is printed at the top of every receipt. You can change it later in Settings.";
        }

        @Override
        protected JComponent build() {
            f = field("Shop name", name);
            return f;
        }

        @Override
        public void shown() {
            f.requestFocusInWindow();
        }

        @Override
        public void commit() {
            String n = f.getText().trim();
            if (n.isEmpty()) {
                throw new AppException("Please type your shop's name.");
            }
            name = n;
        }
    }

    private final class PhoneStep extends Wizard.Step {
        private JTextField f;

        @Override
        public String title() {
            return "What is the shop's phone number?";
        }

        @Override
        public String help() {
            return "Shown on receipts so customers can call you.";
        }

        @Override
        public String skipLabel() {
            return "Skip";
        }

        @Override
        public void skip() {
            phone = "";
        }

        @Override
        protected JComponent build() {
            f = field("e.g. 0300 1234567", phone);
            return f;
        }

        @Override
        public void shown() {
            f.requestFocusInWindow();
        }

        @Override
        public void commit() {
            String p = f.getText().trim();
            if (!p.isEmpty() && !p.matches("[0-9+()\\- ]{5,20}")) {
                throw new AppException("That does not look like a phone number. Use digits, spaces, + or -.");
            }
            phone = p;
        }
    }

    private final class AddressStep extends Wizard.Step {
        private JTextField f;

        @Override
        public String title() {
            return "Where is the shop?";
        }

        @Override
        public String help() {
            return "The address printed on receipts.";
        }

        @Override
        public String skipLabel() {
            return "Skip";
        }

        @Override
        public void skip() {
            address = "";
        }

        @Override
        protected JComponent build() {
            f = field("e.g. Shop 4, Main Bazaar, Lahore", address);
            return f;
        }

        @Override
        public void shown() {
            f.requestFocusInWindow();
        }

        @Override
        public void commit() {
            address = f.getText().trim();
        }
    }

    private final class TaxStep extends Wizard.Step {
        private OptionList options;

        @Override
        public String title() {
            return "Do you add sales tax to your bills?";
        }

        @Override
        public String help() {
            return "Most small shops don't. If you are not sure, choose No – you can turn it on later.";
        }

        @Override
        protected JComponent build() {
            options = new OptionList(List.of(
                    new OptionList.Option("no", "check", "No", "Prices already include everything"),
                    new OptionList.Option("yes", "percent", "Yes", "Add a tax percentage on top of every bill")));
            options.setSelected(tax ? "yes" : "no");
            options.onPick(k -> wizard.next());
            return options;
        }

        @Override
        public void shown() {
            options.focusSelectedOrFirst();
        }

        @Override
        public void commit() {
            tax = "yes".equals(options.selected());
            if (!tax) {
                save();
            }
        }
    }

    private final class RateStep extends Wizard.Step {
        private JTextField f;

        @Override
        public boolean visible() {
            return tax;
        }

        @Override
        public String title() {
            return "What is the tax rate?";
        }

        @Override
        public String nextLabel() {
            return "Finish";
        }

        @Override
        protected JComponent build() {
            f = field("e.g. 17", rate);
            f.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setOpaque(false);
            p.add(f, BorderLayout.CENTER);
            JLabel pct = new JLabel("%");
            pct.setFont(Theme.font(18));
            pct.setForeground(Theme.MUTED);
            p.add(pct, BorderLayout.EAST);
            return p;
        }

        @Override
        public void shown() {
            f.requestFocusInWindow();
        }

        @Override
        public void commit() {
            String t = f.getText().trim();
            BigDecimal v;
            try {
                v = new BigDecimal(t.isEmpty() ? "x" : t);
            } catch (NumberFormatException e) {
                throw new AppException("Please type the rate as a number, e.g. 17 or 17.5.");
            }
            if (v.signum() <= 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new AppException("The rate must be more than 0 and at most 100.");
            }
            rate = v.stripTrailingZeros().toPlainString();
            save();
        }
    }
}
