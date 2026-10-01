package com.amir.expense.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.amir.expense.MainActivity
import com.amir.expense.data.AlertFired
import com.amir.expense.data.Budget
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.ExpenseDao
import com.amir.expense.data.formatRupees
import com.amir.expense.data.key
import com.amir.expense.data.range
import java.time.YearMonth

/** Posts one notification the first time a budget crosses 80% and 100% in the current month. */
class BudgetAlerts(private val context: Context, private val dao: ExpenseDao) {

    suspend fun check(ym: YearMonth) {
        // Importing an old statement shouldn't nag about a month that's already over.
        if (ym != YearMonth.now()) return
        val (start, end) = ym.range()
        val txns = dao.txnsBetweenOnce(start, end)
        val categories = dao.categoriesOnce()
        val budgets = BudgetMath.effectiveBudgets(dao.budgetsUpToOnce(ym.key), ym.key)
        val spendByCat = BudgetMath.spendByCategory(txns, categories)
        val names = categories.associate { it.id to it.name }

        for ((catId, budget) in budgets) {
            val name = if (catId == Budget.TOTAL) "Monthly" else names[catId] ?: continue
            val spent = if (catId == Budget.TOTAL) BudgetMath.totalSpend(txns) else spendByCat[catId] ?: 0L
            val fresh = BudgetMath.crossedLevels(spent, budget)
                .filter { dao.markAlert(AlertFired(ym.key, catId, it)) != -1L }
            fresh.maxOrNull()?.let { notify(catId, name, it, spent, budget) }
        }
    }

    @SuppressLint("MissingPermission") // checked just below
    private fun notify(id: Long, name: String, level: Int, spent: Long, budget: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (level >= 100) "$name budget exceeded" else "$name budget $level% used"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("${formatRupees(spent)} of ${formatRupees(budget)} spent")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notification per budget; the 100% alert replaces the 80% one.
        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }

    companion object {
        private const val CHANNEL = "budget"

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }
}
