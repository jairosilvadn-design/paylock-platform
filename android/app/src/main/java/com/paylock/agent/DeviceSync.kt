package com.paylock.agent

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Centraliza a gravação do estado do aparelho (fcmToken, versão do app) no
 * Firestore, em `dispositivos/{imei}`. Usado tanto quando o FCM renova o
 * token sozinho (PayLockMessagingService.onNewToken) quanto logo depois que
 * a pessoa informa o código do cliente/IMEI pela primeira vez
 * (VincularAparelhoActivity) — sem isso, o painel nunca saberia pra qual
 * aparelho mandar os comandos de bloqueio.
 *
 * Importante: isso só funciona se as Firestore Rules já tiverem um
 * documento `dispositivos/{imei}` pré-criado pelo lojista no painel (ver
 * dashboard.html) — a regra permite `update`, não `create`, para o
 * aparelho. Por isso o cliente precisa estar cadastrado no painel ANTES de
 * vincular o aparelho.
 */
object DeviceSync {
    private const val TAG = "DeviceSync"

    fun sincronizarTokenAtual(context: Context, imei: String) {
        AnonymousAuth.ensureSignedIn { sucesso ->
            if (!sucesso) {
                Log.e(TAG, "Sem autenticação — não foi possível sincronizar com o Firestore.")
                return@ensureSignedIn
            }
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> gravar(imei, token) }
                .addOnFailureListener { Log.e(TAG, "Falha ao obter token FCM", it) }
        }
    }

    fun gravar(imei: String, fcmToken: String) {
        FirebaseFirestore.getInstance()
            .collection("dispositivos")
            .document(imei)
            .set(
                mapOf(
                    "fcmToken" to fcmToken,
                    "ultimaSincronizacaoEm" to System.currentTimeMillis(),
                    "versaoApp" to BuildConfig.VERSION_NAME
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { Log.i(TAG, "Dispositivo $imei sincronizado.") }
            .addOnFailureListener {
                Log.e(
                    TAG,
                    "Falha ao gravar dispositivo $imei — cliente foi cadastrado no painel com esse IMEI?",
                    it
                )
            }
    }
}
