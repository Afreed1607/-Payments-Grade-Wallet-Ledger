package com.example.wallet.transfer;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotNull; import java.util.Map; import java.util.UUID; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1") public class TransferController {
 private final TransferService service; public TransferController(TransferService service){this.service=service;}
 @PostMapping("/transfers") ResponseEntity<Map<String,Object>> peer(@RequestHeader("X-User-Id")long u,@RequestHeader("Idempotency-Key")UUID k,@Valid @RequestBody Peer r){return reply(service.move(u,k,"PEER_TRANSFER",r.sourceAccountId,r.destinationAccountId,r.amountPaise,r.note));}
 @PostMapping("/transfers/topup") ResponseEntity<Map<String,Object>> topup(@RequestHeader("X-User-Id")long u,@RequestHeader("Idempotency-Key")UUID k,@Valid @RequestBody Cash r){return reply(service.move(u,k,"TOPUP",r.systemAccountId,r.walletAccountId,r.amountPaise,r.note));}
 @PostMapping("/transfers/withdraw") ResponseEntity<Map<String,Object>> withdraw(@RequestHeader("X-User-Id")long u,@RequestHeader("Idempotency-Key")UUID k,@Valid @RequestBody Cash r){return reply(service.move(u,k,"WITHDRAWAL",r.walletAccountId,r.systemAccountId,r.amountPaise,r.note));}
 @PostMapping("/admin/risk/{id}/decision") Map<String,Object> decide(@RequestHeader("X-Admin-Id")long a,@RequestHeader("Idempotency-Key")UUID ignored,@PathVariable long id,@RequestBody Decision d){return service.decide(a,id,d.release);}
 private ResponseEntity<Map<String,Object>> reply(TransferService.Result r){return ResponseEntity.status(r.status()).body(r.body());}
 public record Peer(@NotNull Long sourceAccountId,@NotNull Long destinationAccountId,@NotNull Long amountPaise,String note){} public record Cash(@NotNull Long walletAccountId,@NotNull Long systemAccountId,@NotNull Long amountPaise,String note){} public record Decision(boolean release){}
}
