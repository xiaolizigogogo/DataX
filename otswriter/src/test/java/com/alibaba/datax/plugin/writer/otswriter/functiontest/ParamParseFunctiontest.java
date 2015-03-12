package com.alibaba.datax.plugin.writer.otswriter.functiontest;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.writer.otswriter.OtsWriterMasterProxy;
import com.alibaba.datax.plugin.writer.otswriter.common.Utils;
import com.alibaba.datax.plugin.writer.otswriter.model.OTSAttrColumn;
import com.alibaba.datax.plugin.writer.otswriter.model.OTSConf;
import com.alibaba.datax.plugin.writer.otswriter.model.OTSConst;
import com.alibaba.datax.plugin.writer.otswriter.model.OTSOpType;
import com.alibaba.datax.plugin.writer.otswriter.model.OTSPKColumn;
import com.alibaba.datax.plugin.writer.otswriter.utils.GsonParser;
import com.aliyun.openservices.ots.OTSClient;
import com.aliyun.openservices.ots.model.ColumnType;
import com.aliyun.openservices.ots.model.PrimaryKeyType;
import com.aliyun.openservices.ots.model.TableMeta;

public class ParamParseFunctiontest {
    
    private static String tableName = "ots_writer_param_parse";
    private static Configuration p = Utils.loadConf();
    private static final Logger LOG = LoggerFactory.getLogger(ParamParseFunctiontest.class);
    
    @BeforeClass
    public static void setBeforeClass() {
        OTSClient ots = new OTSClient(p.getString("endpoint"), p.getString("accessid"), p.getString("accesskey"), p.getString("instance-name"));
        
        TableMeta tableMeta = new TableMeta(tableName);
        tableMeta.addPrimaryKeyColumn("Uid", PrimaryKeyType.STRING);
        tableMeta.addPrimaryKeyColumn("Pid", PrimaryKeyType.INTEGER);
        tableMeta.addPrimaryKeyColumn("Mid", PrimaryKeyType.INTEGER);
        tableMeta.addPrimaryKeyColumn("UID", PrimaryKeyType.STRING);
        try {
            Utils.createTable(ots, tableName, tableMeta);
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }
    
    private String linesToJson(Map<String, String> lines) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        Set<Entry<String, String>> entrys = lines.entrySet();
        int i = 0;
        for (Entry<String, String> e : entrys) {
            if (i == (entrys.size() - 1)) {
                sb.append(e.getValue());
            } else {
                sb.append(e.getValue() + ",");
            }
            i++;
        }
        sb.append("}");
        return sb.toString();
    }
    
    @Before
    public void waiting() throws InterruptedException {
        Thread.sleep(1000);
    }

    private Map<String, String> getLines() {
        Map<String, String> lines = new LinkedHashMap<String, String>();
        lines.put("endpoint",     "\"endpoint\":\""+ p.getString("endpoint") +"\"");
        lines.put("accessId",     "\"accessId\":\""+ p.getString("accessid") +"\"");
        lines.put("accessKey",    "\"accessKey\":\""+ p.getString("accesskey") +"\"");
        lines.put("instanceName", "\"instanceName\":\""+ p.getString("instance-name") +"\"");
        lines.put("table",        "\"table\":\""+ tableName +"\"");
        lines.put("primaryKey",   "\"primaryKey\":[{\"name\":\"Uid\", \"type\":\"String\"},{\"name\":\"Pid\", \"type\":\"Int\"},{\"name\":\"Mid\", \"type\":\"Int\"},{\"name\":\"UID\", \"type\":\"String\"}]");
        lines.put("column",       "\"column\":[{\"name\":\"attr_0\", \"type\":\"String\"}]");
        lines.put("writeMode",    "\"writeMode\":\"putRow\"");
        return lines;
    }
    
    /**
     * 输入：输入完整的配置，且每个配置都是合法的
     * 期望：程序能正常解析所有配置，且每个配置的值都符合预期
     * @throws Exception
     */
    @Test
    public void testParamIsValid() throws Exception {
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        Map<String, String> lines = this.getLines();
        String json = this.linesToJson(lines);
        Configuration param = Configuration.from(json);
        proxy.init(param);
        OTSConf conf = proxy.getOTSConf();
        assertEquals(p.getString("endpoint"), conf.getEndpoint());
        assertEquals(p.getString("accessid"), conf.getAccessId());
        assertEquals(p.getString("accesskey"), conf.getAccessKey());
        assertEquals(p.getString("instance-name"), conf.getInstanceName());
        assertEquals(tableName, conf.getTableName());
        assertEquals(OTSOpType.PUT_ROW, conf.getOperation());
        
        List<OTSPKColumn> pk = conf.getPrimaryKeyColumn();
        assertEquals(4, pk.size());
        assertEquals("Uid", pk.get(0).getName());
        assertEquals(PrimaryKeyType.STRING, pk.get(0).getType());
        assertEquals("Pid", pk.get(1).getName());
        assertEquals(PrimaryKeyType.INTEGER, pk.get(1).getType());
        assertEquals("Mid", pk.get(2).getName());
        assertEquals(PrimaryKeyType.INTEGER, pk.get(2).getType());
        assertEquals("UID", pk.get(3).getName());
        assertEquals(PrimaryKeyType.STRING, pk.get(3).getType());
        
        List<OTSAttrColumn> attr = conf.getAttributeColumn();
        assertEquals(1, attr.size());
        assertEquals("attr_0", attr.get(0).getName());
        assertEquals(ColumnType.STRING, attr.get(0).getType());

    }
    
    /**
     * 输入：分别设置所有的配置为NULL，测试程序的行为
     * 期望：当对应字段为空时，期望获得对应字段不存在的异常
     */
    @Test
    public void testParamIsNull() {
        // 备注：key表示对应字段被设置为空，value表示对应的错误消息
        Map<String, String> input = new LinkedHashMap<String, String>();
        input.put("endpoint",     "The param 'endpoint' is not exist.");
        input.put("accessId",     "The param 'accessId' is not exist.");
        input.put("accessKey",    "The param 'accessKey' is not exist.");
        input.put("instanceName", "The param 'instanceName' is not exist.");
        input.put("table",        "The param 'table' is not exist.");
        input.put("primaryKey",   "The param 'primaryKey' is not exist.");
        input.put("column",       "The param 'column' is not exist.");
        input.put("writeMode",    "The param 'writeMode' is not exist.");
        
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        for (Entry<String, String> entry : input.entrySet()) {
            LOG.debug("Param:{}, Expect:{}", entry.getKey(), entry.getValue());
            Map<String, String> lines = this.getLines();
            lines.remove(entry.getKey());
            String json = this.linesToJson(lines);
            
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals(entry.getValue(), e.getMessage());
            }
        }
    }
    
    /**
     * 输入：分别设置endpoint、accessId、accessKey、instanceName、table和operation的值为空字符串
     * 期望：当对应字段为空字符串时，期望获得值为空字符串的异常
     */
    @Test
    public void testParamIsEmpty() {
        // 备注：key表示对应字段被设置为空字符串，value表示对应的错误消息
        Map<String, String> input = new LinkedHashMap<String, String>();
        input.put("endpoint",     "The param length of 'endpoint' is zero.");
        input.put("accessId",     "The param length of 'accessId' is zero.");
        input.put("accessKey",    "The param length of 'accessKey' is zero.");
        input.put("instanceName", "The param length of 'instanceName' is zero.");
        input.put("table",        "The param length of 'table' is zero.");
        input.put("writeMode",    "The param length of 'writeMode' is zero.");
        
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        for (Entry<String, String> entry : input.entrySet()) {
            LOG.debug("Param:{}, Expect:{}", entry.getKey(), entry.getValue());
            Map<String, String> lines = this.getLines();
            lines.put(entry.getKey(), "\""+ entry.getKey() +"\":\"\"");
            String json = this.linesToJson(lines);
            System.out.println(json);
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals(entry.getValue(), e.getMessage());
            }
        }
    }
    
    /**
     * 主要是对PrimaryKey的参数解析
     * @throws Exception 
     */
    @Test
    public void testParamPrimaryKeyParse() throws Exception {
        // 备注：key表示对应字段被设置为空字符串，value表示对应的错误消息
        Map<String, String> input = new LinkedHashMap<String, String>();
        // 缺少name，期望异常退出
        input.put("{\"type\":\"String\"}", "The only support 'name' and 'type' fileds in json map of 'primaryKey'.");
        // 缺少type，期望异常退出
        input.put("{\"name\":\"Uid\"}", "The only support 'name' and 'type' fileds in json map of 'primaryKey'.");
        // 缺少name和type，期望异常退出
        input.put("{}", "The only support 'name' and 'type' fileds in json map of 'primaryKey'.");
        // 包括name和type，但是多了一个value，期望异常退出
        input.put("{\"name\":\"Uid\", \"type\":\"String\", \"value\":\"\"}", "The only support 'name' and 'type' fileds in json map of 'primaryKey'.");
        // 错误的type值，期望异常退出
        input.put("{\"name\":\"Uid\", \"type\":\"Integer\"}", "Primary key type only support 'string' and 'int', not support 'Integer'.");
        // 空字符串name，期望异常退出
        input.put("{\"name\":\"\", \"type\":\"String\"}", "The name of item can not be a empty string in 'primaryKey'.");
        // pk类型和meta不匹配，期望异常退出
        input.put("{\"name\":\"Uid\", \"type\":\"Int\"}", "The type of 'primaryKey' not match meta, column name : Uid, input type: INTEGER, primary key type : STRING in meta.");
        // pk个数和meta不匹配，期望异常退出
        input.put("{\"name\":\"Uid\", \"type\":\"String\"},{\"name\":\"Uid1\", \"type\":\"String\"}", "The count of 'primaryKey' not equal meta, input count : 5, primary key count : 4 in meta.");
        // 重复的column，期望异常退出
        input.put("{\"name\":\"UID\", \"type\":\"String\"}", "Missing the column 'Uid' in 'primaryKey'.");

        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        for (Entry<String, String> entry : input.entrySet()) {
            LOG.debug("Param:{}, Expect:{}", entry.getKey(), entry.getValue());
            Map<String, String> lines = this.getLines();
            lines.put("primaryKey", "\"primaryKey\":["+ entry.getKey() +",{\"name\":\"Pid\", \"type\":\"Int\"},{\"name\":\"Mid\", \"type\":\"Int\"},{\"name\":\"UID\", \"type\":\"String\"}]");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals(entry.getValue(), e.getMessage());
            }
        }
        
        Map<String, String> newInput = new LinkedHashMap<String, String>();
        // primaryKey的值不是{}
        newInput.put("{}", "The param 'primaryKey' is not a json array.");
        
        // primaryKey的值是空[]
        newInput.put("[]", "The param 'primaryKey' is a empty json array.");
        for (Entry<String, String> entry : newInput.entrySet()) {
            LOG.debug("Param:{}, Expect:{}", entry.getKey(), entry.getValue());
            Map<String, String> lines = this.getLines();
            lines.put("primaryKey", "\"primaryKey\":"+ entry.getKey() +"");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals(entry.getValue(), e.getMessage());
            }
        }
        
        // name出现大小写的两个列为重复列,预期解析正常
        
        Map<String, String> lines = this.getLines();
        String json = this.linesToJson(lines);
        Configuration p = Configuration.from(json);
        proxy.init(p);
    }

    /**
     * 主要是对Column的参数解析
     * @throws Exception 
     */
    @Test
    public void testParamColumnParse() throws Exception {
        // 备注：key表示对应字段被设置为空字符串，value表示对应的错误消息
        Map<String, String> input = new LinkedHashMap<String, String>();
        // 缺少name，期望异常退出
        input.put("{\"type\":\"String\"}", "The only support 'name' and 'type' fileds in json map of 'column'.");
        // 缺少type，期望异常退出
        input.put("{\"name\":\"Uid\"}", "The only support 'name' and 'type' fileds in json map of 'column'.");
        // 缺少name和type，期望异常退出
        input.put("{}", "The only support 'name' and 'type' fileds in json map of 'column'.");
        // 包括name和type，但是多了一个value，期望异常退出
        input.put("{\"name\":\"Uid\", \"type\":\"String\", \"value\":\"\"}", "The only support 'name' and 'type' fileds in json map of 'column'.");
        // 错误的type值，期望异常退出
        input.put("{\"name\":\"Uid\", \"type\":\"Integer\"}", "Column type only support 'string','int','double','bool' and 'binary', not support 'Integer'.");
        // 空字符串name，期望异常退出
        input.put("{\"name\":\"\", \"type\":\"String\"}", "The name of item can not be a empty string in 'column'.");
        // 重复的column，期望异常退出
        input.put("{\"name\":\"attr_0\", \"type\":\"String\"}", "Multi item in 'column', column name : attr_0 .");
        
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        for (Entry<String, String> entry : input.entrySet()) {
            LOG.debug("Param:{}, Expect:{}", entry.getKey(), entry.getValue());
            Map<String, String> lines = this.getLines();
            lines.put("column", "\"column\":["+ entry.getKey() +",{\"name\":\"attr_0\", \"type\":\"String\"}]");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals(entry.getValue(), e.getMessage());
            }
        }
        
        // column的值是{}
        {
            Map<String, String> lines = this.getLines();
            lines.put("column", "\"column\":{}");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals("The param 'column' is not a json array.", e.getMessage());
            }
        }
         //column的值是空[]，但是操作是PutRow，预期正常
        {
            Map<String, String> lines = this.getLines();
            lines.put("column", "\"column\":[]");
            lines.put("writeMode", "\"writeMode\":\"PutRow\"");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            
            proxy.init(p);
        }
        // column的值是空[]，但是操作是UpdateRow，预期失败
        {
            Map<String, String> lines = this.getLines();
            lines.put("column", "\"column\":[]");
            lines.put("writeMode", "\"writeMode\": \"UpdateRow\"");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            try {
                proxy.init(p);
                assertTrue(false);
            } catch (Exception e) {
                assertEquals("The param 'column' is a empty json array.", e.getMessage());
            }
        }
        // name出现大小写的两个列为重复列
        // col1, COL1
        {
            Map<String, String> lines = this.getLines();
            lines.put("column", "\"column\":[{\"name\":\"col1\", \"type\":\"string\"},{\"name\":\"COL1\", \"type\":\"string\"}]");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            proxy.init(p);
        }
    }
    
    @Test
    public void testParamWriteMode() throws Exception {
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        List<String> input = new ArrayList<String>();
        //writeMode的置为PutRow
        input.add("PutRow");
        //writeMode的置为UpdateRow
        input.add("UpdateRow");
        //writeMode的置为putrow
        input.add("putrow");
        //writeMode的置为uopdaterow
        input.add("updaterow");
        //writeMode的置为PUTROW
        input.add("PUTROW");
        //writeMode的置为UPADTEROW
        input.add("UPDATEROW");
        
        for (String str : input) {
            LOG.debug("Param:{}", str);
            Map<String, String> lines = this.getLines();
            lines.put("writeMode", "\"writeMode\":\""+ str +"\"");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            proxy.init(p);
        }

        //writeMode的置为hello
        try {
            Map<String, String> lines = this.getLines();
            lines.put("writeMode", "\"writeMode\":\"hello\"");
            String json = this.linesToJson(lines);
            Configuration p = Configuration.from(json);
            proxy.init(p);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("The 'writeMode' only support 'PutRow' and 'UpdateRow' not 'hello'.", e.getMessage());
        }
    }
    
    // 测试默认值是否正确
    @Test
    public void testOptionConfigDefaultValue() throws Exception {
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        Map<String, String> lines = this.getLines();
        String json = this.linesToJson(lines);
        Configuration p = Configuration.from(json);
        proxy.init(p);
        OTSConf conf = proxy.getOTSConf();
        assertEquals(18,  conf.getRetry());
        assertEquals(100, conf.getSleepInMilliSecond());
        assertEquals(100, conf.getBatchWriteCount());
        assertEquals(5,  conf.getConcurrencyWrite());
        assertEquals(1,   conf.getIoThreadCount());
        assertEquals(60000, conf.getSocketTimeout());
        assertEquals(60000, conf.getConnectTimeout());
        assertEquals(1024*1024, conf.getRestrictConf().getRequestTotalSizeLimition());
    }
    
    // 测试输入值是否正确
    @Test
    public void testOptionConfigSpecialValue() throws Exception {
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        Map<String, String> lines = this.getLines();
        lines.put(OTSConst.RETRY, "\"maxRetryTime\": 11");
        lines.put(OTSConst.SLEEP_IN_MILLI_SECOND, "\"retrySleepInMillionSecond\": 12");
        lines.put(OTSConst.BATCH_WRITE_COUNT, "\"batchWriteCount\": 13");
        lines.put(OTSConst.CONCURRENCY_WRITE, "\"concurrencyWrite\": 14");
        lines.put(OTSConst.IO_THREAD_COUNT, "\"ioThreadCount\": 15");
        lines.put(OTSConst.SOCKET_TIMEOUT, "\"socketTimeoutInMillionSecond\": 17");
        lines.put(OTSConst.CONNECT_TIMEOUT, "\"connectTimeoutInMillionSecond\": 18");
        lines.put(OTSConst.REQUEST_TOTAL_SIZE_LIMITION, "\"requestTotalSizeLimition\": 19");
        String json = this.linesToJson(lines);
        Configuration p = Configuration.from(json);
        proxy.init(p);
        OTSConf conf = proxy.getOTSConf();
        assertEquals(11, conf.getRetry());
        assertEquals(12, conf.getSleepInMilliSecond());
        assertEquals(13, conf.getBatchWriteCount());
        assertEquals(14, conf.getConcurrencyWrite());
        assertEquals(15, conf.getIoThreadCount());
        assertEquals(17, conf.getSocketTimeout());
        assertEquals(18, conf.getConnectTimeout());
        assertEquals(19, conf.getRestrictConf().getRequestTotalSizeLimition());
    }
    
    @Test
    public void testSplit() throws Exception {
        OtsWriterMasterProxy proxy = new OtsWriterMasterProxy();
        Map<String, String> lines = this.getLines();
        String json = this.linesToJson(lines);
        Configuration con = Configuration.from(json);
        proxy.init(con);
        List<Configuration> confs = proxy.split(10);
        assertEquals(10, confs.size());
        
        for (Configuration c : confs) {
            OTSConf conf = GsonParser.jsonToConf(c.getString(OTSConst.OTS_CONF));
            assertEquals(p.getString("endpoint"), conf.getEndpoint());
            assertEquals(p.getString("accessid"), conf.getAccessId());
            assertEquals(p.getString("accesskey"), conf.getAccessKey());
            assertEquals(p.getString("instance-name"), conf.getInstanceName());
            assertEquals(tableName, conf.getTableName());
            assertEquals(OTSOpType.PUT_ROW, conf.getOperation());
            
            List<OTSPKColumn> pk = conf.getPrimaryKeyColumn();
            assertEquals(4, pk.size());
            assertEquals("Uid", pk.get(0).getName());
            assertEquals(PrimaryKeyType.STRING, pk.get(0).getType());
            assertEquals("Pid", pk.get(1).getName());
            assertEquals(PrimaryKeyType.INTEGER, pk.get(1).getType());
            assertEquals("Mid", pk.get(2).getName());
            assertEquals(PrimaryKeyType.INTEGER, pk.get(2).getType());
            assertEquals("UID", pk.get(3).getName());
            assertEquals(PrimaryKeyType.STRING, pk.get(3).getType());
            
            List<OTSAttrColumn> attr = conf.getAttributeColumn();
            assertEquals(1, attr.size());
            assertEquals("attr_0", attr.get(0).getName());
            assertEquals(ColumnType.STRING, attr.get(0).getType());
        }
    }
}
