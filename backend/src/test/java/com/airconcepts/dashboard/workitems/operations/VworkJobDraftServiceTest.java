package com.airconcepts.dashboard.workitems.operations;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.security.Role;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository;
import com.airconcepts.dashboard.xero.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class VworkJobDraftServiceTest {
  private final UUID entity=UUID.randomUUID(), item=UUID.randomUUID(), quoteId=UUID.randomUUID(), contactId=UUID.randomUUID();
  private final CurrentUser user=new CurrentUser(UUID.randomUUID().toString(),Role.DIRECTOR);
  private final VworkJobDraftContext contexts=mock(VworkJobDraftContext.class);
  private final XeroConnectionService connections=mock(XeroConnectionService.class);
  private final XeroQuoteClient quotes=mock(XeroQuoteClient.class);
  private final XeroContactClient contacts=mock(XeroContactClient.class);
  private final VworkJobDraftService service=new VworkJobDraftService(contexts,connections,quotes,contacts);
  private final TenantAccess access=new TenantAccess(entity,UUID.randomUUID(),UUID.randomUUID(),"synthetic",
      Set.of("accounting.invoices.read","accounting.contacts.read"));
  private final VworkJobDraftContext.Context context=new VworkJobDraftContext.Context(quoteId,"Q-1","J-1",4,
      List.of(new VworkTemplatesRepository.Template("template-a",true)));
  private XeroQuoteClient.Quote quote() {
    var line=new XeroQuoteClient.Line("Install",new BigDecimal("2"),new BigDecimal("123"),new BigDecimal("246"),
        new BigDecimal("36.9"),null,null,null,null,List.of());
    return new XeroQuoteClient.Quote(quoteId,"Q-1","J-1",XeroQuoteClient.Status.ACCEPTED,contactId,"Synthetic customer",
        null,null,Instant.parse("2026-11-20T00:00:00Z"),new BigDecimal("282.9"),"NZD","EXCLUSIVE",List.of(line));
  }
  @Test void gl12_prefillsContactAndPriceFreeScopeWithoutGuessingSiteAddress() {
    when(contexts.load(user,entity,item)).thenReturn(context);
    when(connections.authorisedAccess(user,entity)).thenReturn(access);
    when(quotes.detail(access,quoteId)).thenReturn(quote());
    when(contacts.detail(access,contactId)).thenReturn(new XeroContactClient.Contact(contactId,"Synthetic customer",
        "Ada","Example",null,List.of(),List.of(new XeroContactClient.Phone("DEFAULT","555123",null,null)),List.of()));
    var draft=service.draft(user,entity,item);
    assertThat(draft.reference()).isEqualTo("J-1");
    assertThat(draft.contactPerson()).isEqualTo("Ada Example");
    assertThat(draft.phone()).isEqualTo("555123");
    assertThat(draft.siteAddress()).isNull();
    assertThat(draft.missingFields()).contains("siteAddress");
    assertThat(draft.scope()).singleElement().satisfies(line -> {
      assertThat(line.description()).isEqualTo("Install");
      assertThat(line.quantity()).isEqualTo("2");
      assertThat(Arrays.stream(line.getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
          .containsExactly("description","quantity");
    });
    verify(contexts,times(2)).load(user,entity,item);
  }
  @Test void gl06_missingScopeStopsBeforeContactProviderCall() {
    when(contexts.load(user,entity,item)).thenReturn(context);
    var readOnly=new TenantAccess(entity,access.tenantId(),access.grantId(),"synthetic",Set.of("accounting.invoices.read"));
    when(connections.authorisedAccess(user,entity)).thenReturn(readOnly);
    when(quotes.detail(readOnly,quoteId)).thenReturn(quote());
    assertThatThrownBy(() -> service.draft(user,entity,item)).isInstanceOfSatisfying(ApiFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
    verifyNoInteractions(contacts);
  }
  @Test void gl06_connectionChangeDoesNotReadContactInAnotherTenant() {
    when(contexts.load(user,entity,item)).thenReturn(context);
    var other=new TenantAccess(entity,UUID.randomUUID(),access.grantId(),"synthetic",access.grantedScopes());
    when(connections.authorisedAccess(user,entity)).thenReturn(access,other);
    when(quotes.detail(access,quoteId)).thenReturn(quote());
    assertThatThrownBy(() -> service.draft(user,entity,item)).isInstanceOfSatisfying(ApiFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("WORK_ITEM_CHANGED"));
    verifyNoInteractions(contacts);
  }
  @Test void gl06_deniedContextDoesNotReadAnyProviderData() {
    when(contexts.load(user,entity,item)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
    assertThatThrownBy(() -> service.draft(user,entity,item)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(connections,quotes,contacts);
  }
  @Test void gl06_contactPermissionRevokedDuringReadPreventsDraftReturn() {
    when(contexts.load(user,entity,item)).thenReturn(context);
    var revoked=new TenantAccess(entity,access.tenantId(),access.grantId(),"synthetic",Set.of("accounting.invoices.read"));
    when(connections.authorisedAccess(user,entity)).thenReturn(access,access,revoked);
    when(quotes.detail(access,quoteId)).thenReturn(quote());
    when(contacts.detail(access,contactId)).thenReturn(new XeroContactClient.Contact(contactId,"Synthetic",
        null,null,null,List.of(),List.of(),List.of()));
    assertThatThrownBy(() -> service.draft(user,entity,item)).isInstanceOfSatisfying(ApiFailure.class,
        failure -> assertThat(failure.code()).isEqualTo("XERO_SCOPE_REQUIRED"));
  }
}
