package com.oocl.jms;


import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import com.oocl.config.Config;
import com.tibco.tibjms.TibjmsConnectionFactory;

public class JmsProducer {
	
	private String serverURL;
	private String user = "admin";
	private String password = "admin";
	private String queueName = "queue.sample";
	private int count = 150;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
//		String url = "tcp://localhost:7222";
//		String user = "seven";
//		String password = "seven";
		
		JmsProducer producer = new JmsProducer();
		producer.run();
	}
	
	public JmsProducer() {
		init();
	}
	
	private void init() {
		this.serverURL = Config.getParams("serverURL");
		this.user = Config.getParams("user");
		this.password = Config.getParams("password");
		this.queueName = Config.getParams("queues");
		this.count = Integer.parseInt(Config.getParams("count"));
		
	}
	
	
	public void run() {
		Connection connection = null;
		Session session = null;
		Destination destination = null;
		MessageProducer msgProducer = null;
		try {
			ConnectionFactory factory = new TibjmsConnectionFactory(serverURL);
			connection = factory.createConnection(user, password);
			
			System.out.println(connection + "successful" +  "\nStarting send message");
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			destination = session.createQueue(queueName);
			msgProducer = session.createProducer(destination);
			
			Message msg = session.createMessage();
			for(int i=0; i<count; i++) {
				msg.setJMSType("this is the" + i + "message");
				msgProducer.send(msg);
				System.out.println("this is the" + i + "message send successful");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(connection != null) {
				try {
					connection.close();
				} catch (JMSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			System.out.println("end!");
		}
		
	}

}
