package com.nianliu.opshub.bootstrap;

import com.nianliu.opshub.infra.dal.repository.rbac.RbacRepository;
import com.nianliu.opshub.infra.dal.generator.RbacCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 不依赖外部服务的 SQL 参数与生成安全回归。
 * @author nianliu-wjj
 * @since 2026-11-18
 */
class RbacPersistenceTest {
    @TempDir
    Path output;
    @Test
    void queriesBindParametersAndNeverExposePassword() {
        JdbcTemplate jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_account(id bigint auto_increment primary key,username varchar(64),password varchar(100),icon varchar,email varchar,type int,real_name varchar,remark varchar,create_time timestamp,update_time timestamp,login_time timestamp,enable int,`lock` int,version bigint default 0,is_deleted int default 0)");
        jdbc.execute("CREATE TABLE sys_account_role(id bigint auto_increment primary key,account_id bigint,role_id bigint,unique(account_id,role_id))");
        jdbc.execute("CREATE TABLE sys_rbac_version(id bigint primary key,version bigint)");
        jdbc.update("INSERT INTO sys_rbac_version VALUES(1,1)");
        var repository=new RbacRepository(jdbc);
        long id=repository.insert("sys_account",new LinkedHashMap<>(Map.of("username","alice","password","hash","enable",1,"`lock`",0)));
        assertEquals(1,repository.page("accounts",1,20,"alice").total());
        assertEquals(0,repository.page("accounts",1,20,"' OR 1=1 --").total());
        assertFalse(repository.get("accounts",id).keySet().stream().anyMatch(k->k.equalsIgnoreCase("password")));
        assertEquals("hash",repository.credentials("alice").entrySet().stream().filter(e->e.getKey().equalsIgnoreCase("password")).findFirst().orElseThrow().getValue());
        repository.replaceAssignments("accounts",id,List.of(1L,1L,2L));
        assertEquals(2,repository.assignments("accounts",id).size());
        repository.changed();assertEquals(2,repository.version());
        jdbc.execute("CREATE TABLE sys_role(id bigint primary key,code varchar,status int,version bigint default 0,is_deleted int default 0)");
        jdbc.execute("CREATE TABLE sys_menu(id bigint primary key,parent_id bigint,name varchar,code varchar,url varchar,type int,sort int,hidden int,version bigint default 0,is_deleted int default 0)");
        jdbc.execute("CREATE TABLE sys_role_menu(role_id bigint,menu_id bigint)");
        jdbc.update("INSERT INTO sys_role(id,code,status) VALUES(1,'reader',1),(2,'disabled',0)");
        jdbc.update("INSERT INTO sys_menu(id,parent_id,name,code,url,type,sort,hidden) VALUES(1,0,'Accounts','sys:account','/account',2,1,0),(2,0,'Write','sys:account:add',null,3,2,0)");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) VALUES(1,1),(2,2)");
        assertEquals(Set.of("ROLE_READER","sys:account"),new HashSet<>(repository.authorities(id)));
        assertEquals(1,repository.grantedMenus(id).size());
        repository.update("sys_account",id,0,new LinkedHashMap<>(Map.of("email","changed@example.com")));
        assertThrows(com.nianliu.opshub.domain.model.exception.OpsException.class,()->repository.update("sys_account",id,0,new LinkedHashMap<>(Map.of("email","stale@example.com"))));
        repository.delete("accounts",id,1);
        assertNull(repository.credentials("alice"));assertEquals(0,repository.page("accounts",1,20,"").total());assertTrue(repository.assignments("accounts",id).isEmpty());
    }
    @Test
    void generatorDoesNotOverwriteExistingSourcesOrQueries() throws Exception {
        Path sql=Path.of("../sql/rbac-schema.sql");
        RbacCodeGenerator.main(new String[]{sql.toString(),output.toString()});
        Path entity=output.resolve("src/main/java/com/nianliu/opshub/infra/dal/dataobject/rbac/SysAccount.java");
        String original=Files.readString(entity);
        assertTrue(original.contains("@Column(value = \"lock\")"));
        assertTrue(original.contains("KeyType.Auto"));
        assertTrue(original.contains("version = true"));assertTrue(original.contains("isLogicDelete = true"));
        Path custom=output.resolve("src/main/resources/sqlmap/rbac/Custom.xml");Files.createDirectories(custom.getParent());Files.writeString(custom,"custom-query");
        assertThrows(IllegalStateException.class,()->RbacCodeGenerator.main(new String[]{sql.toString(),output.toString()}));
        assertEquals(original,Files.readString(entity));assertEquals("custom-query",Files.readString(custom));
    }
}
