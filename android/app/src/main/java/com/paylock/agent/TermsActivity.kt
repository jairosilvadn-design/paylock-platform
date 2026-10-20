package com.paylock.agent

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de aceite do Termo de Adesão — ponto único onde o fluxo "pessoa
 * aceita → sistema executa" acontece.
 *
 * Comportamento depende do que já foi concedido ao app ANTES desta tela
 * (isso não é escolhido pelo app, é decidido no momento da instalação):
 *
 *  - Se o app já foi provisionado como **Device Owner** (via QR Code ou ADB,
 *    com o aparelho sem conta): ao aceitar, aplica o conjunto completo de
 *    restrições (DevicePolicyEnforcer) — bloqueio de reset de fábrica,
 *    bloqueio de dev options, anti-desinstalação e Modo Kiosk real.
 *  - Caso contrário (instalação normal, aparelho já em uso): ao aceitar,
 *    pede apenas a permissão padrão de **Administrador do Dispositivo**
 *    (tela nativa do Android). Isso NÃO bloqueia o Kiosk/anti-desinstalação
 *    de verdade — só acrescenta o passo extra de "desativar antes de
 *    desinstalar" e permite exibir a tela de cobrança com prioridade.
 */
class TermsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val REQUEST_DEVICE_ADMIN = 1001
        private const val PREFS = "paylock_prefs"
        private const val KEY_TERMO_ACEITO = "termo_aceito"

        fun termoAceito(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_TERMO_ACEITO, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContentView(R.layout.activity_terms)

        findViewById<TextView>(R.id.tvTermo).text = getString(R.string.texto_termo_adesao)

        findViewById<Button>(R.id.btnAceitar).setOnClickListener { aceitarTermo() }
        findViewById<Button>(R.id.btnRecusar).setOnClickListener {
            Toast.makeText(this, "É necessário aceitar os termos para usar o aparelho financiado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun aceitarTermo() {
        prefs.edit().putBoolean(KEY_TERMO_ACEITO, true).apply()

        // Auto-registro: o aparelho já se cadastra sozinho no Firestore como
        // "aguardando configuração" assim que a pessoa aceita — o lojista só
        // completa os dados comerciais (valor, vencimento, PIX, etc.) depois,
        // direto no painel. Nenhum vínculo manual é necessário.
        DeviceSync.registrarAutomaticamente(this)

        if (PayLockDeviceAdminReceiver.isDeviceOwner(this)) {
            // Caminho completo: aparelho foi provisionado (QR Code / ADB).
            DevicePolicyEnforcer(this).aplicarPoliticasIniciais()
            Toast.makeText(this, "Termo aceito. Restrições aplicadas.", Toast.LENGTH_SHORT).show()
            irParaMain()
        } else {
            // Caminho simplificado: pede só Admin de Dispositivo (tela nativa do Android).
            solicitarAdminDispositivo()
        }
    }

    private fun solicitarAdminDispositivo() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PayLockDeviceAdminReceiver.getComponentName(this@TermsActivity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "O PayLock precisa desta permissão para avisar sobre pagamentos pendentes " +
                    "e dificultar a remoção acidental do app enquanto houver saldo devedor."
            )
        }
        startActivityForResult(intent, REQUEST_DEVICE_ADMIN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_ADMIN) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Admin de dispositivo ativado. Obrigado!", Toast.LENGTH_SHORT).show()
                irParaMain()
            } else {
                Toast.makeText(
                    this,
                    "Sem essa permissão, o PayLock não consegue avisar sobre pagamentos pendentes.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun irParaMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
