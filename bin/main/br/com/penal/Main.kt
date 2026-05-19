package br.com.penal

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8765
    embeddedServer(Netty, host = "127.0.0.1", port = port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val jsonConfig = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    install(CORS) {
        allowHost("127.0.0.1:8765")
        allowHost("localhost:8765")
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.message))
        }
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Erro interno do servidor"))
        }
    }

    val storage = AppStorage(File("data"), jsonConfig)
    val authService = AuthService(storage)
    val calculator = PenalCalculator()

    routing {
        get("/") {
            call.respondRedirect("/HTML/inicio.html")
        }

        get("/api/status") {
            call.respond(StatusResponse("online", "Calculadora Penal Kotlin", LocalDateTime.now().toString()))
        }

        post("/api/auth/register") {
            val request = call.receive<RegisterRequest>()
            val user = authService.register(request)
            val token = authService.issueToken(user.id)
            call.respond(HttpStatusCode.Created, AuthResponse(token, user.toResponse()))
        }

        post("/api/auth/login") {
            val request = call.receive<LoginRequest>()
            val user = authService.login(request)
            val token = authService.issueToken(user.id)
            call.respond(AuthResponse(token, user.toResponse()))
        }

        post("/api/auth/google") {
            throw ApiException(
                HttpStatusCode.NotImplemented,
                "Login com Google precisa de credenciais OAuth. Use e-mail e senha neste MVP."
            )
        }

        get("/api/me") {
            val user = authService.requireUser(call)
            call.respond(user.toResponse())
        }

        post("/api/calculate") {
            val request = call.receive<CalculateRequest>()
            call.respond(calculator.calculate(request))
        }

        post("/api/calculations") {
            val user = authService.requireUser(call)
            val request = call.receive<CalculateRequest>()
            val result = calculator.calculate(request)
            val saved = storage.saveCalculation(user.id, request, result)
            call.respond(HttpStatusCode.Created, saved)
        }

        get("/api/calculations") {
            val user = authService.requireUser(call)
            call.respond(storage.listCalculations(user.id).map { it.toSummary() })
        }

        get("/api/calculations/{id}") {
            val user = authService.requireUser(call)
            val id = call.parameters["id"].orEmpty()
            val calculation = storage.findCalculation(user.id, id)
                ?: throw ApiException(HttpStatusCode.NotFound, "Calculo nao encontrado")
            call.respond(calculation)
        }

        post("/api/report") {
            val request = call.receive<CalculateRequest>()
            val result = calculator.calculate(request)
            call.respondText(buildReportHtml(request, result), ContentType.Text.Html)
        }

        get("/api/calculations/{id}/report") {
            val user = authService.requireUser(call)
            val id = call.parameters["id"].orEmpty()
            val calculation = storage.findCalculation(user.id, id)
                ?: throw ApiException(HttpStatusCode.NotFound, "Calculo nao encontrado")
            call.respondText(buildReportHtml(calculation.request, calculation.result), ContentType.Text.Html)
        }

        staticFiles("/HTML", File("HTML"))
        staticFiles("/CSS", File("CSS"))
        staticFiles("/JS", File("JS"))
        staticFiles("/Img", File("Img"))
        staticFiles("/Docs", File("Docs"))
    }
}

class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

@Serializable
data class ErrorResponse(val message: String)

@Serializable
data class StatusResponse(val status: String, val app: String, val timestamp: String)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val oab: String = "",
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserResponse)

@Serializable
data class UserResponse(val id: String, val fullName: String, val oab: String, val email: String)

@Serializable
data class User(
    val id: String,
    val fullName: String,
    val oab: String,
    val email: String,
    val salt: String,
    val passwordHash: String,
    val createdAt: String
) {
    fun toResponse() = UserResponse(id, fullName, oab, email)
}

@Serializable
data class CalculateRequest(
    val clientName: String = "",
    val processNumber: String = "",
    val prisonDate: String,
    val years: Int = 0,
    val months: Int = 0,
    val days: Int = 0,
    val detractionDays: Int = 0,
    val workDays: Int = 0,
    val studyHours: Int = 0,
    val readingBooks: Int = 0,
    val extraRemissionDays: Int = 0,
    val seriousFaults: Int = 0,
    val crimeSubtype: String,
    val initialRegime: String = "FECHADO",
    val contactName: String = "",
    val whatsapp: String = "",
    val email: String = "",
    val notes: String = ""
)

@Serializable
data class CalculationResult(
    val totalDays: Int,
    val effectiveDays: Int,
    val detractionDays: Int,
    val remissionDays: Int,
    val progressionFraction: Double,
    val progressionLabel: String,
    val semiOpenDate: String,
    val semiOpenDateFormatted: String,
    val semiOpenDaysToServe: Int,
    val openDate: String,
    val openDateFormatted: String,
    val openDaysToServe: Int,
    val paroleDate: String? = null,
    val paroleDateFormatted: String? = null,
    val paroleDaysToServe: Int? = null,
    val paroleLabel: String,
    val endDate: String,
    val endDateFormatted: String,
    val warnings: List<String> = emptyList()
)

@Serializable
data class StoredCalculation(
    val id: String,
    val userId: String,
    val request: CalculateRequest,
    val result: CalculationResult,
    val createdAt: String
) {
    fun toSummary() = CalculationSummary(
        id = id,
        clientName = request.clientName,
        processNumber = request.processNumber,
        totalDays = result.totalDays,
        semiOpenDateFormatted = result.semiOpenDateFormatted,
        openDateFormatted = result.openDateFormatted,
        paroleDateFormatted = result.paroleDateFormatted ?: "Nao aplicavel",
        createdAt = createdAt
    )
}

@Serializable
data class CalculationSummary(
    val id: String,
    val clientName: String,
    val processNumber: String,
    val totalDays: Int,
    val semiOpenDateFormatted: String,
    val openDateFormatted: String,
    val paroleDateFormatted: String,
    val createdAt: String
)

class AuthService(private val storage: AppStorage) {
    private val sessions = ConcurrentHashMap<String, String>()
    private val random = SecureRandom()

    fun register(request: RegisterRequest): User {
        val name = request.fullName.trim()
        val email = request.email.trim().lowercase()
        val password = request.password

        if (name.length < 3) throw ApiException(HttpStatusCode.BadRequest, "Nome muito curto")
        if (!email.contains("@") || !email.contains(".")) throw ApiException(HttpStatusCode.BadRequest, "E-mail invalido")
        if (password.length < 8) throw ApiException(HttpStatusCode.BadRequest, "Senha deve ter no minimo 8 caracteres")
        if (storage.findUserByEmail(email) != null) throw ApiException(HttpStatusCode.Conflict, "E-mail ja cadastrado")

        val salt = randomBytes()
        val user = User(
            id = UUID.randomUUID().toString(),
            fullName = name,
            oab = request.oab.trim(),
            email = email,
            salt = salt,
            passwordHash = hashPassword(password, salt),
            createdAt = LocalDateTime.now().toString()
        )
        storage.saveUser(user)
        return user
    }

    fun login(request: LoginRequest): User {
        val email = request.email.trim().lowercase()
        val user = storage.findUserByEmail(email)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "E-mail ou senha invalidos")
        if (hashPassword(request.password, user.salt) != user.passwordHash) {
            throw ApiException(HttpStatusCode.Unauthorized, "E-mail ou senha invalidos")
        }
        return user
    }

    fun issueToken(userId: String): String {
        val token = randomBytes(32)
        sessions[token] = userId
        return token
    }

    fun requireUser(call: ApplicationCall): User {
        val authHeader = call.request.headers[HttpHeaders.Authorization].orEmpty()
        val token = authHeader.removePrefix("Bearer").trim()
        if (token.isBlank()) throw ApiException(HttpStatusCode.Unauthorized, "Login necessario")
        val userId = sessions[token] ?: throw ApiException(HttpStatusCode.Unauthorized, "Sessao invalida")
        return storage.findUserById(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "Usuario nao encontrado")
    }

    private fun randomBytes(size: Int = 16): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(bytes)
    }
}

class PenalCalculator {
    private val locale = Locale.forLanguageTag("pt-BR")
    private val formatter = DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", locale)

    fun calculate(request: CalculateRequest): CalculationResult {
        val start = parseDate(request.prisonDate)
        val totalDays = validateAndTotalDays(request)
        val rule = progressionRule(request.crimeSubtype)
        val remissionDays = calculateRemission(request)
        val detractionDays = max(request.detractionDays, 0)
        val credits = min(detractionDays + remissionDays, totalDays)
        val effectiveDays = max(totalDays - credits, 0)

        val firstProgression = ceil(totalDays * rule.fraction).toInt()
        val secondProgression = min(totalDays, firstProgression * 2)

        val semiOpenDays = when (request.initialRegime.uppercase()) {
            "SEMIABERTO", "ABERTO" -> 0
            else -> max(firstProgression - credits, 0)
        }

        val openDays = when (request.initialRegime.uppercase()) {
            "ABERTO" -> 0
            "SEMIABERTO" -> max(firstProgression - credits, 0)
            else -> max(secondProgression - credits, 0)
        }

        val parole = paroleRule(request.crimeSubtype)
        val paroleDays = parole?.let { max(ceil(totalDays * it.fraction).toInt() - credits, 0) }
        val endDays = effectiveDays

        val warnings = buildList {
            add("Estimativa educativa. A concessao depende de decisao judicial e requisitos subjetivos.")
            if (request.seriousFaults > 0) {
                add("Falta grave informada: revise marco interruptivo, perda de remicao e decisao judicial aplicavel.")
            }
            if (remissionDays > 0) {
                add("Remicao computada como credito de pena cumprida conforme parametros informados.")
            }
            if (request.crimeSubtype.startsWith("hediondo_morte")) {
                add("Livramento condicional vedado para crime hediondo com resultado morte.")
            }
        }

        return CalculationResult(
            totalDays = totalDays,
            effectiveDays = effectiveDays,
            detractionDays = detractionDays,
            remissionDays = remissionDays,
            progressionFraction = rule.fraction,
            progressionLabel = rule.label,
            semiOpenDate = start.plusDays(semiOpenDays.toLong()).toString(),
            semiOpenDateFormatted = format(start.plusDays(semiOpenDays.toLong())),
            semiOpenDaysToServe = semiOpenDays,
            openDate = start.plusDays(openDays.toLong()).toString(),
            openDateFormatted = format(start.plusDays(openDays.toLong())),
            openDaysToServe = openDays,
            paroleDate = paroleDays?.let { start.plusDays(it.toLong()).toString() },
            paroleDateFormatted = paroleDays?.let { format(start.plusDays(it.toLong())) },
            paroleDaysToServe = paroleDays,
            paroleLabel = parole?.label ?: "Nao aplicavel",
            endDate = start.plusDays(endDays.toLong()).toString(),
            endDateFormatted = format(start.plusDays(endDays.toLong())),
            warnings = warnings
        )
    }

    private fun parseDate(value: String): LocalDate {
        return try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.BadRequest, "Data de inicio da pena invalida")
        }
    }

    private fun validateAndTotalDays(request: CalculateRequest): Int {
        if (request.years < 0 || request.months < 0 || request.days < 0) {
            throw ApiException(HttpStatusCode.BadRequest, "Pena nao pode conter valores negativos")
        }
        if (request.months > 11) throw ApiException(HttpStatusCode.BadRequest, "Meses devem estar entre 0 e 11")
        if (request.days > 30) throw ApiException(HttpStatusCode.BadRequest, "Dias devem estar entre 0 e 30")
        val totalDays = request.years * 365 + request.months * 30 + request.days
        if (totalDays <= 0) throw ApiException(HttpStatusCode.BadRequest, "Informe uma pena maior que zero")
        return totalDays
    }

    private fun calculateRemission(request: CalculateRequest): Int {
        val work = max(request.workDays, 0) / 3
        val study = max(request.studyHours, 0) / 12
        val reading = max(request.readingBooks, 0) * 4
        val extra = max(request.extraRemissionDays, 0)
        return work + study + reading + extra
    }

    private fun progressionRule(crimeSubtype: String): Rule {
        return when (crimeSubtype) {
            "comum_primario" -> Rule(0.16, "Crime comum - primario (16%)")
            "comum_reincidente" -> Rule(0.20, "Crime comum - reincidente (20%)")
            "violencia_primario" -> Rule(0.25, "Violencia ou grave ameaca - primario (25%)")
            "violencia_reincidente" -> Rule(0.30, "Violencia ou grave ameaca - reincidente (30%)")
            "hediondo_primario" -> Rule(0.40, "Crime hediondo/equiparado - primario (40%)")
            "hediondo_reincidente" -> Rule(0.60, "Crime hediondo/equiparado - reincidente (60%)")
            "hediondo_morte_primario" -> Rule(0.50, "Hediondo com resultado morte - primario (50%)")
            "hediondo_morte_reincidente" -> Rule(0.70, "Hediondo com resultado morte - reincidente (70%)")
            "organizacao_criminosa" -> Rule(0.50, "Comando de organizacao criminosa (50%)")
            else -> throw ApiException(HttpStatusCode.BadRequest, "Tipo de crime invalido")
        }
    }

    private fun paroleRule(crimeSubtype: String): Rule? {
        if (crimeSubtype.startsWith("hediondo_morte")) return null
        return when {
            crimeSubtype.startsWith("hediondo") -> Rule(2.0 / 3.0, "Livramento condicional - crime hediondo (2/3)")
            crimeSubtype.contains("reincidente") -> Rule(0.50, "Livramento condicional - reincidente (1/2)")
            else -> Rule(1.0 / 3.0, "Livramento condicional - primario (1/3)")
        }
    }

    private fun format(date: LocalDate): String = formatter.format(date)

    private data class Rule(val fraction: Double, val label: String)
}

class AppStorage(private val dataDir: File, private val json: Json) {
    private val usersFile = File(dataDir, "users.json")
    private val calculationsFile = File(dataDir, "calculations.json")

    init {
        dataDir.mkdirs()
        if (!usersFile.exists()) usersFile.writeText("[]")
        if (!calculationsFile.exists()) calculationsFile.writeText("[]")
    }

    @Synchronized
    fun findUserByEmail(email: String): User? = users().firstOrNull { it.email == email }

    @Synchronized
    fun findUserById(id: String): User? = users().firstOrNull { it.id == id }

    @Synchronized
    fun saveUser(user: User) {
        val all = users().toMutableList()
        all.add(user)
        usersFile.writeText(json.encodeToString(ListSerializer(User.serializer()), all))
    }

    @Synchronized
    fun saveCalculation(userId: String, request: CalculateRequest, result: CalculationResult): StoredCalculation {
        val all = calculations().toMutableList()
        val saved = StoredCalculation(
            id = UUID.randomUUID().toString(),
            userId = userId,
            request = request,
            result = result,
            createdAt = LocalDateTime.now().toString()
        )
        all.add(saved)
        calculationsFile.writeText(json.encodeToString(ListSerializer(StoredCalculation.serializer()), all))
        return saved
    }

    @Synchronized
    fun listCalculations(userId: String): List<StoredCalculation> {
        return calculations()
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }
    }

    @Synchronized
    fun findCalculation(userId: String, id: String): StoredCalculation? {
        return calculations().firstOrNull { it.userId == userId && it.id == id }
    }

    private fun users(): List<User> {
        return json.decodeFromString(ListSerializer(User.serializer()), usersFile.readText())
    }

    private fun calculations(): List<StoredCalculation> {
        return json.decodeFromString(ListSerializer(StoredCalculation.serializer()), calculationsFile.readText())
    }
}

private fun buildReportHtml(request: CalculateRequest, result: CalculationResult): String {
    val cliente = html(request.clientName.ifBlank { "Nao informado" })
    val processo = html(request.processNumber.ifBlank { "Nao informado" })
    val contato = html(request.contactName.ifBlank { "Nao informado" })
    val avisos = result.warnings.joinToString("") { "<li>${html(it)}</li>" }

    return """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
          <meta charset="UTF-8">
          <title>Relatorio de Calculo Penal</title>
          <style>
            body { font-family: Arial, sans-serif; margin: 32px; color: #1c2332; }
            h1, h2 { color: #000f50; }
            .linha { border-left: 4px solid #c69452; padding: 12px; margin: 12px 0; background: #fbfaf7; }
            small { color: #5a6475; }
            @media print { button { display: none; } }
          </style>
        </head>
        <body>
          <button onclick="window.print()">Imprimir / salvar PDF</button>
          <h1>Relatorio de Calculo Penal</h1>
          <p><strong>Cliente:</strong> $cliente</p>
          <p><strong>Processo:</strong> $processo</p>
          <p><strong>Contato:</strong> $contato</p>
          <p><strong>Pena total:</strong> ${result.totalDays} dias</p>
          <p><strong>Detracao:</strong> ${result.detractionDays} dias | <strong>Remicao:</strong> ${result.remissionDays} dias</p>
          <div class="linha"><small>${html(result.progressionLabel)}</small><h2>Semiaberto: ${result.semiOpenDateFormatted}</h2><p>${result.semiOpenDaysToServe} dias a cumprir</p></div>
          <div class="linha"><small>Progressao seguinte estimada</small><h2>Aberto: ${result.openDateFormatted}</h2><p>${result.openDaysToServe} dias a cumprir</p></div>
          <div class="linha"><small>${html(result.paroleLabel)}</small><h2>Livramento: ${html(result.paroleDateFormatted ?: "Nao aplicavel")}</h2><p>${result.paroleDaysToServe?.toString() ?: "-"} dias a cumprir</p></div>
          <div class="linha"><small>Cumprimento integral</small><h2>Termino da pena: ${result.endDateFormatted}</h2></div>
          <h2>Observacoes</h2>
          <ul>$avisos</ul>
        </body>
        </html>
    """.trimIndent()
}

private fun html(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
