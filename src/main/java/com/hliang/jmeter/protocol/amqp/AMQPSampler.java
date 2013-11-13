package com.icix.jmeter.protocol.amqp;

import java.io.IOException;
import java.util.*;
import java.security.*;

import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.AMQP.Queue.BindOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.lang.StringUtils;

public abstract class AMQPSampler extends AbstractSampler implements ThreadListener {

    /**
	 * 
	 */
	private static final long serialVersionUID = 8788563225815907169L;
	public static final boolean DEFAULT_EXCHANGE_DURABLE = true;
    public static final boolean DEFAULT_EXCHANGE_REDECLARE = false;
    public static final boolean DEFAULT_QUEUE_REDECLARE = false;

    public static final int DEFAULT_PORT = 5672;
    public static final String DEFAULT_PORT_STRING = Integer.toString(DEFAULT_PORT);

    public static final int DEFAULT_TIMEOUT = 1000;
    public static final String DEFAULT_TIMEOUT_STRING = Integer.toString(DEFAULT_TIMEOUT);

    public static final int DEFAULT_ITERATIONS = 1;
    public static final String DEFAULT_ITERATIONS_STRING = Integer.toString(DEFAULT_ITERATIONS);

    private static final Logger log = LoggingManager.getLoggerForClass();


    //++ These are JMX names, and must not be changed
    private static final String EXCHANGE = "AMQPSampler.Exchange";
    private static final String EXCHANGE_TYPE = "AMQPSampler.ExchangeType";
    private static final String EXCHANGE_DURABLE = "AMQPSampler.ExchangeDurable";
    private static final String EXCHANGE_REDECLARE = "AMQPSampler.ExchangeRedeclare";
    private static final String QUEUE = "AMQPSampler.Queue";
    private static final String ROUTING_KEY = "AMQPSampler.RoutingKey";
    private static final String VIRUTAL_HOST = "AMQPSampler.VirtualHost";
    private static final String HOST = "AMQPSampler.Host";
    private static final String PORT = "AMQPSampler.Port";
    private static final String SSL = "AMQPSampler.SSL";
    private static final String USERNAME = "AMQPSampler.Username";
    private static final String PASSWORD = "AMQPSampler.Password";
    private static final String TIMEOUT = "AMQPSampler.Timeout";
    private static final String ITERATIONS = "AMQPSampler.Iterations";
    private static final String MESSAGE_TTL = "AMQPSampler.MessageTTL";
    
    private static final String QUEUE_DURABLE = "AMQPSampler.QueueDurable";
    private static final String QUEUE_REDECLARE = "AMQPSampler.Redeclare";
    private static final String QUEUE_EXCLUSIVE = "AMQPSampler.QueueExclusive";
    private static final String QUEUE_AUTO_DELETE = "AMQPSampler.QueueAutoDelete";
    private static final int DEFAULT_HEARTBEAT = 1;

    private transient ConnectionFactory factory;
    private transient Connection connection;
    private transient String replyTo;
    

    protected AMQPSampler(){
        factory = new ConnectionFactory();
        factory.setRequestedHeartbeat(DEFAULT_HEARTBEAT);
    }

    protected boolean initChannel() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        Channel consumerChannel = this.getConsumerChannel();
        Channel publisherChannel = this.getPublisherChannel();
        boolean changeToRedeclare = false;
        if(this.getExchangeRedeclare() || this.getPublisherExchangeRedeclare())
        {
        	synchronized(DEFAULT_PORT_STRING)
        	{
		        changeToRedeclare = (!this.getExchangeRedeclare().equals(this.getConsumerRedeclareFlag())) || (!this.getPublisherExchangeRedeclare().equals(this.getPublisherRedeclareFlag()));
		        if(changeToRedeclare)
		        {
		        	this.setConsumerRedeclareFlag(this.getExchangeRedeclare());
		        	this.setPublisherRedeclareFlag(this.getPublisherExchangeRedeclare());
		        }
        	}
        }
        
        if(consumerChannel != null && !consumerChannel.isOpen()){
            log.warn("consumerChannel " + consumerChannel.getChannelNumber()
                    + " closed unexpectedly: ", consumerChannel.getCloseReason());
            consumerChannel = null; // so we re-open it below
        }
        if(publisherChannel != null && !publisherChannel.isOpen()){
            log.warn("publisherChannel " + publisherChannel.getChannelNumber()
                    + " closed unexpectedly: ", publisherChannel.getCloseReason());
            publisherChannel = null; // so we re-open it below
        }
        if(changeToRedeclare)
        {
        	if(!StringUtils.isBlank(getExchange()))
        	{
        		this.deleteExchange();
        	}
        	if(!StringUtils.isBlank(getPublisherExchange()))
        	{
        		this.deletePublisherExchange();
        	}
        }
        if(consumerChannel == null) {
        	consumerChannel = createChannel();
            this.setConsumerChannel(consumerChannel);

            //TODO: Break out queue binding
            boolean queueConfigured = (getQueue() != null && !getQueue().isEmpty());

            if(queueConfigured) {
                if (getQueueRedeclare()) {
                    deleteQueue();
                }

                AMQP.Queue.DeclareOk declareQueueResp = consumerChannel.queueDeclare(getQueue(), queueDurable(), queueExclusive(), queueAutoDelete(), getQueueArguments());
            }

            if(!StringUtils.isBlank(getExchange())) { //Use a named exchange

                AMQP.Exchange.DeclareOk declareExchangeResp = consumerChannel.exchangeDeclare(getExchange(), getExchangeType(), getExchangeDurable());
                if (queueConfigured) {
                    BindOk ok = consumerChannel.queueBind(getQueue(), getExchange(), getRoutingKey());
                    this.replyTo = getRoutingKey();
                }
            }

            log.info("bound to:"
                    +"\n\t queue: " + getQueue()
                    +"\n\t exchange: " + getExchange()
                    +"\n\t routing key: " + getRoutingKey()
                    +"\n\t arguments: " + getQueueArguments()
            );
        }
        
        if(publisherChannel == null) {
        	publisherChannel = createChannel();
            this.setPublisherChannel(publisherChannel);
            if(!StringUtils.isBlank(getPublisherExchange())) { //Use a named exchange
                AMQP.Exchange.DeclareOk declarePublisherExchangeResp = publisherChannel.exchangeDeclare(getPublisherExchange(), getPublisherExchangeType(), getPublisherExchangeDurable());                
            }
            log.info("publisher bound to:"
                    +"\n\t exchange: " + getPublisherExchange());
                 
        }      
        return true;
    }

    private Map<String, Object> getQueueArguments() {
        Map<String, Object> arguments = new HashMap<String, Object>();

        if(getMessageTTL() != null && !getMessageTTL().isEmpty())
            arguments.put("x-message-ttl", getMessageTTLAsInt());

        return arguments;
    }

    
    protected abstract Channel getConsumerChannel();
    protected abstract void setConsumerChannel(Channel channel);
    protected abstract Channel getPublisherChannel();
    protected abstract void setPublisherChannel(Channel channel);
    protected abstract String getPublisherExchange() ;
    protected abstract void setPublisherExchange(String name);
    protected abstract String getPublisherExchangeType() ;
    protected abstract void setPublisherExchangeType(String name);
    protected abstract Boolean getPublisherExchangeDurable() ;
    protected abstract void setPublisherExchangeDurable(Boolean content);
    protected abstract Boolean getPublisherExchangeRedeclare() ;
    protected abstract void setPublisherExchangeRedeclare(Boolean content);
    protected abstract String getCorrelationId();    
    protected abstract void setCorrelationId(String c);
    protected abstract String getMessageRoutingKey();
    protected abstract void setMessageRoutingKey(String content) ;
    protected abstract void setConsumerRedeclareFlag(Boolean b);
    protected abstract void setPublisherRedeclareFlag(Boolean b);
    protected abstract Boolean getConsumerRedeclareFlag();
    protected abstract Boolean getPublisherRedeclareFlag();
    
    
    // TODO: make this configurable
    protected BasicProperties getProperties() {
    	String uuid = UUID.randomUUID().toString();
        AMQP.BasicProperties properties =  new BasicProperties("application/json",
                null,
                null,
                1,
                0, uuid, null, null,
                uuid, new Date(), null, null,
                null, null);
        return properties;
    }

    /**
     * @return a string for the sampleResult Title
     */
    protected String getTitle() {
        return this.getName();
    }

    protected int getTimeoutAsInt() {
        if (getPropertyAsInt(TIMEOUT) < 1) {
            return DEFAULT_TIMEOUT;
        }
        return getPropertyAsInt(TIMEOUT);
    }

    public String getTimeout() {
        return getPropertyAsString(TIMEOUT, DEFAULT_TIMEOUT_STRING);
    }


    public void setTimeout(String s) {
        setProperty(TIMEOUT, s);
    }

    public String getIterations() {
        return getPropertyAsString(ITERATIONS, DEFAULT_ITERATIONS_STRING);
    }

    public void setIterations(String s) {
        setProperty(ITERATIONS, s);
    }

    public int getIterationsAsInt() {
        return getPropertyAsInt(ITERATIONS);
    }

    public String getExchange() {
        return getPropertyAsString(EXCHANGE);
    }

    public void setExchange(String name) {
        setProperty(EXCHANGE, name);
    }


    public String getExchangeType() {
        return getPropertyAsString(EXCHANGE_TYPE);
    }

    public void setExchangeType(String name) {
        setProperty(EXCHANGE_TYPE, name);
    }

    public Boolean getExchangeDurable() {
        return getPropertyAsBoolean(EXCHANGE_DURABLE);
    }

    public void setExchangeDurable(Boolean content) {
        setProperty(EXCHANGE_DURABLE, content);
    }

    public Boolean getExchangeRedeclare() {
        return getPropertyAsBoolean(EXCHANGE_REDECLARE);
    }

    public void setExchangeRedeclare(Boolean content) {
        setProperty(EXCHANGE_REDECLARE, content);
    }

    public String getQueue() {
        return getPropertyAsString(QUEUE);
    }

    public void setQueue(String name) {
        setProperty(QUEUE, name);
    }


    public String getRoutingKey() {
        return getPropertyAsString(ROUTING_KEY);
    }

    public void setRoutingKey(String name) {
        setProperty(ROUTING_KEY, name);
    }


    public String getVirtualHost() {
        return getPropertyAsString(VIRUTAL_HOST);
    }

    public void setVirtualHost(String name) {
        setProperty(VIRUTAL_HOST, name);
    }


    public String getMessageTTL() {
        return getPropertyAsString(MESSAGE_TTL);
    }

    public void setMessageTTL(String name) {
        setProperty(MESSAGE_TTL, name);
    }
                   
   
   
    
    public String getReplyTo() {
		return replyTo;
	}

	public void setReplyTo(String replyTo) {
		this.replyTo = replyTo;
	}

	protected Integer getMessageTTLAsInt() {
        if (getPropertyAsInt(MESSAGE_TTL) < 1) {
            return null;
        }
        return getPropertyAsInt(MESSAGE_TTL);
    }

    public String getHost() {
        return getPropertyAsString(HOST);
    }

    public void setHost(String name) {
        setProperty(HOST, name);
    }


    public String getPort() {
        return getPropertyAsString(PORT);
    }

    public void setPort(String name) {
        setProperty(PORT, name);
    }

    protected int getPortAsInt() {
        if (getPropertyAsInt(PORT) < 1) {
            return DEFAULT_PORT;
        }
        return getPropertyAsInt(PORT);
    }

    public void setConnectionSSL(String content) {
        setProperty(SSL, content);
    }

    public void setConnectionSSL(Boolean value) {
        setProperty(SSL, value.toString());
    }

    public boolean connectionSSL() {
        return getPropertyAsBoolean(SSL);
    }


    public String getUsername() {
        return getPropertyAsString(USERNAME);
    }

    public void setUsername(String name) {
        setProperty(USERNAME, name);
    }


    public String getPassword() {
        return getPropertyAsString(PASSWORD);
    }

    public void setPassword(String name) {
        setProperty(PASSWORD, name);
    }

    /**
     * @return the whether or not the queue is durable
     */
    public String getQueueDurable() {
        return getPropertyAsString(QUEUE_DURABLE);
    }

    public void setQueueDurable(String content) {
        setProperty(QUEUE_DURABLE, content);
    }

    public void setQueueDurable(Boolean value) {
        setProperty(QUEUE_DURABLE, value.toString());
    }

    public boolean queueDurable(){
        return getPropertyAsBoolean(QUEUE_DURABLE);
    }

    /**
     * @return the whether or not the queue is exclusive
     */
    public String getQueueExclusive() {
        return getPropertyAsString(QUEUE_EXCLUSIVE);
    }

    public void setQueueExclusive(String content) {
        setProperty(QUEUE_EXCLUSIVE, content);
    }

    public void setQueueExclusive(Boolean value) {
        setProperty(QUEUE_EXCLUSIVE, value.toString());
    }

    public boolean queueExclusive(){
        return getPropertyAsBoolean(QUEUE_EXCLUSIVE);
    }

    /**
     * @return the whether or not the queue should auto delete
     */
    public String getQueueAutoDelete() {
        return getPropertyAsString(QUEUE_AUTO_DELETE);
    }

    public void setQueueAutoDelete(String content) {
        setProperty(QUEUE_AUTO_DELETE, content);
    }

    public void setQueueAutoDelete(Boolean value) {
        setProperty(QUEUE_AUTO_DELETE, value.toString());
    }

    public boolean queueAutoDelete(){
        return getPropertyAsBoolean(QUEUE_AUTO_DELETE);
    }


    public Boolean getQueueRedeclare() {
        return getPropertyAsBoolean(QUEUE_REDECLARE);
    }

    public void setQueueRedeclare(Boolean content) {
       setProperty(QUEUE_REDECLARE, content);
    }

    protected void cleanup() {
        try {
            //getChannel().close();   // closing the connection will close the channel if it's still open
        	this.setConsumerRedeclareFlag(false);
        	this.setPublisherRedeclareFlag(false);
            if(connection != null && connection.isOpen())
                connection.close();
        } catch (IOException e) {
            log.error("Failed to close connection", e);
        }
    }

    @Override
    public void threadFinished() {
        log.info("AMQPSampler.threadFinished called");
        cleanup();
    }

    @Override
    public void threadStarted() {

    }

    protected Channel createChannel() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        log.info("Creating channel " + getVirtualHost()+":"+getPortAsInt());

         if (connection == null || !connection.isOpen()) {
            factory.setConnectionTimeout(getTimeoutAsInt());
            factory.setVirtualHost(getVirtualHost());
            factory.setHost(getHost());
            factory.setPort(getPortAsInt());
            factory.setUsername(getUsername());
            factory.setPassword(getPassword());
            if (connectionSSL()) {
                factory.useSslProtocol("TLS");
            }

            log.info("RabbitMQ ConnectionFactory using:"
                  +"\n\t virtual host: " + getVirtualHost()
                  +"\n\t host: " + getHost()
                  +"\n\t port: " + getPort()
                  +"\n\t username: " + getUsername()
                  +"\n\t password: " + getPassword()
                  +"\n\t timeout: " + getTimeout()
                  +"\n\t heartbeat: " + factory.getRequestedHeartbeat()
                  +"\nin " + this
                  );

            connection = factory.newConnection();
         }

         Channel channel = connection.createChannel();
         if(!channel.isOpen()){
             log.fatalError("Failed to open channel: " + channel.getCloseReason().getLocalizedMessage());
         }
        return channel;
    }

    protected void deleteQueue() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        // use a different channel since channel closes on exception.
        Channel channel = createChannel();
        try {
            log.info("Deleting queue " + getQueue());
            channel.queueDelete(getQueue());
        }
        catch(Exception ex) {
            log.debug(ex.toString(), ex);
            // ignore it.
        }
        finally {
            if (channel.isOpen())  {
                channel.close();
            }
        }
    }

    protected void deleteExchange() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        // use a different channel since channel closes on exception.
        Channel channel = createChannel();
        try {
            log.info("Deleting exchange " + getExchange());
            channel.exchangeDelete(getExchange());
        }
        catch(Exception ex) {
            log.debug(ex.toString(), ex);
            // ignore it.
        }
        finally {
            if (channel.isOpen())  {
                channel.close();
            }
        }
    }
    protected void deletePublisherExchange() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        // use a different channel since channel closes on exception.
        Channel channel = createChannel();
        try {
            log.info("Deleting exchange " + getPublisherExchange());
            channel.exchangeDelete(getPublisherExchange());
        }
        catch(Exception ex) {
            log.debug(ex.toString(), ex);
            // ignore it.
        }
        finally {
            if (channel.isOpen())  {
                channel.close();
            }
        }
    }
}
