package com.smartbillingpro.service;

import com.smartbillingpro.db.AppException;
import com.smartbillingpro.db.Db;
import com.smartbillingpro.model.Expense;
import com.smartbillingpro.model.PaymentMethod;
import com.smartbillingpro.repo.AuditRepo;
import com.smartbillingpro.repo.ExpenseRepo;
import com.smartbillingpro.repo.PaymentRepo;
import com.smartbillingpro.repo.ReferenceRepo;
import com.smartbillingpro.repo.SequenceRepo;
import com.smartbillingpro.util.Dates;
import com.smartbillingpro.util.Money;
import com.smartbillingpro.util.Text;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shop running costs (rent, electricity, salaries...) used for net profit. */
public final class ExpenseService {

    public static final String[] DEFAULT_CATEGORIES = {"Rent", "Electricity", "Salaries", "Transport", "Repairs",
        "Internet & Phone", "Shop supplies", "Other"};

    private final Db db;
    private final ExpenseRepo expenses;
    private final PaymentRepo payments;
    private final ReferenceRepo refs;
    private final SequenceRepo sequences;
    private final AuditRepo audit;

    public ExpenseService(Db db, ExpenseRepo expenses, PaymentRepo payments, ReferenceRepo refs, SequenceRepo sequences,
                          AuditRepo audit) {
        this.db = db;
        this.expenses = expenses;
        this.payments = payments;
        this.refs = refs;
        this.sequences = sequences;
        this.audit = audit;
    }

    public Expense add(LocalDate date, String category, long amount, long methodId, String note) {
        if (date == null) {
            throw new AppException("Please enter the expense date.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new AppException("The expense date cannot be in the future.");
        }
        if (Text.blank(category)) {
            throw new AppException("Please choose or type an expense category.");
        }
        if (amount <= 0) {
            throw new AppException("Enter an amount greater than zero.");
        }
        PaymentMethod m = refs.method(methodId);
        if (m == null) {
            throw new AppException("Choose how the expense was paid.");
        }
        long id = db.tx(() -> {
            String no = SequenceRepo.format("EXP", sequences.next("expense"));
            long eid = expenses.insert(no, date.format(Dates.ISO_DATE), category.trim(), amount, m.id(), m.name(),
                    Text.trimToNull(note));
            payments.insert(SequenceRepo.format("PAY", sequences.next("payment")), "OUT", "EXPENSE", null, amount,
                    m.id(), m.name(), null, null, null, eid, null, category.trim() + (Text.blank(note) ? "" : ": " + note.trim()));
            audit.log("EXPENSE", no + " " + category.trim() + " " + Money.plain(amount));
            return eid;
        });
        return expenses.find(id);
    }

    /** Cancel an expense recorded by mistake; it stays visible as "Voided" and the money is booked back. */
    public void voidExpense(long id, String reason) {
        if (Text.blank(reason)) {
            throw new AppException("Please enter why the expense is being voided.");
        }
        db.txRun(() -> {
            Expense e = expenses.find(id);
            if (e == null) {
                throw new AppException("Expense not found.");
            }
            if (e.voided()) {
                throw new AppException("This expense is already voided.");
            }
            expenses.voidExpense(id, reason.trim());
            payments.insert(SequenceRepo.format("PAY", sequences.next("payment")), "IN", "EXPENSE", null, e.amount(),
                    e.methodId(), e.methodName(), null, null, null, id, null, "Voided " + e.expenseNo() + ": " + reason.trim());
            audit.log("EXPENSE_VOID", e.expenseNo() + ": " + reason.trim());
        });
    }

    public List<Expense> list(LocalDate from, LocalDate to) {
        return expenses.list(from.format(Dates.ISO_DATE), to.format(Dates.ISO_DATE));
    }

    public List<String> categories() {
        Set<String> all = new LinkedHashSet<>(List.of(DEFAULT_CATEGORIES));
        all.addAll(expenses.categories());
        return new ArrayList<>(all);
    }
}
