package vn.ticketscenter.fulfillment.refund.controller;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

/** Typed, bounded request parsing: array/string coercion must not change the API contract. */
final class RefundRequestJson {
    private final String source;
    private int position;
    private RefundRequestJson(String source){this.source=source;}
    static CreateRefundRequestDto parse(String source){return new RefundRequestJson(source).object();}
    private CreateRefundRequestDto object(){
        expect('{');Set<String> fields=new HashSet<>();UUID order=null;String reason=null;List<UUID> tickets=null;
        do {
            String key=string();if(!fields.add(key))throw RefundException.invalid();expect(':');
            switch(key){
                case "orderId" -> order=RefundWeb.id(string());
                case "reason" -> reason=string();
                case "ticketIds" -> {
                    expect('[');tickets=new ArrayList<>();
                    do {if(tickets.size()==8)throw RefundException.invalid();tickets.add(RefundWeb.id(string()));}while(take(','));
                    expect(']');
                }
                default -> throw RefundException.invalid();
            }
        }while(take(','));
        expect('}');space();if(position!=source.length()||fields.size()!=3)throw RefundException.invalid();
        return new CreateRefundRequestDto(order,tickets,reason);
    }
    private String string(){
        expect('"');StringBuilder result=new StringBuilder();
        while(position<source.length()){
            char value=source.charAt(position++);if(value=='"')return result.toString();
            if(value<0x20)throw RefundException.invalid();
            if(value=='\\'){
                if(position==source.length())throw RefundException.invalid();
                value=switch(source.charAt(position++)){
                    case '"' -> '"';case '\\' -> '\\';case '/' -> '/';
                    case 'b' -> '\b';case 'f' -> '\f';case 'n' -> '\n';case 'r' -> '\r';case 't' -> '\t';
                    case 'u' -> unicode();default -> throw RefundException.invalid();
                };
            }
            result.append(value);
            if(result.length()>500)throw RefundException.invalid();
        }
        throw RefundException.invalid();
    }
    private char unicode(){
        if(position+4>source.length())throw RefundException.invalid();int value=0;
        for(int i=0;i<4;i++){
            char c=source.charAt(position++);int digit=c>='0'&&c<='9'?c-'0':c>='a'&&c<='f'?c-'a'+10:c>='A'&&c<='F'?c-'A'+10:-1;
            if(digit<0)throw RefundException.invalid();value=value*16+digit;
        }
        return (char)value;
    }
    private void space(){while(position<source.length()&&" \t\r\n".indexOf(source.charAt(position))>=0)position++;}
    private boolean take(char token){space();if(position<source.length()&&source.charAt(position)==token){position++;return true;}return false;}
    private void expect(char token){if(!take(token))throw RefundException.invalid();}
}
