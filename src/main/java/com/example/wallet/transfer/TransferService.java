package com.example.wallet.transfer;

import com.example.wallet.common.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Explicit SQL keeps the monetary transaction, locks, and cache updates reviewable. */
@Service
public class TransferService {
  private final JdbcTemplate jdbc; private final ObjectMapper json;
  public TransferService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

  @Transactional(isolation=Isolation.READ_COMMITTED)
  public Result move(long actor, UUID key, String type, long sourceId, long destinationId, long amount, String note) {
    if(amount<=0 || sourceId==destinationId) throw error("VALIDATION_ERROR","Amount must be positive and accounts must differ.",HttpStatus.BAD_REQUEST);
    String hash=hash(type+"|"+sourceId+"|"+destinationId+"|"+amount+"|"+(note==null?"":note));
    if(jdbc.update("INSERT INTO idempotency_keys(user_id,key,request_hash,status) VALUES (?,?,?,'IN_PROGRESS') ON CONFLICT (user_id,key) DO NOTHING",actor,key,hash)==0) return replay(actor,key,hash);
    List<Account> locked=jdbc.query("SELECT id,user_id,account_type,balance_paise,status FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",(rs,i)->new Account(rs.getLong(1),(Long)rs.getObject(2),rs.getString(3),rs.getLong(4),rs.getString(5)),sourceId,destinationId);
    if(locked.size()!=2) throw error("RESOURCE_NOT_FOUND","One or both accounts do not exist.",HttpStatus.NOT_FOUND);
    Account source=locked.stream().filter(a->a.id==sourceId).findFirst().orElseThrow(); Account destination=locked.stream().filter(a->a.id==destinationId).findFirst().orElseThrow();
    // Customer-originated debits must belong to the caller.  Cash-in is a
    // controlled system counterparty; its corresponding customer credit must
    // still belong to the caller.
    if (wallet(source) && (source.userId == null || source.userId != actor)) throw error("FORBIDDEN","The source wallet is not owned by the caller.",HttpStatus.FORBIDDEN);
    if (!wallet(source) && (destination.userId == null || destination.userId != actor)) throw error("FORBIDDEN","The destination wallet is not owned by the caller.",HttpStatus.FORBIDDEN);
    if(!"ACTIVE".equals(source.status)||!"ACTIVE".equals(destination.status)) throw error("ACCOUNT_UNAVAILABLE","Both accounts must be active.",HttpStatus.UNPROCESSABLE_ENTITY);
    if(wallet(source)&&source.balance<amount) throw error("INSUFFICIENT_FUNDS","The wallet does not have sufficient funds.",HttpStatus.UNPROCESSABLE_ENTITY);
    Risk risk=risk(sourceId,destinationId,amount); String state=risk.hold?"HELD":"COMPLETED";
    long id=jdbc.queryForObject("INSERT INTO transfers(transfer_type,status,amount_paise,source_account_id,destination_account_id,note) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,type,state,amount,sourceId,destinationId,note);
    audit(id,null,state,"user:"+actor,risk.reason); if(risk.ruleId!=null) jdbc.update("INSERT INTO risk_flags(transfer_id,rule_id,risk_label,explanation) VALUES (?,?,?,?)",id,risk.ruleId,risk.hold?"review":"likely_ok",risk.reason);
    if(!risk.hold) post(id,sourceId,destinationId,amount);
    Map<String,Object> body=Map.of("id",id,"status",state,"amount_paise",amount,"currency","INR"); int http=risk.hold?202:201;
    jdbc.update("UPDATE idempotency_keys SET status='COMPLETED',response_status=?,response_body=CAST(? AS jsonb),transfer_id=?,updated_at=now() WHERE user_id=? AND key=?",http,write(body),id,actor,key);
    return new Result(http,body);
  }
  @Transactional
  public Map<String,Object> decide(long admin,long id,boolean release) {
    Map<String,Object> t=jdbc.queryForMap("SELECT * FROM transfers WHERE id=? FOR UPDATE",id); if(!"HELD".equals(t.get("status"))) throw error("INVALID_STATE","Only held transfers can be decided.",HttpStatus.CONFLICT);
    if(release) { long s=((Number)t.get("source_account_id")).longValue(),d=((Number)t.get("destination_account_id")).longValue(),a=((Number)t.get("amount_paise")).longValue(); List<Account> l=jdbc.query("SELECT id,user_id,account_type,balance_paise,status FROM accounts WHERE id IN (?,?) ORDER BY id FOR UPDATE",(rs,i)->new Account(rs.getLong(1),(Long)rs.getObject(2),rs.getString(3),rs.getLong(4),rs.getString(5)),s,d); Account source=l.stream().filter(x->x.id==s).findFirst().orElseThrow(); if(wallet(source)&&source.balance<a) throw error("INSUFFICIENT_FUNDS","Funds are no longer available.",HttpStatus.UNPROCESSABLE_ENTITY); post(id,s,d,a); jdbc.update("UPDATE transfers SET status='COMPLETED',updated_at=now() WHERE id=?",id); audit(id,"HELD","COMPLETED","admin:"+admin,"risk hold released"); } else { jdbc.update("UPDATE transfers SET status='FAILED',updated_at=now() WHERE id=?",id); audit(id,"HELD","FAILED","admin:"+admin,"risk hold rejected"); }
    return Map.of("id",id,"status",release?"COMPLETED":"FAILED");
  }
  private Result replay(long user,UUID key,String hash) { return jdbc.query("SELECT request_hash,status,response_status,response_body::text FROM idempotency_keys WHERE user_id=? AND key=?",rs->{ if(!rs.next()||!"COMPLETED".equals(rs.getString(2))) throw error("REQUEST_IN_PROGRESS","The request is still running; retry shortly.",HttpStatus.CONFLICT); if(!hash.equals(rs.getString(1))) throw error("IDEMPOTENCY_KEY_REUSED","The idempotency key was used with a different request.",HttpStatus.UNPROCESSABLE_ENTITY); try{return new Result(rs.getInt(3),json.readValue(rs.getString(4),new TypeReference<>(){}));}catch(Exception e){throw new IllegalStateException(e);}},user,key); }
  private Risk risk(long source,long destination,long amount) { for(Map<String,Object> r:jdbc.queryForList("SELECT id,rule_code,threshold_value,threshold_window_seconds,action FROM risk_rules WHERE enabled")){ String code=(String)r.get("rule_code"); boolean fires=false; if("VELOCITY".equals(code)){Long n=jdbc.queryForObject("SELECT count(*) FROM transfers WHERE source_account_id=? AND status='COMPLETED' AND created_at>=now()-(?*interval '1 second')",Long.class,source,((Number)r.get("threshold_window_seconds")).longValue());fires=n>((Number)r.get("threshold_value")).longValue();} else if("AMOUNT_SPIKE".equals(code)){Long avg=jdbc.queryForObject("SELECT COALESCE(avg(amount_paise),0)::bigint FROM transfers WHERE source_account_id=? AND status='COMPLETED' AND created_at>=now()-interval '30 days'",Long.class,source);fires=avg>0&&amount>avg*((Number)r.get("threshold_value")).longValue();} else if("NEW_PAYEE_LARGE_AMOUNT".equals(code)){Long n=jdbc.queryForObject("SELECT count(*) FROM transfers WHERE source_account_id=? AND destination_account_id=? AND status='COMPLETED'",Long.class,source,destination);fires=n==0&&amount>((Number)r.get("threshold_value")).longValue();} if(fires)return new Risk(((Number)r.get("id")).longValue(),"HOLD".equals(r.get("action")),"Risk rule "+code+" matched."); }return new Risk(null,false,"posted"); }
  private void post(long id,long source,long dest,long amount){jdbc.update("INSERT INTO ledger_entries(transfer_id,account_id,entry_type,amount_paise) VALUES (?,?,'DEBIT',?),(?,?,'CREDIT',?)",id,source,amount,id,dest,amount);jdbc.update("UPDATE accounts SET balance_paise=balance_paise-?,updated_at=now() WHERE id=?",amount,source);jdbc.update("UPDATE accounts SET balance_paise=balance_paise+?,updated_at=now() WHERE id=?",amount,dest);}
  private void audit(long id,String old,String now,String actor,String reason){jdbc.update("INSERT INTO transfer_status_audits(transfer_id,previous_status,new_status,changed_by,reason) VALUES (?,?,?,?,?)",id,old,now,actor,reason);}
  private boolean wallet(Account a){return "MAIN_WALLET".equals(a.type)||"SAVINGS_WALLET".equals(a.type);} private String write(Map<String,Object>b){try{return json.writeValueAsString(b);}catch(Exception e){throw new IllegalStateException(e);}} private String hash(String x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}} private ApiException error(String c,String m,HttpStatus s){return new ApiException(c,m,s);}
  private record Account(long id,Long userId,String type,long balance,String status){} private record Risk(Long ruleId,boolean hold,String reason){} public record Result(int status,Map<String,Object> body){}
}
