# Calculadora Penal Kotlin

Aplicacao web com backend em Kotlin/Ktor para calculo de prazos de execucao penal, cadastro/login de usuarios e historico de calculos.

## Como rodar

1. Abra o PowerShell nesta pasta.
2. Execute:

```powershell
.\gradlew.bat run
```

3. Acesse:

```text
http://127.0.0.1:8765
```

Na primeira execucao, o Gradle Wrapper baixa o Gradle e as dependencias do projeto.

## Endpoints

- `GET /api/status`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/me`
- `POST /api/calculate`
- `POST /api/calculations`
- `GET /api/calculations`
- `GET /api/calculations/{id}`
- `POST /api/report`
- `GET /api/calculations/{id}/report`

## Regras implementadas

Progressao de regime:

- Crime comum primario: 16%
- Crime comum reincidente: 20%
- Violencia ou grave ameaca primario: 25%
- Violencia ou grave ameaca reincidente: 30%
- Hediondo primario: 40%
- Hediondo reincidente: 60%
- Hediondo com resultado morte primario: 50%
- Hediondo com resultado morte reincidente: 70%

Livramento condicional:

- Primario: 1/3
- Reincidente: 1/2
- Hediondo: 2/3
- Hediondo com resultado morte: nao aplicavel

Remicao:

- Trabalho: 1 dia a cada 3 dias trabalhados
- Estudo: 1 dia a cada 12 horas
- Leitura: ate 4 dias por livro informado
- Campo extra para dias ja deferidos judicialmente

Os dados locais ficam em `data/users.json` e `data/calculations.json`.
