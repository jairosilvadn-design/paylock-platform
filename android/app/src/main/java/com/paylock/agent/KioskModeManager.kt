package com.paylock.agent

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Liga/desliga o Modo Quiosque Restrito (Lock Task Mode) e decide quais apps
 * ficam visíveis durante o bloqueio: WhatsApp, apps de banco instalados, e a
 * própria tela de pagamento do PayLock.
 */
class KioskModeManager(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = PayLockDeviceAdminReceiver.getComponentName(context)

    companion object {
        private const val TAG = "KioskModeManager"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"

        // Heurística simples: qualquer app cuja categoria declarada seja
        // FINANCE, ou cujo nome do pacote esteja numa lista conhecida de
        // bancos/carteiras brasileiros. Ajuste essa lista conforme a base de
        // clientes (ou carregue de uma coleção `appsPermitidos` no Firestore,
        // administrável pelo lojista, para não depender de atualização do app).
        private val PACOTES_BANCARIOS_CONHECIDOS = setOf(
            "com.nu.production",            // Nubank
            "br.com.bb.android",             // Banco do Brasil
            "com.itau",                       // Itaú
            "com.bradesco",                   // Bradesco
            "com.santander.app",              // Santander
            "br.com.caixa.caixatem",          // Caixa Tem
            "com.picpay",                      // PicPay
            "com.mercadopago.wallet"           // Mercado Pago
        )
    }

    /** Monta a lista de pacotes permitidos no modo kiosk, incluindo o próprio app. */
    private fun pacotesPermitidos(): Array<String> {
        val pm = context.packageManager
        val instalados = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toSet()

        val permitidos = mutableSetOf(context.packageName)
        if (instalados.contains(WHATSAPP_PACKAGE)) permitidos.add(WHATSAPP_PACKAGE)
        permitidos.addAll(PACOTES_BANCARIOS_CONHECIDOS.filter { instalados.contains(it) })

        return permitidos.toTypedArray()
    }

    /**
     * Ativa o aviso de cobrança. O comportamento depende do modo em que o
     * app foi instalado (decidido fora do app, no momento do provisioning):
     *  - Device Owner: Modo Kiosk real — define pacotes permitidos e a tela
     *    de pagamento entra em Lock Task Mode (outros apps ficam ocultos).
     *  - Admin de Dispositivo (modo simplificado): sem Lock Task Mode (o
     *    Android não permite isso sem ser Device Owner). A tela de pagamento
     *    só abre em tela cheia como um aviso forte — a pessoa consegue
     *    apertar Home e usar o celular normalmente.
     */
    fun ativarKiosk(activity: Activity) {
        val isOwner = PayLockDeviceAdminReceiver.isDeviceOwner(context)

        if (isOwner) {
            dpm.setLockTaskPackages(admin, pacotesPermitidos())
            // Restringe as teclas físicas que normalmente escapam do kiosk.
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
            )
            Log.i(TAG, "Kiosk (Device Owner) ativado. Pacotes permitidos: ${pacotesPermitidos().joinToString()}")
        } else {
            Log.i(TAG, "Sem Device Owner — exibindo aviso de cobrança sem Lock Task Mode.")
        }

        val intent = Intent(context, PixPaymentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
        marcarEstado(ativo = true)
        // A própria PixPaymentActivity decide se chama startLockTask() (só
        // quando for Device Owner — ver onResume() dela).
    }

    /** Desativa o bloqueio e devolve o uso normal do aparelho. */
    fun desativarKiosk(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "stopLockTask chamado fora do lock task mode: ${e.message}")
        }
        marcarEstado(ativo = false)
        Log.i(TAG, "Kiosk desativado.")
    }

    /** Persiste o estado para que o BootCompletedReceiver saiba reabrir o kiosk após reboot. */
    private fun marcarEstado(ativo: Boolean) {
        context.getSharedPreferences("paylock_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("kiosk_ativo", ativo).apply()
    }
}
