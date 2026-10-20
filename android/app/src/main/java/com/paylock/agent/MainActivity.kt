package com.paylock.agent

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela padrão do app. No primeiro uso, leva direto para o Termo de Adesão
 * (TermsActivity) — é lá que o fluxo "pessoa aceita → sistema executa"
 * acontece, incluindo o auto-registro do aparelho (DeviceSync). Depois
 * disso, só mostra o status — nenhum passo manual extra é necessário.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("paylock_prefs", MODE_PRIVATE)
        if (!TermsActivity.termoAceito(prefs)) {
            startActivity(Intent(this, TermsActivity::class.java))
            finish()
            return
        }

        val deviceId = DeviceIdentity.obterDeviceId(this)
        // Garante que o token FCM atual está salvo, sempre que o app abre.
        DeviceSync.sincronizarTokenAtual(this, deviceId)

        val status = TextView(this).apply {
            textSize = 16f
            setPadding(48, 96, 48, 48)
            text = buildString {
                append("PayLock — Agente instalado\n\n")
                append(
                    if (PayLockDeviceAdminReceiver.isDeviceOwner(this@MainActivity))
                        "Modo: Device Owner (controle completo).\n"
                    else
                        "Modo: Admin de Dispositivo (simplificado).\n"
                )
                append("Código do aparelho: $deviceId\n\n")
                append("O lojista completará seu cadastro (valor, vencimento, PIX) no painel.")
            }
        }
        setContentView(status)
    }
}
