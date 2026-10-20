// GET /.netlify/functions/termo-info?clienteId=IMEI
// Endpoint PÚBLICO (sem login) usado pela página de aceite (web/aceite.html).
// Devolve só o mínimo necessário para mostrar o termo ao cliente — nunca
// dados sensíveis de outros clientes, nem a chave PIX antes do aceite.
const { db } = require("./_firebaseAdmin");

exports.handler = async (event) => {
  const clienteId = event.queryStringParameters?.clienteId;
  if (!clienteId) {
    return { statusCode: 400, body: JSON.stringify({ error: "clienteId é obrigatório" }) };
  }

  const snap = await db.collection("clientes").doc(clienteId).get();
  if (!snap.exists) {
    return { statusCode: 404, body: JSON.stringify({ error: "Cliente não encontrado" }) };
  }

  const c = snap.data();
  return {
    statusCode: 200,
    headers: { "Cache-Control": "no-store" },
    body: JSON.stringify({
      nome: c.nome || "",
      valorParcela: c.valorParcela || 0,
      vencimento: c.vencimento || "",
      termoAceito: !!(c.termo && c.termo.aceito),
      termoDataHoraISO: c.termo?.dataHoraISO || null,
    }),
  };
};
