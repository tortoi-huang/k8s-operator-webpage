package org.huang.k8s.http;

import com.sun.net.httpserver.HttpServer;
import io.javaoperatorsdk.operator.Operator;
import org.huang.k8s.probes.LivenessHandler;
import org.huang.k8s.probes.StartupHandler;

import java.io.IOException;
import java.net.InetSocketAddress;

public class OperatorHttpServer {
     final HttpServer server;

    public OperatorHttpServer(final int port, final Operator operator) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/startup", new StartupHandler(operator));
        server.createContext("/healthz", new LivenessHandler(operator));
        server.setExecutor(null);
    }

    public void start() {
        this.server.start();
    }
}
