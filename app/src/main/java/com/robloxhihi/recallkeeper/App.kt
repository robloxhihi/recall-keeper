package com.robloxhihi.recallkeeper

import android.app.Application
import com.robloxhihi.recallkeeper.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
