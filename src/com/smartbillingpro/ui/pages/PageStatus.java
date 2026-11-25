package com.smartbillingpro.ui.pages;

import com.smartbillingpro.model.Purchase;
import com.smartbillingpro.model.Sale;
import com.smartbillingpro.model.SaleStatus;
import com.smartbillingpro.ui.DataTable;
import com.smartbillingpro.ui.Ui;

/** Consistent status labels and colours for financial records. */
final class PageStatus {

    private PageStatus() {
    }

    /** Paid (green) / Partial (amber) / Credit (red), or the return state. */
    static DataTable.Status sale(Sale s) {
        if (s.status() == SaleStatus.RETURNED) {
            return new DataTable.Status("Returned", Ui.Tone.NEUTRAL);
        }
        String suffix = s.status() == SaleStatus.PARTIALLY_RETURNED ? " • part return" : "";
        return switch (s.paymentStatus()) {
            case "Paid" -> new DataTable.Status("Paid" + suffix, Ui.Tone.GOOD);
            case "Partial" -> new DataTable.Status("Partly paid" + suffix, Ui.Tone.WARN);
            default -> new DataTable.Status("On credit" + suffix, Ui.Tone.BAD);
        };
    }

    static DataTable.Status purchase(Purchase p) {
        if (p.reversed()) {
            return new DataTable.Status("Cancelled", Ui.Tone.NEUTRAL);
        }
        if (p.balanceDue() == 0) {
            return new DataTable.Status("Paid", Ui.Tone.GOOD);
        }
        return p.amountPaid() == 0 ? new DataTable.Status("Unpaid", Ui.Tone.BAD)
                : new DataTable.Status("Partly paid", Ui.Tone.WARN);
    }
}
