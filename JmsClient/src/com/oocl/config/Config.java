package com.oocl.config;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Config {
	
	private static final String configFile="config.property";
	
	private static final Map<String, String> PARAMMAP_MAP = new HashMap<String, String>();
	
	static {
		init();
	}
	
	private static void init() {
		Properties props = new Properties();
		try {
			InputStream in = new BufferedInputStream(new FileInputStream(configFile));
			props.load(in);
			Enumeration<?> en = props.propertyNames();
			while(en.hasMoreElements()) {
				String key = (String) en.nextElement();
				String value = props.getProperty(key);
				System.out.println(key + " : " + value);
				PARAMMAP_MAP.put(key, value);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String getParams(String key) {
		return PARAMMAP_MAP.get(key);
	}

}
