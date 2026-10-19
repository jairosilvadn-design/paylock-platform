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

## 3. App Android — build automático do APK (sem Android Studio)

Você **não precisa instalar Android Studio nem compilar nada manualmente**.
O workflow [`.github/workflows/build-apk.yml`](../.github/workflows/build-apk.yml)
compila, assina e publica o APK sozinho toda vez que algo em `android/` muda
(ou quando você dispara manualmente). Só precisa configurar 1 segredo
obrigatório antes do primeiro run:

1. Baixe o `google-services.json` do seu app Android no Firebase Console
   (**Project settings → Your apps → Add app → Android**, package name
   `com.paylock.agent`).
2. Converta o arquivo para base64 e salve como segredo do repositório
   (GitHub → aba **Settings** do repo → **Secrets and variables → Actions →
   New repository secret**):
   ```bash
   # Linux/Mac
   base64 -w0 google-services.json
   # cole o resultado no segredo GOOGLE_SERVICES_JSON_BASE64
   ```
3. Dispare o build: aba **Actions** do repositório → workflow
   **"Build & Release Android APK"** → **Run workflow**. (Ou simplesmente dê
   um push mexendo em algo dentro de `android/`.)
4. Quando terminar, abra a aba **Releases** do repositório: lá estará o
   `app-release.apk` pronto para baixar, mais o **checksum** já calculado
   (necessário para o QR Code, ver passo 4 abaixo).

### Sobre a assinatura (keystore) — importante para atualizações futuras

Na **primeira vez** que você rodar o workflow sem configurar um keystore
próprio, ele gera um automaticamente e avisa isso nas notas do Release,
anexando o arquivo `release.jks.b64`. **Antes de instalar esse primeiro APK
em qualquer aparelho de cliente**, salve esse keystore como segredos fixos do
repositório (copiando os valores exatos que aparecem nas notas do Release):

- `PAYLOCK_KEYSTORE_BASE64` (conteúdo do `release.jks.b64` anexado)
- `PAYLOCK_KEYSTORE_PASSWORD`
- `PAYLOCK_KEY_ALIAS`
- `PAYLOCK_KEY_PASSWORD`

Isso garante que **toda build futura use a mesma assinatura** — essencial
porque o Android só aceita atualizar um app Device Owner já instalado se a
nova versão tiver a mesma assinatura da anterior. Se você perder o keystore
depois de já ter instalado o app em aparelhos de clientes, não vai conseguir
atualizar esses apps remotamente (só reinstalando do zero via novo
provisioning).

## 4. Gerar o QR Code de provisioning (Android Enterprise)

Veja o passo a passo detalhado em [`QR_PROVISIONING.md`](QR_PROVISIONING.md).

## 5. Fluxo operacional diário

1. Lojista cadastra o cliente/aparelho no painel (`dashboard.html`).
2. Na entrega do aparelho: cliente assina o Termo de Adesão, loja provisiona
   o app via QR Code com o aparelho recém-formatado.
3. App vira Device Owner → aplica restrições → sincroniza `fcmToken`.
4. Rotina `check-overdue` (de hora em hora) ou botão manual no painel
   dispara bloqueio/desbloqueio.
