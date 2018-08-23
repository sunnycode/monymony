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

import io.ycld.monymony.lib.webs.Config;
import io.ycld.monymony.lib.webs.ConnectionCloseFilter;
import io.ycld.monymony.lib.webs.JettyServiceProvider;
import io.ycld.redissolve.struct.map.RedisMap;

import org.eclipse.jetty.server.Server;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;

/**
 * Guice Module which registers essential classes.
 */
public class MonitoringServiceModule extends ServletModule {
  @Override
  protected void configureServlets() {
    bind(LegacyChecksResource.class).asEagerSingleton();
    bind(FancyCheckResource.class).asEagerSingleton();
    bind(DeepPingResource.class).asEagerSingleton();

    Config config = new Config("logit.", "io/ycld/monymony/monymony.properties");

    bind(Config.class).toInstance(config);
    bind(Server.class).toProvider(JettyServiceProvider.class).asEagerSingleton();
    bind(DateTimeFormatter.class).annotatedWith(Names.named("external.datetimeformat")).toInstance(
        ISODateTimeFormat.basicDateTime().withZone(DateTimeZone.UTC));
    bind(ConnectionCloseFilter.class).asEagerSingleton();
    bind(RedisMap.class).asEagerSingleton();

    serve("*").with(GuiceContainer.class);
    filter("*").through(ConnectionCloseFilter.class);
  }
}
