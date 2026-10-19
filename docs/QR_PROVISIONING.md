# QR Code de provisioning (Android Enterprise / `afw#setup`)

O Device Owner só pode ser configurado em um aparelho **recém-formatado**
(saído de fábrica ou após um reset), antes de qualquer conta Google ser
adicionada. O fluxo oficial do Android para isso é o **QR Code provisioning**.

> Alternativa mais simples para operação em pequena escala, sem precisar
> gerar QR Code: com o aparelho formatado, toque 6 vezes na tela de boas-vindas
> até aparecer "QR code" **ou** use o comando ADB abaixo (aparelho ainda sem
> conta configurada, com depuração USB temporariamente habilitada só para
> este passo único):
> ```bash
> adb shell dpm set-device-owner com.paylock.agent/.PayLockDeviceAdminReceiver
> ```
> Essa via dispensa QR Code, mas exige USB e não injeta o IMEI/parâmetros
> automaticamente — você precisaria configurar o IMEI manualmente depois pelo
> app. Para operação em volume (várias ativações por dia na loja), o QR Code é
> mais prático.

## 1. Monte o payload JSON do QR Code

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
    "com.paylock.agent/com.paylock.agent.PayLockDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM":
    "CHECKSUM_SHA256_DO_APK_EM_BASE64URL",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
    "https://SEU-DOMINIO/releases/app-release.apk",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": false,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {
    "imei": "IMEI_DO_APARELHO",
    "clienteId": "ID_DO_CLIENTE_NO_FIRESTORE"
  }
}
```

- `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM`: já vem pronto nas notas do
  Release gerado pelo workflow (ver [`DEPLOY.md`](DEPLOY.md), seção 3) — é
  calculado automaticamente a partir do mesmo APK publicado.
- `PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`: a URL direta do
  arquivo `.apk` anexado àquele Release (clique com o botão direito no
  arquivo na aba **Releases** do GitHub → "Copy link address"). Essa URL
  precisa ficar acessível publicamente durante o provisioning — um asset de
  Release do GitHub já atende isso.
- `PROVISIONING_ADMIN_EXTRAS_BUNDLE`: dados extras entregues ao app logo após
  o provisionamento, usados por `onProfileProvisioningComplete` para chamar
  `DeviceIdentity.salvarImei(...)`.

## 2. Gere a imagem do QR Code

Qualquer gerador de QR Code a partir do JSON acima serve, pois o conteúdo do
QR Code é apenas o texto do JSON (compactado, sem quebras de linha).
Exemplo em Node, usando a lib `qrcode` (pode rodar isso como um script local,
não faz parte do site publicado):

```bash
npm install -g qrcode
echo '{"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":"com.paylock.agent/com.paylock.agent.PayLockDeviceAdminReceiver", ...}' \
  | qrcode -o provisioning-qrcode.png
```

Ou gere programaticamente dentro do próprio painel (ex: endpoint
`/.netlify/functions/gerar-qrcode?clienteId=...` usando a lib `qrcode` do
Node para devolver o PNG) — assim cada aparelho recebe um QR Code com o
`imei`/`clienteId` corretos embutidos, em vez de um QR Code fixo e genérico.

## 3. Ativar no aparelho

1. Formate/restaure o aparelho de fábrica (ou use um aparelho novo lacrado).
2. Na tela de boas-vindas do Android (antes de conectar wifi), toque
   **6 vezes** em qualquer ponto da tela para abrir o leitor de QR Code do
   provisioning.
3. Conecte a uma rede wifi quando solicitado.
4. Aponte a câmera para o QR Code gerado no passo 2.
5. O Android baixa o APK da URL informada, valida o checksum, instala o app e
   o define como **Device Owner**, chamando automaticamente
   `onProfileProvisioningComplete` — que aplica as restrições
   (`DevicePolicyEnforcer.aplicarPoliticasIniciais()`).
6. Verifique no painel que o aparelho aparece como sincronizado (campo
   `dispositivos/{imei}.fcmToken` preenchido).

## Observações importantes

- **Sempre** obtenha a assinatura do Termo de Adesão **antes** deste passo —
  o provisioning torna a remoção do app extremamente difícil por design (essa
  é a garantia da dívida), então ativá-lo sem consentimento do cliente seria
  controle não autorizado do aparelho de terceiro.
- Teste sempre primeiro em um aparelho de teste seu, não no aparelho real do
  primeiro cliente — reverter um Device Owner mal configurado normalmente
  exige reset de fábrica (ou, antes de aplicar as restrições, o comando
  `dpm remove-active-admin`).
