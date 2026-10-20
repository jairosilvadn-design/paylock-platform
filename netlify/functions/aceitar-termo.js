// POST /.netlify/functions/aceitar-termo
// Body: { clienteId: string }
// Endpoint PÚBLICO (sem login) — é o próprio cliente quem chama isso, ao
// tocar em "Aceitar e baixar o app" na página web/aceite.html. Grava o
// aceite com data/hora e IP para fins de auditoria (ver docs/TERMO_ADESAO.md).
const { db } = require("./_firebaseAdmin");

exports.handler = async (event) => {
  if (event.httpMethod !== "POST") {
    return { statusCode: 405, body: JSON.stringify({ error: "Método não permitido" }) };
  }

  const { clienteId } = JSON.parse(event.body || "{}");
  if (!clienteId) {
    return { statusCode: 400, body: JSON.stringify({ error: "clienteId é obrigatório" }) };
  }

  const ref = db.collection("clientes").doc(clienteId);
  const snap = await ref.get();
  if (!snap.exists) {
    return { statusCode: 404, body: JSON.stringify({ error: "Cliente não encontrado" }) };
  }

  const ip =
    event.headers["x-nf-client-connection-ip"] ||
    event.headers["x-forwarded-for"]?.split(",")[0]?.trim() ||
    "desconhecido";

  await ref.set(
    {
      termo: {
        aceito: true,
        versao: "1.0",
        dataHoraISO: new Date().toISOString(),
        ip,
        userAgent: event.headers["user-agent"] || "desconhecido",
      },
    },
    { merge: true }
  );

  return { statusCode: 200, body: JSON.stringify({ ok: true }) };
};
