package com.airconcepts.dashboard.forecast;
import static org.assertj.core.api.Assertions.*;
import com.airconcepts.dashboard.forecast.excel.*;
import com.airconcepts.dashboard.web.ApiFailure;
import java.nio.file.*;
import java.io.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
class WorkbookValidatorTest {
  private final WorkbookValidator validator=new WorkbookValidator();
  static byte[] fixture()throws IOException{return Files.readAllBytes(Path.of("src/test/resources/forecast/template-v1.xlsx"));}
  @Test void frUc04_12_v1FixturePasses()throws Exception{try(var workbook=validator.validate("template.xlsx",fixture())){assertThat(workbook.getNumberOfSheets()).isEqualTo(5);}}
  @Test void frUc04_12_rejectsTypeSizeMissingSheetsHeadersAndKeyFormulas()throws Exception{
    assertThatThrownBy(()->validator.validate("template.xlsm",fixture())).isInstanceOf(ApiFailure.class);
    assertThatThrownBy(()->validator.validate("template.xlsx",new byte[5*1024*1024+1])).isInstanceOf(ApiFailure.class);
    assertThatThrownBy(()->validator.validate("template.xlsx","not OOXML".getBytes())).isInstanceOf(ApiFailure.class);
    for(int kind=0;kind<3;kind++)try(var w=new XSSFWorkbook(new ByteArrayInputStream(fixture()));var out=new ByteArrayOutputStream()){
      if(kind==0)w.removeSheetAt(4);
      if(kind==1){var header=w.getSheet("Assumptions").getRow(2).getCell(0);header.setBlank();header.setCellValue("Renamed");}
      if(kind==2)w.getSheetAt(1).getRow(67).getCell(1).setCellFormula("B5+B26+B67");
      w.write(out);
      if(kind==1)try(var persisted=new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))){
        assertThat(persisted.getSheet("Assumptions").getRow(2).getCell(0).getStringCellValue()).as("Header mutation must survive serialization").isEqualTo("Renamed");
      }
      try(var accepted=validator.validate("template.xlsx",out.toByteArray())){fail("Mutation "+kind+" was accepted");}
      catch(WorkbookFailure failure){assertThat(failure.problems()).isNotEmpty();}
    }
  }
  @Test void frUc04_12_unsupportedFunctionNamesCell()throws Exception{
    try(var w=new XSSFWorkbook(new ByteArrayInputStream(fixture()));var out=new ByteArrayOutputStream()){
      w.getSheet("Assumptions").createRow(9).createCell(0).setCellFormula("SIN(1)");w.write(out);
      assertThatThrownBy(()->validator.validate("template.xlsx",out.toByteArray())).isInstanceOfSatisfying(WorkbookFailure.class,f->{
        assertThat(f.code()).isEqualTo("FORECAST_FORMULA_UNSUPPORTED");assertThat(f.problems()).anySatisfy(p->assertThat(p.cell()).isEqualTo("A10"));
      });
    }
  }
  @Test void frUc04_12_rejectsVbaAndExternalLinkPartsBeforePoiParsing()throws Exception{
    for(String part:java.util.List.of("xl/vbaProject.bin","xl/externalLinks/externalLink1.xml")){
      try(var input=new java.util.zip.ZipInputStream(new ByteArrayInputStream(fixture()));var bytes=new ByteArrayOutputStream();var zip=new java.util.zip.ZipOutputStream(bytes)){
        java.util.zip.ZipEntry entry;while((entry=input.getNextEntry())!=null){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));input.transferTo(zip);zip.closeEntry();}
        zip.putNextEntry(new java.util.zip.ZipEntry(part));zip.write("synthetic".getBytes());zip.closeEntry();zip.finish();
        assertThatThrownBy(()->validator.validate("template.xlsx",bytes.toByteArray())).isInstanceOf(ApiFailure.class);
      }
    }
  }
  @Test void frUc04_12_zipBombInflationIsRejectedBeforeWorkbookParsing()throws Exception{
    try(var bytes=new ByteArrayOutputStream();var zip=new java.util.zip.ZipOutputStream(bytes)){
      zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
      byte[] block=new byte[1024*1024];
      for(int i=0;i<51;i++)zip.write(block);
      zip.closeEntry();zip.finish();
      assertThat(bytes.size()).isLessThan(WorkbookValidator.MAX_UPLOAD_BYTES);
      assertThatThrownBy(()->validator.validate("template.xlsx",bytes.toByteArray()))
          .isInstanceOfSatisfying(WorkbookFailure.class,f->{
            assertThat(f.code()).isEqualTo("FORECAST_TEMPLATE_INVALID");
            assertThat(f.problems()).anySatisfy(p->assertThat(p.expected()).contains("50 MiB"));
          });
    }
  }
}
