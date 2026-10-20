package com.paylock.agent

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Vincula este aparelho a um cliente já cadastrado no painel.
 *
 * Automático sempre que possível: a página web/aceite.html copia o código
 * do cliente para a área de transferência do aparelho logo antes de iniciar
 * o download do APK (ver aceite.html). Se esse valor ainda estiver no
 * clipboard quando o app abrir pela primeira vez, o vínculo acontece
 * sozinho, sem a pessoa digitar nada — "baixou, aceitou, já era".
 *
 * Se não encontrar nada no clipboard (ex: reinstalação, teste manual, ou o
 * navegador bloqueou a cópia automática), cai no formulário manual.
 */
class VincularAparelhoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vincular_aparelho)

        val codigoDoClipboard = lerCodigoDoClipboard()
        if (!codigoDoClipboard.isNullOrBlank()) {
            vincular(codigoDoClipboard)
            return
        }

        // Sem nada no clipboard — mostra o formulário manual.
        findViewById<Button>(R.id.btnVincular).setOnClickListener {
            val imei = findViewById<EditText>(R.id.etImei).text.toString().trim()
            if (imei.isBlank()) {
                Toast.makeText(this, "Informe o código do cliente/IMEI.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vincular(imei)
        }
    }

    /**
     * Lê o clipboard de forma simples: qualquer texto curto, sem espaços,
     * é aceito como possível código de cliente/IMEI. A confirmação real de
     * que o código existe acontece no Firestore (DeviceSync) — se o código
     * copiado estiver errado, a gravação falha silenciosamente e a pessoa
     * ainda consegue corrigir manualmente reabrindo o app (volta a mostrar
     * o formulário, já que o vínculo não terá sido salvo).
     */
    private fun lerCodigoDoClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) return null
        val texto = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        if (texto.isNullOrBlank() || texto.length > 40 || texto.contains(" ") || texto.contains("\n")) {
            return null
        }
        return texto
    }

    private fun vincular(imei: String) {
        DeviceIdentity.salvarImei(this, imei)
        DeviceSync.sincronizarTokenAtual(this, imei)

        findViewById<TextView?>(R.id.etImei)?.setText(imei)
        Toast.makeText(this, "Aparelho vinculado ao cliente $imei!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
