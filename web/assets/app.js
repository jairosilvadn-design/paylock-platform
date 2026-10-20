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
async function configurarPendente(deviceId) {
  configurandoPendente = true;
  document.getElementById("imei").value = deviceId;
  document.getElementById("imei").readOnly = true;
  document.getElementById("termoAceito").checked = true;
  document.getElementById("form-titulo").textContent = `Completar cadastro — aparelho ${deviceId}`;
  document.getElementById("btnCancelarEdicao").hidden = false;
  document.getElementById("cliente-form").scrollIntoView({ behavior: "smooth" });

  // Modelo/marca/versão do Android já vieram sozinhos do app — preenche
  // automaticamente, o lojista não precisa digitar nada disso.
  const info = await obterInfoDispositivo(deviceId);
  if (info) {
    document.getElementById("modelo").value = formatarInfoDispositivo(info);
  }
}

async function obterInfoDispositivo(deviceId) {
  const snap = await db.collection("dispositivos").doc(deviceId).get();
  return snap.exists ? snap.data() : null;
}

function formatarInfoDispositivo(info) {
  const partes = [info.marca, info.modelo].filter(Boolean).join(" ");
  return info.versaoAndroid ? `${partes} (Android ${info.versaoAndroid})` : partes;
}

// ---------------------------------------------------------------------------
// Listagem em tempo real
// ---------------------------------------------------------------------------
function watchClientes() {
  db.collection("clientes")
    .orderBy("criadoEm", "desc")
    .onSnapshot(async (snapshot) => {
      const pendentesTbody = document.getElementById("pendentes-tbody");
      const clientesTbody = document.getElementById("clientes-tbody");
      pendentesTbody.innerHTML = "";
      clientesTbody.innerHTML = "";

      for (const doc of snapshot.docs) {
        const c = doc.data();

        if (c.status === "PENDENTE_CONFIGURACAO") {
          const dataAceite = c.termo?.dataHoraISO
            ? new Date(c.termo.dataHoraISO).toLocaleString("pt-BR")
            : "-";
          const info = await obterInfoDispositivo(doc.id);
          const tr = document.createElement("tr");
          tr.innerHTML = `
            <td><code>${escapeHtml(doc.id)}</code></td>
            <td>${escapeHtml(info ? formatarInfoDispositivo(info) : "sincronizando...")}</td>
            <td>${escapeHtml(dataAceite)}</td>
            <td><button class="btn-configurar" data-id="${doc.id}">Configurar</button></td>
          `;
          pendentesTbody.appendChild(tr);
          continue;
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
          </td>
        `;
        clientesTbody.appendChild(tr);
      }

      pendentesTbody.querySelectorAll(".btn-configurar").forEach((btn) =>
        btn.addEventListener("click", () => configurarPendente(btn.dataset.id))
      );
      clientesTbody.querySelectorAll(".btn-bloquear").forEach((btn) =>
        btn.addEventListener("click", () => enviarComando(btn.dataset.imei, "BLOQUEAR"))
      );
      clientesTbody.querySelectorAll(".btn-desbloquear").forEach((btn) =>
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
