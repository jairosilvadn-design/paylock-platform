package com.paylock.agent

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver de administração do dispositivo (Device Admin / Device Owner).
 *
 * Este app só deve ser ativado como **Device Owner** durante o provisionamento
 * inicial do aparelho (via QR Code / afw#setup), em um aparelho recém
 * resetado de fábrica e com o consentimento do cliente (Termo de Adesão).
 * Depois de virar Device Owner, outras contas Google e a remoção do app
 * ficam bloqueadas por política do próprio Android Enterprise — não é algo
 * que o app precisa "forçar" por fora da API oficial.
 */
class PayLockDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PayLockDeviceAdmin"

        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, PayLockDeviceAdminReceiver::class.java)

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin habilitado.")
    }

    /**
     * Disparado automaticamente ao final do provisionamento Android Enterprise
     * (fluxo do QR Code / ADB). As restrições só são aplicadas depois que a
     * pessoa aceitar o Termo de Adesão dentro do app (ver TermsActivity) —
     * aqui só abrimos o app, que decide o que mostrar.
     */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Provisionamento (Device Owner) concluído. Aguardando aceite do termo.")

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(launch)
    }

    /**
     * Enquanto houver saldo devedor (ver Termo de Adesão, item 3), o app NÃO
     * deve permitir a desativação voluntária do Device Admin. Isso é reforçado
     * pela própria API: um Device Owner com `setUninstallBlocked` ativo exige
     * que o próprio Device Owner remova as restrições antes da desativação.
     * Aqui apenas registramos a tentativa para fins de auditoria.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Tentativa de desativação do Device Admin detectada.")
        return "Este aparelho está em financiamento ativo (PayLock). Quite o saldo " +
            "devedor no painel ou fale com a loja para liberar o aparelho."
    }
}
