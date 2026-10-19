# PayLock Platform

Plataforma SaaS simplificada de gestão de crédito com garantia em celular (modelo
"financiamento de aparelho com bloqueio remoto por inadimplência"), inspirada em
soluções como PayJoy e MDMs corporativos (Android Enterprise).

> ⚠️ **Uso legal e consentido.** Esta plataforma só deve ser usada com clientes que
> assinaram um **Termo de Adesão** (ver [`docs/TERMO_ADESAO.md`](docs/TERMO_ADESAO.md))
> informando, de forma clara, que o aparelho financiado permanecerá sob controle
> remoto (MDM/Device Owner) até a quitação da dívida, e que o cliente pode, a
> qualquer momento, quitar/renegociar para recuperar o uso pleno do aparelho.
> Nunca instale o agente em um aparelho sem esse consentimento explícito — isso
> configuraria monitoramento/controle não autorizado de terceiros.

## Stack (somente estas três ferramentas)

1. **GitHub** — código-fonte (painel web + app Android) e CI/CD. O APK do app
   Android é **compilado, assinado e publicado automaticamente** pelo GitHub
   Actions ([`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml))
   — você não precisa instalar Android Studio nem compilar nada manualmente,
   só baixar o `.apk` pronto na aba **Releases** (ver `docs/DEPLOY.md`).
2. **Netlify** — hospedagem do painel web (estático) + Netlify Functions
   (serverless) para os webhooks/backend.
3. **Firebase**
   - **Firestore** — clientes, dispositivos (IMEI), parcelas, valores, chave PIX.
   - **Firebase Authentication** — login dos lojistas/administradores.
   - **Firebase Cloud Messaging (FCM)** — comandos de bloqueio/desbloqueio e
     sincronização em tempo real com o app Android.

## Estrutura do repositório

```
paylock-platform/
├── web/                    # Painel Admin (estático, hospedado na Netlify)
│   ├── index.html          # Login
│   ├── dashboard.html      # Cadastro de clientes/aparelhos + ações
│   └── assets/
│       ├── firebase-config.js
│       ├── app.js
│       └── styles.css
├── netlify/
│   └── functions/
│       ├── send-command.js     # Envia comando Bloquear/Desbloquear via FCM
│       └── check-overdue.js    # Rotina agendada: bloqueia vencidos
├── firebase/
│   ├── firestore.rules
│   └── firebase.json
├── android/                 # App Agente (Kotlin, Android Enterprise Device Owner)
│   └── app/src/main/...
├── docs/
│   ├── DEPLOY.md            # Passo a passo GitHub → Netlify → Firebase
│   ├── QR_PROVISIONING.md   # Geração do QR Code afw#setup
│   └── TERMO_ADESAO.md      # Modelo de termo de consentimento do cliente
├── netlify.toml
└── .env.example
```

## Fluxo resumido

1. Lojista cadastra o cliente no painel (nome, IMEI, valor da parcela, chave PIX,
   vencimento) → grava em `clientes/{clienteId}` no Firestore.
2. No momento da entrega do aparelho, o cliente assina o Termo de Adesão e o
   lojista provisiona o app via QR Code (Android Enterprise / `afw#setup`),
   tornando o app **Device Owner**.
3. O app Android assina o token FCM do aparelho e grava em
   `dispositivos/{imei}.fcmToken`.
4. Ao vencer a parcela (rotina `check-overdue`, agendada) ou por ação manual no
   painel, uma Netlify Function envia uma mensagem FCM `data` para o token do
   aparelho com `{ comando: "BLOQUEAR", pixKey, valor, vencimento }`.
5. O app recebe a mensagem (mesmo em background, via `FirebaseMessagingService`),
   ativa o **Kiosk Mode** (`startLockTask` + `setLockTaskPackages`), permitindo
   apenas WhatsApp, apps de banco e a tela própria do PayLock com a chave PIX.
6. Após o pagamento confirmado no painel, o status muda para `EM_DIA` e uma nova
   mensagem FCM `DESBLOQUEAR` libera o aparelho (`stopLockTask`).

Veja o passo a passo completo de deploy em [`docs/DEPLOY.md`](docs/DEPLOY.md).
