package com.smartbillingpro.ui;

import com.smartbillingpro.util.Dates;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

/** Date range filter: quick presets plus custom From / To dates. */
public final class DateRangeBar extends JPanel {

    public enum Preset {
        TODAY("Today"), YESTERDAY("Yesterday"), LAST_7("Last 7 days"), LAST_30("Last 30 days"),
        THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), CUSTOM("Custom range");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final JComboBox<Preset> preset = new JComboBox<>(Preset.values());
    private final JSpinner from = dateSpinner();
    private final JSpinner to = dateSpinner();
    private Runnable onChange = () -> { };
    private boolean adjusting;

    public DateRangeBar(Preset initial) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        setOpaque(false);
        preset.setSelectedItem(initial);
        add(preset);
        add(Ui.muted("From"));
        add(from);
        add(Ui.muted("to"));
        add(to);
        applyPreset();
        preset.addActionListener(e -> {
            applyPreset();
            fire();
        });
        from.addChangeListener(e -> customChanged());
        to.addChangeListener(e -> customChanged());
    }

    private static JSpinner dateSpinner() {
        JSpinner s = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        s.setEditor(new JSpinner.DateEditor(s, Dates.displayPattern()));
        s.setPreferredSize(new java.awt.Dimension(128, s.getPreferredSize().height));
        return s;
    }

    private void customChanged() {
        if (adjusting) {
            return;
        }
        adjusting = true;
        preset.setSelectedItem(Preset.CUSTOM);
        if (to() .isBefore(from())) {
            set(to, from());
        }
        adjusting = false;
        fire();
    }

    private void applyPreset() {
        LocalDate t = LocalDate.now();
        LocalDate a;
        LocalDate b = t;
        switch ((Preset) preset.getSelectedItem()) {
            case TODAY -> a = t;
            case YESTERDAY -> {
                a = t.minusDays(1);
                b = a;
            }
            case LAST_7 -> a = t.minusDays(6);
            case LAST_30 -> a = t.minusDays(29);
            case THIS_MONTH -> a = t.withDayOfMonth(1);
            case LAST_MONTH -> {
                a = t.minusMonths(1).withDayOfMonth(1);
                b = t.withDayOfMonth(1).minusDays(1);
            }
            case THIS_YEAR -> a = t.withDayOfYear(1);
            default -> {
                return;
            }
        }
        adjusting = true;
        set(from, a);
        set(to, b);
        adjusting = false;
    }

    private static void set(JSpinner s, LocalDate d) {
        s.setValue(Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }

    private static LocalDate get(JSpinner s) {
        return ((Date) s.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public LocalDate from() {
        return get(from);
    }

    public LocalDate to() {
        return get(to);
    }

    public String label() {
        Preset p = (Preset) preset.getSelectedItem();
        return p == Preset.CUSTOM ? Dates.formatDate(from()) + " – " + Dates.formatDate(to()) : p.toString();
    }

    public void onChange(Runnable r) {
        this.onChange = r;
    }

    private void fire() {
        if (!adjusting) {
            onChange.run();
        }
    }
}
