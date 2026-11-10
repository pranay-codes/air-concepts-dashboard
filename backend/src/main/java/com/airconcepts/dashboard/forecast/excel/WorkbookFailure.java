package com.airconcepts.dashboard.forecast.excel;
import com.airconcepts.dashboard.web.ApiFailure;
import java.util.List;
import org.springframework.http.HttpStatus;
public class WorkbookFailure extends ApiFailure {
  public record Problem(String sheet,String cell,String expected){}
  private final List<Problem> problems;
  public WorkbookFailure(String code,String message,List<Problem> problems){super(HttpStatus.UNPROCESSABLE_ENTITY,code,message);this.problems=List.copyOf(problems);}
  public List<Problem> problems(){return problems;}
}
