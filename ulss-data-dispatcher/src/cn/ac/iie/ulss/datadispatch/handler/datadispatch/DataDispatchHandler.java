/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cn.ac.iie.ulss.datadispatch.handler.datadispatch;

import cn.ac.ict.ncic.util.dao.DaoPool;
import cn.ac.iie.ulss.datadispatch.commons.RuntimeEnv;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/**
 *
 * @author alexmu
 */
public class DataDispatchHandler extends AbstractHandler {

    //docs shcema
    private static String docsSchemaContent = null;
    private static Schema docsSchema = null;
    private static DatumReader<GenericRecord> docsReader = null;
    private static DataDispatchHandler dataDispatchHandler = null;
    private Map<String, DataDispatcher> docSchema2Dispatcher = new HashMap<String, DataDispatcher>();
    static Logger logger = null;

    static {
        PropertyConfigurator.configure("log4j.properties");
        logger = Logger.getLogger(DataDispatchHandler.class.getName());
    }

    private DataDispatchHandler() {
    }

    public static DataDispatchHandler getDataDispatchHandler() {
        if (dataDispatchHandler != null) {
            return dataDispatchHandler;
        }
        dataDispatchHandler = new DataDispatchHandler();
        ResultSet rs = null;
        try {
            logger.info("get data schema and corresponding message queue from metadb...");
            while (true) {
                try {
                    rs = DaoPool.getDao(RuntimeEnv.METADB_CLUSTER).executeQuery("select dataschema.schema_name,dataschema.schema_content,dataschema_mq.mq from dataschema left outer join dataschema_mq on dataschema.schema_name=dataschema_mq.schema_name");
                    break;
                } catch (Exception ex) {
                    logger.warn("get information from metadb unsuccessfully for " + ex.getMessage(), ex);
                    try {
                        Thread.sleep(500);
                    } catch (Exception ex_) {
                    }
                }
            }
            logger.info("get data schema and corresponding message queue from metadb successfully");
            logger.info("parsing data schemas...");
            while (rs.next()) {
                String schemaName = rs.getString("schema_name").toLowerCase();
                String schemaContent = rs.getString("schema_content").toLowerCase();
                String mq = rs.getString("mq");
                logger.info("schema " + schemaName + " found");
                logger.info("schema " + schemaName + "'s content is:\n" + schemaContent);
                if (schemaName.equals("docs")) {
                    logger.info("parsing schema docs...");
                    docsSchemaContent = schemaContent;
                    Protocol protocol = Protocol.parse(docsSchemaContent);//maybe failed
                    docsSchema = protocol.getType(schemaName);
                    docsReader = new GenericDatumReader<GenericRecord>(docsSchema);
                    logger.info("parse schema docs successfully");
                } else {
                    logger.info("constructing data dispatcher for schema " + schemaName + "...");
                    DataDispatcher dataDispatcher = DataDispatcher.getDataDispatcher(schemaName, schemaContent, mq);
                    if (dataDispatcher != null) {
                        dataDispatchHandler.docSchema2Dispatcher.put(schemaName, dataDispatcher);
                        logger.info("construct data dispatcher for schema " + schemaName + " successfully");
                    } else {
                        dataDispatchHandler = null;
                        logger.info("constructing data dispatcher for schema " + schemaName + " is failed");
                        break;
                    }
                }
            }

            logger.info("parsing data schemas is finished");

            if (docsSchema == null) {
                logger.error("schema docs is not found in metadb,please check metadb to ensure that docs schema exist");
                dataDispatchHandler = null;
            }

            if (dataDispatchHandler.docSchema2Dispatcher.size() < 1) {
                logger.warn("no bussiness data schema is found in metadb,please check metadb to ensure that this condition is reasonable");
            }

        } catch (Exception ex) {
            logger.error("constructing data dispath handler is failed for " + ex.getMessage(), ex);
            dataDispatchHandler = null;
        } finally {
            Connection tmpConn = null;
            try {
                tmpConn = rs.getStatement().getConnection();
            } catch (Exception ex) {
            }
            try {
                rs.close();
            } catch (Exception ex) {
            }
            try {
                tmpConn.close();
            } catch (Exception ex) {
            }
        }
        return dataDispatchHandler;
    }

    public static String getDocSchemaContent(String pSchemaName) {
        DataDispatcher dataDispatcher = dataDispatchHandler.docSchema2Dispatcher.get(pSchemaName);

        if (dataDispatcher == null) {
            return null;
        } else {
            return dataDispatcher.getDocSchemaContent();
        }
    }

    @Override
    public void handle(String string, Request baseRequest, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {
        String remoteHost = baseRequest.getRemoteAddr();
        int remotePort = baseRequest.getRemotePort();
        String reqID = String.valueOf(System.nanoTime());
        logger.info("receive request from " + remoteHost + ":" + remotePort + " and assigned id " + reqID);

        //retrive data
        ServletInputStream servletInputStream = null;
        byte[] req = null;
        try {
            logger.debug("req " + reqID + ":retriving bussisness data for request  ...");
            baseRequest.setHandled(true);
            servletInputStream = baseRequest.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int i = 0;
            while ((i = servletInputStream.read(b, 0, 1024)) > 0) {
                out.write(b, 0, i);
            }
            req = out.toByteArray();
            logger.debug("req " + reqID + ":length of bussisness data is " + req.length);
        } catch (Exception ex) {
            String errInfo = "req " + reqID + ":retrive bussiness data unsuccessfully for " + ex.getMessage();
            logger.error(errInfo, ex);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            httpServletResponse.getWriter().println("-1\n" + errInfo);
            return;
        } finally {
            try {
                servletInputStream.close();
            } catch (Exception ex) {
            }
        }

        //decode data
        ByteArrayInputStream docsbis = null;
        GenericRecord docsRecord = null;
        try {
            logger.debug("req " + reqID + ":decoding bussisness data ...");
            docsbis = new ByteArrayInputStream(req);//tuning
            BinaryDecoder docsbd = new DecoderFactory().binaryDecoder(docsbis, null);

            new GenericData.Record(docsSchema);
            docsRecord = docsReader.read(docsRecord, docsbd);
            logger.debug("req " + reqID + ":decode bussisness data sccessfully");
        } catch (Exception ex) {
            String errInfo = "req " + reqID + ":decode bussiness data unsuccessfully for " + ex.getMessage();
            logger.error(errInfo, ex);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            httpServletResponse.getWriter().println("-1\n" + errInfo);
            return;
        } finally {
            try {
                docsbis = null;
            } catch (Exception ex) {
            }
        }

        //check format of docs 
        String docSchemaName = null;
        try {
            docSchemaName = docsRecord.get("doc_schema_name").toString();
        } catch (Exception ex) {
            String errInfo = "req " + reqID + ":wrong format of bussiness data,can't get docSchemaName";
            logger.error(errInfo, ex);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            httpServletResponse.getWriter().println("-1\n" + errInfo);
            return;
        }

        logger.debug("req " + reqID + ":doc shcema name is " + docSchemaName);

        DataDispatcher dataDispatcher = docSchema2Dispatcher.get(docSchemaName);
        if (dataDispatcher == null) {
            synchronized (docSchema2Dispatcher) {
                dataDispatcher = docSchema2Dispatcher.get(docSchemaName);
                if (dataDispatcher == null) {
                    ResultSet rs = null;
                    try {
                        String sql = "select dataschema.schema_name,dataschema.schema_content,dataschema_mq.mq from dataschema inner join dataschema_mq on dataschema.schema_name=dataschema_mq.schema_name and dataschema.schema_name='" + docSchemaName + "'";
                        logger.debug("req " + reqID + ":" + sql);
                        for (int tryTimes = 0; tryTimes < 10; tryTimes++) {
                            try {
                                rs = DaoPool.getDao(RuntimeEnv.METADB_CLUSTER).executeQuery(sql);
                                break;
                            } catch (Exception ex) {
                                rs = null;
                                logger.warn("req " + reqID + ":get information from metadb unsuccessfully for " + ex.getMessage(), ex);
                                try {
                                    Thread.sleep(500);
                                } catch (Exception ex_) {
                                }
                            }
                        }

                        if (rs == null) {
                            String errInfo = "req " + reqID + ":internal error for can't connect to metadb";
                            logger.error(errInfo);
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                            httpServletResponse.getWriter().println("-1\n" + errInfo);
                            return;
                        }

                        if (rs.next()) {
                            String schemaName = rs.getString("schema_name").toLowerCase();
                            String schemaContent = rs.getString("schema_content").toLowerCase();
                            String mq = rs.getString("mq");
                            dataDispatcher = DataDispatcher.getDataDispatcher(schemaName, schemaContent, mq);
                            if (dataDispatcher != null) {
                                dataDispatchHandler.docSchema2Dispatcher.put(schemaName, dataDispatcher);
                                logger.info("req " + reqID + ":constructing data dispatcher for schema " + schemaName + " is finished successfully");
                            } else {
                                String warningInfo = "req " + reqID + ":internal error for constructing data dispatcher for schema " + schemaName + " is failed";
                                logger.warn(warningInfo);
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.getWriter().println("-1\n" + warningInfo);
                                return;
                            }
                        } else {
                            String warningInfo = "req " + reqID + ":unkonow schema " + docsRecord.get("doc_schema_name").toString();
                            logger.warn(warningInfo);
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                            httpServletResponse.getWriter().println("-1\n" + warningInfo);
                            return;
                        }
                    } catch (Exception ex) {
                        String warningInfo = "req " + reqID + ":constructing data dispatcher for schema " + docsRecord.get("doc_schema_name").toString() + " is failed for " + ex.getMessage();
                        logger.warn(warningInfo, ex);
                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        httpServletResponse.getWriter().println("-1\n" + warningInfo);
                        return;
                    } finally {
                        Connection tmpConn = null;
                        try {
                            tmpConn = rs.getStatement().getConnection();
                        } catch (Exception ex) {
                        }
                        try {
                            rs.close();
                        } catch (Exception ex) {
                        }
                        try {
                            tmpConn.close();
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        }

        try {
            Object bzSysSign = docsRecord.get("sign");
            GenericArray docsSet = (GenericData.Array<GenericRecord>) docsRecord.get("doc_set");
            if (docsSet == null) {
                String errInfo = "req " + reqID + ":wrong format of bussiness data, doc_set is null";
                logger.error(errInfo);
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                httpServletResponse.getWriter().println("-1\n" + errInfo);
            } else {
                if (docsSet.size() <= 0) {
                    String errInfo = "req " + reqID + ":doc_set is empty";
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    httpServletResponse.getWriter().println("-1\n" + errInfo);
                } else {
                    dataDispatcher.dispatch(req);
                    logger.info("req " + reqID + ":sending " + docsSet.size() + " records of " + docSchemaName + " to metaq successfully");
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    httpServletResponse.getWriter().println("0\n" + bzSysSign + "\n" + reqID);
                }
            }
        } catch (Exception ex) {
            String errInfo = "req " + reqID + ":internal error for sending data unsuccessfully for " + ex.getMessage();
            logger.error(errInfo, ex);
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            httpServletResponse.getWriter().println("-1\n" + errInfo);
        }
    }
}
