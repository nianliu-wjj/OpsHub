package com.nianliu.opshub.bootstrap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.nianliu.opshub.common.validation.*;
import com.nianliu.opshub.common.opslog.*;
import com.nianliu.opshub.biz.shared.template.*;
import com.nianliu.opshub.domain.model.exception.*;
/**
 * @author nianliu-wjj
 * @since 2026-11-18
 */
class FrameworkTest {
 public static class Request {
  @jakarta.validation.constraints.Min(1) public int count=0;
  @SpelAssert(assertTrue="#this.count < 5", groups=ValidationGroups.Update.class) public String marker;
 }
 @Test void nativeAndGroupedValidation() {
  Request r=new Request(); assertThrows(ParamRejectException.class,()->RequestValidator.validate(r,"CREATE"));
  r.count=6; assertDoesNotThrow(()->RequestValidator.validate(r,"CREATE",ValidationGroups.Create.class));
  assertThrows(ParamRejectException.class,()->RequestValidator.validate(r,"UPDATE",ValidationGroups.Update.class));
  assertDoesNotThrow(()->RequestValidator.validate(1L,"DELETE"));
 }
 @Test void duplicateResultReachesCleanupAndGuardBlocksExecution() {
  DegradeGuard guard=new DegradeGuard(""); BizProcessTemplate template=new BizProcessTemplate(guard);
  var tail=new java.util.concurrent.atomic.AtomicReference<String>();
  var callback=new AbstractProcessCallback<Long,String>() {
   public String execute(Long r) { throw new DuplicateKeyException("exists"); }
   public String onDuplicate(Long r) { return "existing"; }
   public void postProcess(Long r,String result) { tail.set(result); }
  };
  assertEquals("existing",template.doInProcess("CREATE",1L,callback));assertEquals("existing",tail.get());
  guard.degrade("CREATE");assertThrows(OpsException.class,()->template.doInProcess("CREATE",1L,callback));
 }
 @Test void numericDiffIgnoresScale() { assertTrue(OpsLogDiffer.diffFields(java.util.Map.of("amount",new java.math.BigDecimal("1.0")),java.util.Map.of("amount",new java.math.BigDecimal("1.00"))).isEmpty()); }
}
