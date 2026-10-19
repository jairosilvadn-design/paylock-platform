// POST /.netlify/functions/send-command
// Body: { imei: string, comando: "BLOQUEAR" | "DESBLOQUEAR" }
// Header: Authorization: Bearer <Firebase ID token do lojista logado>
//
// Só dispara o comando se o cliente tiver assinado o Termo de Adesão
// (clientes/{imei}.termo.aceito === true). Atualiza o status no Firestore e
// envia uma mensagem FCM "data-only" (sem notification) para o app Android,
// que decide o que fazer com base no campo "comando".
const { db, messaging, verifyIdToken } = require("./_firebaseAdmin");

exports.handler = async (event) => {
  if (event.httpMethod !== "POST") {
    return { statusCode: 405, body: JSON.stringify({ error: "Método não permitido" }) };
  }

  try {
    await verifyIdToken(event.headers.authorization || event.headers.Authorization);

    const { imei, comando } = JSON.parse(event.body || "{}");
    if (!imei || !["BLOQUEAR", "DESBLOQUEAR"].includes(comando)) {
      return { statusCode: 400, body: JSON.stringify({ error: "imei e comando (BLOQUEAR|DESBLOQUEAR) são obrigatórios" }) };
    }

    const clienteRef = db.collection("clientes").doc(imei);
    const dispositivoRef = db.collection("dispositivos").doc(imei);

    const [clienteSnap, dispositivoSnap] = await Promise.all([clienteRef.get(), dispositivoRef.get()]);

    if (!clienteSnap.exists) {
      return { statusCode: 404, body: JSON.stringify({ error: "Cliente não encontrado" }) };
    }
    const cliente = clienteSnap.data();

    if (comando === "BLOQUEAR" && !(cliente.termo && cliente.termo.aceito)) {
      return { statusCode: 412, body: JSON.stringify({ error: "Cliente sem Termo de Adesão aceito. Bloqueio não autorizado." }) };
    }

    const fcmToken = dispositivoSnap.exists ? dispositivoSnap.data().fcmToken : null;
    if (!fcmToken) {
      return { statusCode: 409, body: JSON.stringify({ error: "Aparelho ainda não sincronizado (sem fcmToken). Verifique o provisionamento." }) };
    }

    await messaging.send({
      token: fcmToken,
      data: {
        comando,
        pixKey: cliente.pixKey || "",
        valorPendente: String(cliente.valorParcela || 0),
        vencimento: cliente.vencimento || "",
      },
      android: { priority: "high" },
    });

    await clienteRef.update({ status: comando === "BLOQUEAR" ? "BLOQUEADO" : "EM_DIA" });
    await dispositivoRef.update({ kioskAtivo: comando === "BLOQUEAR", ultimoComandoEm: new Date().toISOString() });

    return { statusCode: 200, body: JSON.stringify({ ok: true }) };
  } catch (err) {
    const statusCode = err.statusCode || 500;
    return { statusCode, body: JSON.stringify({ error: err.message }) };
  }
};
