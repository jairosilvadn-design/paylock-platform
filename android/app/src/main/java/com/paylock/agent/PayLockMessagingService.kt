package com.paylock.agent

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
                PaymentState.atualizar(pixKey, valor, vencimento)
                val intent = Intent(this, PixPaymentActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            "DESBLOQUEAR" -> {
                // A própria PixPaymentActivity, ao detectar DESBLOQUEAR via
                // broadcast/estado compartilhado, chama stopLockTask() e
                // finaliza-se, devolvendo o launcher normal ao usuário.
                PaymentState.limpar()
                sendBroadcast(Intent(ACTION_DESBLOQUEAR))
            }
            else -> Log.w(TAG, "Comando desconhecido: $comando")
        }
    }

}

const val ACTION_DESBLOQUEAR = "com.paylock.agent.ACTION_DESBLOQUEAR"

/** Estado simples em memória/SharedPreferences com os dados de pagamento pendente. */
object PaymentState {
    var pixKey: String? = null
        private set
    var valor: String? = null
        private set
    var vencimento: String? = null
        private set

    fun atualizar(pix: String, v: String, venc: String) {
        pixKey = pix
        valor = v
        vencimento = venc
    }

    fun limpar() {
        pixKey = null
        valor = null
        vencimento = null
    }
}
