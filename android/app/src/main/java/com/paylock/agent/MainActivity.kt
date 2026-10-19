package com.paylock.agent

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela padrão do app, aberta pelo launcher ou ao final do provisionamento.
 * Mostra o status do financiamento quando o aparelho NÃO está em modo kiosk
 * (uso normal). Em produção, busque o status em tempo real no Firestore
 * (`clientes/{imei}`), análogo ao `dashboard.html` do painel.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            textSize = 16f
            setPadding(48, 96, 48, 48)
            text = buildString {
                append("PayLock — Agente instalado\n\n")
                append(if (PayLockDeviceAdminReceiver.isDeviceOwner(this@MainActivity))
                    "Status: Device Owner ativo.\n" else "Status: aguardando provisionamento.\n")
                append("IMEI: ${DeviceIdentity.obterImei(this@MainActivity) ?: "não configurado"}")
            }
        }
        setContentView(status)
    }
}
