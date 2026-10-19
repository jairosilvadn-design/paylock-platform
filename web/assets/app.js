firebase.initializeApp(window.PAYLOCK_FIREBASE_CONFIG);
const auth = firebase.auth();
const db = firebase.firestore();

auth.onAuthStateChanged((user) => {
  if (!user) {
    window.location.href = "index.html";
    return;
  }
  document.getElementById("user-email").textContent = user.email;
  watchClientes();
});

document.getElementById("logout-btn").addEventListener("click", () => auth.signOut());

// ---------------------------------------------------------------------------
// Cadastro
// ---------------------------------------------------------------------------
document.getElementById("cliente-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const imei = document.getElementById("imei").value.trim();
  const termoAceito = document.getElementById("termoAceito").checked;

  if (!termoAceito) {
    alert("O cliente precisa ter assinado o Termo de Adesão antes do cadastro.");
    return;
  }

  const data = {
    nome: document.getElementById("nome").value.trim(),
    cpf: document.getElementById("cpf").value.trim(),
    imei,
    modelo: document.getElementById("modelo").value.trim(),
    valorParcela: parseFloat(document.getElementById("valor").value),
    vencimento: document.getElementById("vencimento").value, // YYYY-MM-DD
    pixKey: document.getElementById("pix").value.trim(),
    status: "EM_DIA", // EM_DIA | ATRASADO | BLOQUEADO
    termo: {
      aceito: true,
      versao: "1.0",
      dataHoraISO: new Date().toISOString(),
    },
    criadoEm: firebase.firestore.FieldValue.serverTimestamp(),
    criadoPor: auth.currentUser.uid,
  };

  await db.collection("clientes").doc(imei).set(data, { merge: true });

  // Garante que o doc de dispositivo exista (o app Android grava o fcmToken aqui
  // na primeira sincronização, após o provisionamento via QR Code).
  await db.collection("dispositivos").doc(imei).set(
    { imei, clienteId: imei, fcmToken: null, kioskAtivo: false },
    { merge: true }
  );

  e.target.reset();
});

// ---------------------------------------------------------------------------
// Listagem em tempo real
// ---------------------------------------------------------------------------
function watchClientes() {
  db.collection("clientes")
    .orderBy("criadoEm", "desc")
    .onSnapshot((snapshot) => {
      const tbody = document.getElementById("clientes-tbody");
      tbody.innerHTML = "";
      snapshot.forEach((doc) => {
        const c = doc.data();
        const tr = document.createElement("tr");
        tr.innerHTML = `
          <td>${escapeHtml(c.nome)}</td>
          <td>${escapeHtml(c.imei)}</td>
          <td>R$ ${Number(c.valorParcela || 0).toFixed(2)}</td>
          <td>${escapeHtml(c.vencimento || "-")}</td>
          <td><span class="badge badge-${(c.status || "").toLowerCase()}">${escapeHtml(c.status)}</span></td>
          <td>
            <button class="btn-bloquear" data-imei="${c.imei}">Bloquear</button>
            <button class="btn-desbloquear" data-imei="${c.imei}">Desbloquear</button>
          </td>
        `;
        tbody.appendChild(tr);
      });

      tbody.querySelectorAll(".btn-bloquear").forEach((btn) =>
        btn.addEventListener("click", () => enviarComando(btn.dataset.imei, "BLOQUEAR"))
      );
      tbody.querySelectorAll(".btn-desbloquear").forEach((btn) =>
        btn.addEventListener("click", () => enviarComando(btn.dataset.imei, "DESBLOQUEAR"))
      );
    });
}

// ---------------------------------------------------------------------------
// Ação manual de bloqueio/desbloqueio: chama a Netlify Function, que usa o
// Firebase Admin SDK (server-side, com credencial de serviço) para enviar a
// mensagem FCM e atualizar o Firestore. O painel nunca fala direto com o FCM.
// ---------------------------------------------------------------------------
async function enviarComando(imei, comando) {
  const idToken = await auth.currentUser.getIdToken();
  const res = await fetch("/.netlify/functions/send-command", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`,
    },
    body: JSON.stringify({ imei, comando }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    alert("Erro ao enviar comando: " + (body.error || res.status));
  }
}

function escapeHtml(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]));
}
