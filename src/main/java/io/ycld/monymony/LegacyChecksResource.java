/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ycld.monymony;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;

@Path("/check")
public class LegacyChecksResource {
  public LegacyChecksResource() {
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/ping")
  public Response checkHttpsPing(final @QueryParam("h") String hostPort,
      final @QueryParam("t") Long timeout) {
    String urlString = "https://" + hostPort + "/ping";
    String message = "unexpected response != 'OK'";

    final Long theTimeout = timeout != null ? timeout : 3000L;

    try {
      String result = getSecureUrlContentsAsString_OkHttp(urlString, theTimeout);

      if (result.equals("OK\n")) {
        return Response.status(Status.OK).entity("OK\n").build();
      }
    } catch (Exception e) {
      message = e.getMessage();
    }

    return Response.status(Status.OK).entity("ERROR:" + message + "\n").build();
  }

  private static String getSecureUrlContentsAsString_OkHttp(String urlString, Long timeout)
      throws Exception {
    OkHttpClient client = new OkHttpClient();

    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(new KeyManager[0], new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
    SSLContext.setDefault(ctx);

    final SSLSocketFactory orig = ctx.getSocketFactory();
    client.setSslSocketFactory(orig);

    final SocketFactory fac = SocketFactory.getDefault();

    client.setSocketFactory(new SocketFactory() {
      @Override
      public Socket createSocket(InetAddress host, int port) throws IOException {
        return fac.createSocket(host, port);
      }

      @Override
      public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
          int localPort) throws IOException {
        return fac.createSocket(address, port, localAddress, localPort);
      }

      @Override
      public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return fac.createSocket(host, port);
      }

      @Override
      public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
          throws IOException, UnknownHostException {
        return fac.createSocket(host, port, localHost, localPort);
      }

      @Override
      public Socket createSocket() throws IOException {
        Socket s = fac.createSocket();
        s.setTcpNoDelay(true);

        return s;
      }
    });

    client.setHostnameVerifier(new HostnameVerifier() {
      @Override
      public boolean verify(String arg0, SSLSession arg1) {
        return true;
      }
    });

    client.setConnectTimeout(timeout, TimeUnit.MILLISECONDS);
    client.setReadTimeout(timeout, TimeUnit.MILLISECONDS);

    Request.Builder request = new Request.Builder().url(urlString).method("GET", null);

    com.squareup.okhttp.Response response = client.newCall(request.build()).execute();

    String res = response.body().string();

    return res;
  }

  private static class DefaultTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

    @Override
    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return null;
    }
  }
}
