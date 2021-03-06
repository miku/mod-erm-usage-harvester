package org.olf.erm.usage.harvester.rest.impl;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.parsing.Parser;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.impl.ErmUsageHarvesterAPI;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.olf.erm.usage.harvester.Token;

@RunWith(VertxUnitRunner.class)
public class ErmUsageHarvesterAPITest {

  private static final String TENANT_ERR_MSG = "Tenant must be set";
  private static final String TENANT = "testtenant";

  private static Vertx vertx;

  private static final String deployCfg =
      "{\n"
          + "  \"okapiUrl\": \"http://localhost:9130\",\n"
          + "  \"tenantsPath\": \"/_/proxy/tenants\",\n"
          + "  \"reportsPath\": \"/counter-reports\",\n"
          + "  \"providerPath\": \"/usage-data-providers\",\n"
          + "  \"aggregatorPath\": \"/aggregator-settings\"\n"
          + "}\n"
          + "";

  @BeforeClass
  public static void setup(TestContext context) {
    vertx = Vertx.vertx();

    int port = NetworkUtils.nextFreePort();
    JsonObject cfg = new JsonObject(deployCfg);
    cfg.put("testing", true);
    cfg.put("http.port", port);
    RestAssured.reset();
    RestAssured.port = port;
    RestAssured.basePath = "erm-usage-harvester";
    RestAssured.defaultParser = Parser.JSON;
    vertx.deployVerticle(
        "org.folio.rest.RestVerticle",
        new DeploymentOptions().setConfig(cfg),
        context.asyncAssertSuccess());
  }

  @AfterClass
  public static void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
    RestAssured.reset();
  }

  @Test
  public void startHarvesterNoTenant() {
    when().get("/start").then().statusCode(400).body(containsString(TENANT_ERR_MSG));
  }

  @Test
  public void startHarvesterNoToken() {
    Error response =
        given()
            .header(new Header(XOkapiHeaders.TENANT, TENANT))
            .when()
            .get("/start")
            .then()
            .statusCode(500)
            .extract()
            .as(org.folio.rest.jaxrs.model.Error.class);
    assertThat(response)
        .isEqualToComparingFieldByFieldRecursively(ErmUsageHarvesterAPI.ERR_NO_TOKEN);
  }

  @Test
  public void startHarvester200() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .header(new Header(XOkapiHeaders.TOKEN, Token.createFakeJWTForTenant(TENANT)))
        .when()
        .get("/start")
        .then()
        .statusCode(200)
        .body("message", containsString(TENANT));
  }

  @Test
  public void startProviderNoTenant() {
    when()
        .get("/start/5b8ab2bd-e470-409c-9a6c-845d979da05e")
        .then()
        .statusCode(400)
        .body(containsString(TENANT_ERR_MSG));
  }

  @Test
  public void startProviderNoToken() {
    Error response =
        given()
            .header(new Header(XOkapiHeaders.TENANT, TENANT))
            .when()
            .get("/start")
            .then()
            .statusCode(500)
            .extract()
            .as(Error.class);
    assertThat(response)
        .isEqualToComparingFieldByFieldRecursively(ErmUsageHarvesterAPI.ERR_NO_TOKEN);
  }

  @Test
  public void startProvider200() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .header(new Header(XOkapiHeaders.TOKEN, Token.createFakeJWTForTenant(TENANT)))
        .when()
        .get("/start/5b8ab2bd-e470-409c-9a6c-845d979da05e")
        .then()
        .statusCode(200)
        .body(
            "message",
            allOf(containsString(TENANT), containsString("5b8ab2bd-e470-409c-9a6c-845d979da05e")));
  }

  @Test
  public void getImplementations() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/impl")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(2))
        .body("implementations.type", hasItems("test1", "test2"));
  }

  @Test
  public void getImplementationsAggregator() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/impl?aggregator=true")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(1))
        .body("implementations.type", hasItem("test2"))
        .body("implementations.isAggregator", everyItem(is(true)));
  }

  @Test
  public void getImplementationsNonAggregator() {
    given()
        .header(new Header(XOkapiHeaders.TENANT, TENANT))
        .when()
        .get("/impl?aggregator=false")
        .then()
        .statusCode(200)
        .body("implementations.size()", greaterThanOrEqualTo(1))
        .body("implementations.type", hasItem("test1"))
        .body("implementations.isAggregator", everyItem(is(false)));
  }
}
