package com.nianliu.opshub.common.opslog;
import com.alibaba.fastjson2.JSON;
import java.util.*;
public final class OpsLogDiffer {
 private OpsLogDiffer() {}
 public static List<FieldChange> diffFields(Object before,Object after) {
  Map<String,Object> old=before==null?Map.of():JSON.parseObject(JSON.toJSONString(before));
  Map<String,Object> next=after==null?Map.of():JSON.parseObject(JSON.toJSONString(after));
  Set<String> keys=new TreeSet<>(old.keySet()); keys.addAll(next.keySet()); List<FieldChange> changes=new ArrayList<>();
  for(String key:keys) { Object a=old.get(key),b=next.get(key); boolean equal=Objects.equals(a,b);
   if(a instanceof Number && b instanceof Number) equal=new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()))==0;
   if(!equal) changes.add(new FieldChange(key,a,b));
  } return List.copyOf(changes);
 }
}