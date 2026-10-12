package vn.ticketscenter.fulfillment.refund.controller;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundDecisionRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

/** Only bounded strings/null are accepted; arrays, numbers and duplicate fields never coerce into text. */
final class AdminRefundJson {
    private final String source;private int position;
    private AdminRefundJson(String source){this.source=source;}
    static AdminRefundDecisionRequestDto decision(String source) {
        var fields=new AdminRefundJson(source).object();
        if(!fields.containsKey("decision")||!Set.of("decision","note","rejectReason","rejectionReason").containsAll(fields.keySet()))throw RefundException.invalid();
        if(fields.containsKey("rejectReason")&&fields.containsKey("rejectionReason"))throw RefundException.invalid();
        return new AdminRefundDecisionRequestDto(fields.get("decision"),fields.get("note"),fields.getOrDefault("rejectReason",fields.get("rejectionReason")));
    }
    static void retry(String source){if(!new AdminRefundJson(source).object().isEmpty())throw RefundException.invalid();}
    private Map<String,String> object(){
        var fields=new HashMap<String,String>();expect('{');
        if(!take('}')){do{String key=string();if(fields.containsKey(key))throw RefundException.invalid();expect(':');
            String value;if(takeNull())value=null;else value=string();fields.put(key,value);
        }while(take(','));expect('}');}
        space();if(position!=source.length())throw RefundException.invalid();return fields;
    }
    private boolean takeNull(){space();if(source.startsWith("null",position)){position+=4;return true;}return false;}
    private String string(){expect('"');var result=new StringBuilder();while(position<source.length()){
        char value=source.charAt(position++);if(value=='"')return result.toString();if(value<0x20)throw RefundException.invalid();
        if(value=='\\'){if(position==source.length())throw RefundException.invalid();value=switch(source.charAt(position++)){
            case '"'->'"';case '\\'->'\\';case '/'->'/';case 'b'->'\b';case 'f'->'\f';case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case 'u'->unicode();default->throw RefundException.invalid();};}
        result.append(value);if(result.length()>1000)throw RefundException.invalid();
    }throw RefundException.invalid();}
    private char unicode(){if(position+4>source.length())throw RefundException.invalid();int value=0;for(int i=0;i<4;i++){
        char c=source.charAt(position++);int digit=c>='0'&&c<='9'?c-'0':c>='a'&&c<='f'?c-'a'+10:c>='A'&&c<='F'?c-'A'+10:-1;
        if(digit<0)throw RefundException.invalid();value=value*16+digit;
    }return (char)value;}
    private void space(){while(position<source.length()&&" \t\r\n".indexOf(source.charAt(position))>=0)position++;}
    private boolean take(char c){space();if(position<source.length()&&source.charAt(position)==c){position++;return true;}return false;}
    private void expect(char c){if(!take(c))throw RefundException.invalid();}
}
