package com.smartbillingpro.ui.dialogs;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.service.LegacyImportService;
import com.smartbillingpro.ui.FormDialog;
import com.smartbillingpro.ui.Msg;
import com.smartbillingpro.ui.Ui;

import javax.swing.JComboBox;
import java.awt.Component;
import java.io.File;
import java.util.List;

/** Bring products, customers and suppliers across from the previous (online) version. */
public final class LegacyImportDialog extends FormDialog {

    private final AppContext ctx;
    private final File file;
    private final JComboBox<LegacyImportService.LegacyStore> store = new JComboBox<>();

    public LegacyImportDialog(Component parent, AppContext ctx, File file, List<LegacyImportService.LegacyStore> stores) {
        super(parent, "Import data from the previous version", "Import");
        this.ctx = ctx;
        this.file = file;
        cancelButton.setText("Not now");
        for (LegacyImportService.LegacyStore s : stores) {
            store.addItem(s);
        }
        setBody(Ui.vbox(12,
                Ui.muted("<html>Data from the earlier Smart Billing Pro was found on this computer:<br><b>"
                        + file.getAbsolutePath() + "</b><br><br>Products (with stock), categories, suppliers, customers "
                        + "and shop details can be copied into this offline version. The old file is not changed.</html>"),
                Ui.row("Shop to import", store)));
    }

    @Override
    protected boolean onSave() {
        LegacyImportService.LegacyStore s = (LegacyImportService.LegacyStore) store.getSelectedItem();
        LegacyImportService.Result r = ctx.legacyImport().importStore(file, s);
        Msg.info(this, "Import complete", "Imported " + r.products() + " products, " + r.categories() + " categories, "
                + r.suppliers() + " suppliers and " + r.customers() + " customers.\n\n"
                + String.join("\n", r.notes().stream().limit(12).toList()));
        return true;
    }
}
