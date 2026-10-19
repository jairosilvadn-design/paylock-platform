// Preencha com as credenciais do seu projeto Firebase (Configurações do projeto
// → Seus apps → Web app). São chaves PÚBLICAS do client SDK — a segurança real
// fica nas Firestore Rules e nas regras do Firebase Auth, não no sigilo destes
// valores. Ainda assim, prefira configurar via variável de ambiente da Netlify
// e injetar no build quando possível.
window.PAYLOCK_FIREBASE_CONFIG = {
  apiKey: "SUA_API_KEY",
  authDomain: "SEU_PROJETO.firebaseapp.com",
  projectId: "SEU_PROJETO",
  storageBucket: "SEU_PROJETO.appspot.com",
  messagingSenderId: "SEU_SENDER_ID",
  appId: "SEU_APP_ID",
};
