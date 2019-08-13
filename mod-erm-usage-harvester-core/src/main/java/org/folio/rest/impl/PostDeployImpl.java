package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.folio.rest.jaxrs.model.PeriodicConfig;
import org.folio.rest.jaxrs.model.PeriodicConfig.PeriodicInterval;
import org.folio.rest.resource.interfaces.PostDeployVerticle;
import org.olf.erm.usage.harvester.OkapiClient;
import org.olf.erm.usage.harvester.periodic.PeriodicConfigPgUtil;
import org.olf.erm.usage.harvester.periodic.SchedulingUtil;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostDeployImpl implements PostDeployVerticle {

  private static final Logger log = LoggerFactory.getLogger(PostDeployImpl.class);

  private void processTenants(Context vertxContext, List<String> tenantList) {
    tenantList.forEach(
        tenant ->
            PeriodicConfigPgUtil.get(vertxContext, tenant)
                .setHandler(
                    ar -> {
                      if (ar.succeeded()) {
                        PeriodicConfig periodicConfig = ar.result();
                        SchedulingUtil.createOrUpdateJob(periodicConfig, tenant);
                      } else {
                        log.error(
                            "Tenant: {}, failed getting PeriodicConfig: {}",
                            tenant,
                            ar.cause().getMessage());
                      }
                    }));
  }

  @Override
  public void init(Vertx arg0, Context arg1, Handler<AsyncResult<Boolean>> arg2) {
    if (Boolean.TRUE.equals(arg1.config().getBoolean("testing"))) {
      arg2.handle(Future.succeededFuture(true));
      return;
    }

    PeriodicConfig periodicConfig =
        new PeriodicConfig()
            .withStartAt(
                Date.from(
                    LocalDateTime.now()
                        .withSecond(0)
                        .plusMinutes(1)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()))
            .withPeriodicInterval(PeriodicInterval.DAILY);
    PeriodicConfigPgUtil.upsert(arg1, "diku", periodicConfig);

    try {
      Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
      scheduler.getContext().put("vertxContext", arg1);
      scheduler.start();

      new OkapiClient(arg0, arg1.config())
          .getTenants()
          .setHandler(
              ar -> {
                if (ar.succeeded()) {
                  processTenants(arg1, ar.result());
                } else {
                  log.error("failed getting tenants");
                }
              });
    } catch (SchedulerException e) {
      log.error("Error setting up quartz scheduler: " + e.getMessage(), e);
      arg2.handle(Future.failedFuture(e.getMessage()));
      return;
    }

    arg2.handle(Future.succeededFuture(true));
  }
}
