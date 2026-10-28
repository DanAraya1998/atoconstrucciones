package com.daraya.atoconstrucciones.documents.fiscal;
import com.daraya.atoconstrucciones.shared.ApiException;
import tools.jackson.databind.json.JsonMapper;
public final class FiscalParserMain {
    private FiscalParserMain() {}
    public static void main(String[] args)throws Exception {
        try {System.out.print(JsonMapper.builder().build().writeValueAsString(FiscalParser.parse(System.in.readNBytes(5*1024*1024+1))));}
        catch(ApiException e) {System.out.print(JsonMapper.builder().build().writeValueAsString(java.util.Map.of("error",e.code(),"detail",e.getMessage())));System.exit(2);}
    }
}
