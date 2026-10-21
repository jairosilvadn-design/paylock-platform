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
        PaymentState.carregarSeNecessario(this)

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
        // Lock Task Mode (impede sair pelo Home/Recents) só é possível —
        // e só é chamado — quando o app é Device Owner. No modo simplificado
        // (Admin de Dispositivo), a tela abre normalmente como um aviso forte,
        // mas a pessoa consegue sair apertando Home.
        if (PayLockDeviceAdminReceiver.isDeviceOwner(this)) {
            startLockTask()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(desbloqueioReceiver)
    }

    override fun onBackPressed() {
        if (PayLockDeviceAdminReceiver.isDeviceOwner(this)) {
            // Intencional: no modo Kiosk real, o botão Voltar não sai da tela.
            return
        }
        super.onBackPressed()
    }

    private fun copiarChavePix() {
        val chave = PaymentState.pixKey ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Chave PIX", chave))
        Toast.makeText(this, "Chave PIX copiada!", Toast.LENGTH_SHORT).show()
    }
}
