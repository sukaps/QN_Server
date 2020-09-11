package me.singleNeuron

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.gson.*
import io.ktor.features.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.singleNeuron.base.MarkdownAble
import me.singleNeuron.data.appcenter.AppCenterBuildData
import me.singleNeuron.data.github.GithubWebHookData
import me.singleNeuron.me.singleNeuron.data.appcenter.AppCenterCheckUpdateData
import me.singleNeuron.me.singleNeuron.data.appcenter.AppCenterCrashData
import me.singleNeuron.me.singleNeuron.data.appcenter.AppCenterDistributeData
import java.io.File
import java.util.*

private lateinit var botToken:String

fun main(args: Array<String>){
    print("请输入Telegram Bot Token: ")
    try {
        botToken = readLine()?:""
        GlobalScope.launch {
            val httpClient = HttpClient()
            val response: HttpResponse = httpClient.get("https://api.telegram.org/bot$botToken/getMe")
            println(response.readText())
            sendMessageToDevGroup("Link Start!")
            sendMessageToDevGroup(AppCenterCrashData(
                    name = "No Such Filed girlFriend Found in Object cinit",
                    reason = "决明",
                    url = "https://github.com/cinit",
                    app_version = "NaN"
            ))
            httpClient.close()
        }
    }catch (e:Exception) {
        println(e)
    }
    io.ktor.server.netty.EngineMain.main(args)
}

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }

    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }
        post("/webhook/github") {
            val log = call.application.environment.log
            //val string = call.receiveText()
            val data = call.receive<GithubWebHookData>()
            log.debug(data.toString())
            call.respond("success")
            if (data.ref=="refs/heads/master") {
                for (commit in data.commits) {
                    for (string in commit.modified) {
                        if (string=="CardMsgBlackList.json") {
                            log.debug("start downloading: ")
                            val httpClient = HttpClient()
                            val response: HttpResponse = httpClient.get("https://raw.githubusercontent.com/cinit/QNotified/master/CardMsgBlackList.json")
                            log.debug("downloading: ${response.status}")
                            if (response.status.isSuccess()) {
                                val json = response.readText()
                                val file = File("/root/CardMsgBlackList.json")
                                if (!file.exists()) {
                                    if (!file.createNewFile()) {
                                        log.debug("File Create Failed")
                                        return@post
                                    }
                                }
                                file.writeText(json)
                                log.debug("File download Success")
                                log.debug(json)
                            }
                            httpClient.close()
                            return@post
                        }
                    }
                }
            }
        }
        post("/webhook/appcenter/build") {
            val log = call.application.environment.log
            //val string = call.receiveText()
            val data = call.receive<AppCenterBuildData>()
            log.debug(data.toString())
            call.respond("")
            sendMessageToDevGroup(data)
        }
        post("/webhook/appcenter/crash") {
            val log = call.application.environment.log
            //val string = call.receiveText()
            val data = call.receive<AppCenterCrashData>()
            log.debug(data.toString())
            call.respond("")
            if(Regex("""me\.|nil\.nadph""", RegexOption.IGNORE_CASE).containsMatchIn(data.toString())) {
                sendMessageToDevGroup(data)
            }
        }
        post("/webhook/appcenter/distribute"){
            val log = call.application.environment.log
            //val string = call.receiveText()
            val data = call.receive<AppCenterDistributeData>()
            log.debug(data.toString())
            call.respond("")
            val httpClient = getHttpClientWithGson()
            val checkUpdateData = httpClient.get<AppCenterCheckUpdateData>("https://api.appcenter.ms/v0.1/public/sdk/apps/ddf4b597-1833-45dd-af28-96ca504b8123/releases/latest")
            if (!checkUpdateData.download_url.isBlank()) {
                val downloadResponse: HttpResponse = httpClient.get(checkUpdateData.download_url)
                if (downloadResponse.status.isSuccess()) {
                    val fileName = "${checkUpdateData.app_name}-release ${checkUpdateData.short_version}.apk"
                    val dir = File("/root/QNotified_release")
                    if (!dir.exists()) dir.mkdir()
                    val file = File(dir.absolutePath+File.separator+fileName)
                    downloadResponse.content.copyAndClose(file.writeChannel())
                    val response:HttpResponse = httpClient.post("https://api.telegram.org/bot$botToken/sendDocument"){
                        contentType(ContentType.MultiPart.FormData)
                        body = MultiPartFormDataContent(
                                formData {
                                    append("chat_id","-1001186899631")
                                    append("document", InputProvider {
                                        file.inputStream().asInput()
                                    }, Headers.build {
                                        append(HttpHeaders.ContentDisposition,"filename=$fileName")
                                    })
                                }
                        )
                    }
                    log.debug(response.readText())
                }else {
                    log.debug("下载更新 ${checkUpdateData.short_version} 失败")
                }
            }
            httpClient.close()
        }
    }
}

suspend fun sendMessageToDevGroup(msg:String) {
    val httpClient = getHttpClientWithGson()
    val response: HttpResponse = httpClient.post("https://api.telegram.org/bot$botToken/sendMessage"){
        contentType(ContentType.Application.Json)
        body = mapOf(
                "chat_id" to "-1001186899631",
                "parse_mode" to "Markdown",
                "text" to msg
        )
    }
    httpClient.close()
    println(response.readText())
}

suspend fun sendMessageToDevGroup(msg:MarkdownAble) {
    sendMessageToDevGroup(msg.toMarkdown())
    //println(msg.toMarkdown())
}

fun getHttpClientWithGson():HttpClient {
    return HttpClient(Apache){
        install(JsonFeature) {
            serializer = GsonSerializer {
            }
        }
    }
}