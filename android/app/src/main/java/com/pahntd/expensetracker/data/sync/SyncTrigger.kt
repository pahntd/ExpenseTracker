package com.pahntd.expensetracker.data.sync

enum class SyncTrigger {
    LOGIN,
    STARTUP,
    RECONNECTED,
    MANUAL,
    PERIODIC
}