// Inicialização compartilhada do Firebase Admin SDK para as Netlify Functions.
// Credenciais vêm de variáveis de ambiente da Netlify (nunca commitadas):
//   FIREBASE_PROJECT_ID
//   FIREBASE_CLIENT_EMAIL
//   FIREBASE_PRIVATE_KEY   (com \n escapado, ver .env.example)
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      privateKey: (process.env.FIREBASE_PRIVATE_KEY || "").replace(/\\n/g, "\n"),
    }),
  });
}

async function verifyIdToken(authHeader) {
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    const err = new Error("Token de autenticação ausente.");
    err.statusCode = 401;
    throw err;
  }
  const idToken = authHeader.slice("Bearer ".length);
  return admin.auth().verifyIdToken(idToken);
}

module.exports = { admin, db: admin.firestore(), messaging: admin.messaging(), verifyIdToken };
