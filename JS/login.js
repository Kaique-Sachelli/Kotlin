const notificacao = document.getElementById('notificacao');

function mostrarNotificacao(mensagem, tipo = 'info') {
  notificacao.textContent = mensagem;
  notificacao.className = tipo;
  notificacao.classList.add('show');

  setTimeout(() => {
    notificacao.classList.remove('show');
  }, 3500);
}

async function requisicao(url, dados) {
  const resposta = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(dados)
  });

  const corpo = await resposta.json().catch(() => ({}));
  if (!resposta.ok) {
    throw new Error(corpo.message || 'Erro na requisicao');
  }
  return corpo;
}

function salvarSessao(dados) {
  localStorage.setItem('tokenCalculadoraPenal', dados.token);
  localStorage.setItem('usuarioCalculadoraPenal', JSON.stringify(dados.user));
}

document.querySelectorAll('.toggle-senha').forEach((botao) => {
  botao.addEventListener('click', () => {
    const campo = document.getElementById(botao.dataset.target);
    const visivel = campo.type === 'text';
    campo.type = visivel ? 'password' : 'text';
    botao.textContent = visivel ? 'Ver' : 'Ocultar';
  });
});

document.getElementById('formEntrar').addEventListener('submit', async (evento) => {
  evento.preventDefault();

  try {
    const dados = await requisicao('/api/auth/login', {
      email: document.getElementById('emailLogin').value,
      password: document.getElementById('senhaLogin').value
    });

    salvarSessao(dados);
    mostrarNotificacao('Login realizado com sucesso', 'sucesso');
    setTimeout(() => {
      window.location.href = 'inicio.html';
    }, 600);
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
});

document.getElementById('formCadastrar').addEventListener('submit', async (evento) => {
  evento.preventDefault();

  try {
    const dados = await requisicao('/api/auth/register', {
      fullName: document.getElementById('nomeCadastro').value,
      oab: document.getElementById('oabCadastro').value,
      email: document.getElementById('emailCadastro').value,
      password: document.getElementById('senhaCadastro').value
    });

    salvarSessao(dados);
    mostrarNotificacao('Conta criada com sucesso', 'sucesso');
    setTimeout(() => {
      window.location.href = 'inicio.html';
    }, 600);
  } catch (erro) {
    mostrarNotificacao(erro.message, 'erro');
  }
});

document.getElementById('botaoGoogle').addEventListener('click', async () => {
  try {
    await requisicao('/api/auth/google', {});
  } catch (erro) {
    mostrarNotificacao(erro.message, 'info');
  }
});
