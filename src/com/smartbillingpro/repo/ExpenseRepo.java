package com.smartbillingpro.repo;

import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Expense;
import com.smartbillingpro.util.Dates;

import java.util.List;

public final class ExpenseRepo {
    private final Db db;

    public ExpenseRepo(Db db) {
        this.db = db;
    }

    public long insert(String no, String expenseDate, String category, long amount, Long methodId, String methodName,
                       String note) {
        return db.insert("INSERT INTO expenses (expense_no, created_at, expense_date, category, amount, payment_method_id, "
                + "payment_method_name, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", no, Dates.now(), expenseDate, category,
                amount, methodId, methodName, note);
    }

    public void voidExpense(long id, String reason) {
        db.update("UPDATE expenses SET status = 'VOIDED', void_reason = ? WHERE id = ?", reason, id);
    }

    private static Expense map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long m = rs.getLong("payment_method_id");
        Long method = rs.wasNull() ? null : m;
        return new Expense(rs.getLong("id"), rs.getString("expense_no"), rs.getString("created_at"),
                rs.getString("expense_date"), rs.getString("category"), rs.getLong("amount"), method,
                rs.getString("payment_method_name"), rs.getString("note"), rs.getString("status"),
                rs.getString("void_reason"));
    }

    public Expense find(long id) {
        return db.one("SELECT * FROM expenses WHERE id = ?", ExpenseRepo::map, id);
    }

    /** Expenses whose expense date is within [from, to] (ISO dates, inclusive). */
    public List<Expense> list(String fromDate, String toDate) {
        return db.list("SELECT * FROM expenses WHERE expense_date >= ? AND expense_date <= ? ORDER BY expense_date DESC, id DESC",
                ExpenseRepo::map, fromDate, toDate);
    }

    public List<String> categories() {
        return db.list("SELECT DISTINCT category FROM expenses ORDER BY category COLLATE NOCASE", rs -> rs.getString(1));
    }
}
