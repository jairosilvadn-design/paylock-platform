# Termo de Adesão — Financiamento de Aparelho com Garantia Eletrônica

> Este é um **modelo** de termo. Tenha um advogado revisando antes de usar em
> produção — regras de proteção ao consumidor (CDC), LGPD e normas locais variam.
> O texto deve ser exibido e assinado (assinatura eletrônica + registro de IP/hora)
> **antes** da ativação do app no aparelho do cliente, e uma cópia deve ficar
> disponível para o cliente baixar a qualquer momento no painel/app.

---

**TERMO DE ADESÃO AO PROGRAMA DE FINANCIAMENTO DE APARELHO CELULAR COM GARANTIA
ELETRÔNICA (PAYLOCK)**

Pelo presente termo, eu, **{{NOME_CLIENTE}}**, CPF **{{CPF_CLIENTE}}**, declaro
estar ciente e de acordo com as seguintes condições relativas à aquisição do
aparelho **{{MODELO_APARELHO}}** (IMEI **{{IMEI}}**) por meio de crediário/
financiamento junto a **{{NOME_LOJISTA}}**:

1. **Garantia eletrônica.** Enquanto houver saldo devedor, o aparelho terá
   instalado um aplicativo de administração remota ("Agente PayLock") com
   permissões de **Proprietário do Dispositivo (Device Owner)**, que permite à
   loja credora: (a) bloquear o uso do aparelho em caso de atraso no pagamento,
   restringindo-o a um modo restrito (Kiosk) que mantém disponíveis apenas
   WhatsApp, aplicativos bancários e a tela de pagamento do PayLock; e (b)
   restaurar o uso normal automaticamente após a confirmação do pagamento.
2. **Não é monitoramento.** O Agente PayLock **não** acessa mensagens, fotos,
   contatos, localização ou qualquer conteúdo pessoal do aparelho. Suas únicas
   funções são: aplicar/remover o modo restrito e exibir os dados de pagamento
   pendente (valor e chave PIX).
3. **Impossibilidade de remoção durante o financiamento.** Por se tratar de
   garantia da dívida, o cliente concorda que, enquanto houver saldo devedor,
   não poderá desinstalar o Agente, restaurar o aparelho de fábrica, nem
   desativar as permissões administrativas concedidas, sob pena de
   caracterizar tentativa de fraude contra a garantia, podendo a loja vencer
   antecipadamente a dívida e adotar as medidas de cobrança cabíveis.
4. **Quitação e liberação.** Ao quitar integralmente o valor financiado, o
   cliente tem direito à remoção **imediata e gratuita** das restrições e, se
   desejar, do próprio aplicativo, mediante solicitação pelo painel ou canais
   de atendimento informados abaixo.
5. **Canais de atendimento.** Em caso de bloqueio indevido, erro no valor ou
   dúvidas, o cliente pode contatar: **{{CANAL_ATENDIMENTO}}**.
6. **Dados pessoais (LGPD).** Os dados cadastrais (nome, CPF, IMEI, chave PIX,
   histórico de pagamentos) são tratados exclusivamente para fins de gestão do
   financiamento, conforme a Lei Geral de Proteção de Dados (Lei 13.709/2018).

Declaro ter lido e concordado com as condições acima.

Data/hora: **{{DATA_HORA_ASSINATURA}}**
IP de assinatura: **{{IP_ASSINATURA}}**
Assinatura eletrônica: **{{HASH_ASSINATURA}}**

---

## Campos a persistir no Firestore (`clientes/{clienteId}.termo`)

```json
{
  "aceito": true,
  "versao": "1.0",
  "dataHoraISO": "2026-10-19T14:32:00Z",
  "ip": "203.0.113.10",
  "hash": "sha256-do-texto-exibido+assinatura"
}
```

O painel (ver `web/dashboard.html`) **não deve permitir** marcar um aparelho
como provisionado/ativo sem que `clientes/{clienteId}.termo.aceito === true`.
