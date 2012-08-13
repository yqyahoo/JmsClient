/* 
 * Copyright 2001-2006 TIBCO Software Inc.
 * All rights reserved.
 * For more information, please contact:
 * TIBCO Software Inc., Palo Alto, California, USA
 * 
 * $Id: tibjmsMsgProducerPerf.java 22715 2006-08-10 21:06:15Z $
 * 
 */

/*
 * This is a sample message producer class used to measure performance.
 *
 * For the the specified number of threads this sample creates a 
 * session and a message producer for the specified destination.
 * Once the specified number of messages are produced the performance
 * results are printed and the program exits.
 *
 * Usage:  java tibjmsMsgProducerPerf  [options]
 *
 *  where options are:
 *
 *   -server       <url>         EMS server URL. Default is
 *                               "tcp://localhost:7222".
 *   -user         <username>    User name. Default is null.
 *   -password     <password>    User password. Default is null.
 *   -topic        <topic-name>  Topic name. Default is "topic.sample".
 *   -queue        <queue-name>  Queue name. No default.
 *   -size         <num bytes>   Message payload size in bytes. Default is 100.
 *   -count        <num msgs>    Number of messages to send. Default is 10k.
 *   -time         <seconds>     Number of seconds to run. Default is 0 (forever).
 *   -delivery     <mode>        Delivery mode. Default is NON_PERSISTENT.
 *                               Other values: PERSISTENT and RELIABLE.
 *   -threads      <num threads> Number of message producer threads. Default is 1.
 *   -connections  <num conns>   Number of message producer connections. Default is 1.
 *   -txnsize      <num msgs>    Number of nessages per producer transaction. Default is 0.
 *   -rate         <msg/sec>     Message rate for producer threads.
 *   -payload      <file name>   File containing message payload.
 *   -factory      <lookup name> Lookup name for connection factory.
 *   -uniquedests                Each producer thread uses a unique destination.
 *   -compression                Enable message compression.
 */

import java.io.*;
import java.util.*;
import javax.jms.*;
import javax.naming.*;

public class tibjmsMsgProducerPerf implements Runnable
{
    // parameters
    private String serverUrl = "tcp://localhost:7222";
    private String username = null;
    private String password = null;
    private String destName = "topic.sample";
    private String payloadFile = null;
    private String factoryName = null;
    private boolean useTopic = true;
    private boolean uniqueDests = false;
    private boolean compression = false;
    private int msgRate = 0;
    private int txnSize = 0;
    private int count = 10000;
    private int runTime = 0;
    private int msgSize = 0;
    private int threads = 1;
    private int connections = 1;
    private int deliveryMode = DeliveryMode.NON_PERSISTENT;

    // variables
    private int connIter;
    private int destIter;
    private int sentCount;
    private long startTime;
    private long endTime;
    private long elapsed;
    private boolean stopNow;
    private Vector connsVector;

    /**
     * Constructor
     * 
     * @param args the command line arguments
     */
    public tibjmsMsgProducerPerf(String[] args)
    {
        parseArgs(args);

        try {
            tibjmsUtilities.initSSLParams(serverUrl,args);

            // print parameters
            System.err.println();
            System.err.println("------------------------------------------------------------------------");
            System.err.println("tibjmsMsgProducerPerf SAMPLE");
            System.err.println("------------------------------------------------------------------------");
            System.err.println("Server....................... " + serverUrl);
            System.err.println("User......................... " + username);
            System.err.println("Destination.................. " + destName);
            System.err.println("Message Size................. " + (payloadFile != null ? payloadFile : String.valueOf(msgSize)));
            if (count > 0)
                System.err.println("Count........................ " + count);
            if (runTime > 0)
                System.err.println("Duration..................... " + runTime);
            System.err.println("Production Threads........... " + threads);
            System.err.println("Production Connections....... " + connections);
            System.err.println("DeliveryMode................. " + deliveryModeName(deliveryMode));
            System.err.println("Compression.................. " + compression);
            if (msgRate > 0)
                System.err.println("Message Rate................. " + msgRate);
            if (txnSize > 0)
                System.err.println("Transaction Size............. " + txnSize);
            if (factoryName != null)
                System.err.println("Connection Factory........... " + factoryName);
            System.err.println("------------------------------------------------------------------------");
            System.err.println();

            // lookup the connection factory
            ConnectionFactory factory = null;
            if (factoryName != null)
            {
                tibjmsUtilities.initJNDI(serverUrl);
                factory = (ConnectionFactory) tibjmsUtilities.lookup(factoryName);
            }
            else 
            {
                factory = new com.tibco.tibjms.TibjmsConnectionFactory(serverUrl);
            }
            
            // create the connections
            connsVector = new Vector(connections);
            for (int i=0;i<connections;i++)
            {
                Connection conn = factory.createConnection(username, password);
                conn.start();
                connsVector.add(conn);
            }

            // create the producer threads
            Vector tv = new Vector(threads);
            for (int i=0;i<threads;i++)
            {
                Thread t = new Thread(this);
                tv.add(t);
                t.start();
            }

            // run for the specified amout of time
            if (runTime > 0)
            {
                try 
                {
                    Thread.sleep(runTime * 1000);
                } 
                catch (InterruptedException e) {}

                // ensure producer threads stop now
                stopNow = true;
                for (int i=0;i<threads;i++)
                {
                    Thread t = (Thread)tv.elementAt(i);
                    t.interrupt();
                }
            }

            // wait for the producer threads to exit
            for (int i=0;i<threads;i++)
            {
                Thread t = (Thread)tv.elementAt(i);
                try 
                {
                    t.join();
                } 
                catch (InterruptedException e) {}
            }

            // close the connections
            for (int i=0;i<connections;i++) 
            {
                Connection conn = (Connection)connsVector.elementAt(i);
                conn.close();
            }

            // print performance
            System.err.println(getPerformance());
        }
        catch (NamingException e)
        {
            e.printStackTrace();
        }
        catch (JMSException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Returns a connection.
     */
    private synchronized Connection getConnection()
    {
        Connection connection = (Connection)connsVector.elementAt(connIter++);
        if (connIter == connections)
            connIter = 0;
        return connection;
    }

    /**
     * Returns a destination.
     */
    private synchronized Destination getDestination(Session s) throws JMSException
    {
        if (useTopic)
        {
            if (!uniqueDests)
                return s.createTopic(destName);
            else
                return s.createTopic(destName + "." + ++destIter);
        }
        else
        {
            if (!uniqueDests)
                return s.createQueue(destName);
            else
                return s.createQueue(destName + "." + ++destIter);
        }
    }

    /**
     * Update the total sent count.
     */
    private synchronized void countSends(int count)
    {
        sentCount += count;
    }

    /**
     * The producer thread's run method.
     */
    public void run()
    {
        int msgCount = 0;
        MsgRateChecker msgRateChecker = null;
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {}

        try {
            // get the connection
            Connection connection = getConnection();
            
            // create a session
            Session session = connection.createSession(txnSize > 0, Session.AUTO_ACKNOWLEDGE);
            
            // get the destination
            Destination destination = getDestination(session);
            
            // create the producer
            MessageProducer msgProducer = session.createProducer(destination);

            // set the delivery mode
            msgProducer.setDeliveryMode(deliveryMode);
            
            // performance settings
            msgProducer.setDisableMessageID(true);
            msgProducer.setDisableMessageTimestamp(true);

            // create the message
            Message msg = createMessage(session);

            // enable compression if ncessary
            if (compression)
                msg.setBooleanProperty("JMS_TIBCO_COMPRESS", true); 

            // initialize message rate checking
            if (msgRate > 0)
                msgRateChecker = new MsgRateChecker(msgRate);

            startTiming();
            
            // publish messages
            while ((count == 0 || msgCount < (count/threads)) && !stopNow)
            {
                msgProducer.send(msg);

                msgCount++;
                
                // commit the transaction if necessary
                if (txnSize > 0 && msgCount % txnSize == 0)
                    session.commit();

                // check the message rate
                if (msgRate > 0)
                    msgRateChecker.checkMsgRate(msgCount);
            }
            
            // commit any remaining messages
            if (txnSize > 0)
                session.commit();
        }
        catch (JMSException e)
        {
            if (!stopNow)
                e.printStackTrace();
        }

        stopTiming();
        
        countSends(msgCount);
    }

    /**
     * Create the message.
     */
    private Message createMessage(Session session) throws JMSException
    {
        String payload = null;
        
        // create the message
        BytesMessage msg = session.createBytesMessage();

        // add the payload
        if (payloadFile != null)
        {
            try
            {
                InputStream instream = 
                    new BufferedInputStream(new FileInputStream(payloadFile));
                int size = instream.available();
                byte[] bytesRead = new byte[size];
                instream.read(bytesRead);
                payload = new String(bytesRead);
            }
            catch(IOException e)
            {
                System.err.println("Error: unable to load payload file - " + e.getMessage());
            }
        }
        else if (msgSize > 0)
        {
            StringBuffer sb = new StringBuffer(msgSize);
            char c = 'A';
            for (int i = 0; i < msgSize; i++)
            {
                sb.append(c++);
                if (c > 'z')
                    c = 'A';
            }
            payload = sb.toString();
        }
        
        if (payload != null)
        {
            // add the payload to the message
            msg.writeBytes(payload.getBytes());
        }
        
        return msg;
    }

    private synchronized void startTiming()
    {
        if (startTime == 0)
            startTime = System.currentTimeMillis();
    }
    
    private synchronized void stopTiming()
    {
        endTime = System.currentTimeMillis();
    }

    /**
     * Convert delivery mode to a string.
     */
    private static String deliveryModeName(int mode) {
        switch(mode)
        {
            case javax.jms.DeliveryMode.PERSISTENT:         
                return "PERSISTENT";
            case javax.jms.DeliveryMode.NON_PERSISTENT:     
                return "NON_PERSISTENT";
            case com.tibco.tibjms.Tibjms.RELIABLE_DELIVERY: 
                return "RELIABLE";
            default:                                        
                return "(unknown)";
        }
    }

    /**
     * Get the performance results.
     */
    private String getPerformance()
    {
        if (endTime > startTime)
        {
            elapsed = endTime - startTime;
            double seconds = elapsed/1000.0;
            int perf = (int)((sentCount * 1000.0)/elapsed);
            return (sentCount + " times took " + seconds + " seconds, performance is " + perf + " messages/second");
        }
        else
        {
            return "interval too short to calculate a message rate";
        }
    }

    /**
     * Print the usage and exit.
     */
    private void usage()
    {
        System.err.println("\nUsage: java tibjmsMsgProducerPerf [options] [ssl options]");
        System.err.println("\n");
        System.err.println("   where options are:");
        System.err.println("");
        System.err.println("   -server       <server URL>  - EMS server URL, default is local server");
        System.err.println("   -user         <user name>   - user name, default is null");
        System.err.println("   -password     <password>    - password, default is null");
        System.err.println("   -topic        <topic-name>  - topic name, default is \"topic.sample\"");
        System.err.println("   -queue        <queue-name>  - queue name, no default");
        System.err.println("   -size         <nnnn>        - Message payload in bytes");
        System.err.println("   -count        <nnnn>        - Number of messages to send, default 10k");
        System.err.println("   -time         <seconds>     - Number of seconds to run");
        System.err.println("   -threads      <nnnn>        - Number of threads to use for sends");
        System.err.println("   -connections  <nnnn>        - Number of connections to use for sends");
        System.err.println("   -delivery     <nnnn>        - DeliveryMode, default NON_PERSISTENT");
        System.err.println("   -txnsize      <count>       - Number of messages per transaction");
        System.err.println("   -rate         <msg/sec>     - Message rate for each producer thread");
        System.err.println("   -payload      <file name>   - File containing message payload.");
        System.err.println("   -factory      <lookup name> - Lookup name for connection factory.");
        System.err.println("   -uniquedests                - Each producer uses a different destination");
        System.err.println("   -compression                - Enable compression while sending msgs ");
        System.err.println("   -help-ssl                   - help on ssl parameters\n");
        
        System.exit(0);
    }

    /**
     * Parse the command line arguments.
     */
    private void parseArgs(String[] args)
    {
        int i=0;

        while(i < args.length)
        {
            if (args[i].compareTo("-server")==0)
            {
                if ((i+1) >= args.length) usage();
                serverUrl = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-queue")==0)
            {
                if ((i+1) >= args.length) usage();
                destName = args[i+1];
                i += 2;
                useTopic = false;
            }
            else if (args[i].compareTo("-topic")==0)
            {
                if ((i+1) >= args.length) usage();
                destName = args[i+1];
                i += 2;
                useTopic = true;
            }
            else if (args[i].compareTo("-user")==0)
            {
                if ((i+1) >= args.length) usage();
                username = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-password")==0)
            {
                if ((i+1) >= args.length) usage();
                password = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-delivery")==0)
            {
                if ((i+1) >= args.length) usage();
                String dm = args[i+1];
                i += 2;
                if (dm.compareTo("PERSISTENT")==0)
                    deliveryMode = javax.jms.DeliveryMode.PERSISTENT;
                else if (dm.compareTo("NON_PERSISTENT")==0)
                    deliveryMode = javax.jms.DeliveryMode.NON_PERSISTENT;
                else if (dm.compareTo("RELIABLE")==0)
                    deliveryMode = com.tibco.tibjms.Tibjms.RELIABLE_DELIVERY;
                else {
                    System.err.println("Error: invalid value of -delivery parameter");
                    usage();
                }
            }
            else if (args[i].compareTo("-count")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    count = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) {
                    System.err.println("Error: invalid value of -count parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-time")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    runTime = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) {
                    System.err.println("Error: invalid value of -time parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-threads")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    threads = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -threads parameter");
                    usage();
                }
                if (threads < 1) {
                    System.err.println("Error: invalid value of -threads parameter, must be >= 1");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-connections")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    connections = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) {
                    System.err.println("Error: invalid value of -connections parameter");
                    usage();
                }
                if (connections < 1) 
                {
                    System.err.println("Error: invalid value of -connections parameter, must be >= 1");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-size")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    msgSize = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -size parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-txnsize")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    txnSize = Integer.parseInt(args[i+1]);
                }
                catch(NumberFormatException e) 
                {
                    System.err.println("Error: invalid value of -txnsize parameter");
                    usage();
                }
                if (txnSize < 1) 
                {
                    System.err.println("Error: invalid value of -txnsize parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-rate")==0)
            {
                if ((i+1) >= args.length) usage();
                try 
                {
                    msgRate = Integer.parseInt(args[i+1]);
                }
                catch (NumberFormatException e)
                {
                    System.err.println("Error: invalid value of -rate parameter");
                    usage();
                }
                if (msgRate < 1)
                {
                    System.err.println("Error: invalid value of -rate parameter");
                    usage();
                }
                i += 2;
            }
            else if (args[i].compareTo("-payload")==0)
            {
                if ((i+1) >= args.length) usage();
                payloadFile = args[i+1];
                i += 2;
            }
            else if (args[i].compareTo("-factory")==0)
            {
                if ((i+1) >= args.length) usage();
                factoryName = args[i+1];
                i += 2;
            }
            else if (args[i].startsWith("-ssl"))
            {
                i += 2;
            }
            else if (args[i].compareTo("-uniquedests")==0)
            {
                uniqueDests = true;
                i += 1;
            }
            else if (args[i].compareTo("-compression")==0)
            {
                compression = true;
                i += 1;
            } 
            else if (args[i].compareTo("-help")==0)
            {
                usage();
            }
            else if (args[i].compareTo("-help-ssl")==0)
            {
                tibjmsUtilities.sslUsage();
            }
            else
            {
                System.err.println("Error: invalid option: " + args[i]);
                usage();
            }
        }
    }

    /**
     * Get the total elapsed time.
     */
    public long getElapsedTime()
    {
        return elapsed;
    }

    /**
     * Get the total produced message count.
     */
    public int getSentCount()
    {
        return sentCount;
    }

    /**
     * Class used to control the producer's send rate.
     */
    private class MsgRateChecker 
    {
        int rate;
        long sampleStart;
        int sampleTime;
        int sampleCount;
        
        MsgRateChecker(int rate)
        {
            this.rate = rate;
            // initialize
            this.sampleTime = 10;
        }
        
        void checkMsgRate(int count)
        {
            if (msgRate < 100)
            {
                if (count % 10 == 0)
                {
                   try {
                       long sleepTime = (long)((10.0/(double)msgRate)*1000);
                       Thread.sleep(sleepTime);
                   } catch(InterruptedException e) {}
                }
            }
            else if (sampleStart == 0)
            {
                sampleStart = System.currentTimeMillis();
            }
            else
            {
                long elapsed = System.currentTimeMillis() - sampleStart;
                if (elapsed >= sampleTime)
                {
                    int actualMsgs = count - sampleCount;
                    int expectedMsgs = (int)(elapsed*((double)msgRate/1000.0));
                    if (actualMsgs > expectedMsgs)
                    {
                        long sleepTime = (long)((double)(actualMsgs-expectedMsgs)/((double)msgRate/1000.0));
                        try 
                        {
                            Thread.sleep(sleepTime);
                        }
                        catch (InterruptedException e) {}
                        if (sampleTime > 20)
                            sampleTime -= 10;
                    }
                    else
                    {
                        if (sampleTime < 300)
                            sampleTime += 10;
                    }
                    sampleStart = System.currentTimeMillis();
                    sampleCount = count;
                }
            }
        }
    }
    
    /**
     * main
     */
    public static void main(String[] args)
    {
        tibjmsMsgProducerPerf t = new tibjmsMsgProducerPerf(args);
    }
}
