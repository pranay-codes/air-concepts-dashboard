package com.airconcepts.dashboard.forecast.excel;
import com.airconcepts.dashboard.forecast.excel.WorkbookFailure.Problem;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
@Component
public class WorkbookValidator {
  public static final int MAX_UPLOAD_BYTES=5*1024*1024;
  public static final long MAX_INFLATED_BYTES=50L*1024*1024;
  private static final Set<String> FUNCTIONS=Set.of("IF","OR","SUM","SUMIFS","MAX","DATE","YEAR","MONTH","EDATE","INDEX","MATCH");
  private static final Pattern FUNCTION=Pattern.compile("([A-Za-z_][A-Za-z0-9_.]*)\\s*\\(");
  static {ZipSecureFile.setMinInflateRatio(0.01);ZipSecureFile.setMaxEntrySize(MAX_INFLATED_BYTES);}
  public XSSFWorkbook validate(String filename,byte[] bytes){
    if(filename==null||!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")||bytes==null||bytes.length>MAX_UPLOAD_BYTES||bytes.length<4
        ||bytes[0]!='P'||bytes[1]!='K')throw invalid("Workbook","","Upload one .xlsx file no larger than 5 MiB.");
    checkZip(bytes);XSSFWorkbook workbook=null;
    try {
      workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes));validateStructure(workbook);return workbook;
    }catch(WorkbookFailure failure){close(workbook);throw failure;}
    catch(IOException|RuntimeException invalid){close(workbook);throw invalid("Workbook","","Use an unencrypted standard v1 OOXML workbook.");}
  }
  private void checkZip(byte[] bytes){
    boolean contentTypes=false,book=false;long inflated=0;Set<String> names=new HashSet<>();
    try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
      ZipEntry entry;byte[] buffer=new byte[8192];
      while((entry=zip.getNextEntry())!=null){
        String name=entry.getName().toLowerCase(Locale.ROOT);
        if(!names.add(name)||name.contains("vbaproject")||name.contains("externallinks/")||name.startsWith("/")||name.contains("../"))throw invalid("Workbook","","No macros, external links or invalid ZIP entries.");
        contentTypes|=name.equals("[content_types].xml");book|=name.equals("xl/workbook.xml");
        for(int n;(n=zip.read(buffer))!=-1;){inflated+=n;if(inflated>MAX_INFLATED_BYTES)throw invalid("Workbook","","Workbook expands beyond the 50 MiB safety limit.");}
        zip.closeEntry();
      }
      if(!contentTypes||!book)throw invalid("Workbook","","Upload a valid OOXML workbook.");
    }catch(IOException error){throw invalid("Workbook","","Upload a valid OOXML workbook.");}
  }
  private void validateStructure(XSSFWorkbook w){
    List<Problem> problems=new ArrayList<>();
    Set<String> expected=new HashSet<>(Set.of("Assumptions","Xero_Debtors","Xero_Creditors","Staffing"));int forecasts=0;
    for(var sheet:w){if(sheet.getSheetName().startsWith("Forecast"))forecasts++;else if(!expected.remove(sheet.getSheetName()))problems.add(new Problem(sheet.getSheetName(),"","Only v1 sheets are allowed."));}
    if(w.getNumberOfSheets()!=5||forecasts!=1||!expected.isEmpty())throw invalid("Workbook","","Required sheets: Assumptions, Forecast, Xero_Debtors, Xero_Creditors, Staffing.");
    if(w.isMacroEnabled()||!w.getExternalLinksTable().isEmpty()||w.getNumberOfNames()!=0)problems.add(new Problem("Workbook","","No macros, external links or defined names."));
    var a=w.getSheet("Assumptions");String[] headings={"Assumption","Value","Note"};for(int c=0;c<3;c++)text(a,2,c,headings[c],problems);
    String[] labels={"Forecast start month","Opening bank balance","Debtor payment delay after due date (days)","Creditor payment delay after due date (days)"};
    for(int i=0;i<labels.length;i++)text(a,i+3,0,labels[i],problems);
    for(String name:List.of("Xero_Debtors","Xero_Creditors")){
      var sheet=w.getSheet(name);String[] headers={"Contact","Invoice No","Invoice Date","Due Date","Amount Due","Override Pay Date","Forecast Month"};
      for(int c=0;c<headers.length;c++)text(sheet,3,c,headers[c],problems);
      for(int row:new int[]{4,203})contains(sheet,row,6,"Assumptions!$B$4",problems);
    }
    Sheet forecast=forecast(w);
    formula(forecast,2,1,"Assumptions!$B$4",problems);formula(forecast,4,1,"Assumptions!$B$5",problems);
    formula(forecast,25,1,"SUM(B8:B25)",problems);formula(forecast,66,1,"B50+B53+B66",problems);formula(forecast,67,1,"B5+B26-B67",problems);
    contains(forecast,52,1,"Staffing!$D$30:$BN$30",problems);
    var staffing=w.getSheet("Staffing");text(staffing,4,0,"First Name",problems);text(staffing,4,1,"Surname",problems);text(staffing,4,2,"Role",problems);text(staffing,4,7,"Monthly",problems);formula(staffing,29,3,"SUM(D23:D29)",problems);
    int cells=0;List<Problem> unsupported=new ArrayList<>();
    for(var sheet:w){
      if(sheet.getProtect())problems.add(new Problem(sheet.getSheetName(),"","Sheet protection must be off."));
      for(var row:sheet)for(var cell:row){
        if(++cells>20000)throw invalid("Workbook","","The standard v1 layout has at most 20,000 cells.");
        if(cell.getCellType()!=CellType.FORMULA)continue;
        String expr=cell.getCellFormula().replaceAll("\"(?:[^\"]|\"\")*\"", "\"\"");
        if(expr.contains("[")||expr.contains("]"))problems.add(problem(cell,"No external workbook references."));
        var functions=FUNCTION.matcher(expr);while(functions.find())if(!FUNCTIONS.contains(functions.group(1).toUpperCase(Locale.ROOT)))unsupported.add(problem(cell,"Use only supported v1 formula functions."));
      }
    }
    if(!unsupported.isEmpty())throw new WorkbookFailure("FORECAST_FORMULA_UNSUPPORTED","The workbook contains unsupported formulas.",unsupported);
    if(!problems.isEmpty())throw new WorkbookFailure("FORECAST_TEMPLATE_INVALID","Use the standard v1 forecast template.",problems);
  }
  public static Sheet forecast(Workbook w){for(var sheet:w)if(sheet.getSheetName().startsWith("Forecast"))return sheet;throw invalid("Workbook","","A Forecast sheet is required.");}
  private static void text(Sheet s,int r,int c,String expected,List<Problem> out){var cell=cell(s,r,c);if(cell==null||cell.getCellType()!=CellType.STRING||!cell.getStringCellValue().equals(expected))out.add(new Problem(s.getSheetName(),new CellReference(r,c).formatAsString(),expected));}
  private static void formula(Sheet s,int r,int c,String expected,List<Problem> out){var cell=cell(s,r,c);if(cell==null||cell.getCellType()!=CellType.FORMULA||!cell.getCellFormula().equals(expected))out.add(new Problem(s.getSheetName(),new CellReference(r,c).formatAsString(),expected));}
  private static void contains(Sheet s,int r,int c,String expected,List<Problem> out){var cell=cell(s,r,c);if(cell==null||cell.getCellType()!=CellType.FORMULA||!cell.getCellFormula().contains(expected))out.add(new Problem(s.getSheetName(),new CellReference(r,c).formatAsString(),"Formula referencing "+expected));}
  private static Cell cell(Sheet s,int r,int c){return s.getRow(r)==null?null:s.getRow(r).getCell(c);}
  static Problem problem(Cell c,String expected){return new Problem(c.getSheet().getSheetName(),c.getAddress().formatAsString(),expected);}
  public static WorkbookFailure invalid(String sheet,String cell,String expected){return new WorkbookFailure("FORECAST_TEMPLATE_INVALID","The workbook is not a supported forecast template.",List.of(new Problem(sheet,cell,expected)));}
  private static void close(XSSFWorkbook w){if(w!=null)try{w.close();}catch(IOException ignored){}}
}
