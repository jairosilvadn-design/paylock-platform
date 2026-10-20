package com.paylock.agent

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Auto-registro do aparelho: assim que a pessoa aceita o termo (dentro do
 * app), o aparelho se cadastra sozinho no Firestore como "aguardando
 * configuração" — sem precisar de nenhum vínculo manual feito antes. O
 * lojista só completa os dados comerciais (valor, vencimento, PIX, etc.)
 * depois, direto no painel.
 */
object DeviceSync {
    private const val TAG = "DeviceSync"
    const val STATUS_PENDENTE = "PENDENTE_CONFIGURACAO"

    /** Chamado uma vez, logo após o aceite do termo (TermsActivity). */
    fun registrarAutomaticamente(context: Context) {
        val deviceId = DeviceIdentity.obterDeviceId(context)

        AnonymousAuth.ensureSignedIn { sucesso ->
            if (!sucesso) {
                Log.e(TAG, "Sem autenticação — não foi possível auto-registrar o aparelho.")
                return@ensureSignedIn
            }

            val db = FirebaseFirestore.getInstance()

            // Cria (se ainda não existir) o registro do "cliente" com status
            // pendente — o lojista completa nome/CPF/valor/PIX depois.
            db.collection("clientes").document(deviceId)
                .set(
                    mapOf(
                        "status" to STATUS_PENDENTE,
                        "termo" to mapOf(
                            "aceito" to true,
                            "versao" to "1.0",
                            "dataHoraISO" to java.time.Instant.now().toString()
                        ),
                        "criadoEm" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener { Log.i(TAG, "Cliente $deviceId auto-registrado (pendente de configuração).") }
                .addOnFailureListener { Log.e(TAG, "Falha ao auto-registrar cliente $deviceId", it) }

            sincronizarTokenAtual(context, deviceId)
        }
    }

    /** Atualiza o fcmToken do aparelho já conhecido — chamado sempre que o app abre ou o token renova. */
    fun sincronizarTokenAtual(context: Context, deviceId: String) {
        AnonymousAuth.ensureSignedIn { sucesso ->
            if (!sucesso) return@ensureSignedIn
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> gravar(deviceId, token) }
                .addOnFailureListener { Log.e(TAG, "Falha ao obter token FCM", it) }
        }
    }

    fun gravar(deviceId: String, fcmToken: String) {
        FirebaseFirestore.getInstance()
            .collection("dispositivos")
            .document(deviceId)
            .set(
                mapOf(
                    "deviceId" to deviceId,
                    "fcmToken" to fcmToken,
                    "ultimaSincronizacaoEm" to System.currentTimeMillis(),
                    "versaoApp" to BuildConfig.VERSION_NAME
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener { Log.i(TAG, "Dispositivo $deviceId sincronizado.") }
            .addOnFailureListener { Log.e(TAG, "Falha ao gravar dispositivo $deviceId", it) }
    }
}
