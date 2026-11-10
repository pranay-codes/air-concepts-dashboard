package com.airconcepts.dashboard.forecast;
import com.airconcepts.dashboard.forecast.ForecastAdjustmentRepository.AdjustmentState;
import com.airconcepts.dashboard.security.CurrentUser;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class ForecastStoredDocuments {
  public record StoredDocument(UUID id,String type,String number,String contact,String status,boolean outstanding,LocalDate dueDate,LocalDate xeroExpectedDate,
      @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amountDue,AdjustmentState adjustment){}
  public record DocumentPage(List<StoredDocument> items,int page,int pageSize,long itemCount){}
  private final JdbcTemplate jdbc;private final ForecastAccess access;private final ForecastAdjustmentRepository adjustments;
  public ForecastStoredDocuments(JdbcTemplate jdbc,ForecastAccess access,ForecastAdjustmentRepository adjustments){this.jdbc=jdbc;this.access=access;this.adjustments=adjustments;}
  public StoredDocument get(CurrentUser user,UUID entity,UUID id){
    access.requireRead(user,entity);
    return jdbc.query("select * from public.forecast_documents where entity_id=? and xero_document_id=?",(r,n)->{
      var due=r.getDate("due_date");var expected=r.getDate("xero_expected_date");
      return new StoredDocument(id,r.getString("document_type"),r.getString("number"),r.getString("contact_name"),r.getString("status"),r.getBoolean("outstanding"),
          due==null?null:due.toLocalDate(),expected==null?null:expected.toLocalDate(),r.getBigDecimal("amount_due"),adjustments.state(entity,id));
    },entity,id).stream().findFirst().orElseThrow(ForecastValidation::missing);
  }
  public DocumentPage list(CurrentUser user,UUID entity,String type,boolean settled,int page,int size){
    access.requireRead(user,entity);
    if(type!=null&&!Set.of("ACCREC","ACCPAY").contains(type))throw ForecastValidation.invalid("type","Choose invoices or bills.");
    if(page<1||size<1||size>100)throw ForecastValidation.invalid("page","Use a positive page and 1–100 rows.");
    String where=" where entity_id=? and (? or outstanding) and (?::text is null or document_type=?)";
    var rows=jdbc.query("select * from public.forecast_documents"+where+" order by issue_date desc,xero_document_id limit ? offset ?",(r,n)->{
      UUID id=r.getObject("xero_document_id",UUID.class);var due=r.getDate("due_date");var expected=r.getDate("xero_expected_date");
      return new StoredDocument(id,r.getString("document_type"),r.getString("number"),r.getString("contact_name"),r.getString("status"),r.getBoolean("outstanding"),
          due==null?null:due.toLocalDate(),expected==null?null:expected.toLocalDate(),r.getBigDecimal("amount_due"),adjustments.state(entity,id));
    },entity,settled,type,type,size,(long)(page-1)*size);
    Long count=jdbc.queryForObject("select count(*) from public.forecast_documents"+where,Long.class,entity,settled,type,type);
    return new DocumentPage(rows,page,size,count==null?0:count);
  }
}
