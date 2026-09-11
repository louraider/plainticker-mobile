package com.myapp

import android.app.Application
import android.content.Context

class PlainTickerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

/** The process-wide dependency graph, from any Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as PlainTickerApp).container
