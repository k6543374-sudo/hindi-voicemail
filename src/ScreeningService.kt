package org.voicemail.hindi

import android.telecom.Call
import android.telecom.CallScreeningService

class ScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        val number = details.handle?.schemeSpecificPart.orEmpty().filter { it.isDigit() }
        val chosen = p.getString("numbers", "").orEmpty().split(',').map { it.filter(Char::isDigit) }
        val match = p.getBoolean("enabled", false) && number.isNotEmpty() && chosen.any { it.isNotEmpty() && it == number }
        val block = match && p.getBoolean("block", false)
        respondToCall(details, CallResponse.Builder()
            .setDisallowCall(block).setRejectCall(block).setSilenceCall(match && !block)
            .setSkipCallLog(false).setSkipNotification(false).build())
    }
}
