package com.paylock.agent

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela padrão do app. No primeiro uso, leva direto para o Termo de Adesão
 * (TermsActivity) — é lá que o fluxo "pessoa aceita → sistema executa"
 * acontece, tanto no caminho completo (Device Owner) quanto no simplificado
 * (Admin de Dispositivo).
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

        // Sem IMEI/código do cliente vinculado, o painel nunca saberia pra
        // qual aparelho mandar os comandos — pede antes de seguir.
        val imei = DeviceIdentity.obterImei(this)
        if (imei == null) {
            startActivity(Intent(this, VincularAparelhoActivity::class.java))
            finish()
            return
        }

        // Garante que o token FCM atual está salvo no Firestore sempre que o
        // app abre (cobre o caso do Device Owner, que já sabe o IMEI desde o
        // provisioning, mas ainda não tinha sincronizado o token).
        DeviceSync.sincronizarTokenAtual(this, imei)

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
                append("IMEI: ${DeviceIdentity.obterImei(this@MainActivity) ?: "não configurado"}")
            }
        }
        setContentView(status)
    }
}
