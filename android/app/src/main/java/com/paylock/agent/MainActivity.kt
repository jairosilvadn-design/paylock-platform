package com.paylock.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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

        pedirPermissaoDeNotificacaoSeNecessario()

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

    /** No Android 13+, a notificação de cobrança exige essa permissão em tempo de execução. */
    private fun pedirPermissaoDeNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val concedida = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedida) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }
}
