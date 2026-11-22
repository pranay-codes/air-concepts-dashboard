package com.airconcepts.dashboard.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.airconcepts.dashboard.workitems.*;
import com.airconcepts.dashboard.workitems.operations.MockInvoiceGateway;
import com.airconcepts.dashboard.workitems.sync.*;
import com.airconcepts.dashboard.xero.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GreenLineMockQuoteSyncTest {
  @Test void gl01_firstMockSyncFindsAcceptedQuoteAndCompletesWithoutProviderCalls() {
    var entity=UUID.randomUUID(); var tenant=UUID.randomUUID(); var grant=UUID.randomUUID(); var item=UUID.randomUUID();
    var owner=UUID.randomUUID(); var manager=UUID.randomUUID(); var now=Instant.parse("2026-11-21T00:00:00Z");
    var clock=Clock.fixed(now,ZoneOffset.UTC);
    var access=new TenantAccess(entity,tenant,grant,"synthetic",Set.of("accounting.invoices.read"));
    var quotes=new MockXeroQuoteClient(RestClient.builder().build(),clock,t -> {});
    var row=new XeroQuoteClient.Quote(UUID.randomUUID(),"Q-1","J-1",XeroQuoteClient.Status.ACCEPTED,
        UUID.randomUUID(),"Customer",LocalDate.of(2026,11,21),null,now,BigDecimal.TEN,"NZD","Exclusive",List.of());
    quotes.seed(tenant,row);
    var repository=mock(QuoteMirrorRepository.class);
    when(repository.lockConfig(entity)).thenReturn(Optional.of(new QuoteMirrorRepository.Config(true,90,2,
        new StageMachine.Settings(owner,true,manager,true))));
    when(repository.lockMapping(new QuoteMirrorService.Mapping(entity,tenant,grant))).thenReturn(true);
    when(repository.provenanceMatches(any())).thenReturn(true);
    when(repository.lockQuote(entity,row.id())).thenReturn(Optional.empty());
    when(repository.insert(any(),any(),any(),any(),any(),anyString())).thenReturn(item);
    var sync=new GreenLineSyncConfig().greenLineQuoteSync(new QuoteMirrorService(repository,clock),quotes,
        mock(XeroInvoiceClient.class),new MockInvoiceGateway());
    var result=sync.run(entity,() -> access,null);
    assertThat(result.complete()).isTrue();
    assertThat(result.created()).isEqualTo(1);
    verify(repository).insert(any(),eq(row),argThat(state -> state.stage()==WorkItemStage.TO_SCHEDULE),any(),isNull(),eq("BACKFILL"));
  }
}
