package com.airconcepts.dashboard.forecast.sync;

import com.airconcepts.dashboard.entities.EntityService;
import com.airconcepts.dashboard.security.*;
import com.airconcepts.dashboard.invoices.InvoiceType;
import com.airconcepts.dashboard.web.*;
import com.airconcepts.dashboard.xero.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ForecastSyncService {
  public static final int MAX_SETTLEMENT_READS=200;
  public static final boolean INCREMENTAL_OPTIMISATION_ENABLED=false;
  private final ForecastDocumentRepository repository;
  private final XeroForecastClient client;
  private final XeroConnectionService connections;
  private final XeroReadHealth health;
  private final EntityService entities;
  private final Clock clock;
  public ForecastSyncService(ForecastDocumentRepository repository,XeroForecastClient client,XeroConnectionService connections,
      XeroReadHealth health,EntityService entities,Clock clock) {
    this.repository=repository;this.client=client;this.connections=connections;this.health=health;this.entities=entities;this.clock=clock;
  }
  public List<SyncRun> history(CurrentUser user,UUID entity) { authorised(user,entity);return repository.history(entity); }
  public SyncRun runManual(CurrentUser user,UUID entity) {
    authorised(user,entity);
    return run(entity,"MANUAL",UUID.fromString(user.subject()),()->connections.authorisedAccess(user,entity));
  }
  /** Scheduler only. Manual requests use the independently authorised entry point above. */
  public void runScheduled() {
    for(UUID entity:repository.connectedEntities()) {
      try { run(entity,"SCHEDULED",null,()->connections.systemAccess(entity)); }
      catch(RuntimeException failure) { /* Safe run diagnostics already persisted; continue with the next entity. */ }
    }
  }
  private void authorised(CurrentUser user,UUID entity) { user.requireRole(Role.DIRECTOR,Role.FINANCIAL_ADMIN);entities.get(user,entity); }
  private SyncRun run(UUID entity,String trigger,UUID actor,Supplier<TenantAccess> resolve) {
    var started=clock.instant(); UUID run=repository.begin(entity,trigger,actor,started); TenantAccess access=null;
    try {
      access=resolve.get();health.check(access);int rereads=0;
      for(var type:InvoiceType.values()) {
        Map<UUID,ForecastSource> documents=new LinkedHashMap<>();
        ForecastSource.Limits limits=new ForecastSource.Limits(null,null,null);long total=-1;int pages=-1;
        for(int page=1;;page++) {
          access=refreshAccess(resolve,access);
          health.check(access);
          var result=client.page(access,type,page,null);limits=result.limits();
          if(total<0) {total=result.itemCount();pages=result.pageCount();}
          else if(total!=result.itemCount()||pages!=result.pageCount()) throw new ProviderFailure("XERO_INVALID_RESPONSE","Xero paging changed; rerun the sync.",200);
          for(var doc:result.documents()) if(documents.putIfAbsent(doc.id(),doc)!=null) throw new ProviderFailure("XERO_INVALID_RESPONSE","Duplicate document across pages.",200);
          if(page>=pages) break;
        }
        var missing=new ArrayList<>(repository.outstanding(entity,type));missing.removeAll(documents.keySet());
        rereads+=missing.size();
        if(rereads>MAX_SETTLEMENT_READS) throw new ApiFailure(HttpStatus.CONFLICT,"FORECAST_SYNC_TOO_MANY_CHANGES","More than 200 settlement checks are required. Ask an administrator to review and rerun sync.");
        List<UUID> deleted=new ArrayList<>();
        for(UUID id:missing) {
          access=refreshAccess(resolve,access);
          health.check(access);
          try { var read=client.detailWithLimits(access,type,id);documents.put(id,read.document());limits=read.limits(); }
          catch(ProviderFailure failure) { if(failure.upstreamStatus()==404) deleted.add(id); else throw failure; }
        }
        repository.apply(entity,run,access,List.copyOf(documents.values()),deleted,limits,clock.instant());
      }
      health.success(access,started);repository.succeed(entity,run);return repository.get(entity,run);
    } catch(ApiFailure failure) {
      repository.fail(entity,run,failure.code(),failure instanceof ProviderFailure p?p.upstreamStatus():null);
      if(access!=null&&failure instanceof ProviderFailure p) health.failure(access,started,p);
      throw failure;
    } catch(RuntimeException failure) {
      repository.fail(entity,run,"FORECAST_SYNC_FAILED",null);
      throw new ApiFailure(HttpStatus.INTERNAL_SERVER_ERROR,"FORECAST_SYNC_FAILED","Sync did not complete. Check history and retry.");
    }
  }
  private TenantAccess refreshAccess(Supplier<TenantAccess> resolve,TenantAccess previous) {
    var current=resolve.get();
    if(!current.entityId().equals(previous.entityId())||!current.tenantId().equals(previous.tenantId())
        ||!current.grantId().equals(previous.grantId()))
      throw new ApiFailure(HttpStatus.CONFLICT,"FORECAST_SYNC_CONNECTION_CHANGED","The Xero connection changed. Rerun sync for the current connection.");
    return current;
  }
}
