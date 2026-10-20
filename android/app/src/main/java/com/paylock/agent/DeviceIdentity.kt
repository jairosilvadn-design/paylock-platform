package com.paylock.agent

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

/**
 * Identificador do aparelho — usado como ID do documento em
 * `clientes/{deviceId}` e `dispositivos/{deviceId}`.
 *
 * Não depende de nada externo (nem QR Code, nem clipboard, nem digitação):
 * usa `Settings.Secure.ANDROID_ID`, disponível em qualquer app sem
 * permissão especial. É único por combinação app+aparelho (muda se o
 * aparelho for formatado — o que é raro e aceitável aqui, já que o app
 * normalmente trava exatamente esse tipo de ação).
 *
 * Isso permite o fluxo "baixou, instalou, aceitou, já era": o app se
 * auto-registra no Firestore sozinho (ver DeviceSync), sem precisar de
 * nenhum vínculo manual feito pelo lojista ou pela pessoa.
 */
object DeviceIdentity {
    private const val PREFS = "paylock_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun obterDeviceId(context: Context): String {
        prefs(context).getString(KEY_DEVICE_ID, null)?.let { return it }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "sem-id-${System.currentTimeMillis()}"
        prefs(context).edit().putString(KEY_DEVICE_ID, androidId).apply()
        return androidId
    }
}
