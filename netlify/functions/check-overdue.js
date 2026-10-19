// Rotina automática de trava por vencimento.
// Configurada no netlify.toml como "scheduled function" (cron), ex: a cada hora.
// Para cada cliente com vencimento <= hoje e status != BLOQUEADO, envia BLOQUEAR.
// Reaproveita a mesma lógica de send-command, mas roda com credencial de
// servidor (sem Authorization de usuário, pois é disparada pelo cron da Netlify).
const { db, messaging } = require("./_firebaseAdmin");

exports.handler = async () => {
  const hojeISO = new Date().toISOString().slice(0, 10); // YYYY-MM-DD

  const snap = await db
    .collection("clientes")
    .where("status", "in", ["EM_DIA", "ATRASADO"])
    .get();

  const resultados = [];

  for (const doc of snap.docs) {
    const cliente = doc.data();
    const imei = doc.id;

    if (!cliente.vencimento || cliente.vencimento > hojeISO) continue; // ainda não venceu
    if (!(cliente.termo && cliente.termo.aceito)) continue; // sem termo assinado, não bloqueia

    const dispositivoSnap = await db.collection("dispositivos").doc(imei).get();
    const fcmToken = dispositivoSnap.exists ? dispositivoSnap.data().fcmToken : null;
    if (!fcmToken) {
      resultados.push({ imei, ok: false, motivo: "sem fcmToken" });
      continue;
    }

    try {
      await messaging.send({
        token: fcmToken,
        data: {
          comando: "BLOQUEAR",
          pixKey: cliente.pixKey || "",
          valorPendente: String(cliente.valorParcela || 0),
          vencimento: cliente.vencimento || "",
        },
        android: { priority: "high" },
      });
      await doc.ref.update({ status: "BLOQUEADO" });
      await db.collection("dispositivos").doc(imei).update({ kioskAtivo: true, ultimoComandoEm: new Date().toISOString() });
      resultados.push({ imei, ok: true });
    } catch (err) {
      resultados.push({ imei, ok: false, motivo: err.message });
    }
  }

  return { statusCode: 200, body: JSON.stringify({ processados: resultados.length, resultados }) };
};
