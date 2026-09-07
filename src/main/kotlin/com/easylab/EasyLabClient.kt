// EasyLab typed client SDK for Kotlin/JVM.
//
// One entrypoint, one baseUrl: the easylab gateway. It serves both the
// easylab.v1.* surface (lab/ops/registry) and the agent.v1.* surface
// (sessions/providers/... forwarded to the abc agent backend). External
// frontends never talk to the agent directly.
//
// Usage:
//   val client = EasyLabClient(baseUrl = "https://easylab.example.com", token = "...")
//   val repos = client.lab.listRepos(ListReposRequest()) // suspend
package com.easylab

import com.connectrpc.ProtocolClient
import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaLiteProtobufStrategy
import com.connectrpc.http.clone
import com.connectrpc.Interceptor
import com.connectrpc.StreamFunction
import com.connectrpc.UnaryFunction
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.connectrpc.protocols.NetworkProtocol
import com.agent.v1.AgentServiceClient
import com.agent.v1.AgentServiceClientInterface
import com.easylab.v1.LabServiceClient
import com.easylab.v1.LabServiceClientInterface
import com.easylab.v1.OpsServiceClient
import com.easylab.v1.OpsServiceClientInterface
import com.easylab.v1.RegistryServiceClient
import com.easylab.v1.RegistryServiceClientInterface
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** Injects an `Authorization: Bearer <token>` header on every request. */
class BearerInterceptor(private val token: String) : Interceptor {
    override fun unaryFunction(): UnaryFunction {
        return UnaryFunction(
            requestFunction = { request ->
                val headers = request.headers.toMutableMap()
                headers["Authorization"] = listOf("Bearer $token")
                request.clone(headers = headers)
            },
            responseFunction = { response -> response },
        )
    }

    override fun streamFunction(): StreamFunction {
        return StreamFunction(
            requestFunction = { request ->
                val headers = request.headers.toMutableMap()
                headers["Authorization"] = listOf("Bearer $token")
                request.clone(headers = headers)
            },
        )
    }
}

/** The typed easylab gateway client: lab + ops + registry + agent surfaces. */
class EasyLabClient(
    baseUrl: String,
    token: String,
    okHttp: OkHttpClient = OkHttpClient(),
) {
    private val host = baseUrl.trimEnd('/')

    private val protocolClient = ProtocolClient(
        httpClient = ConnectOkHttpClient(okHttp),
        ProtocolClientConfig(
            host = host,
            serializationStrategy = GoogleJavaLiteProtobufStrategy(),
            networkProtocol = NetworkProtocol.CONNECT,
            ioCoroutineContext = Dispatchers.IO,
            interceptors = listOf({ _: ProtocolClientConfig -> BearerInterceptor(token) }),
        ),
    )

    /** Lab surface (repos/branches/blobs/revisions/search/graph/...). */
    val lab: LabServiceClientInterface = LabServiceClient(protocolClient)

    /** Ops surface (services/sandboxes/builds/tasks/sync). */
    val ops: OpsServiceClientInterface = OpsServiceClient(protocolClient)

    /** Registry surface (packages). */
    val registry: RegistryServiceClientInterface = RegistryServiceClient(protocolClient)

    /** Agent surface (sessions/providers/...) served through the gateway. */
    val agent: AgentServiceClientInterface = AgentServiceClient(protocolClient)
}
