package com.fyr

import android.app.Application
import com.fyr.data.Store

/**
 * Holds the single [Store] for the process.
 *
 * Created eagerly rather than lazily so the very first frame Compose draws is
 * already backed by real data — a habit tracker that flashes empty before
 * filling in undermines the one thing it is asking you to trust it with.
 */
class FyrApp : Application() {

    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
    }
}
