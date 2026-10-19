package com.paylock.agent

import android.content.Context
import android.content.SharedPreferences

/**
 * Identificador do aparelho usado como chave em `clientes/{imei}` e
 * `dispositivos/{imei}`.
 *
 * A partir do Android 10, apps não podem mais ler o IMEI real via
 * TelephonyManager (exige permissão de sistema). Como este app roda como
 * Device Owner, duas abordagens funcionam:
 *   1. Receber o IMEI como parte extra do payload de provisionamento (QR
 *      Code), gravado aqui no primeiro boot — abordagem recomendada e usada
 *      por este template.
 *   2. Usar `DevicePolicyManager.getEnrollmentSpecificId()` (Android 12+)
 *      como alternativa que não exige permissão especial.
 *
 * Por simplicidade, o template grava o valor recebido do QR Code
 * (`android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE` → chave "imei") em
 * SharedPreferences no primeiro provisionamento.
 */
object DeviceIdentity {
    private const val PREFS = "paylock_prefs"
    private const val KEY_IMEI = "imei"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun salvarImei(context: Context, imei: String) {
        prefs(context).edit().putString(KEY_IMEI, imei).apply()
    }

    fun obterImei(context: Context): String? =
        prefs(context).getString(KEY_IMEI, null)
}
