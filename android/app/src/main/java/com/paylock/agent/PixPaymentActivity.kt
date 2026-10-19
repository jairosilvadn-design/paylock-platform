package com.paylock.agent

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Tela exibida quando o aparelho entra em Modo Quiosque por inadimplência.
 * Mostra valor pendente, chave PIX e um botão "Copiar chave PIX". É a única
 * tela "própria" visível durante o bloqueio, junto com WhatsApp e os apps
 * bancários liberados pelo KioskModeManager.
 */
class PixPaymentActivity : AppCompatActivity() {

    private lateinit var kioskModeManager: KioskModeManager
    private lateinit var desbloqueioReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pix_payment)

        kioskModeManager = KioskModeManager(this)

        findViewById<TextView>(R.id.tvValorPendente).text =
            "Valor pendente: R$ ${PaymentState.valor ?: "--"}"
        findViewById<TextView>(R.id.tvVencimento).text =
            "Vencimento: ${PaymentState.vencimento ?: "--"}"
        findViewById<TextView>(R.id.tvChavePix).text = PaymentState.pixKey ?: "--"

        findViewById<Button>(R.id.btnCopiarPix).setOnClickListener {
            copiarChavePix()
        }

        desbloqueioReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                kioskModeManager.desativarKiosk(this@PixPaymentActivity)
                finish()
            }
        }
        ContextCompat.registerReceiver(
            this,
            desbloqueioReceiver,
            IntentFilter(ACTION_DESBLOQUEAR),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        // Garante que, mesmo se o usuário tentar sair (home, recents), a tela
        // volte ao modo kiosk enquanto o bloqueio estiver ativo.
        startLockTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(desbloqueioReceiver)
    }

    override fun onBackPressed() {
        // Intencional: o botão Voltar não deve sair da tela de pagamento
        // enquanto o aparelho estiver bloqueado.
    }

    private fun copiarChavePix() {
        val chave = PaymentState.pixKey ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Chave PIX", chave))
        Toast.makeText(this, "Chave PIX copiada!", Toast.LENGTH_SHORT).show()
    }
}
