package com.paylock.agent

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Pede o código do cliente/IMEI que já deve estar cadastrado no painel.
 * Só é exibida no modo simplificado (Admin de Dispositivo), quando o app
 * não recebeu o IMEI automaticamente via provisioning (QR Code/ADB) — ver
 * MainActivity. Sem isso, o painel nunca saberia pra qual aparelho mandar
 * os comandos de bloqueio/desbloqueio.
 */
class VincularAparelhoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vincular_aparelho)

        findViewById<Button>(R.id.btnVincular).setOnClickListener {
            val imei = findViewById<EditText>(R.id.etImei).text.toString().trim()
            if (imei.isBlank()) {
                Toast.makeText(this, "Informe o código do cliente/IMEI.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            DeviceIdentity.salvarImei(this, imei)
            DeviceSync.sincronizarTokenAtual(this, imei)

            Toast.makeText(this, "Aparelho vinculado! Sincronizando...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
