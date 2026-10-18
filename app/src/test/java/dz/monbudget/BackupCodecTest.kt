package dz.monbudget

import dz.monbudget.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val snapshot = BackupSnapshot(
        initialBalanceCents = 123_456,
        incomes = listOf(Income("i1", 8_000_000, 20000, "SALARY", "Octobre \"net\"", "2026-10", IncomeStatus.RECEIVED)),
        expenses = listOf(Expense("e1", 5_000, 20001, "cat_eau", "Eau", "Espèces", "note", ExpenseStatus.PAID, "L1")),
        lists = listOf(ShoppingList("L1", "Semaine", 20000, 0, ListStatus.CONVERTED)),
        items = listOf(
            ShoppingItem("it1", "L1", "Huile", "cat_alimentation", 2500, "L", 140_000, null, "Marché", "", true),
            ShoppingItem("it2", "L1", "Riz", "cat_alimentation", 1000, "Kg", 25_000, 26_000, "", "", false)
        ),
        budgets = listOf(BudgetLimit("2026-10|ALL", "2026-10", "ALL", 5_000_000)),
        goals = listOf(SavingsGoal("g1", "Voyage", 10_000_000, null, 20000), SavingsGoal("g2", "Réserve", 5_000_000, 21000, 20000)),
        savingsTx = listOf(SavingsTransaction("t1", "g1", 100_000, TxType.DEPOSIT, 20002, "")),
        bills = listOf(Bill("b1", "Électricité", 450_000, 20010, "cat_energie", true))
    )

    @Test fun encode_then_decode_returns_identical_data() {
        assertEquals(snapshot, BackupCodec.decode(BackupCodec.encode(snapshot)))
    }

    @Test fun empty_database_roundtrips() {
        val empty = BackupSnapshot(0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }

    @Test fun invalid_files_are_rejected_before_any_change() {
        assertThrows(Exception::class.java) { BackupCodec.decode("pas du json") }
        assertThrows(Exception::class.java) { BackupCodec.decode("{\"app\":\"Autre\",\"format\":1}") }
        assertThrows(Exception::class.java) { BackupCodec.decode("{\"app\":\"MonBudgetDZ\",\"format\":99}") }
        assertThrows(Exception::class.java) { BackupCodec.decode("{\"app\":\"MonBudgetDZ\",\"format\":1}") }   // tables manquantes
    }
}
