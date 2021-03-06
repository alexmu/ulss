/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cn.ac.iie.ulss.dataredistribution.tools;

import cn.ac.iie.ulss.dataredistribution.commons.GlobalVariables;
import cn.ac.iie.ulss.dataredistribution.handler.GetRuleFromDBThread;
import cn.ac.iie.ulss.dataredistribution.commons.RuntimeEnv;
import cn.ac.iie.ulss.dataredistribution.dao.SimpleDaoImpl;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 *
 * @author evan yang
 */
public class GetSchemaFromDB {

    static Logger logger = null;
    private static SimpleDaoImpl simpleDao;

    static {
        PropertyConfigurator.configure("log4j.properties");
        logger = Logger.getLogger(GetRuleFromDBThread.class.getName());
    }

    /**
     *
     * get schemas from the oracle 
     */
    public static void getSchemaFromDB() {
        Map<String, String> topicToSchemaContent = (Map<String, String>) RuntimeEnv.getParam(GlobalVariables.TOPIC_TO_SCHEMACONTENT);
        Map<String, String> topicToSchemaName = (Map<String, String>) RuntimeEnv.getParam(GlobalVariables.TOPIC_TO_SCHEMANAME);

        String dbCluster = (String) RuntimeEnv.getParam(RuntimeEnv.DB_CLUSTER);
        simpleDao = SimpleDaoImpl.getDaoInstance(dbCluster);
        logger.info("getting schema from oracle...");
        String sql = "select DATASCHEMA_MQ.MQ,DATASCHEMA.SCHEMA_CONTENT from DATASCHEMA_MQ,DATASCHEMA WHERE DATASCHEMA_MQ.SCHEMA_NAME=DATASCHEMA.SCHEMA_NAME";
        List<List<String>> rs = simpleDao.queryForList(sql);
        for (List<String> r1 : rs) {
            topicToSchemaContent.put(r1.get(0), r1.get(1).toLowerCase());
        }

        String sqldocs = "select SCHEMA_CONTENT from DATASCHEMA WHERE SCHEMA_NAME='docs'";
        List<List<String>> rsdocs = simpleDao.queryForList(sqldocs);
        List<String> r2 = rsdocs.get(0);
        String docsSchema = r2.get(0);
        RuntimeEnv.addParam(GlobalVariables.DOCS_SCHEMA_CONTENT, docsSchema.toLowerCase());

        String sqls = "select MQ,SCHEMA_NAME from DATASCHEMA_MQ";
        List<List<String>> rss = simpleDao.queryForList(sqls);
        for (List<String> r3 : rss) {
            topicToSchemaName.put(r3.get(0), r3.get(1));
        }

        logger.info("get schema from metadb successfully");
    }
}
