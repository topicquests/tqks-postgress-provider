package org.topicquests.pg;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.topicquests.pg.PostgresConnection;
import org.topicquests.pg.PostgresConnectionFactory;
import org.topicquests.pg.api.IPostgresConnection;
import org.topicquests.pg.api.IPostgresConnectionFactory;

import org.topicquests.support.ResultPojo;
import org.topicquests.support.api.IResult;

import net.minidev.json.JSONObject;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Properties;

public class PostgresConnectionFactoryTest {

  private void initAll() {
    System.out.println("in initAll");
    setupTQAdminUser();
    setupDBTables();
  }

  private void setupDBTables() {
    System.out.println("in setupDBTables");
    assertEquals("tq_admin", provider.getUser());

    final String [] tableSchema = {
      "DROP INDEX IF EXISTS vid",
      "DROP INDEX IF EXISTS eid",
      "DROP TABLE IF EXISTS vertex",
      "DROP TABLE IF EXISTS edge",
          
      "CREATE TABLE IF NOT EXISTS vertex ("
      + "id VARCHAR(64) PRIMARY KEY,"
      + "json JSON NOT NULL)", 

      "CREATE TABLE IF NOT EXISTS edge ("
      + "id VARCHAR(64) PRIMARY KEY,"
      + "json JSON NOT NULL)",

      "CREATE UNIQUE INDEX vid ON vertex(id)",
      "CREATE UNIQUE INDEX eid ON edge(id)"
    };

    executeStatements(tableSchema);
  }

  @Test
  @DisplayName("SQL tests")
  void TestAll() {
    initAll();
    InsertAndSelect();
    InsertAndSelect2();
    BatchInsertAndSelect();
    updateRow1();
    updateRow2();
    rollbackTest();
    getRowCount();
    tearDownAll();
  }

  //
  // Test the connection to the topic map proxy database.
  //
  @Test
  @DisplayName("TQ Proxy Connection test")
  void TestTQProxy() {
    setupTQAdminUser();
    InsertAndSelect3();
    InsertAndSelect4();
    DeleteUser1();
    closeConnection();
    shutDownProvider();
  }

  void InsertAndSelect() {
    System.out.println("in InsertAndSelect");
    final String
        VERTEX_TABLE	= "vertex",
        EDGE_TABLE      = "edge",
        V_ID            = Long.toString(System.currentTimeMillis());

    assertEquals("tq_admin", provider.getUser());

    // Generate Some SQL
    JSONObject jo = new JSONObject();
    jo.put("Hello", "World");

    // Insert
    String sql = "INSERT INTO " + VERTEX_TABLE +
        " values('" + V_ID + "', '" + jo.toJSONString() + "')";

    IResult r = null;
    r = conn.beginTransaction();
    r = conn.executeSQL(sql, r);
    r = conn.endTransaction(r);
    if (r.hasError()) {
      fail(r.getErrorString());
    }
    
    // Select
    sql = "SELECT json FROM " + VERTEX_TABLE + " where id='" + V_ID + "'";
    r = conn.executeSelect(sql);
    if (r.hasError()) {
      fail("Error in SELECT: " + r.getErrorString());
    }
    
    Object o = r.getResultObject();
    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          assertEquals("{\"Hello\":\"World\"}", rs.getString("json"));
        }
      } catch (Exception e) {
        e.printStackTrace();
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }

  void InsertAndSelect2() {
    System.out.println("in InsertAndSelect2");
    final String
        VERTEX_TABLE	= "vertex",
        EDGE_TABLE      = "edge",
        V_ID            = Long.toString(System.currentTimeMillis());

    assertEquals("tq_admin", provider.getUser());

    // Generate Some SQL
    JSONObject jo = new JSONObject();
    jo.put("Hello", "World");

    String [] vals = new String [2];
    vals[0] = V_ID;
    vals[1] = jo.toJSONString();
    
    // Insert
    String sql = "INSERT INTO " + VERTEX_TABLE + " values(?, to_json(?::json))";
    IResult r = null;
    try {
      conn.beginTransaction();
      r = conn.executeSQL(sql, vals);
      conn.endTransaction();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Select
    sql = "SELECT json FROM " + VERTEX_TABLE + " where id=?";
    try {
      r = conn.executeSelect(sql, V_ID);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    Object o = r.getResultObject();

    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          assertEquals("{\"Hello\":\"World\"}", rs.getString("json"));
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }
  
  void BatchInsertAndSelect() {
    System.out.println("in BatchInsertAndSelect");
    final String VERTEX_TABLE = "vertex";
    final int numRows = 20;

    assertEquals("tq_admin", provider.getUser());

    // Generate Some SQL
    JSONObject jo = new JSONObject();
    jo.put("Hello", "World");

    String[] vals = new String[numRows * 2];
    for (int i = 0; i < numRows; i++) {
      int idx1 = 2*i;
      int idx2 = 2*i+1;
      vals[idx1] = Integer.toString(i);
      vals[idx2] = jo.toJSONString();
      System.out.println("row #" + i + " - (" + idx1 + "," + idx2 + ") {" + vals[idx1] + ", " + vals[idx2] + "}");
    }
    
    // Batch Insert
    String sql = "INSERT INTO " + VERTEX_TABLE + " values(?, to_json(?::json))";
    IResult r = null;
    try {
      conn.beginTransaction();
      r = conn.executeBatch(sql, vals);
      conn.endTransaction();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Select
    sql = "SELECT * FROM " + VERTEX_TABLE;
    try {
      r = conn.executeSelect(sql);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    Object o = r.getResultObject();

    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        while (rs.next()) {
          System.out.println("id: " + rs.getString("id") + ", json: " + rs.getString("json"));
          assertEquals("{\"Hello\":\"World\"}", rs.getString("json"));
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }
  
  void InsertAndSelect3() {
    System.out.println("in InsertAndSelect3");
    final String
        USERS_TABLE	= "tq_authentication.users";

    assertEquals("tq_admin", provider.getUser());

    setUserRole();

    // Insert into users table
    String sql = "INSERT INTO " + USERS_TABLE +
        " values('locator', 'test@email.org', 'testpwd', 'handle', " +
        "'Joe', 'User', 'en', true)";
    IResult r = null;

    try {
      conn.beginTransaction();
      r = conn.executeSQL(sql);
      conn.endTransaction();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Select
    sql = "SELECT * FROM " + USERS_TABLE + " WHERE locator = ?";
    try {
      r = conn.executeSelect(sql, "locator");
    } catch (Exception e) {
      fail(e.getMessage());
    }
    Object o = r.getResultObject();

    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          assertEquals("handle", rs.getString("handle"));
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }
  
  void InsertAndSelect4() {
    System.out.println("in InsertAndSelect4");
    final String TREE_TABLE = "tq_tree.conv";
    final String CONTEXT = "context1";

    assertEquals("tq_admin", provider.getUser());

    // Insert into conv tree table
    String sql = "INSERT INTO " + TREE_TABLE +
        " (context, lox, parent_lox) VALUES (?, ?, ?)";
    IResult r = null;

    String[] insert_vals = {CONTEXT, "loxroot", "",
                            CONTEXT, "lox1", "loxroot",
                            CONTEXT, "lox2", "loxroot",
                            CONTEXT, "lox3", "loxroot",
                            CONTEXT, "lox4", "lox1",
                            CONTEXT, "lox5", "lox1",
                            CONTEXT, "lox6", "lox2"
    };

    try {
      conn.setConvRole();
      conn.beginTransaction();
      r = conn.executeBatch(sql, insert_vals);;
      conn.endTransaction();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Select
    sql = "SELECT * FROM " + TREE_TABLE + " WHERE context = ?";
    try {
      r = conn.executeSelect(sql, CONTEXT);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    Object o = r.getResultObject();

    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        while (rs.next()) {
          System.out.println("---------------");
          System.out.println("id: " + rs.getInt("id"));
          System.out.println("context: " + rs.getString("context"));
          System.out.println("lox: " + rs.getString("lox"));
          System.out.println("parent: " + rs.getString("parent_lox"));
          System.out.println("path: " + rs.getString("parent_path"));
        }
        System.out.println("---------------");
      } catch (Exception e) {
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }
  
  void DeleteUser1() {
    System.out.println("in DeleteUser1");
    final String
        USERS_TABLE	= "tq_authentication.users";

    assertEquals("tq_admin", provider.getUser());

    setUserRole();

    // Insert into users table
    String sql = "DELETE FROM " + USERS_TABLE + " WHERE handle = 'handle'";

    IResult r = null;
    try {
      conn.beginTransaction();
      r = conn.executeSQL(sql);
      conn.endTransaction();
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }
  
  void updateRow1() {
    System.out.println("in updateRow1");
    final String VERTEX_TABLE = "vertex";
    String V_ID = "";

    // Select row with the max id.
    String sql = "SELECT max(id) FROM " + VERTEX_TABLE;
    IResult r = null;
    
    r = conn.beginTransaction();
    r = conn.executeSQL(sql, r);
    r = conn.endTransaction(r);
    if (r.hasError()) {
      fail(r.getErrorString());
    }
    
    Object o = r.getResultObject();
    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          V_ID = rs.getString(1);
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }
      
      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }

    // Update the json value in the row containing the max id.
    JSONObject jo = new JSONObject();
    jo.put("Goodbye", "World");

    String [] vals = new String [2];
    vals[0] = jo.toJSONString();
    vals[1] = V_ID;
    
    sql = "UPDATE " + VERTEX_TABLE + " SET json = to_json(?::json) WHERE id = ?";
    r = null;
    r = conn.beginTransaction();
    conn.executeSQL(sql, r, vals);
    conn.endTransaction(r);
    if (r.hasError()) {
      fail(r.getErrorString());
    }

    // Select updated row
    sql = "SELECT json FROM " + VERTEX_TABLE + " where id = ?";
    r = null;
    try {
      r = conn.executeSelect(sql, V_ID);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    o = r.getResultObject();

    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          assertEquals("{\"Goodbye\":\"World\"}", rs.getString("json"));
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }
      
      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }
  
  void updateRow2() {
    System.out.println("in updateRow2");
    final String VERTEX_TABLE = "vertex";
    String V_ID = "";
       
    // Select row with the max id.
    String sql = "SELECT max(id) FROM " + VERTEX_TABLE;
    IResult r = conn.executeSelect(sql);
    
    Object o = r.getResultObject();
    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          V_ID = rs.getString(1);
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }

    // Update the json value in the row containing the max id.
    JSONObject jo = new JSONObject();
    jo.put("Goodbye", "World");

    sql = "UPDATE " + VERTEX_TABLE + " SET json = '" + jo.toJSONString() +
        "' WHERE id = '" + V_ID + "'";
    r = conn.beginTransaction();
    conn.executeSQL(sql, r);
    conn.endTransaction(r);
    if (r.hasError()) {
      fail(r.getErrorString());
    }

    // Select updated row
    sql = "SELECT json FROM " + VERTEX_TABLE + " where id = ?";
    r = null;
    try {
      r = conn.executeSelect(sql, V_ID);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    o = r.getResultObject();

    if (o != null) {
      ResultSet rs = (ResultSet)o;

      try {
        if (rs.next()) {
          assertEquals("{\"Goodbye\":\"World\"}", rs.getString("json"));
        }
      } catch (Exception e) {
        fail(e.getMessage());
      }

      conn.closeResultSet(rs, r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    }
  }

  void rollbackTest() {
    System.out.println("in rollbackTest");
    final String TREE_TABLE = "tq_tree.conv";
    final String CONTEXT = "context1";

    assertEquals("tq_admin", provider.getUser());

    // Get the current row count in the tree table.
    int startRowCount = getTreeTableCount();

    // Insert into conv tree table
    String sql = "INSERT INTO " + TREE_TABLE +
        " (context, lox, parent_lox) VALUES (?, ?, ?)";
    IResult r = null;

    String[] insert_vals = {CONTEXT, "loxroot1", "",
                            CONTEXT, "lox1a", "loxroot1",
                            CONTEXT, "lox2a", "loxroot1",
                            CONTEXT, "lox3a", "loxroot1",
                            CONTEXT, "lox4a", "lox1a",
                            CONTEXT, "lox5a", "lox1a",
                            CONTEXT, "lox6a", "lox2a"
    };

    // Start a transaction, insert some values into the conversation tree,
    // then rollback the transaction.
    try {
      conn.setConvRole();
      conn.beginTransaction();
      r = conn.executeBatch(sql, insert_vals);
      r = conn.rollback();
      if (r.hasError()) {
        fail(r.getErrorString());
      }
      
      conn.closeConnection(r);
      conn = provider.getConnection();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    // Get the row count in the tree table after rollback.
    int endRowCount = getTreeTableCount();

    System.out.println("rollbackTest: start count = " + startRowCount + ", end count = " + endRowCount);
    // start and end counts should be the same
    assertEquals(startRowCount, endRowCount);
  }

  private int getTreeTableCount() {
    final String TREE_TABLE = "tq_tree.conv";
    final String CONTEXT = "context1";
    int rowCount = 0;

    // Select
    String sql = "SELECT * FROM " + TREE_TABLE + " WHERE context = ?";
    IResult r = null;
    try {
      r = conn.executeCount(sql);
    } catch (Exception e) {
      fail(e.getMessage());
    }

    Object o = r.getResultObject();

    if (o != null) {
      Long count = (Long)o;
      rowCount = count.intValue();
    }

    return rowCount;
  }
  
  void getRowCount() {
    System.out.println("in getRowCount");
    final String
        VERTEX_TABLE	= "vertex",
        EDGE_TABLE      = "edge",
        V_ID            = Long.toString(System.currentTimeMillis());

    setUserRole();

    assertEquals("tq_admin", provider.getUser());

    // Select
    String sql = "SELECT * FROM " + VERTEX_TABLE;
    IResult r = null;
    try {
      r = conn.executeCount(sql);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
    } catch (Exception e) {
      fail(e.getMessage());
    }

    Object o = r.getResultObject();

    if (o != null) {
      Long count = (Long)o;
      assertEquals(22, count.longValue());
    } else {
      fail("count not found");
    }
  }
  
  private void tearDownAll() {
    System.out.println("in tearDownAll");

    String[] testDropDBs = {
      "DROP INDEX IF EXISTS vid",
      "DROP INDEX IF EXISTS eid",
      "DROP TABLE IF EXISTS vertex",
      "DROP TABLE IF EXISTS edge"
    };

    executeStatements(testDropDBs);
    closeConnection();
    shutDownProvider();
  }

  //
  // Helper attributes and methods.
  //
  private static IPostgresConnectionFactory provider;
  private static Properties props;
  private static IPostgresConnection conn;

  private static final String ROOT_DB = "postgres";
  private static final String TEST_DB = "testdb";
  private static final String TEMPLATE_DB = "template1";
  private static final String TQ_ADMIN_DB = "tq_database";

  static IResult executeStatements(String[] stmts) {
    IResult r = new ResultPojo();
    
    if (conn != null) {
      conn.executeMultiSQL(stmts, r);
      if (r.hasError())
        fail(r.getErrorString());
    }

    return r;
  }

  private void closeConnection() {
    if (conn != null) {
      IResult r = new ResultPojo();

      conn.closeConnection(r);
      if (r.hasError()) {
        fail(r.getErrorString());
      }
      conn = null;
    }
  }
  
  private void setupTQAdminUser() {
    System.out.println("Setting up DB connection as tq_admin user...");
    provider = new PostgresConnectionFactory(TQ_ADMIN_DB, "tq_admin", "tq-admin-pwd");

    try {
      conn = provider.getConnection();
    } catch (Exception e) {
      fail(e.getMessage());
    }
    System.out.println("DONE - Setting up DB connection as tq_admin user...");
  }

  private void shutDownProvider() {
    try {
      provider.shutDown();
    } catch (SQLException e) {
      fail(e.getMessage());
    }
  }

  private void setUserRole() {
    String[] setRoleStmt = {
      "SET ROLE tq_admin"
    };

    executeStatements(setRoleStmt);
  }

  private void setProxyRole() {
    String[] setProxyStmt = {
      "SET ROLE tq_proxy"
    };

    executeStatements(setProxyStmt);
  }
}
