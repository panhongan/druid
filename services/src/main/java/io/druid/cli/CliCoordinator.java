/*
 * Druid - a distributed column store.
 * Copyright (C) 2012, 2013  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package io.druid.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.metamx.common.concurrent.ScheduledExecutorFactory;
import com.metamx.common.logger.Logger;
import io.airlift.command.Command;
import io.druid.client.indexing.IndexingServiceClient;
import io.druid.db.DatabaseRuleManager;
import io.druid.db.DatabaseRuleManagerConfig;
import io.druid.db.DatabaseRuleManagerProvider;
import io.druid.db.DatabaseSegmentManager;
import io.druid.db.DatabaseSegmentManagerConfig;
import io.druid.db.DatabaseSegmentManagerProvider;
import io.druid.guice.ConfigProvider;
import io.druid.guice.Jerseys;
import io.druid.guice.JsonConfigProvider;
import io.druid.guice.LazySingleton;
import io.druid.guice.LifecycleModule;
import io.druid.guice.ManageLifecycle;
import io.druid.server.coordinator.DruidCoordinator;
import io.druid.server.coordinator.DruidCoordinatorConfig;
import io.druid.server.coordinator.LoadQueueTaskMaster;
import io.druid.server.http.BackwardsCompatibleCoordinatorResource;
import io.druid.server.http.BackwardsCompatibleInfoResource;
import io.druid.server.http.CoordinatorDynamicConfigsResource;
import io.druid.server.http.CoordinatorRedirectInfo;
import io.druid.server.http.CoordinatorResource;
import io.druid.server.http.DBResource;
import io.druid.server.http.DatasourcesResource;
import io.druid.server.http.InfoResource;
import io.druid.server.http.RedirectFilter;
import io.druid.server.http.RedirectInfo;
import io.druid.server.http.RulesResource;
import io.druid.server.http.ServersResource;
import io.druid.server.http.TiersResource;
import io.druid.server.initialization.JettyServerInitializer;
import org.apache.curator.framework.CuratorFramework;
import org.eclipse.jetty.server.Server;

import java.util.List;

/**
 */
@Command(
    name = "coordinator",
    description = "Runs the Coordinator, see http://druid.io/docs/0.6.69/Coordinator.html for a description."
)
public class CliCoordinator extends ServerRunnable
{
  private static final Logger log = new Logger(CliCoordinator.class);

  public CliCoordinator()
  {
    super(log);
  }

  @Override
  protected List<Object> getModules()
  {
    return ImmutableList.<Object>of(
        new Module()
        {
          @Override
          public void configure(Binder binder)
          {
            ConfigProvider.bind(binder, DruidCoordinatorConfig.class);

            JsonConfigProvider.bind(binder, "druid.manager.segments", DatabaseSegmentManagerConfig.class);
            JsonConfigProvider.bind(binder, "druid.manager.rules", DatabaseRuleManagerConfig.class);

            binder.bind(RedirectFilter.class).in(LazySingleton.class);
            binder.bind(RedirectInfo.class).to(CoordinatorRedirectInfo.class).in(LazySingleton.class);

            binder.bind(DatabaseSegmentManager.class)
                  .toProvider(DatabaseSegmentManagerProvider.class)
                  .in(ManageLifecycle.class);

            binder.bind(DatabaseRuleManager.class)
                  .toProvider(DatabaseRuleManagerProvider.class)
                  .in(ManageLifecycle.class);

            binder.bind(IndexingServiceClient.class).in(LazySingleton.class);

            binder.bind(DruidCoordinator.class);

            LifecycleModule.register(binder, DruidCoordinator.class);

            binder.bind(JettyServerInitializer.class).toInstance(new CoordinatorJettyServerInitializer());
            Jerseys.addResource(binder, BackwardsCompatibleInfoResource.class);
            Jerseys.addResource(binder, InfoResource.class);
            Jerseys.addResource(binder, BackwardsCompatibleCoordinatorResource.class);
            Jerseys.addResource(binder, CoordinatorResource.class);
            Jerseys.addResource(binder, CoordinatorDynamicConfigsResource.class);
            Jerseys.addResource(binder, TiersResource.class);
            Jerseys.addResource(binder, RulesResource.class);
            Jerseys.addResource(binder, ServersResource.class);
            Jerseys.addResource(binder, DatasourcesResource.class);
            Jerseys.addResource(binder, DBResource.class);

            LifecycleModule.register(binder, Server.class);
          }

          @Provides
          @LazySingleton
          public LoadQueueTaskMaster getLoadQueueTaskMaster(
              CuratorFramework curator, ObjectMapper jsonMapper, ScheduledExecutorFactory factory, DruidCoordinatorConfig config
          )
          {
            return new LoadQueueTaskMaster(curator, jsonMapper, factory.create(1, "Master-PeonExec--%d"), config);
          }
        }
    );
  }
}
