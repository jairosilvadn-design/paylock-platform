package com.paylock.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Após reiniciar o aparelho, se havia um bloqueio ativo (kiosk), reabre a
 * tela de pagamento automaticamente — sem isso, um reboot seria uma forma
 * fácil de burlar o bloqueio.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val dispositivoBloqueado = context
            .getSharedPreferences("paylock_prefs", Context.MODE_PRIVATE)
            .getBoolean("kiosk_ativo", false)

        if (dispositivoBloqueado) {
            PaymentState.carregarSeNecessario(context)
            val launch = Intent(context, PixPaymentActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(launch)
        }
    }
}
