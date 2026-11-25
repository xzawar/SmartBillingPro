package com.smartbillingpro;

import com.smartbillingpro.app.AppContext;
import com.smartbillingpro.app.AppPaths;
import com.smartbillingpro.db.AppException;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.service.InventoryService;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Minimal assertion helpers and fixtures (no external test library needed). */
final class TestSupport {

    private TestSupport() {
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void eq(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " - expected <" + expected + "> but was <" + actual + ">");
        }
    }

    /** Asserts that {@code r} throws an {@link AppException} whose message contains {@code fragment}. */
    static RuntimeException fails(Runnable r, String fragment) {
        try {
            r.run();
        } catch (AppException | IllegalArgumentException e) {
            if (fragment != null && !e.getMessage().toLowerCase().contains(fragment.toLowerCase())) {
                throw new AssertionError("Expected error containing \"" + fragment + "\" but got: " + e.getMessage());
            }
            return e;
        }
        throw new AssertionError("Expected an error containing \"" + fragment + "\" but nothing was thrown");
    }

    static Path tempDir() throws IOException {
        return Files.createTempDirectory("sbp-test-");
    }

    static AppContext newContext(Path dir) {
        System.setProperty("sbp.dataDir", dir.toString());
        return new AppContext(new AppPaths(dir));
    }

    static long money(String s) {
        return Money.parse(s);
    }

    static long qty(String s) {
        return Qty.parse(s);
    }

    static Unit unit(AppContext ctx, String code) {
        return ctx.inventory().unitByCode(code);
    }

    /** Creates a simple product and returns its variant id. */
    static long simpleProduct(AppContext ctx, String name, String barcode, String cost, String price, String stock,
                              String unitCode) {
        InventoryService.VariantForm v = new InventoryService.VariantForm(null, "", barcode, null, money(cost),
                money(price), qty(stock), qty("2"), true);
        long pid = ctx.inventory().saveProduct(new InventoryService.ProductForm(null, name, null, null,
                unit(ctx, unitCode).id(), null, null, null, false, List.of(v)));
        return ctx.inventory().product(pid).firstVariant().id();
    }

    static long cashMethod(AppContext ctx) {
        return ctx.database().db().scalar("SELECT id FROM payment_methods WHERE is_cash = 1");
    }

    static List<String> list(String... s) {
        return new ArrayList<>(List.of(s));
    }
}
