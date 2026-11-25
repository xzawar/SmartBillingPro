package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Category;
import com.smartbillingpro.model.Dimension;
import com.smartbillingpro.model.Product;
import com.smartbillingpro.model.SellableItem;
import com.smartbillingpro.model.StockMovement;
import com.smartbillingpro.model.Unit;
import com.smartbillingpro.model.Variant;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.ProductRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.StockRepo;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Qty;
import com.smartbillingpro.util.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Products, variations, categories, units and every stock change. */
public final class InventoryService {

    /** One variation as entered in the product form. {@code id} is null for a new variation. */
    public record VariantForm(Long id, String name, String barcode, String sku, long costPrice, long salePrice,
                              long openingStock, long minStock, boolean active) {
    }

    /** The product form. A product without variations has exactly one unnamed variation. */
    public record ProductForm(Long id, String name, Long categoryId, String description, long unitId,
                              String weightValue, String weightUnit, Long supplierId, boolean hasVariants,
                              List<VariantForm> variants) {
    }

    public enum AdjustMode {
        ADD("Add stock"), REMOVE("Remove stock"), SET("Set counted quantity");

        private final String label;

        AdjustMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final String[] ADJUST_REASONS = {"Stock count correction", "Damaged", "Expired", "Lost / theft",
        "Returned to supplier", "Received without bill", "Used in shop", "Other"};

    private final Db db;
    private final ProductRepo products;
    private final StockRepo stock;
    private final ReferenceRepo refs;
    private final AuditRepo audit;

    public InventoryService(Db db, ProductRepo products, StockRepo stock, ReferenceRepo refs, AuditRepo audit) {
        this.db = db;
        this.products = products;
        this.stock = stock;
        this.refs = refs;
        this.audit = audit;
    }

    // ------------------------------------------------------------ queries

    public List<Product> products(boolean includeArchived) {
        return products.list(includeArchived);
    }

    public Product product(long id) {
        return products.find(id);
    }

    public byte[] image(long productId) {
        return products.image(productId);
    }

    public SellableItem sellable(long variantId) {
        return products.sellable(variantId);
    }

    /** Exact barcode (or SKU) lookup used by the scanner. */
    public SellableItem findByCode(String code) {
        if (Text.blank(code)) {
            return null;
        }
        return products.sellableByCode(code.trim());
    }

    public List<SellableItem> sellablesForProduct(long productId) {
        return products.sellablesForProduct(productId);
    }

    public List<SellableItem> search(String term, int limit) {
        return products.searchSellable(term, limit);
    }

    public String barcodeOwner(String barcode, long excludeVariantId) {
        return products.barcodeOwner(barcode.trim(), excludeVariantId);
    }

    public List<StockMovement> movements(String fromInclusive, String toExclusive, Long productId, String search) {
        return stock.movements(fromInclusive, toExclusive, productId, search, 5000);
    }

    // ------------------------------------------------------------ products

    public long saveProduct(ProductForm f) {
        String name = Text.trim(f.name());
        if (name.isEmpty()) {
            throw new AppException("Please enter the product name.");
        }
        if (name.length() > 120) {
            throw new AppException("The product name is too long (max 120 characters).");
        }
        Unit unit = refs.unit(f.unitId());
        if (unit == null) {
            throw new AppException("Please choose a unit of measurement.");
        }
        List<VariantForm> variants = f.variants();
        if (variants == null || variants.isEmpty()) {
            throw new AppException("Add at least one variation.");
        }
        if (!f.hasVariants() && variants.size() != 1) {
            throw new AppException("A product without variations has exactly one price and barcode.");
        }
        validateWeight(f.weightValue());
        Set<String> names = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (VariantForm v : variants) {
            if (f.hasVariants()) {
                if (Text.blank(v.name())) {
                    throw new AppException("Every variation needs a name, e.g. Small, Medium, 500 ml.");
                }
                if (!names.add(v.name().trim().toLowerCase())) {
                    throw new AppException("Two variations are called \"" + v.name().trim() + "\". Names must be different.");
                }
            }
            if (v.salePrice() < 0 || v.costPrice() < 0) {
                throw new AppException("Prices cannot be negative.");
            }
            if (v.openingStock() < 0 || v.minStock() < 0) {
                throw new AppException("Stock quantities cannot be negative.");
            }
            if (!unit.fractional() && (!Qty.isWhole(v.openingStock()) || !Qty.isWhole(v.minStock()))) {
                throw new AppException("Products counted in " + unit.code() + " need whole-number stock quantities.");
            }
            String barcode = Text.trimToNull(v.barcode());
            if (barcode != null) {
                if (barcode.length() > 64 || barcode.chars().anyMatch(Character::isISOControl)) {
                    throw new AppException("The barcode \"" + barcode + "\" is not valid.");
                }
                if (!barcodes.add(barcode)) {
                    throw new AppException("The barcode " + barcode + " is entered twice.");
                }
                String owner = products.barcodeOwner(barcode, v.id() == null ? -1 : v.id());
                if (owner != null) {
                    throw new AppException("The barcode " + barcode + " is already used by " + owner + ".");
                }
            }
            String sku = Text.trimToNull(v.sku());
            if (sku != null) {
                if (!skus.add(sku.toLowerCase())) {
                    throw new AppException("The SKU " + sku + " is entered twice.");
                }
                String owner = products.skuOwner(sku, v.id() == null ? -1 : v.id());
                if (owner != null) {
                    throw new AppException("The SKU " + sku + " is already used by " + owner + ".");
                }
            }
        }

        return db.tx(() -> {
            long productId;
            Product existing = null;
            if (f.id() == null) {
                productId = products.insertProduct(name, f.categoryId(), Text.trimToNull(f.description()), unit.id(),
                        Text.trimToNull(f.weightValue()), Text.trimToNull(f.weightUnit()), f.supplierId(),
                        f.hasVariants(), null);
            } else {
                existing = products.find(f.id());
                if (existing == null) {
                    throw new AppException("The product no longer exists.");
                }
                productId = existing.id();
                if (existing.unitId() != unit.id()) {
                    boolean anyHistory = existing.variants().stream()
                            .anyMatch(v -> v.stockQty() != 0 || products.variantHasHistory(v.id()));
                    if (anyHistory) {
                        throw new AppException("The unit cannot be changed after stock or sales were recorded "
                                + "for this product. Create a new product instead.");
                    }
                }
                products.updateProduct(productId, name, f.categoryId(), Text.trimToNull(f.description()), unit.id(),
                        Text.trimToNull(f.weightValue()), Text.trimToNull(f.weightUnit()), f.supplierId(),
                        f.hasVariants());
            }

            // Variations removed from the form: archive when they have history, otherwise delete.
            if (existing != null) {
                for (Variant old : existing.variants()) {
                    boolean kept = f.variants().stream().anyMatch(v -> v.id() != null && v.id() == old.id());
                    if (!kept) {
                        if (old.stockQty() > 0 || products.variantHasHistory(old.id())) {
                            products.updateVariant(old.id(), old.name(), old.barcode(), old.sku(), old.costPrice(),
                                    old.salePrice(), old.minStock(), 999, false);
                        } else {
                            products.deleteVariant(old.id());
                        }
                    }
                }
            }
            int order = 0;
            for (VariantForm v : f.variants()) {
                String vname = f.hasVariants() ? v.name().trim() : "";
                String barcode = Text.trimToNull(v.barcode());
                String sku = Text.trimToNull(v.sku());
                if (v.id() == null) {
                    long vid = products.insertVariant(productId, vname, barcode, sku, v.costPrice(), v.salePrice(),
                            v.minStock(), order);
                    if (v.openingStock() > 0) {
                        stock.apply(vid, v.openingStock(), "OPENING", v.costPrice(), null, "Opening stock", null, null, null);
                    }
                } else {
                    Variant old = products.variant(v.id());
                    if (old == null || old.productId() != productId) {
                        throw new AppException("A variation no longer exists. Please reopen the product.");
                    }
                    products.updateVariant(v.id(), vname, barcode, sku, v.costPrice(), v.salePrice(), v.minStock(),
                            order, v.active());
                    if (old.salePrice() != v.salePrice() || old.costPrice() != v.costPrice()) {
                        audit.log("PRICE", name + (vname.isEmpty() ? "" : " (" + vname + ")") + ": price "
                                + Money.plain(old.salePrice()) + " -> " + Money.plain(v.salePrice()) + ", cost "
                                + Money.plain(old.costPrice()) + " -> " + Money.plain(v.costPrice()));
                    }
                }
                order++;
            }
            audit.log(f.id() == null ? "PRODUCT_ADD" : "PRODUCT_EDIT", name);
            return productId;
        });
    }

    private static void validateWeight(String weight) {
        if (Text.blank(weight)) {
            return;
        }
        try {
            if (new BigDecimal(weight.trim()).signum() < 0) {
                throw new AppException("Weight cannot be negative.");
            }
        } catch (NumberFormatException e) {
            throw new AppException("Weight must be a number, e.g. 500 or 1.5.");
        }
    }

    public void setImage(long productId, byte[] jpeg) {
        products.setImage(productId, jpeg);
    }

    public void setArchived(long productId, boolean archived) {
        db.txRun(() -> {
            products.setProductActive(productId, !archived);
            Product p = products.find(productId);
            audit.log(archived ? "PRODUCT_ARCHIVE" : "PRODUCT_RESTORE", p == null ? String.valueOf(productId) : p.name());
        });
    }

    /** Delete a product that has never been stocked, bought or sold; otherwise it must be archived. */
    public boolean canDelete(long productId) {
        Product p = products.find(productId);
        return p != null && p.variants().stream().noneMatch(v -> v.stockQty() != 0 || products.variantHasHistory(v.id()));
    }

    public void delete(long productId) {
        if (!canDelete(productId)) {
            throw new AppException("This product has stock or sales history, so it can only be archived.");
        }
        db.txRun(() -> {
            Product p = products.find(productId);
            products.deleteProduct(productId);
            audit.log("PRODUCT_DELETE", p.name());
        });
    }

    // ------------------------------------------------------------ stock

    /**
     * Change stock by hand. Always requires a reason and always writes a stock movement.
     *
     * @param qty quantity in thousandths (for SET: the counted quantity)
     * @return the new stock level
     */
    public long adjustStock(long variantId, AdjustMode mode, long qty, String reason, String note) {
        if (Text.blank(reason)) {
            throw new AppException("Please choose a reason for the stock change.");
        }
        SellableItem item = products.sellable(variantId);
        if (item == null) {
            throw new AppException("Product not found.");
        }
        if (qty < 0 || (qty == 0 && mode != AdjustMode.SET)) {
            throw new AppException("Enter a quantity greater than zero.");
        }
        if (!item.fractional() && !Qty.isWhole(qty)) {
            throw new AppException(item.displayName() + " is counted in whole " + item.unitCode() + ".");
        }
        long delta = switch (mode) {
            case ADD -> qty;
            case REMOVE -> -qty;
            case SET -> qty - item.stockQty();
        };
        if (delta == 0) {
            throw new AppException("The counted quantity is the same as the current stock. Nothing to change.");
        }
        String fullReason = Text.blank(note) ? reason.trim() : reason.trim() + " - " + note.trim();
        return db.tx(() -> {
            long balance = stock.apply(variantId, delta, delta > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT",
                    item.costPrice(), null, fullReason, null, null, null);
            audit.log("STOCK_ADJUST", item.displayName() + " " + (delta > 0 ? "+" : "") + Qty.format(delta) + " "
                    + item.unitCode() + ": " + fullReason);
            return balance;
        });
    }

    // ------------------------------------------------------------ categories

    public List<Category> categories() {
        return refs.categories();
    }

    public long addCategory(String name) {
        String n = Text.trim(name);
        if (n.isEmpty()) {
            throw new AppException("Please enter a category name.");
        }
        if (refs.categoryByName(n) != null) {
            throw new AppException("A category called \"" + n + "\" already exists.");
        }
        return db.tx(() -> {
            long id = refs.insertCategory(n);
            audit.log("CATEGORY_ADD", n);
            return id;
        });
    }

    public void renameCategory(long id, String name) {
        String n = Text.trim(name);
        if (n.isEmpty()) {
            throw new AppException("Please enter a category name.");
        }
        Category other = refs.categoryByName(n);
        if (other != null && other.id() != id) {
            throw new AppException("A category called \"" + n + "\" already exists.");
        }
        refs.renameCategory(id, n);
    }

    public void deleteCategory(long id) {
        long used = refs.categoryUsage(id);
        if (used > 0) {
            throw new AppException("This category still has " + used + " product(s). Move them to another category first.");
        }
        refs.deleteCategory(id);
    }

    // ------------------------------------------------------------ units

    public List<Unit> units(boolean activeOnly) {
        return refs.units(activeOnly);
    }

    public Unit unit(long id) {
        return refs.unit(id);
    }

    public Unit unitByCode(String code) {
        return refs.unitByCode(code);
    }

    public long addUnit(String code, String name, Dimension dimension, BigDecimal factor) {
        if (Text.blank(code) || Text.blank(name)) {
            throw new AppException("Enter both a short code (e.g. dz) and a name (e.g. Dozen).");
        }
        if (code.trim().length() > 8) {
            throw new AppException("The unit code must be at most 8 characters.");
        }
        if (factor == null || factor.signum() <= 0) {
            throw new AppException("The conversion factor must be greater than zero.");
        }
        if (refs.unitByCode(code.trim()) != null) {
            throw new AppException("A unit with code \"" + code.trim() + "\" already exists.");
        }
        return refs.insertUnit(code.trim(), name.trim(), dimension, factor);
    }

    public void setUnitActive(long id, boolean active) {
        if (!active && refs.unitUsage(id) > 0) {
            Unit u = refs.unit(id);
            if (u != null && u.builtIn()) {
                throw new AppException("This unit is used by products and cannot be hidden.");
            }
        }
        refs.setUnitActive(id, active);
    }

    /**
     * Convert a measured amount between units of the same dimension, e.g. 250 g
     * into kg = 0.25. Result is in thousandths of the target unit.
     */
    public static long convert(long amountMilli, Unit from, BigDecimal toFactor) {
        BigDecimal value = Qty.toDecimal(amountMilli).multiply(from.factor()).divide(toFactor, Qty.SCALE, RoundingMode.HALF_UP);
        return Qty.fromDecimal(value);
    }
}
