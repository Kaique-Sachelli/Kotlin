const notificacao = document.getElementById('notificacao');
const chaveToken = 'tokenCalculadoraPenal';
const chaveUsuario = 'usuarioCalculadoraPenal';

let ultimoCalculo = null;
let ultimaEntrada = null;

function token() {
  return localStorage.getItem(chaveToken);
}

function usuario() {
  const texto = localStorage.getItem(chaveUsuario);
  return texto ? JSON.parse(texto) : null;
}

function mostrarNotificacao(mensagem, tipo = 'info') {
  notificacao.textContent = mensagem;
  notificacao.className = tipo;
  notificacao.classList.add('show');

  setTimeout(() => {
    notificacao.classList.remove('show');
  }, 3500);
}

async function requisicao(url, opcoes = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(opcoes.headers || {})
  };

  if (token()) {
    headers.Authorization = `Bearer ${token()}`;
  }

  const resposta = await fetch(url, {
    ...opcoes,
    headers
  });

  const contentType = resposta.headers.get('content-type') || '';
  const corpo = contentType.includes('application/json')
    ? await resposta.json().catch(() => ({}))
    : await resposta.text();

  if (!resposta.ok) {
    throw new Error(corpo.message || corpo || 'Erro na requisicao');
  }

  return corpo;
}

function numero(id) {
  return Number(document.getElementById(id).value || 0);
}

function texto(id) {
  return document.getElementById(id).value.trim();
}

function montarEntrada() {
  return {
    clientName: texto('cliente'),
    processNumber: texto('processo'),
    prisonDate: texto('dataPrisao'),
    years: numero('anos'),
    months: numero('meses'),
    days: numero('dias'),
    detractionDays: numero('detracao'),
    workDays: numero('diasTrabalho'),
    studyHours: numero('horasEstudo'),
    readingBooks: numero('livros'),
    extraRemissionDays: numero('remicaoExtra'),
    seriousFaults: numero('faltasGraves'),
    crimeSubtype: document.getElementById('tipoCrime').value,
    initialRegime: document.getElementById('regimeInicial').value,
    contactName: texto('nomeContato'),
    whatsapp: texto('whatsapp'),
    email: texto('emailContato'),
    notes: texto('observacoes')
  };
}

function preencherResultado(resultado) {
  document.getElementById('resultadoConteudo').classList.remove('vazio');
  document.getElementById('resultadoSemiaberto').textContent = resultado.semiOpenDateFormatted;
  document.getElementById('resultadoSemiabertoDias').textContent = `${resultado.semiOpenDaysToServe} dias a cumprir`;
  document.getElementById('resultadoAberto').textContent = resultado.openDateFormatted;
  document.getElementById('resultadoAbertoDias').textContent = `${resultado.openDaysToServe} dias a cumprir`;
  document.getElementById('resultadoLivramento').textContent = resultado.paroleDateFormatted || 'Nao aplicavel';
  document.getElementById('resultadoLivramentoDias').textContent = resultado.paroleDaysToServe === null || resultado.paroleDaysToServe === undefined
    ? resultado.paroleLabel
    : `${resultado.paroleDaysToServe} dias a cumprir`;
  document.getElementById('resultadoTermino').textContent = resultado.endDateFormatted;
  document.getElementById('resultadoResumo').textContent =
    `Pena total: ${resultado.totalDays} dias | Detração: ${resultado.detractionDays} | Remição: ${resultado.remissionDays}`;

  const lista = document.getElementById('listaAvisos');
  lista.innerHTML = '';
  resultado.warnings.forEach((aviso) => {
    const item = document.createElement('li');
    item.textContent = aviso;
    lista.appendChild(item);
  });
}

async function calcular(evento) {
  evento.preventDefault();

  try {
    ultimaEntrada = montarEntrada();
    ultimoCalculo = await requisicao('/api/calculate', {
      method: 'POST',
      body: JSON.stringify(ultimaEntrada)
    });
    preencherResultado(ultimoCalculo);
    mostrarNotificacao('Calculo realizado com sucesso', 'sucesso');
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
}

async function salvarCalculo() {
  if (!token()) {
    mostrarNotificacao('Faca login para salvar o calculo', 'info');
    setTimeout(() => {
      window.location.href = 'login.html';
    }, 800);
    return;
  }

  if (!ultimaEntrada) {
    mostrarNotificacao('Calcule antes de salvar', 'info');
    return;
  }

  try {
    await requisicao('/api/calculations', {
      method: 'POST',
      body: JSON.stringify(ultimaEntrada)
    });
    mostrarNotificacao('Calculo salvo no historico', 'sucesso');
    carregarHistorico();
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
}

async function exportarRelatorio() {
  if (!ultimaEntrada) {
    mostrarNotificacao('Calcule antes de exportar', 'info');
    return;
  }

  try {
    const html = await requisicao('/api/report', {
      method: 'POST',
      body: JSON.stringify(ultimaEntrada)
    });
    const janela = window.open('', '_blank');
    janela.document.write(html);
    janela.document.close();
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
}

async function carregarHistorico() {
  const lista = document.getElementById('listaHistorico');

  if (!token()) {
    lista.innerHTML = '<p class="mensagem-historico">Faca login para visualizar seus calculos salvos.</p>';
    document.getElementById('totalExecucoes').textContent = '0';
    document.getElementById('totalPendentes').textContent = '0';
    document.getElementById('totalRecentes').textContent = '0';
    return;
  }

  try {
    const historico = await requisicao('/api/calculations');
    lista.innerHTML = '';

    if (historico.length === 0) {
      lista.innerHTML = '<p class="mensagem-historico">Nenhum calculo salvo ainda.</p>';
    }

    historico.slice(0, 5).forEach((calculo) => {
      const linha = document.createElement('div');
      linha.className = 'linha-historico';
      linha.innerHTML = `
        <div>
          <strong>${calculo.clientName || 'Cliente sem nome'}</strong>
          <p>Processo ${calculo.processNumber || 'nao informado'} &middot; ${calculo.totalDays} dias</p>
        </div>
        <button type="button" data-id="${calculo.id}">Abrir</button>
      `;
      lista.appendChild(linha);
    });

    lista.querySelectorAll('button[data-id]').forEach((botao) => {
      botao.addEventListener('click', () => abrirCalculo(botao.dataset.id));
    });

    document.getElementById('totalExecucoes').textContent = historico.length;
    document.getElementById('totalRecentes').textContent = Math.min(historico.length, 5);
    document.getElementById('totalPendentes').textContent = historico.filter((item) => {
      const partes = item.semiOpenDateFormatted || '';
      return partes.length > 0;
    }).length;
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
}

async function abrirCalculo(id) {
  try {
    const calculo = await requisicao(`/api/calculations/${id}`);
    ultimaEntrada = calculo.request;
    ultimoCalculo = calculo.result;
    preencherFormulario(calculo.request);
    preencherResultado(calculo.result);
    window.location.href = '#calculadora';
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
}

function preencherFormulario(entrada) {
  document.getElementById('cliente').value = entrada.clientName || '';
  document.getElementById('processo').value = entrada.processNumber || '';
  document.getElementById('dataPrisao').value = entrada.prisonDate || '';
  document.getElementById('anos').value = entrada.years || 0;
  document.getElementById('meses').value = entrada.months || 0;
  document.getElementById('dias').value = entrada.days || 0;
  document.getElementById('detracao').value = entrada.detractionDays || 0;
  document.getElementById('diasTrabalho').value = entrada.workDays || 0;
  document.getElementById('horasEstudo').value = entrada.studyHours || 0;
  document.getElementById('livros').value = entrada.readingBooks || 0;
  document.getElementById('remicaoExtra').value = entrada.extraRemissionDays || 0;
  document.getElementById('faltasGraves').value = entrada.seriousFaults || 0;
  document.getElementById('tipoCrime').value = entrada.crimeSubtype || 'comum_primario';
  document.getElementById('regimeInicial').value = entrada.initialRegime || 'FECHADO';
  document.getElementById('nomeContato').value = entrada.contactName || '';
  document.getElementById('whatsapp').value = entrada.whatsapp || '';
  document.getElementById('emailContato').value = entrada.email || '';
  document.getElementById('observacoes').value = entrada.notes || '';
}

function configurarAutenticacao() {
  const botao = document.getElementById('botaoAutenticacao');
  const dadosUsuario = usuario();

  if (!dadosUsuario) return;

  botao.textContent = 'Sair';
  botao.href = '#';
  botao.title = dadosUsuario.fullName;
  botao.addEventListener('click', (evento) => {
    evento.preventDefault();
    localStorage.removeItem(chaveToken);
    localStorage.removeItem(chaveUsuario);
    mostrarNotificacao('Sessao encerrada', 'info');
    setTimeout(() => window.location.reload(), 500);
  });
}

document.getElementById('formCalculo').addEventListener('submit', calcular);
document.getElementById('botaoSalvar').addEventListener('click', salvarCalculo);
document.getElementById('botaoExportar').addEventListener('click', exportarRelatorio);
document.getElementById('botaoAtualizarHistorico').addEventListener('click', carregarHistorico);

configurarAutenticacao();
carregarHistorico();
