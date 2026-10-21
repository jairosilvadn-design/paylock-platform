package com.paylock.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recebe os comandos BLOQUEAR/DESBLOQUEAR enviados pelo backend (Netlify
 * Function `send-command` ou `check-overdue`) via FCM data message.
 *
 * Usamos mensagens "data-only" (sem `notification`) para garantir que o
 * `onMessageReceived` seja chamado mesmo com o app em background/finalizado
 * em aparelhos Android recentes, e para não exibir nada ao usuário antes de
 * aplicarmos o kiosk (evita que ele tente reagir/escapar antes da trava).
 */
class PayLockMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "PayLockMessaging"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val deviceId = DeviceIdentity.obterDeviceId(this)
        AnonymousAuth.ensureSignedIn { sucesso ->
            if (sucesso) DeviceSync.gravar(deviceId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val comando = message.data["comando"] ?: return
        val pixKey = message.data["pixKey"].orEmpty()
        val valor = message.data["valorPendente"].orEmpty()
        val vencimento = message.data["vencimento"].orEmpty()

        Log.i(TAG, "Comando recebido: $comando")

        when (comando) {
            "BLOQUEAR" -> {
                PaymentState.atualizar(this, pixKey, valor, vencimento)
                NotificationHelper.mostrar(this, valor, vencimento)
                val intent = Intent(this, PixPaymentActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            "DESBLOQUEAR" -> {
                // Desbloqueio normal (ex: pagou a parcela do mês). A própria
                // PixPaymentActivity, ao detectar via broadcast, chama
                // stopLockTask() e finaliza-se. NÃO libera a anti-desinstalação
                // — o aparelho pode voltar a ser bloqueado no próximo atraso.
                PaymentState.limpar(this)
                NotificationHelper.cancelar(this)
                sendBroadcast(Intent(ACTION_DESBLOQUEAR))
            }
            "QUITAR" -> {
                // Financiamento quitado de vez: libera TODAS as restrições,
                // incluindo a anti-desinstalação (setUninstallBlocked) — só
                // tem efeito em aparelhos Device Owner; no modo simplificado
                // nunca houve bloqueio de desinstalação pra liberar.
                DevicePolicyEnforcer(this).liberarRestricoesPorQuitacao()
                PaymentState.limpar(this)
                NotificationHelper.cancelar(this)
                sendBroadcast(Intent(ACTION_DESBLOQUEAR))
            }
            else -> Log.w(TAG, "Comando desconhecido: $comando")
        }
    }

}

const val ACTION_DESBLOQUEAR = "com.paylock.agent.ACTION_DESBLOQUEAR"

/**
 * Dados de pagamento pendente — persistidos em SharedPreferences (não só em
 * memória), porque o Android pode matar o processo do app em background a
 * qualquer momento. Sem isso, UserPresentReceiver (que reabre a tela de
 * cobrança a cada desbloqueio) não teria valor/PIX pra mostrar depois que o
 * processo fosse recriado do zero.
 */
object PaymentState {
    private const val PREFS = "paylock_prefs"
    private const val KEY_PIX = "payment_pix"
    private const val KEY_VALOR = "payment_valor"
    private const val KEY_VENCIMENTO = "payment_vencimento"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var pixKey: String? = null
        private set
    var valor: String? = null
        private set
    var vencimento: String? = null
        private set

    fun atualizar(context: Context, pix: String, v: String, venc: String) {
        pixKey = pix
        valor = v
        vencimento = venc
        prefs(context).edit()
            .putString(KEY_PIX, pix)
            .putString(KEY_VALOR, v)
            .putString(KEY_VENCIMENTO, venc)
            .apply()
    }

    fun limpar(context: Context) {
        pixKey = null
        valor = null
        vencimento = null
        prefs(context).edit().remove(KEY_PIX).remove(KEY_VALOR).remove(KEY_VENCIMENTO).apply()
    }

    /** Garante que os valores em memória existam, lendo do disco se o processo acabou de nascer. */
    fun carregarSeNecessario(context: Context) {
        if (pixKey != null) return
        val p = prefs(context)
        pixKey = p.getString(KEY_PIX, null)
        valor = p.getString(KEY_VALOR, null)
        vencimento = p.getString(KEY_VENCIMENTO, null)
    }
}
