package com.amir.expense

import android.app.Application
import com.amir.expense.alerts.BudgetAlerts
import com.amir.expense.data.AppDatabase
import com.amir.expense.data.Repository
import com.amir.expense.sync.DriveSync
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    lateinit var repo: Repository
        private set
    lateinit var drive: DriveSync
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        BudgetAlerts.createChannel(this)
        val dao = AppDatabase.build(this).dao()
        repo = Repository(dao, BudgetAlerts(this, dao))
        drive = DriveSync(this, repo)
        drive.schedule()
    }
}
