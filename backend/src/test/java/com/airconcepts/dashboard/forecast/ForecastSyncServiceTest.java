package com.airconcepts.dashboard.forecast;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.forecast.sync.*;
import com.airconcepts.dashboard.invoices.InvoiceType;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.xero.*;
import com.airconcepts.dashboard.web.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class ForecastSyncServiceTest {
  @Test void frUc04_17_refreshesAccessBeforeEachProviderRequest() {
    when(repository.begin(any(),any(),any(),any())).thenReturn(run);
    var renewed=new TenantAccess(entity,access.tenantId(),access.grantId(),"synthetic-renewed");
    when(connections.authorisedAccess(user,entity)).thenReturn(access,renewed);
    for(var type:InvoiceType.values()) {
      when(client.page(any(),eq(type),eq(1),isNull())).thenReturn(new ForecastSource.Page(List.of(),0,0,new ForecastSource.Limits(null,null,null)));
      when(repository.outstanding(entity,type)).thenReturn(List.of());
    }
    service.runManual(user,entity);
    for(var type:InvoiceType.values()) verify(client).page(renewed,type,1,null);
  }
  @Test void frUc04_17_mappingChangeBetweenPagesStopsBeforeNextProviderCall() {
    when(repository.begin(any(),any(),any(),any())).thenReturn(run);
    var remapped=new TenantAccess(entity,UUID.randomUUID(),UUID.randomUUID(),"synthetic-remapped");
    when(connections.authorisedAccess(user,entity)).thenReturn(access,remapped);
    when(client.page(any(),any(),anyInt(),isNull())).thenReturn(new ForecastSource.Page(List.of(),0,0,new ForecastSource.Limits(null,null,null)));
    assertThatThrownBy(()->service.runManual(user,entity)).isInstanceOf(ApiFailure.class)
        .satisfies(failure->assertThat(((ApiFailure)failure).code()).isEqualTo("FORECAST_SYNC_CONNECTION_CHANGED"));
    verifyNoInteractions(client);
    verify(repository,never()).apply(any(),any(),any(),any(),any(),any(),any());
  }
  private final ForecastDocumentRepository repository = mock(ForecastDocumentRepository.class);
  private final XeroForecastClient client = mock(XeroForecastClient.class);
  private final XeroConnectionService connections = mock(XeroConnectionService.class);
  private final XeroReadHealth health = mock(XeroReadHealth.class);
  private final EntityService entities = mock(EntityService.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC);
  private final ForecastSyncService service = new ForecastSyncService(repository, client, connections, health, entities, clock);
  private final UUID entity = UUID.randomUUID(), run = UUID.randomUUID();
  private final CurrentUser user = new CurrentUser(UUID.randomUUID().toString(), Role.FINANCIAL_ADMIN);
  private final TenantAccess access = new TenantAccess(entity, UUID.randomUUID(), UUID.randomUUID(), "synthetic");

  @Test void frUc04_17_pageFailureRollsBackType() {
    when(repository.begin(eq(entity), eq("MANUAL"), any(), any())).thenReturn(run);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    var firstPage=new ArrayList<ForecastSource>();
    for(int i=0;i<100;i++) { var doc=mock(ForecastSource.class);when(doc.id()).thenReturn(UUID.randomUUID());firstPage.add(doc); }
    when(client.page(access, InvoiceType.ACCREC, 1, null)).thenReturn(new ForecastSource.Page(firstPage,2,101,new ForecastSource.Limits(null,null,null)));
    when(client.page(access, InvoiceType.ACCREC, 2, null)).thenThrow(new ProviderFailure("XERO_PROVIDER_FAILED", "Safe", 500));
    assertThatThrownBy(() -> service.runManual(user, entity)).isInstanceOf(ProviderFailure.class);
    verify(repository, never()).apply(any(), any(), any(), any(), any(), any(), any());
    verify(repository).fail(entity, run, "XERO_PROVIDER_FAILED", 500);
  }

  @Test void frUc04_17_concurrentRunReturnsInProgress() {
    when(repository.begin(eq(entity), any(), any(), any())).thenThrow(new ApiFailure(HttpStatus.CONFLICT, "FORECAST_SYNC_IN_PROGRESS", "Running"));
    assertThatThrownBy(() -> service.runManual(user, entity)).isInstanceOf(ApiFailure.class);
    verifyNoInteractions(client, connections, health);
  }

  @Test void frUc04_17_rateLimitedHealthMakesNoProviderCall() {
    when(repository.begin(eq(entity), any(), any(), any())).thenReturn(run);
    when(connections.authorisedAccess(user, entity)).thenReturn(access);
    doThrow(new ProviderFailure("XERO_RATE_LIMITED", "Wait", 429, 60)).when(health).check(access);
    assertThatThrownBy(() -> service.runManual(user, entity)).isInstanceOf(ProviderFailure.class);
    verifyNoInteractions(client);
    verify(repository).fail(entity, run, "XERO_RATE_LIMITED", 429);
  }

  @Test void frUc04_07_standardUserNeverStartsRun() {
    assertThatThrownBy(() -> service.runManual(new CurrentUser(user.subject(), Role.STANDARD_USER), entity))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(repository, connections, client, health, entities);
  }

  @Test void frUc04_17_systemAccessHasNoControllerPath() throws Exception {
    try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/airconcepts/dashboard/web"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".java")).toList())
        assertThat(java.nio.file.Files.readString(file)).doesNotContain("systemAccess(", "runScheduled(");
    }
  }

  @Test void frUc04_17_missingRowsAreReReadAnd404BecomesDeleted() {
    UUID missing=UUID.randomUUID();
    when(repository.begin(eq(entity),any(),any(),any())).thenReturn(run);
    when(connections.authorisedAccess(user,entity)).thenReturn(access);
    for(var type:InvoiceType.values()) when(client.page(access,type,1,null))
        .thenReturn(new ForecastSource.Page(List.of(),0,0,new ForecastSource.Limits(null,null,null)));
    when(repository.outstanding(entity,InvoiceType.ACCREC)).thenReturn(List.of(missing));
    when(repository.outstanding(entity,InvoiceType.ACCPAY)).thenReturn(List.of());
    when(client.detailWithLimits(access,InvoiceType.ACCREC,missing)).thenThrow(new ProviderFailure("XERO_DOCUMENT_UNAVAILABLE","Missing",404));
    service.runManual(user,entity);
    verify(client).detailWithLimits(access,InvoiceType.ACCREC,missing);
    verify(repository).apply(eq(entity),eq(run),eq(access),eq(List.of()),eq(List.of(missing)),any(),any());
  }
}
