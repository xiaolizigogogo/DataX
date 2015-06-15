package com.alibaba.datax.plugin.rdbms.reader;

import com.alibaba.datax.common.plugin.RecordSender;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.plugin.rdbms.reader.util.OriginalConfPretreatmentUtil;
import com.alibaba.datax.plugin.rdbms.reader.util.PreCheckTask;
import com.alibaba.datax.plugin.rdbms.reader.util.ReaderSplitUtil;
import com.alibaba.datax.plugin.rdbms.reader.util.SingleTableSplitUtil;
import com.alibaba.datax.plugin.rdbms.util.DBUtil;
import com.alibaba.datax.plugin.rdbms.util.DataBaseType;
import com.alibaba.datax.plugin.rdbms.util.RdbmsException;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CommonRdbmsReader {

    public static class Job {
        private static final Logger LOG = LoggerFactory
                .getLogger(Job.class);

        public Job(DataBaseType dataBaseType) {
            OriginalConfPretreatmentUtil.DATABASE_TYPE = dataBaseType;
            SingleTableSplitUtil.DATABASE_TYPE = dataBaseType;
        }

        public void init(Configuration originalConfig) {

            OriginalConfPretreatmentUtil.doPretreatment(originalConfig);

            LOG.debug("After job init(), job config now is:[\n{}\n]",
                    originalConfig.toJSON());
        }

        public void preCheck(Configuration originalConfig,DataBaseType dataBaseType) {
            /*检查每个表是否有读权限，以及querySql跟splik Key是否正确*/
            Configuration queryConf = ReaderSplitUtil.doPreCheckSplit(originalConfig);
            String splitPK = queryConf.getString(Key.SPLIT_PK);
            List<Object> connList = queryConf.getList(Constant.CONN_MARK, Object.class);
            String username = queryConf.getString(Key.USERNAME);
            String password = queryConf.getString(Key.PASSWORD);
            ExecutorService exec;
            if (connList.size() < 10){
                exec = Executors.newFixedThreadPool(connList.size());
            }else{
                exec = Executors.newFixedThreadPool(10);
            }
            Collection<PreCheckTask> taskList = new ArrayList<PreCheckTask>();
            for (int i = 0, len = connList.size(); i < len; i++){
                Configuration connConf = Configuration.from(connList.get(i).toString());
                PreCheckTask t = new PreCheckTask(username,password,connConf,dataBaseType,splitPK);
                taskList.add(t);
            }
            List<Future<Boolean>> results = Lists.newArrayList();
            try {
                results = exec.invokeAll(taskList);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            for (Future<Boolean> result : results){
                try {
                    result.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            exec.shutdownNow();
        }


        public List<Configuration> split(Configuration originalConfig,
                                         int adviceNumber) {
            return ReaderSplitUtil.doSplit(originalConfig, adviceNumber);
        }

        public void post(Configuration originalConfig) {
            // do nothing
        }

        public void destroy(Configuration originalConfig) {
            // do nothing
        }

    }

    public static class Task {
        private static final Logger LOG = LoggerFactory
                .getLogger(Task.class);

        private DataBaseType dataBaseType;

        private String username;
        private String password;
        private String jdbcUrl;
        private String mandatoryEncoding;

        // 作为日志显示信息时，需要附带的通用信息。比如信息所对应的数据库连接等信息，针对哪个表做的操作
        private String basicMsg;

        public Task(DataBaseType dataBaseType) {
            this.dataBaseType = dataBaseType;
        }

        public void init(Configuration readerSliceConfig) {

			/* for database connection */

            this.username = readerSliceConfig.getString(Key.USERNAME);
            this.password = readerSliceConfig.getString(Key.PASSWORD);
            this.jdbcUrl = readerSliceConfig.getString(Key.JDBC_URL);
            this.mandatoryEncoding = readerSliceConfig.getString(Key.MANDATORY_ENCODING, "");

            basicMsg = String.format("jdbcUrl:[%s]", this.jdbcUrl);
        }

        public void startRead(Configuration readerSliceConfig,
                              RecordSender recordSender,
                              TaskPluginCollector taskPluginCollector, int fetchSize) {
            String querySql = readerSliceConfig.getString(Key.QUERY_SQL);
            String table = readerSliceConfig.getString(Key.TABLE);

            LOG.info("Begin to read record by Sql: [{}\n] {}.",
                    querySql, basicMsg);

            Connection conn = DBUtil.getConnection(this.dataBaseType, jdbcUrl,
                    username, password);

            // session config .etc related
            DBUtil.dealWithSessionConfig(conn, readerSliceConfig,
                    this.dataBaseType, basicMsg);

            int columnNumber = 0;
            ResultSet rs = null;
            try {
                rs = DBUtil.query(conn, querySql, fetchSize);
                ResultSetMetaData metaData = rs.getMetaData();
                columnNumber = metaData.getColumnCount();
                while (rs.next()) {
                    ResultSetReadProxy.transportOneRecord(recordSender, rs,
                            metaData, columnNumber, mandatoryEncoding, taskPluginCollector);
                }

                LOG.info("Finished read record by Sql: [{}\n] {}.",
                        querySql, basicMsg);
            }catch (Exception e) {
                throw RdbmsException.asQueryException(this.dataBaseType, e, querySql, table, username);
            } finally {
                DBUtil.closeDBResources(null, conn);
            }
        }

        public void post(Configuration originalConfig) {
            // do nothing
        }

        public void destroy(Configuration originalConfig) {
            // do nothing
        }
    }
}
