package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.model.PartyType;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Ui;
import com.smartbillingpro.util.Money;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.Component;

/** Correct an account balance with a recorded adjustment (history is never edited). */
public final class AdjustBalanceDialog extends FormDialog {

    private final AppContext ctx;
    private final PartyType type;
    private final long partyId;
    private final JComboBox<String> direction;
    private final JTextField amount = Ui.money("0.00");
    private final JTextField reason = Ui.field("e.g. opening balance correction, discount allowed");

    public AdjustBalanceDialog(Component parent, AppContext ctx, PartyType type, long partyId, String name) {
        super(parent, "Adjust balance", "Save adjustment");
        this.ctx = ctx;
        this.type = type;
        this.partyId = partyId;
        long bal = ctx.parties().balance(type, partyId);
        setSubtitle(name + "  •  current balance " + Money.format(bal));
        direction = new JComboBox<>(type == PartyType.CUSTOMER
                ? new String[] {"Customer owes MORE (increase)", "Customer owes LESS (decrease)"}
                : new String[] {"We owe the supplier MORE (increase)", "We owe the supplier LESS (decrease)"});
        setBody(Ui.vbox(12, Ui.row("Adjustment", direction), Ui.row("Amount *", amount), Ui.row("Reason *", reason),
                Ui.muted("The adjustment is added to the ledger with today's date; nothing existing is changed.")));
    }

    @Override
    protected boolean onSave() {
        long a = Ui.moneyValue(amount, "the amount", true);
        ctx.parties().adjustBalance(type, partyId, direction.getSelectedIndex() == 0 ? a : -a, reason.getText());
        return true;
    }
}
