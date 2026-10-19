package com.paylock.agent

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log

/**
 * Aplica, via DevicePolicyManager / UserManager, as restrições previstas no
 * Termo de Adesão enquanto o aparelho estiver em financiamento:
 *  - bloquear reset de fábrica;
 *  - bloquear opções de desenvolvedor / depuração USB;
 *  - impedir desinstalação do próprio app;
 *  - impedir a troca/remoção da conta Device Owner.
 *
 * Todas as chamadas abaixo só funcionam se o app for Device Owner — não há
 * nenhum uso de API privada/não documentada aqui, apenas o conjunto padrão
 * de `UserManager.DISALLOW_*` e `DevicePolicyManager` do Android Enterprise.
 */
class DevicePolicyEnforcer(private val context: Context) {

    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = PayLockDeviceAdminReceiver.getComponentName(context)

    /** Chamado uma única vez, logo após o provisionamento (Device Owner). */
    fun aplicarPoliticasIniciais() {
        if (!PayLockDeviceAdminReceiver.isDeviceOwner(context)) {
            Log.w(TAG, "App não é Device Owner — políticas de restrição não serão aplicadas.")
            return
        }

        // Impede reset de fábrica pelas Configurações e pela combinação de botões físicos.
        dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)

        // Impede ativar Opções do Desenvolvedor / Depuração USB (bloqueia o
        // vetor mais comum usado por assistências técnicas para remover MDMs).
        dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)

        // Impede adicionar/remover contas de usuário no aparelho.
        dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.addUserRestriction(admin, UserManager.DISALLOW_REMOVE_USER)

        // Impede desligar o próprio app de dentro de Configurações > Apps.
        dpm.setUninstallBlocked(admin, context.packageName, true)

        // Define quais pacotes podem rodar em Lock Task Mode (Kiosk). A lista
        // final de apps permitidos (WhatsApp + bancos) é resolvida
        // dinamicamente em KioskModeManager, pois varia por aparelho.
        Log.i(TAG, "Políticas de restrição aplicadas com sucesso.")
    }

    /**
     * Libera as restrições quando o financiamento é quitado (comando
     * DESBLOQUEAR + saldo zerado). Mantém o Device Admin ativo (pois ainda é
     * o app instalado), mas devolve o controle total do aparelho ao cliente.
     * Chame isto a partir do fluxo de "quitação" do painel, não do simples
     * "desbloqueio por pagamento da parcela do mês".
     */
    fun liberarRestricoesPorQuitacao() {
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_REMOVE_USER)
        dpm.setUninstallBlocked(admin, context.packageName, false)
        Log.i(TAG, "Restrições liberadas (financiamento quitado).")
    }

    companion object {
        private const val TAG = "DevicePolicyEnforcer"
    }
}
