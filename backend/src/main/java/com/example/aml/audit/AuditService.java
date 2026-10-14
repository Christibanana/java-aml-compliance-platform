package com.example.aml.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AuditService {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public AuditService(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public void record(String action,String type,Object id,String result,Map<String,?> details){
        var auth=SecurityContextHolder.getContext().getAuthentication(); String actor=auth==null?"system":auth.getName();
        try { jdbc.update("insert into sys_audit_log(id,actor,action,resource_type,resource_id,result,details_json) values (?,?,?,?,?,?,?)",UUID.randomUUID(),actor,action,type,id==null?null:id.toString(),result,json.writeValueAsString(details)); }
        catch(Exception ex){ throw new IllegalStateException("审计日志写入失败",ex); }
    }
}
