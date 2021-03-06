/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cn.iie.match.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericArray;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.log4j.Logger;

/**
 *
 * @author liucuili
 */
public class SendUtilData implements Runnable {

    public static Logger log = Logger.getLogger(SendUtilData.class.getName());
    public String schemaName;
    public int batchSize;
    public MQProducerPool mqProducerPool = null;
    public LinkedBlockingQueue<GenericRecord> outBuf; //与匹配线程共享一个缓冲区，发行线程作为消费者进行消费

    SendUtilData(String schema, int bs, LinkedBlockingQueue<GenericRecord> b) {
        this.schemaName = schema;
        mqProducerPool = MQProducerPool.getMQProducerPool(Matcher.schemaname2Metaq.get(this.schemaName), 30);  //应该发往哪个topic,生产者池的大小
        batchSize = bs;
        outBuf = b;
    }

    public void send(byte[] pData) throws Exception {
        mqProducerPool.sendMessage(pData);
    }

    public byte[] packData(List<GenericRecord> data) throws IOException {
        //log.info(Matcher.schemaname2Schema.get(this.schemaName.toLowerCase()));
        DatumWriter<GenericRecord> writer = new GenericDatumWriter<GenericRecord>(Matcher.schemaname2Schema.get(this.schemaName.toLowerCase()));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        BinaryEncoder be = new EncoderFactory().binaryEncoder(bos, null);
        Schema docsSchema = Matcher.schemaname2Schema.get("docs");
        GenericRecord docsRecord = new GenericData.Record(Matcher.schemaname2Schema.get("docs"));
        GenericArray docSet = new GenericData.Array<GenericRecord>(data.size(), Matcher.schemaname2Schema.get("docs").getField("doc_set").schema());

        for (GenericRecord gr : data) {
            writer.write(gr, be);
            be.flush();
            docSet.add(ByteBuffer.wrap(bos.toByteArray()));
            bos.reset();
        }
        docsRecord.put("doc_schema_name", this.schemaName);
        docsRecord.put("doc_set", docSet);
        docsRecord.put("sign", "123456");

        DatumWriter<GenericRecord> docsWriter = new GenericDatumWriter<GenericRecord>(docsSchema);
        ByteArrayOutputStream docsBos = new ByteArrayOutputStream();
        BinaryEncoder docsBe = new EncoderFactory().binaryEncoder(docsBos, null);
        docsWriter.write(docsRecord, docsBe);
        docsBe.flush();
        return docsBos.toByteArray();
    }

    @Override
    public void run() {
        long count = 0;
        long sleepCount = 0;
        List<GenericRecord> tmp = new ArrayList<GenericRecord>();
        while (true) {
            try {
                Thread.sleep(20);
                sleepCount += 20;
            } catch (InterruptedException ex) {
            }
            if (outBuf.isEmpty()) {
                if (sleepCount >= 500 && !tmp.isEmpty()) {
                    try {
                        this.send(this.packData(tmp));
                        log.info("now send data num in total is -> " + this.schemaName + ":" + Matcher.schemaname2Sendtotal.get(this.schemaName).addAndGet(tmp.size()));
                    } catch (Exception ex) {
                        log.error(ex, ex);
                    }
                    tmp.clear();
                    sleepCount = 0;
                }
            } else {
                while (!outBuf.isEmpty()) {
                    log.debug("get one result record " + " from the out buffer " + this.schemaName);
                    GenericRecord gr = outBuf.poll();
                    if (gr != null) {
                        tmp.add(gr);
                        count++;
                    }
                    if (tmp.size() % batchSize == 0) {
                        try {
                            this.send(this.packData(tmp));
                            log.info("now send data num in total is -> " + this.schemaName + ":" + Matcher.schemaname2Sendtotal.get(this.schemaName).addAndGet(tmp.size()));
                        } catch (Exception ex) {
                            log.error(ex, ex);
                        }
                        tmp.clear();
                        sleepCount = 0;
                    }
                }
            }
        }
    }
}