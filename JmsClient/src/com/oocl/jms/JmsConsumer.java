package com.oocl.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import com.tibco.tibjms.TibjmsConnectionFactory;

public class JmsConsumer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JmsConsumer consumer = new JmsConsumer();
		consumer.run();
	}
	
	private String url = "tcp://localhost:7222";
	private String user = "seven";
	private String password = "seven";
	private String queueName = "queue.sample";
	
	public void run() {
		Connection connection = null;
		ConnectionFactory factory = null;
		Destination destination = null;
		Session session = null;
		MessageConsumer msgcConsumer = null;
		Message msg = null;
		
		try {
			factory = new TibjmsConnectionFactory(url);
			connection = factory.createConnection(user, password);
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			destination = session.createQueue(queueName);
			msgcConsumer = session.createConsumer(destination);
			connection.start();
			while(true) {
				msg = msgcConsumer.receive();
				if (msg == null) {
					break;
				}
				msg.acknowledge();
				System.out.println(msg.toString());

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			
		}
		
	}

}
