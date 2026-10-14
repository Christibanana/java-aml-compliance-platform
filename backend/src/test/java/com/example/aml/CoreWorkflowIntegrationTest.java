package com.example.aml;

import com.example.aml.transaction.AmlSimImportService;

import com.fasterxml.jackson.databind.*; import org.junit.jupiter.api.*; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.mock.web.MockMultipartFile; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.test.context.ActiveProfiles; import org.springframework.test.web.servlet.*; import java.nio.charset.StandardCharsets; import java.time.Instant; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CoreWorkflowIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords; @Autowired ObjectMapper json;
    private String bearer; private UUID adminId;

    @BeforeEach void prepareSchemaAndUser() throws Exception {
        jdbc.execute("create table if not exists sys_audit_log(id uuid primary key,actor varchar(80) not null,action varchar(80) not null,resource_type varchar(80) not null,resource_id varchar(100),result varchar(20) not null,client_ip varchar(64),details_json clob,occurred_at timestamp with time zone default current_timestamp not null)");
        jdbc.execute("create table if not exists aml_account_customer(account_id uuid not null,customer_id uuid not null,relation_type varchar(20) not null,ownership_percent numeric(5,2),created_at timestamp with time zone default current_timestamp not null,primary key(account_id,customer_id))");
        jdbc.execute("create table if not exists aml_rule(id uuid primary key,code varchar(30) unique not null,name varchar(150) not null,rule_type varchar(40) not null,enabled boolean not null,severity varchar(10) not null,active_version int not null,implementation_status varchar(20) default 'PLANNED' not null,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_rule_version(id uuid primary key,rule_id uuid not null,version_no int not null,parameters_json clob not null,description clob,created_by uuid,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0,unique(rule_id,version_no))");
        jdbc.execute("create table if not exists aml_rule_execution(id uuid primary key,execution_key varchar(160) unique not null,scope varchar(100) not null,started_at timestamp with time zone not null,finished_at timestamp with time zone,status varchar(20) not null,processed_count int default 0,hit_count int default 0,window_start timestamp with time zone,window_end timestamp with time zone,error_message clob,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_rule_hit(id uuid primary key,execution_id uuid not null,rule_id uuid not null,rule_version int not null,transaction_id uuid,customer_id uuid,hit_key varchar(200) unique not null,score numeric(8,2),explanation clob not null,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_rule_hit_transaction(rule_hit_id uuid not null,transaction_id uuid not null,primary key(rule_hit_id,transaction_id))");
        jdbc.execute("create table if not exists aml_alert(id uuid primary key,alert_no varchar(50) unique not null,rule_hit_id uuid unique not null,customer_id uuid,risk_level varchar(10) not null,status varchar(20) not null,assigned_to uuid,summary varchar(500) not null,closed_reason clob,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_alert_transaction(alert_id uuid not null,transaction_id uuid not null,primary key(alert_id,transaction_id))");
        jdbc.execute("create table if not exists aml_alert_history(id uuid primary key,alert_id uuid not null,from_status varchar(20),to_status varchar(20) not null,operator_id uuid,comment clob,occurred_at timestamp with time zone default current_timestamp,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_case(id uuid primary key,case_no varchar(50) unique not null,title varchar(200) not null,status varchar(30) not null,priority varchar(10) not null,investigator_id uuid,conclusion clob,submitted_at timestamp with time zone,closed_at timestamp with time zone,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_case_alert(case_id uuid not null,alert_id uuid unique not null,primary key(case_id,alert_id))");
        jdbc.execute("create table if not exists aml_case_note(id uuid primary key,case_id uuid not null,author_id uuid,content clob not null,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_case_evidence(id uuid primary key,case_id uuid not null,original_name varchar(255) not null,storage_key varchar(255) unique not null,content_type varchar(100) not null,size_bytes bigint not null,sha256 char(64) not null,uploaded_by uuid,active boolean default true not null,removed_at timestamp with time zone,removed_by uuid,removal_reason clob,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_case_history(id uuid primary key,case_id uuid not null,from_status varchar(30),to_status varchar(30) not null,operator_id uuid,comment clob,occurred_at timestamp with time zone default current_timestamp,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_screening_request(id uuid primary key,customer_id uuid,query_name varchar(200) not null,provider varchar(40) not null,provider_mode varchar(20) not null,status varchar(20) not null,requested_by uuid,error_message clob,completed_at timestamp with time zone,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_screening_match(id uuid primary key,request_id uuid not null,external_entity_id varchar(120),matched_name varchar(200) not null,score numeric(5,4) not null,dataset varchar(100),payload_json clob,data_mode varchar(20) not null,review_status varchar(30) default 'PENDING' not null,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_screening_review(id uuid primary key,match_id uuid not null,decision varchar(30) not null,comment clob not null,reviewer_id uuid,reviewed_at timestamp with time zone default current_timestamp,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_customer_risk(id uuid primary key,customer_id uuid unique not null,score numeric(8,2) not null,risk_level varchar(10) not null,model_version varchar(40) not null,explanation clob not null,source_data_mode varchar(20) default 'NONE' not null,calculated_by uuid,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_customer_risk_history(id uuid primary key,customer_id uuid not null,score numeric(8,2) not null,risk_level varchar(10) not null,model_version varchar(40) not null,explanation clob not null,source_data_mode varchar(20) default 'NONE' not null,calculated_by uuid,assessed_at timestamp with time zone default current_timestamp,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_transaction_risk(id uuid primary key,transaction_id uuid unique not null,score numeric(8,2) not null,risk_level varchar(10) not null,model_version varchar(40) not null,explanation clob not null,source_data_mode varchar(20) default 'NONE' not null,calculated_by uuid,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        jdbc.execute("create table if not exists aml_transaction_risk_history(id uuid primary key,transaction_id uuid not null,score numeric(8,2) not null,risk_level varchar(10) not null,model_version varchar(40) not null,explanation clob not null,source_data_mode varchar(20) default 'NONE' not null,calculated_by uuid,assessed_at timestamp with time zone default current_timestamp,created_at timestamp with time zone default current_timestamp,updated_at timestamp with time zone default current_timestamp,version bigint default 0)");
        UUID role=UUID.randomUUID(),user=UUID.randomUUID(); adminId=user; Instant now=Instant.now();
        jdbc.update("insert into sys_role(id,code,name,created_at,updated_at,version) values(?,?,?,?,?,0)",role,"SYSTEM_ADMIN","系统管理员",now,now);
        for(String code:List.of("CUSTOMER_READ","CUSTOMER_WRITE","ACCOUNT_WRITE","TRANSACTION_IMPORT","RULE_EXECUTE","ALERT_REVIEW","CASE_INVESTIGATE","REPORT_READ","AUDIT_READ","SCREENING_EXECUTE")){
            UUID permission=UUID.randomUUID();jdbc.update("insert into sys_permission(id,code,name,created_at,updated_at,version) values(?,?,?,?,?,0)",permission,code,code,now,now);jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)",role,permission);
        }
        jdbc.update("insert into sys_user(id,username,password_hash,display_name,enabled,failed_attempts,created_at,updated_at,version) values(?,?,?,?,true,0,?,?,0)",user,"admin",passwords.encode("ChangeMe123!"),"测试管理员",now,now);jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",user,role);
        UUID rule=UUID.randomUUID();jdbc.update("insert into aml_rule(id,code,name,rule_type,enabled,severity,active_version,implementation_status) values(?,?,?,?,true,?,1,'IMPLEMENTED')",rule,"AML-R001","单笔大额交易","LARGE_SINGLE_TRANSACTION","HIGH");jdbc.update("insert into aml_rule_version(id,rule_id,version_no,parameters_json,description) values(?,?,1,?,?)",UUID.randomUUID(),rule,"{\"amountThreshold\":100000}","教学阈值");
        MvcResult login=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}")) .andExpect(status().isOk()).andReturn();
        bearer="Bearer "+json.readTree(login.getResponse().getContentAsString()).path("token").asText();
    }

    @Test void completeCoreWorkflowIsAuthorizedAuditedAndIdempotent() throws Exception {
        mvc.perform(get("/api/customers")).andExpect(status().isUnauthorized());
        MvcResult customer=mvc.perform(post("/api/customers").header("Authorization",bearer).contentType("application/json").content("{\"customerNo\":\"CUS-E2E-001\",\"customerType\":\"INDIVIDUAL\",\"fullName\":\"Synthetic Demo Customer\",\"countryCode\":\"CN\",\"riskLevel\":\"HIGH\"}")) .andExpect(status().isOk()).andReturn();
        String customerId=json.readTree(customer.getResponse().getContentAsString()).path("id").asText();
        MvcResult screening=mvc.perform(post("/api/screenings").header("Authorization",bearer).contentType("application/json").content("{\"customerId\":\""+customerId+"\",\"name\":\"Demo Synthetic Candidate\",\"countryCode\":\"CN\",\"provider\":\"MOCK\",\"schema\":\"Person\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.request.dataMode").value("SIMULATED"))
                .andExpect(jsonPath("$.matches.length()").value(1)).andReturn();
        JsonNode screeningJson=json.readTree(screening.getResponse().getContentAsString());String screeningId=screeningJson.path("request").path("id").asText();String matchId=screeningJson.path("matches").get(0).path("id").asText();
        mvc.perform(post("/api/screenings/{requestId}/matches/{matchId}/review",screeningId,matchId).header("Authorization",bearer).contentType("application/json").content("{\"decision\":\"CONFIRMED_MATCH\",\"comment\":\"仅确认模拟候选用于集成测试\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matches[0].reviewStatus").value("CONFIRMED_MATCH"));
        mvc.perform(post("/api/accounts").header("Authorization",bearer).contentType("application/json").content("{\"accountNo\":\"ACC-E2E-001\",\"currency\":\"CNY\",\"customerId\":\""+customerId+"\",\"relationType\":\"OWNER\",\"ownershipPercent\":100}")) .andExpect(status().isOk());
        String csv="externalId,accountNo,counterpartyAccount,direction,amount,currency,transactionTime,transactionType,description\nTX-E2E-001,ACC-E2E-001,CP-001,IN,120000.00,CNY,2026-10-13T02:00:00Z,TRANSFER,synthetic";
        MockMultipartFile file=new MockMultipartFile("file","transactions.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/transactions/import").file(file).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
        mvc.perform(multipart("/api/transactions/import").file(file).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicates").value(1));
        MvcResult transactionPage=mvc.perform(get("/api/transactions?customerId="+customerId).header("Authorization",bearer)).andExpect(status().isOk()).andReturn();String transactionId=json.readTree(transactionPage.getResponse().getContentAsString()).path("content").get(0).path("id").asText();
        mvc.perform(post("/api/rules/execute").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.hits").value(1)).andExpect(jsonPath("$.alerts").value(1));
        mvc.perform(post("/api/rules/execute").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.hits").value(0)).andExpect(jsonPath("$.alerts").value(0));
        mvc.perform(post("/api/risk-scores/customers/{id}/calculate",customerId).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.sourceDataMode").value("SIMULATED"));
        mvc.perform(post("/api/risk-scores/customers/{id}/calculate",customerId).header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/risk-scores/customers/{id}/history",customerId).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/risk-scores/transactions/{id}/calculate",transactionId).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.sourceDataMode").value("SIMULATED"));
        mvc.perform(get("/api/risk-scores/transactions/{id}/history",transactionId).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        MvcResult alerts=mvc.perform(get("/api/alerts").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andReturn();String alertId=json.readTree(alerts.getResponse().getContentAsString()).path("content").get(0).path("id").asText();
        mvc.perform(post("/api/alerts/{id}/transition",alertId).header("Authorization",bearer).contentType("application/json").content("{\"status\":\"IN_REVIEW\",\"comment\":\"开始人工审核\"}")) .andExpect(status().isOk());
        MvcResult escalated=mvc.perform(post("/api/alerts/{id}/escalate",alertId).header("Authorization",bearer).contentType("application/json").content("{\"title\":\"Synthetic transaction investigation\"}")) .andExpect(status().isOk()).andReturn();String caseId=json.readTree(escalated.getResponse().getContentAsString()).path("caseId").asText();
        mvc.perform(post("/api/cases/{id}/assign",caseId).header("Authorization",bearer).contentType("application/json").content("{\"userId\":\""+adminId+"\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.caseInfo.investigatorId").value(adminId.toString()));
        mvc.perform(post("/api/cases/{id}/transition",caseId).header("Authorization",bearer).contentType("application/json").content("{\"status\":\"INVESTIGATING\",\"comment\":\"开始调查\"}")) .andExpect(status().isOk());
        mvc.perform(post("/api/cases/{id}/notes",caseId).header("Authorization",bearer).contentType("application/json").content("{\"content\":\"已核验合成交易来源，仅作教学演示。\"}")) .andExpect(status().isOk());
        MockMultipartFile evidenceFile=new MockMultipartFile("file","investigation.txt","text/plain","synthetic evidence".getBytes(StandardCharsets.UTF_8));
        MvcResult evidence=mvc.perform(multipart("/api/cases/{id}/evidence",caseId).file(evidenceFile).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.evidence.length()").value(1)).andReturn();
        String evidenceId=json.readTree(evidence.getResponse().getContentAsString()).path("evidence").get(0).path("id").asText();
        mvc.perform(get("/api/cases/{caseId}/evidence/{evidenceId}/download",caseId,evidenceId).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(content().bytes("synthetic evidence".getBytes(StandardCharsets.UTF_8)));
        mvc.perform(delete("/api/cases/{caseId}/evidence/{evidenceId}",caseId,evidenceId).header("Authorization",bearer).contentType("application/json").content("{\"reason\":\"replaced by final evidence\"}")) .andExpect(status().isOk()).andExpect(jsonPath("$.evidence.length()").value(0));
        MockMultipartFile finalEvidence=new MockMultipartFile("file","final-evidence.txt","text/plain","final synthetic evidence".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/cases/{id}/evidence",caseId).file(finalEvidence).header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.evidence.length()").value(1));
        mvc.perform(post("/api/cases/{id}/transition",caseId).header("Authorization",bearer).contentType("application/json").content("{\"status\":\"PENDING_REVIEW\",\"comment\":\"提交复核\"}")) .andExpect(status().isOk());
        mvc.perform(post("/api/cases/{id}/transition",caseId).header("Authorization",bearer).contentType("application/json").content("{\"status\":\"CLOSED\",\"comment\":\"复核完成并关闭\"}")) .andExpect(status().isOk());
        mvc.perform(post("/api/cases/{id}/notes",caseId).header("Authorization",bearer).contentType("application/json").content("{\"content\":\"closed mutation\"}")) .andExpect(status().isBadRequest());
        UUID auditRole=UUID.randomUUID(),auditUser=UUID.randomUUID();
        UUID auditPermission=jdbc.queryForObject("select id from sys_permission where code='AUDIT_READ'",UUID.class);
        jdbc.update("insert into sys_role(id,code,name,created_at,updated_at,version) values(?,?,?,?,?,0)",auditRole,"TEST_AUDITOR","测试审计员",Instant.now(),Instant.now());
        jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)",auditRole,auditPermission);
        jdbc.update("insert into sys_user(id,username,password_hash,display_name,enabled,failed_attempts,created_at,updated_at,version) values(?,?,?,?,true,0,?,?,0)",auditUser,"auditor",passwords.encode("Auditor123!"),"测试审计员",Instant.now(),Instant.now());
        jdbc.update("insert into sys_user_role(user_id,role_id) values(?,?)",auditUser,auditRole);
        MvcResult auditorLogin=mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"username\":\"auditor\",\"password\":\"Auditor123!\"}")) .andExpect(status().isOk()).andReturn();
        String auditorBearer="Bearer "+json.readTree(auditorLogin.getResponse().getContentAsString()).path("token").asText();
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").header("Authorization",auditorBearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").header("Authorization",auditorBearer)).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.customers").value(1))
                .andExpect(jsonPath("$.summary.alerts").value(1)).andExpect(jsonPath("$.summary.cases").value(1))
                .andExpect(jsonPath("$.customerRiskDistribution[1].name").value("MEDIUM"))
                .andExpect(jsonPath("$.customerRiskDistribution[1].value").value(1))
                .andExpect(jsonPath("$.ruleHits[0].code").value("AML-R001"))
                .andExpect(jsonPath("$.ruleHits[0].value").value(1))
                .andExpect(jsonPath("$.caseStatuses[3].name").value("CLOSED"))
                .andExpect(jsonPath("$.caseStatuses[3].value").value(1));
        mvc.perform(get("/api/reports/ALERTS/csv").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(header().string("X-Report-Rows","1"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ALT-")));
        mvc.perform(get("/api/reports/CUSTOMER_RISK/csv").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(header().string("X-Report-Rows","1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CUS-E2E-001")));
        mvc.perform(get("/api/audit-logs").header("Authorization",bearer).param("action","EXPORT").param("size","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].resourceType").value("REPORT"));
        mvc.perform(get("/api/audit-logs/export").header("Authorization",bearer).param("action","EXPORT"))
                .andExpect(status().isOk()).andExpect(header().string("X-Report-Rows","2"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REPORT")));
        mvc.perform(get("/api/transactions/integrations").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$[1].name").value("AMLSIM_CSV"))
                .andExpect(jsonPath("$[1].dataMode").value("SIMULATED"))
                .andExpect(jsonPath("$[2].name").value("FINERACT_API"))
                .andExpect(jsonPath("$[2].available").value(false));
        mvc.perform(post("/api/transactions/import/fineract/1").header("Authorization",bearer))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("EXTERNAL_INTEGRATION_FAILED"));
        String amlSimCsv=AmlSimImportService.HEADER+"\nE2E-AMLSIM-001,2026-10-14T08:00:00Z,250.00,TRANSFER,ACC-E2E-001,CP-AMLSIM,false,-1";
        MockMultipartFile amlSimFile=new MockMultipartFile("file","transactions.csv","text/csv",amlSimCsv.getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/transactions/import/amlsim").file(amlSimFile).param("currency","CNY").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(1));
        mvc.perform(multipart("/api/transactions/import/amlsim").file(amlSimFile).param("currency","CNY").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicates").value(1));
        assertThat(jdbc.queryForObject("select count(*) from aml_transaction where source='AMLSIM'",Long.class)).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from aml_alert",Long.class)).isEqualTo(1);assertThat(jdbc.queryForObject("select status from aml_case where id=?",String.class,UUID.fromString(caseId))).isEqualTo("CLOSED");assertThat(jdbc.queryForObject("select count(*) from aml_case_evidence where case_id=? and active=true",Long.class,UUID.fromString(caseId))).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from aml_customer_risk_history where customer_id=?",Long.class,UUID.fromString(customerId))).isEqualTo(2);assertThat(jdbc.queryForObject("select count(*) from sys_audit_log",Long.class)).isGreaterThanOrEqualTo(22);
    }
}
