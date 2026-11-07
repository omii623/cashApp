package com.example.cashapp

import android.app.Application
import com.example.cashapp.di.AppContainer
import com.example.cashapp.notifications.RecurringNotifications
import com.example.cashapp.notifications.RecurringWorker

class CashApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        RecurringNotifications.createChannel(this)
        RecurringWorker.schedule(this)
    }
}
