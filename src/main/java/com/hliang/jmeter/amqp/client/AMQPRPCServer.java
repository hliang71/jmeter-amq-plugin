package com.icix.jmeter.amqp.client;


import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ConsumerCancelledException;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;


public class AMQPRPCServer {
	private static final String RPC_QUEUE_NAME = "rpc_queue";
	private static final String CONSUMER_EXCHANGE_NAME = "my_rpc_exchange.response";
	private static final String PUBLISHER_EXCHANGE_NAME = "my_rpc_exchange.request";
	private static final String CONSUMER_EXCHANGE_TYPE = "topic";
	private static final String PUBLISHER_EXCHANGE_TYPE = "topic";
	private static final String CONSUMER_ROUTING_KEY = "RPCMessage.Response.v1_0";
	private static final int POOL_SIZE = 20;
	
	class AMQPListner implements Runnable 
	{
		private ConnectionFactory factory = null;
		private int assignedNumber = 0; 
		private AMQPRPCServer server = null;
		public AMQPListner(AMQPRPCServer server, ConnectionFactory factory, int assignedNum)
		{
			this.factory = factory;
			this.assignedNumber = assignedNum;
			this.server = server;
		}

		@Override
		public void run(){
			try
			{
				Connection connection = factory.newConnection();
				String uuid = UUID.randomUUID().toString();
				String queueName = RPC_QUEUE_NAME+uuid;
				Map<String, Channel> result = this.server.initChannel(connection, this.assignedNumber, queueName);
				Channel consumerChannel = result.get("consumer");
				
			    Channel publisherChannel = result.get("publisher");
				
			    
				QueueingConsumer consumer = new QueueingConsumer(consumerChannel);
				consumerChannel.basicConsume(queueName, true, consumer);
			    System.out.println("in server ");
				while (true) {
				    QueueingConsumer.Delivery delivery = consumer.nextDelivery();
				    //System.out.printf("delivery is %s, body is %s", delivery, new String(delivery.getBody()));
				    BasicProperties props = delivery.getProperties();
				    //System.out.printf("correlationId is %s", props.getCorrelationId());
				    BasicProperties replyProps = new BasicProperties
				                                     .Builder()
				                                     .correlationId(props.getCorrelationId())
				                                     .messageId(props.getMessageId())
				                                     .build();
			
				    String response = new String(delivery.getBody());
				    //System.out.printf("response is %s", new String(delivery.getBody()));
				    //System.out.printf("reply to is %s",  props.getReplyTo()        );
				    publisherChannel.basicPublish( PUBLISHER_EXCHANGE_NAME, props.getReplyTo(), replyProps, response.getBytes());
			
				    //consumerChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
				}
			}catch(Exception e)
			{
				e.printStackTrace();
			}
			
		}
		
	}
	
	
	public static final void main(String[] args) throws IOException, ShutdownSignalException, ConsumerCancelledException, InterruptedException
	{
		ConnectionFactory factory = new ConnectionFactory();
		factory.setVirtualHost("/");
		factory.setHost("localhost");
		factory.setUsername("guest");
		factory.setPassword("guest");
		factory.setPort(5672);
		ExecutorService executor = Executors.newFixedThreadPool(POOL_SIZE);
		AMQPRPCServer server = new AMQPRPCServer();
	    for (int i = 0; i < POOL_SIZE; i++) {
	      Runnable worker = server.new AMQPListner(server, factory, i);
	      executor.execute(worker);
	    }
	}
	
	
	protected Map<String, Channel> initChannel(Connection connection, int assignedNum, String queueName) throws IOException {
        Channel consumerChannel = connection.createChannel();
        Channel publisherChannel = connection.createChannel();
       
        Map<String, Channel> result = new HashMap<String, Channel>();
        
        if(consumerChannel != null && !consumerChannel.isOpen()){
            
            consumerChannel = connection.createChannel(); 
        }
        if(publisherChannel != null && !publisherChannel.isOpen()){
            publisherChannel = connection.createChannel();
        }
        
        DecimalFormat formatter = new DecimalFormat("#00.###");
        
        String queueRoutingKey = CONSUMER_ROUTING_KEY+formatter.format(assignedNum);
        
        consumerChannel.queueDeclare(queueName, false, false, true,null);
        consumerChannel.exchangeDeclare(CONSUMER_EXCHANGE_NAME, CONSUMER_EXCHANGE_TYPE, false);
        consumerChannel.queueBind(queueName, CONSUMER_EXCHANGE_NAME, queueRoutingKey);
        consumerChannel.basicQos(1);
        publisherChannel.exchangeDeclare(PUBLISHER_EXCHANGE_NAME, PUBLISHER_EXCHANGE_TYPE, false);
        result.put("consumer", consumerChannel);
        result.put("publisher", publisherChannel);
        return result;
    }

}
