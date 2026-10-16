package vn.ticketscenter.fulfillment.checkin.service;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import vn.ticketscenter.fulfillment.checkin.security.SignedQrDecoder;
import vn.ticketscenter.fulfillment.checkin.dao.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInScanCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
public class TicketCheckInService {
    private final TicketCheckInRepository repository;private final SignedQrDecoder decoder;private final CheckInScanCache cache;
    public TicketCheckInService(TicketCheckInRepository repository,SignedQrDecoder decoder,CheckInScanCache cache){
        this.repository=Objects.requireNonNull(repository);this.decoder=Objects.requireNonNull(decoder);this.cache=Objects.requireNonNull(cache);
    }
    public CheckInScanResponseDto scan(UUID actor,UUID key,CheckInScanRequestDto request){
        if(actor==null)throw new CheckInException(401,"UNAUTHENTICATED","Bạn chưa đăng nhập.");
        if(key==null)throw new CheckInException(400,"IDEMPOTENCY_REQUIRED","Cần mã yêu cầu để soát vé an toàn.");
        UUID ticket=null;String name=null;boolean invalid=false;
        if(request.qrContent()!=null){try{var decoded=decoder.decode(request.qrContent());ticket=decoded.ticketId();name=decoded.name();}
            catch(CheckInException e){if(!e.code().equals("INVALID_SIGNATURE"))throw e;invalid=true;}}
        String fingerprint;
        try{fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((request.eventId()+"\n"+
            (request.qrContent()!=null?"QR\n"+request.qrContent():"CODE\n"+request.ticketCode())).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
        // Redis reduces contention; SQL locks and durable request records remain authoritative.
        try(var lease=cache.lock(ticket)){
            var result=repository.scan(actor,key,request.eventId(),ticket,request.ticketCode(),name,fingerprint,invalid);
            cache.completed(actor,key,result);return result;
        }
    }
    public List<RecentCheckInLogDto> recent(UUID actor,UUID event,int limit){
        if(actor==null)throw new CheckInException(401,"UNAUTHENTICATED","Bạn chưa đăng nhập.");
        if(limit<1 || limit>100)throw new CheckInException(400,"INVALID_PAGE_SIZE","Số dòng nhật ký phải từ 1 đến 100.");
        return repository.recent(actor,event,limit);
    }
}
