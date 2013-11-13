package com.icix.jmeter.protocol.amqp;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import com.rabbitmq.client.Channel;

/**
 * JMeter creates an instance of a sampler class for every occurrence of the
 * element in every thread. [some additional copies may be created before the
 * test run starts]
 *
 * Thus each sampler is guaranteed to be called by a single thread - there is no
 * need to synchronize access to instance variables.
 *
 * However, access to class fields must be synchronized.
 */
public class AMQPPublisher extends AMQPSampler implements Interruptible {

    private static final long serialVersionUID = -8420658040465788498L;

    private static final Logger log = LoggingManager.getLoggerForClass();

    private static final String USE_CONSUMER_ROUTING_KEY_FLAG = "$CONSUMER_ROUTING_KEY$";
    private static final String EXCHANGE_PUBLISHER = "AMQPPublisher.Exchange";
    private static final String EXCHANGE_PUBLISHER_TYPE = "AMQPPublisher.ExchangeType";
    private static final String EXCHANGE_PUBLISHER_DURABLE = "AMQPPublisher.ExchangeDurable";
    private static final String EXCHANGE_PUBLISHER_REDECLARE = "AMQPPublisher.ExchangeRedeclare";
    private final static String MESSAGE = "AMQPPublisher.Message";
    private final static String MESSAGE_ROUTING_KEY = "AMQPPublisher.MessageRoutingKey";
    private final static String MESSAGE_TYPE = "AMQPPublisher.MessageType";
    private final static String REPLY_TO_QUEUE = "AMQPPublisher.ReplyToQueue";
    private final static String CORRELATION_ID = "AMQPPublisher.CorrelationId";
    private final static String MESSAGE_HEADERS = "AMQPPublisher.MessageHeaders";
    private static final String AUTO_ACK = "AMQPPublisher.AutoAck";
    private static final String RECEIVE_TIMEOUT = "AMQPConsumer.ReceiveTimeout";
    
    public static boolean DEFAULT_PERSISTENT = false;
    private final static String PERSISTENT = "AMQPConsumer.Persistent";

    public static boolean DEFAULT_USE_TX = false;
    private final static String USE_TX = "AMQPConsumer.UseTx";
    
    private static Boolean consumerExchangeRedeclare;
    private static Boolean publisherExchangeRedeclare;

    private transient Channel consumerChannel;
    private transient Channel publisherChannel;
    private transient QueueingConsumer consumer;
    private transient String consumerTag;
    private transient String publisherRoutingKey;
    private transient String publisherExchange;
    
    

    public AMQPPublisher() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SampleResult sample(Entry e) {
        SampleResult result = new SampleResult();
        result.setSampleLabel(getName());
        result.setSuccessful(false);
        result.setResponseCode("500");

        try {
        	initChannel();
            
            if (consumer == null) {       	
                consumer = new QueueingConsumer(consumerChannel);
                this.publisherRoutingKey = this.getMessageRoutingKey();
                this.publisherExchange = this.getPublisherExchange();
            }
            if (consumerTag == null) {
                consumerTag = consumerChannel.basicConsume(getQueue(), autoAck(), consumer);
            }
        
        } catch (Exception ex) {
            log.error("Failed to initialize channel : ", ex);
            result.setResponseMessage(ex.toString());
            return result;
        }

        String data = getMessage(); // Sampler data

        result.setSampleLabel(getTitle());
        /*
         * Perform the sampling
         */

        // aggregate samples.
        int loop = getIterationsAsInt();
        result.sampleStart(); // Start timing
        QueueingConsumer.Delivery delivery = null;
        try {
            AMQP.BasicProperties messageProperties = getProperties();
            log.info("Message properties :"+ messageProperties);
            byte[] messageBytes = getMessageBytes();

            for (int idx = 0; idx < loop; idx++) {
            	publisherChannel.basicPublish(this.publisherExchange, this.publisherRoutingKey, messageProperties, messageBytes);
                
                delivery = null;
                long exitTimeStamp = System.currentTimeMillis() + getReceiveTimeoutAsInt();
               
                while(delivery == null && System.currentTimeMillis() < exitTimeStamp)
                {
                	delivery = consumer.nextDelivery(getReceiveTimeoutAsInt());
                	log.info("delivery is "+delivery.getProperties().getCorrelationId());
                	String cId = delivery.getProperties().getCorrelationId();
                    if(!this.getCorrelationId().equals(cId))
                    {
                    	delivery = null;
                    }
                }
                
                if(delivery == null){
                    result.setResponseMessage("timed out");
                    return result;
                }
                log.info("result match = "+this.getCorrelationId().equals(delivery.getProperties().getCorrelationId()));
                log.info("message response is "+new String(delivery.getBody()));
                result.setSamplerData(new String(delivery.getBody()));

                if(!autoAck())
                	consumerChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

            }

            // commit the sample.
            if (getUseTx()) {
            	publisherChannel.txCommit();
            }

            /*
             * Set up the sample result details
             */
           //result.setSamplerData(data);
            result.setResponseData("OK", null);
            result.setDataType(SampleResult.TEXT);

            result.setResponseCodeOK();
            result.setResponseMessage("OK");
            result.setSuccessful(true);
        } catch (Exception ex) {
            log.debug(ex.getMessage(), ex);
            result.setResponseCode("000");
            result.setResponseMessage(ex.toString());
        }
        finally {
            result.sampleEnd(); // End timimg
        }

        return result;
    }

    @Override
    public String getPublisherExchange() {
        return getPropertyAsString(EXCHANGE_PUBLISHER);
    }
    @Override
    public void setPublisherExchange(String name) {
        setProperty(EXCHANGE_PUBLISHER, name);
    }

    @Override
    public String getPublisherExchangeType() {
        return getPropertyAsString(EXCHANGE_PUBLISHER_TYPE);
    }
    @Override
    public void setPublisherExchangeType(String name) {
        setProperty(EXCHANGE_PUBLISHER_TYPE, name);
    }
    @Override
    public Boolean getPublisherExchangeDurable() {
        return getPropertyAsBoolean(EXCHANGE_PUBLISHER_DURABLE);
    }
    @Override
    public void setPublisherExchangeDurable(Boolean content) {
        setProperty(EXCHANGE_PUBLISHER_DURABLE, content);
    }
    @Override
    public Boolean getPublisherExchangeRedeclare() {
        return getPropertyAsBoolean(EXCHANGE_PUBLISHER_REDECLARE);
    }
    @Override
    public void setPublisherExchangeRedeclare(Boolean content) {
        setProperty(EXCHANGE_PUBLISHER_REDECLARE, content);
    }
    @Override
    public void setConsumerRedeclareFlag(Boolean b)
    {
    	consumerExchangeRedeclare = b;
    }
    @Override
    public Boolean getConsumerRedeclareFlag()
    {
    	return consumerExchangeRedeclare;
    }
    @Override
    public Boolean getPublisherRedeclareFlag()
    {
    	return publisherExchangeRedeclare;
    }
    @Override
	public void setPublisherRedeclareFlag(Boolean b) {
		publisherExchangeRedeclare = b;
		
	}

    private byte[] getMessageBytes() {
        return getMessage().getBytes();
    }

    /**
     * @return the message routing key for the sample
     */
    @Override
    public String getMessageRoutingKey() {
        return getPropertyAsString(MESSAGE_ROUTING_KEY);
    }
    @Override
    public void setMessageRoutingKey(String content) {
        setProperty(MESSAGE_ROUTING_KEY, content);
    }

    /**
     * @return the message for the sample
     */
    public String getMessage() {
        return getPropertyAsString(MESSAGE);
    }

    public void setMessage(String content) {
        setProperty(MESSAGE, content);
    }

    /**
     * @return the message type for the sample
     */
    public String getMessageType() {
        return getPropertyAsString(MESSAGE_TYPE);
    }

    public void setMessageType(String content) {
        setProperty(MESSAGE_TYPE, content);
    }

    /**
     * @return the reply-to queue for the sample
     */
    public String getReplyToQueue() {
        return getPropertyAsString(REPLY_TO_QUEUE);
    }

    public void setReplyToQueue(String content) {
        setProperty(REPLY_TO_QUEUE, content);
    }

    /**
     * @return the correlation identifier for the sample
     */
    @Override
    public String getCorrelationId() {
        return getPropertyAsString(CORRELATION_ID);
    }
    @Override
    public void setCorrelationId(String content) {
        setProperty(CORRELATION_ID, content);
    }
    
    private Map<String,Object> getMessageHeaders()
    {
    	String headers = getPropertyAsString(MESSAGE_HEADERS);
    	log.info("^^^^^^^^^^^^^^^^^^^^header is"+headers);
    	Map<String,Object> result = new HashMap<String, Object>();
    	
    	
    	if (StringUtils.isNotBlank(headers))
    	{
    		String[] pros = headers.split("\\s*,\\s*");
        	for(String s : pros)
        	{
        		String[] keyValue = s.split("\\s*=\\s*");
        		if(USE_CONSUMER_ROUTING_KEY_FLAG.equals(keyValue[1]))
        		{
        			result.put(keyValue[0], this.getReplyTo());
        		}else
        		{
        			result.put(keyValue[0], keyValue[1]);
        		}
        	}
    	}	
    	return result;
    }
    public String getMessageHeadersAsString()
    {
    	return getPropertyAsString(MESSAGE_HEADERS);
    }
    
    public void setMessageHeaders(String headers)
    {
    	setProperty(MESSAGE_HEADERS, headers);
    }


    public Boolean getPersistent() {
        return getPropertyAsBoolean(PERSISTENT, DEFAULT_PERSISTENT);
    }

    public void setPersistent(Boolean persistent) {
       setProperty(PERSISTENT, persistent);
    }

    public Boolean getUseTx() {
        return getPropertyAsBoolean(USE_TX, DEFAULT_USE_TX);
    }

    public void setUseTx(Boolean tx) {
       setProperty(USE_TX, tx);
    }

    @Override
    public boolean interrupt() {
        cleanup();
        return true;
    }

    @Override
    protected Channel getConsumerChannel() {
        return this.consumerChannel;
    }

    @Override
    protected void setConsumerChannel(Channel channel) {
        this.consumerChannel = channel;
    }
    
    @Override
    protected Channel getPublisherChannel() {
        return this.publisherChannel;
    }

    @Override
    protected void setPublisherChannel(Channel channel) {
        this.publisherChannel = channel;
    }
    /**
     * @return the whether or not to auto ack
     */
    public String getAutoAck() {
        return getPropertyAsString(AUTO_ACK);
    }

    public void setAutoAck(String content) {
        setProperty(AUTO_ACK, content);
    }

    public void setAutoAck(Boolean autoAck) {
        setProperty(AUTO_ACK, autoAck.toString());
    }

    public boolean autoAck(){
        return getPropertyAsBoolean(AUTO_ACK);
    }
    
    protected int getReceiveTimeoutAsInt() {
        if (getPropertyAsInt(RECEIVE_TIMEOUT) < 1) {
            return DEFAULT_TIMEOUT;
        }
        return getPropertyAsInt(RECEIVE_TIMEOUT);
    }

    public String getReceiveTimeout() {
        return getPropertyAsString(RECEIVE_TIMEOUT, DEFAULT_TIMEOUT_STRING);
    }

    public void setReceiveTimeout(String s) {
        setProperty(RECEIVE_TIMEOUT, s);
    }

    @Override
    protected AMQP.BasicProperties getProperties() {
        AMQP.BasicProperties parentProps = super.getProperties();

        int deliveryMode = getPersistent() ? 2 : 1;
        Map<String,Object> headers = parentProps.getHeaders();
        if(headers == null)
        {
        	headers = this.getMessageHeaders();
        }else
        {
        	headers.putAll(this.getMessageHeaders());
        }
        log.info("$$$$$$$$$$$$$$$$$headers"+headers);
        AMQP.BasicProperties publishProperties =
                new AMQP.BasicProperties(parentProps.getContentType(), parentProps.getContentEncoding(),
                headers, deliveryMode, parentProps.getPriority(),
                getCorrelationId(), this.getReplyTo(), parentProps.getExpiration(),
                getCorrelationId(), parentProps.getTimestamp(), getMessageType(),
                parentProps.getUserId(), parentProps.getAppId(), parentProps.getClusterId());

        return publishProperties;
    }

    protected boolean initChannel() throws IOException, NoSuchAlgorithmException, KeyManagementException {
        boolean ret = super.initChannel();
        if (getUseTx()) {
        	publisherChannel.txSelect();
        }
        consumerChannel.basicQos(10);
        return ret;
    }
    
    public void cleanup() {

        try {
            if (consumerTag != null) {
            	consumerChannel.basicCancel(consumerTag);
            }
        } catch(IOException e) {
            log.error("Couldn't safely cancel the sample " + consumerTag, e);
        }

        super.cleanup();

    }

    /*
     * Helper method
     */
    private void trace(String s) {
        String tl = getTitle();
        String tn = Thread.currentThread().getName();
        String th = this.toString();
        log.debug(tn + " " + tl + " " + s + " " + th);
    }

	
}
