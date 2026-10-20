package com.paylock.agent

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * O app Android nunca deve logar com um usuário de lojista (isso é só pro
 * painel web). Para gravar dados próprios no Firestore (fcmToken, estado do
 * kiosk), ele usa autenticação anônima do Firebase — suficiente pra
 * `request.auth != null` nas regras do Firestore, sem expor nenhuma
 * credencial de loja no aparelho do cliente.
 */
object AnonymousAuth {
    private const val TAG = "AnonymousAuth"

    fun ensureSignedIn(onReady: (sucesso: Boolean) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady(true)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(true) }
            .addOnFailureListener {
                Log.e(TAG, "Falha no login anônimo", it)
                onReady(false)
            }
    }
}
