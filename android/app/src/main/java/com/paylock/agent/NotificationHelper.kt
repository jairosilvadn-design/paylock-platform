package com.paylock.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Notificação fixa de cobrança — fica na barra de notificações enquanto o
 * aparelho estiver em atraso (modo simplificado, sem Device Owner). Não
 * trava o uso do celular; é só um lembrete insistente que some quando o
 * lojista confirma o pagamento (comando DESBLOQUEAR).
 */
object NotificationHelper {
    private const val CHANNEL_ID = "paylock_cobranca"
    private const val NOTIFICATION_ID = 1001

    fun mostrar(context: Context, valor: String, vencimento: String) {
        criarCanalSeNecessario(context)

        val intent = Intent(context, PixPaymentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacao = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Pagamento pendente — R$ $valor")
            .setContentText("Vencimento: $vencimento. Toque para ver a chave PIX.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true) // a pessoa não consegue arrastar/fechar essa notificação
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notificacao)
        } catch (e: SecurityException) {
            // Permissão de notificação (Android 13+) não concedida — a tela
            // cheia ainda aparece a cada desbloqueio (ver UserPresentReceiver),
            // só a notificação na barra que não é exibida.
        }
    }

    fun cancelar(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun criarCanalSeNecessario(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val canal = NotificationChannel(
            CHANNEL_ID,
            "Cobrança PayLock",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avisos de pagamento pendente enquanto o aparelho estiver em atraso."
            setBypassDnd(true)
        }
        manager.createNotificationChannel(canal)
    }
}
