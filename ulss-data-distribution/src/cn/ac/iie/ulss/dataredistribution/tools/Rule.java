/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cn.ac.iie.ulss.dataredistribution.tools;

import cn.ac.iie.ulss.dataredistribution.consistenthashing.DynamicAllocate;
import cn.ac.iie.ulss.dataredistribution.consistenthashing.RNode;
import cn.ac.iie.ulss.dataredistribution.consistenthashing.MD5NodeLocator;
import cn.ac.iie.ulss.dataredistribution.consistenthashing.NodeLocator;
import cn.ac.iie.ulss.dataredistribution.handler.SendControl;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.http.HttpResponse;
import org.apache.log4j.PropertyConfigurator;

/**
 *
 * @author evan yang
 */
public class Rule {

    private String topic = null;
    private String serviceName = null;
    private ArrayList nodeUrls = null;
    private int type = -1;
    private String keywords = null;
    private String filters = null;
    private NodeLocator nodelocator = null;
    private String[] IPList = null;
    private Map<RNode, String> nodeToIP = null;
    private ArrayList<String> deadIP = null;
    private String partType = null;
    static org.apache.log4j.Logger logger = null;

    static {
        PropertyConfigurator.configure("log4j.properties");
        logger = org.apache.log4j.Logger.getLogger(Rule.class.getName());
    }

    public Rule(String topic, String serviceName, ArrayList nodeUrls, int type, String keywords, String filters, NodeLocator nodelocator, String[] IPList, Map<RNode, String> nodeToIP, ArrayList<String> deadIP, String partType) {
        this.topic = topic;
        this.serviceName = serviceName;
        this.nodeUrls = nodeUrls;
        this.type = type;
        this.keywords = keywords;
        this.filters = filters;
        this.nodelocator = nodelocator;
        this.IPList = IPList;
        this.nodeToIP = nodeToIP;
        this.deadIP = deadIP;
        this.partType = partType;
    }

    public synchronized String getTopic() {
        return topic;
    }

    public synchronized void setTopic(String topic) {
        this.topic = topic;
    }

    public synchronized String getServiceName() {
        return serviceName;
    }

    public synchronized void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public synchronized ArrayList getNodeUrls() {
        return nodeUrls;
    }

    public synchronized void removeNode(RNode node) {
        if ((this.nodeUrls).remove(node)) {
            DynamicAllocate dynamicallocate = new DynamicAllocate();
            dynamicallocate.setNodes(this.nodeUrls);
            MD5NodeLocator newnodelocator = dynamicallocate.getMD5NodeLocator();
            setNodelocator(newnodelocator);
        }
    }

    public synchronized void addNode(RNode node) {
        if (!(this.nodeUrls).contains(node)) {
            (this.nodeUrls).add(node);
            DynamicAllocate dynamicallocate = new DynamicAllocate();
            dynamicallocate.setNodes(this.nodeUrls);
            MD5NodeLocator newnodelocator = dynamicallocate.getMD5NodeLocator();
            setNodelocator(newnodelocator);
        }
    }

    public synchronized int getType() {
        return type;
    }

    public synchronized void setType(int type) {
        this.type = type;
    }

    public synchronized String getKeywords() {
        return keywords;
    }

    public synchronized void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public synchronized String getFilters() {
        return filters;
    }

    public synchronized void setFilters(String filters) {
        this.filters = filters;
    }

    public synchronized NodeLocator getNodelocator() {
        return nodelocator;
    }

    public synchronized void setNodelocator(MD5NodeLocator nodelocator) {
        this.nodelocator = nodelocator;
    }

    public synchronized String[] getIPList() {
        return IPList;
    }

    public synchronized void setIPList(String[] IPList) {
        this.IPList = IPList;
    }

    public synchronized int updateNodeToIP(String sendIP) {
        if (deadIP.size() >= IPList.length) {
            return 0;
        }

        if (!deadIP.contains(sendIP)) {
            deadIP.add(sendIP);
            while (true) {
                if (deadIP.size() >= IPList.length) {
                    nodeToIP.clear();
                    return 0;
                } else {
                    ArrayList<String> arr = new ArrayList<String>();
                    for (String s : IPList) {
                        if (!deadIP.contains(s)) {
                            arr.add(s);
                        }
                    }

                    int[] ns = new int[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        ns[i] = 0;
                    }
                    Set<RNode> na = nodeToIP.keySet();
                    for (RNode n : na) {
                        String ip = nodeToIP.get(n);
                        for (String s : arr) {
                            if (s.equals(ip)) {
                                ns[arr.indexOf(s)]++;
                            }
                        }
                    }

                    int low = Integer.MAX_VALUE;
                    for (int i : ns) {
                        if (i < low) {
                            low = i;
                        }
                    }

                    ArrayList<String> al = new ArrayList<String>();
                    for (int i = 0; i < ns.length; i++) {
                        if (ns[i] == low) {
                            al.add(arr.get(i));
                        }
                    }

                    Random r = new Random();
                    int rr = r.nextInt(al.size());

                    if (low == 0) {
                        SendControl sc = new SendControl(al.get(rr), "start", "db1", "time", "i");
                        HttpResponse hr = sc.send();
                        if (hr != null) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try {
                                hr.getEntity().writeTo(out);
                            } catch (IOException ex) {
                                logger.info(ex, ex);
                            }
                            String resonseEn = new String(out.toByteArray());
                            if ("-1".equals(resonseEn.split("[\n]")[0])) {
                                logger.error(resonseEn.split("[\n]")[1]);
                                deadIP.add(al.get(rr));
                                continue;
                            } else {
                                for (RNode n : na) {
                                    String ip = nodeToIP.get(n);
                                    if (ip.equals(sendIP)) {
                                        nodeToIP.put(n, al.get(rr));
                                    }
                                }

                                logger.info("choose the ip " + al.get(rr) + " to replace " + sendIP);
                                return 1;
                            }
                        } else {
                            logger.info("cann't connect to the " + al.get(rr));
                            deadIP.add(al.get(rr));
                            continue;
                        }
                    } else {
                        for (RNode n : na) {
                            String ip = nodeToIP.get(n);
                            if (ip.equals(sendIP)) {
                                nodeToIP.put(n, al.get(rr));
                            }
                        }
                        logger.info("choose the ip " + al.get(rr) + " to replace " + sendIP);
                        return 1;
                    }
                }
            }
        }
        return 1;
    }

    public synchronized Map<RNode, String> getNodeToIP() {
        return nodeToIP;
    }

    public synchronized void setNodeToIP(Map<RNode, String> nodeToIP) {
        this.nodeToIP = nodeToIP;
    }

    public synchronized void setDeadIP(ArrayList<String> deadIP) {
        this.deadIP = deadIP;
    }

    public synchronized String getPartType() {
        return partType;
    }

    public synchronized void setPartType(String partType) {
        this.partType = partType;
    }

    public synchronized void changerule(ArrayList<RNode> nurl, MD5NodeLocator nodelocator, String keywords, String partType) {
        this.nodeUrls = nurl;
        this.nodelocator = nodelocator;
        this.keywords = keywords;
        this.partType = partType;
    }
}
