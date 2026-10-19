# Passo a passo de deploy

## 1. Firebase

1. Crie um projeto em https://console.firebase.google.com.
2. **Authentication** → ative o provedor **E-mail/senha** → crie um usuário
   para cada lojista/admin.
3. **Firestore Database** → crie em modo produção, região `southamerica-east1`
   (São Paulo). Depois publique as regras:
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add            # selecione o projeto
   firebase deploy --only firestore:rules,firestore:indexes --config firebase/firebase.json
   ```
4. **Project settings → General → Your apps → Web app (</>) ** — copie o
   objeto `firebaseConfig` e cole em `web/assets/firebase-config.js`.
5. **Project settings → Service accounts → Generate new private key** — baixe
   o JSON. Você vai usar `project_id`, `client_email` e `private_key` nas
   variáveis de ambiente da Netlify (passo 2.4).
6. **Cloud Messaging** → não precisa configurar nada manualmente: o
   `google-services.json` (passo 3) já traz o necessário para o app Android.

## 2. GitHub + Netlify (Painel Web)

1. Crie o repositório no GitHub e faça push deste projeto.
2. Em https://app.netlify.com → **Add new site → Import an existing project**
   → selecione o repositório.
3. Build settings (já definidos em `netlify.toml`):
   - Publish directory: `web`
   - Functions directory: `netlify/functions`
4. **Site settings → Environment variables** → adicione:
   - `FIREBASE_PROJECT_ID`
   - `FIREBASE_CLIENT_EMAIL`
   - `FIREBASE_PRIVATE_KEY` (cole com `\n` literais, entre aspas)
5. Dispare o deploy. O painel fica disponível em `https://SEU-SITE.netlify.app`.
6. **Scheduled Functions** (`check-overdue`, cron por hora) exigem plano pago
   da Netlify. No plano gratuito, substitua por um cron externo gratuito:
   - Crie um GitHub Actions workflow (`.github/workflows/check-overdue.yml`)
     rodando a cada hora e fazendo
     `curl -X POST https://SEU-SITE.netlify.app/.netlify/functions/check-overdue`;
     ou use https://cron-job.org apontando para a mesma URL.

## 3. App Android (gerar o APK)

1. Baixe o `google-services.json` do seu app Android no Firebase Console
   (**Project settings → Your apps → Add app → Android**, package name
   `com.paylock.agent`) e salve em `android/app/google-services.json`
   (arquivo ignorado pelo git — nunca commitar).
2. Abra a pasta `android/` no Android Studio.
3. **Build → Generate Signed Bundle / APK** → APK → crie/selecione um
   keystore → gere o APK **release**.
   - Guarde o keystore com segurança: você precisará da mesma assinatura
     para futuras atualizações do app, já que ele roda como Device Owner.
4. Hospede o APK gerado em algum lugar acessível por HTTPS e publicamente
   baixável durante o provisionamento — por exemplo, como um **GitHub
   Release** deste mesmo repositório (Releases → Draft a new release →
   anexe o `.apk`), ou em um bucket do Firebase Hosting/Storage.
5. Calcule o checksum SHA-256 do APK (exigido pelo provisioning):
   ```bash
   # Linux/Mac
   shasum -a 256 app-release.apk | cut -d' ' -f1 | xxd -r -p | base64 | tr '/+' '_-' | tr -d '='
   ```

## 4. Gerar o QR Code de provisioning (Android Enterprise)

Veja o passo a passo detalhado em [`QR_PROVISIONING.md`](QR_PROVISIONING.md).

## 5. Fluxo operacional diário

1. Lojista cadastra o cliente/aparelho no painel (`dashboard.html`).
2. Na entrega do aparelho: cliente assina o Termo de Adesão, loja provisiona
   o app via QR Code com o aparelho recém-formatado.
3. App vira Device Owner → aplica restrições → sincroniza `fcmToken`.
4. Rotina `check-overdue` (de hora em hora) ou botão manual no painel
   dispara bloqueio/desbloqueio.
