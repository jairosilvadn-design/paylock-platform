package com.paylock.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dispara toda vez que a pessoa desbloqueia a tela do celular. Enquanto o
 * aparelho estiver marcado como bloqueado (atraso), reabre a tela de
 * cobrança (PixPaymentActivity) de novo — insistente, mas sem impedir o
 * uso do WhatsApp/bancos: a pessoa consegue apertar Home e sair a
 * qualquer momento, só volta a ver o aviso no próximo desbloqueio.
 */
class UserPresentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val bloqueado = context
            .getSharedPreferences("paylock_prefs", Context.MODE_PRIVATE)
            .getBoolean("kiosk_ativo", false)

        if (!bloqueado) return
        PaymentState.carregarSeNecessario(context)

        if (PaymentState.pixKey != null) {
            val launch = Intent(context, PixPaymentActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(launch)
        }
    }
}
