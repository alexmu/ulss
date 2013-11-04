/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package cn.ac.ncic.impdatabg.conf;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 *
 * @author AlexMu
 */
public class Configuration {

    Map<String, String> param2Value = new HashMap<String, String>();
    private static Configuration conf = null;
    Logger logger = null;

    {
        PropertyConfigurator.configure("log4j.properties");
        logger = Logger.getLogger(Configuration.class.getName());
    }

    private Configuration() {
    }

    public static Configuration getConfiguration(String pConfFileName) {
        if (conf != null) {
            return conf;
        } else {
            conf = new Configuration();
            if (conf.loadConfiguration(pConfFileName)) {
                return conf;
            } else {
                return null;
            }
        }
    }

    private boolean loadConfiguration(String pConfFileName) {
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(pConfFileName);
            Properties pros = new Properties();
            pros.load(fis);
            Set params = pros.keySet();
            Iterator itr = params.iterator();
            String key = null;
            while (itr.hasNext()) {
                key = (String) itr.next();
                param2Value.put(key, pros.getProperty(key));
            }
        } catch (Exception ex) {
        } finally {
            try {
                fis.close();
            } catch (Exception ex) {
                //do noting
            }
        }
        return true;
    }

    public int getInt(String pParamName, int pDefaultValue) throws ConfigurationException {
        String valueStr = param2Value.get(pParamName);
        int value = pDefaultValue;
        if (valueStr != null && !valueStr.isEmpty()) {
            try {
                value = Integer.parseInt(valueStr);
            } catch (Exception ex) {
                throw new ConfigurationException("can not cast value " + valueStr + " correctly into a integer for parameter " + pParamName);
            }
        }
        return value;
    }

    public boolean getBoolean(String pParamName, boolean pDefaultValue) throws ConfigurationException {
        String valueStr = param2Value.get(pParamName);
        boolean value = pDefaultValue;
        if (valueStr != null && !valueStr.isEmpty()) {
            try {
                value = Boolean.parseBoolean(valueStr);
            } catch (Exception ex) {
                throw new ConfigurationException("can not cast value " + valueStr + " correctly into a boolean value for parameter " + pParamName);
            }
        }
        return value;
    }

    public String getString(String pParamName, String pDefaultValue) {
        String valueStr = param2Value.get(pParamName);
        String value = pDefaultValue;
        if (valueStr != null) {
            value = valueStr;
        }
        return value;
    }

    //for debug
    public void dumpConfiguration() {
        Set params = param2Value.keySet();
        Iterator itr = params.iterator();
        String key = null;
        while (itr.hasNext()) {
            key = (String) itr.next();
            logger.info(key + "=" + param2Value.get(key));
        }
    }
}