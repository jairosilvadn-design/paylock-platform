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
// Cadastro / completar configuração de aparelho pendente
// ---------------------------------------------------------------------------
let configurandoPendente = false;

document.getElementById("cliente-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const imei = document.getElementById("imei").value.trim();
  const termoAceitoMarcado = document.getElementById("termoAceito").checked;

  if (!configurandoPendente && !termoAceitoMarcado) {
    alert("O cliente precisa ter assinado o Termo de Adesão antes do cadastro.");
    return;
  }

  const data = {
    nome: document.getElementById("nome").value.trim(),
    cpf: document.getElementById("cpf").value.trim(),
    imei,
    modelo: document.getElementById("modelo").value.trim(),
    valorParcela: parseFloat(document.getElementById("valor").value),
    quantidadeParcelas: parseInt(document.getElementById("qtdParcelas").value, 10) || null,
    vencimento: document.getElementById("vencimento").value, // YYYY-MM-DD
    horarioAlerta: document.getElementById("horarioAlerta").value || "09:00",
    pixKey: document.getElementById("pix").value.trim(),
    status: "EM_DIA", // PENDENTE_CONFIGURACAO | EM_DIA | ATRASADO | BLOQUEADO
    criadoEm: firebase.firestore.FieldValue.serverTimestamp(),
    criadoPor: auth.currentUser.uid,
  };

  // Se o próprio aparelho já registrou o aceite do termo (fluxo automático),
  // não sobrescreve isso — só preenche se ainda não tiver aceite registrado.
  if (!configurandoPendente) {
    data.termo = {
      aceito: true,
      versao: "1.0",
      dataHoraISO: new Date().toISOString(),
    };
  }

  await db.collection("clientes").doc(imei).set(data, { merge: true });

  // Garante que o doc de dispositivo exista (o próprio app também grava o
  // fcmToken aqui na primeira sincronização).
  await db.collection("dispositivos").doc(imei).set(
    { deviceId: imei, fcmToken: null, kioskAtivo: false },
    { merge: true }
  );

  cancelarEdicao();
});

document.getElementById("btnCancelarEdicao").addEventListener("click", cancelarEdicao);

function cancelarEdicao() {
  configurandoPendente = false;
  document.getElementById("cliente-form").reset();
  document.getElementById("imei").readOnly = false;
  document.getElementById("termoAceito").required = false;
  document.getElementById("form-titulo").textContent = "Cadastrar cliente / aparelho";
  document.getElementById("btnCancelarEdicao").hidden = true;
}

/** Abre o formulário pré-preenchido pra completar um aparelho pendente. */
function configurarPendente(deviceId, termoDataHoraISO) {
  configurandoPendente = true;
  document.getElementById("imei").value = deviceId;
  document.getElementById("imei").readOnly = true;
  document.getElementById("termoAceito").checked = true;
  document.getElementById("form-titulo").textContent = `Completar cadastro — aparelho ${deviceId}`;
  document.getElementById("btnCancelarEdicao").hidden = false;
  document.getElementById("cliente-form").scrollIntoView({ behavior: "smooth" });
}

// ---------------------------------------------------------------------------
// Listagem em tempo real
// ---------------------------------------------------------------------------
function watchClientes() {
  db.collection("clientes")
    .orderBy("criadoEm", "desc")
    .onSnapshot((snapshot) => {
      const pendentesTbody = document.getElementById("pendentes-tbody");
      const clientesTbody = document.getElementById("clientes-tbody");
      pendentesTbody.innerHTML = "";
      clientesTbody.innerHTML = "";

      snapshot.forEach((doc) => {
        const c = doc.data();

        if (c.status === "PENDENTE_CONFIGURACAO") {
          const dataAceite = c.termo?.dataHoraISO
            ? new Date(c.termo.dataHoraISO).toLocaleString("pt-BR")
            : "-";
          const tr = document.createElement("tr");
          tr.innerHTML = `
            <td><code>${escapeHtml(doc.id)}</code></td>
            <td>${escapeHtml(dataAceite)}</td>
            <td><button class="btn-configurar" data-id="${doc.id}">Configurar</button></td>
          `;
          pendentesTbody.appendChild(tr);
          return;
        }

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
            <button class="btn-link-aceite" data-imei="${c.imei}">Copiar link de aceite</button>
          </td>
        `;
        clientesTbody.appendChild(tr);
      });

      pendentesTbody.querySelectorAll(".btn-configurar").forEach((btn) =>
        btn.addEventListener("click", () => configurarPendente(btn.dataset.id))
      );
      clientesTbody.querySelectorAll(".btn-bloquear").forEach((btn) =>
        btn.addEventListener("click", () => enviarComando(btn.dataset.imei, "BLOQUEAR"))
      );
      clientesTbody.querySelectorAll(".btn-desbloquear").forEach((btn) =>
        btn.addEventListener("click", () => enviarComando(btn.dataset.imei, "DESBLOQUEAR"))
      );
      clientesTbody.querySelectorAll(".btn-link-aceite").forEach((btn) =>
        btn.addEventListener("click", () => copiarLinkAceite(btn.dataset.imei))
      );
    });
}

// Link individual (por cliente) para a página pública de aceite do Termo de
// Adesão (web/aceite.html) — opcional, útil quando você quer mostrar os
// dados do cliente (valor, vencimento) ANTES do download. No fluxo
// automático (aparelho se auto-registra), não é necessário usar isso: basta
// mandar o link direto do APK.
function copiarLinkAceite(imei) {
  const url = `${window.location.origin}/aceite.html?cliente=${encodeURIComponent(imei)}`;
  navigator.clipboard.writeText(url).then(
    () => alert("Link copiado:\n" + url),
    () => prompt("Copie o link abaixo:", url)
  );
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
