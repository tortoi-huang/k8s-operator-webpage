package org.huang.k8s.probes;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.javaoperatorsdk.operator.Operator;

import java.io.IOException;

import static org.huang.k8s.http.HandlerUtils.sendMessage;

public class StartupHandler implements HttpHandler {

  private final Operator operator;

  public StartupHandler(Operator operator) {
    this.operator = operator;
  }

  @Override
  public void handle(HttpExchange httpExchange) throws IOException {
    if (operator.getRuntimeInfo().isStarted()) {
      sendMessage(httpExchange, 200, "started");
    } else {
      sendMessage(httpExchange, 400, "not started yet");
    }
  }
}
