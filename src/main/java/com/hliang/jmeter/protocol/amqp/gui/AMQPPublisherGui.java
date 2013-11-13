package com.icix.jmeter.protocol.amqp.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.*;

import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.gui.JLabeledChoice;
import org.apache.jorphan.gui.JLabeledTextArea;
import org.apache.jorphan.gui.JLabeledTextField;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import com.icix.jmeter.protocol.amqp.AMQPPublisher;
import com.icix.jmeter.protocol.amqp.AMQPSampler;

/**
 * AMQP Sampler
 *
 */
public class AMQPPublisherGui extends AMQPSamplerGui {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggingManager.getLoggerForClass();
    private JPanel mainPanel;

    private JLabeledTextField exchangePublisher = new JLabeledTextField("Exchange");
    protected JLabeledChoice exchangePublisherType = new JLabeledChoice("Exchange Type", new String[]{ "direct", "topic", "headers", "fanout"});
    private final JCheckBox exchangePublisherDurable = new JCheckBox("Durable?", AMQPSampler.DEFAULT_EXCHANGE_DURABLE);
    private final JCheckBox exchangePublisherRedeclare = new JCheckBox("Redeclare?", AMQPSampler.DEFAULT_EXCHANGE_REDECLARE);

    private JLabeledTextArea message = new JLabeledTextArea("Message Content");
    private JLabeledTextArea messageheader = new JLabeledTextArea("Message Headers");
    private JLabeledTextField messageRoutingKey = new JLabeledTextField("Routing Key");
    private JLabeledTextField messageType = new JLabeledTextField("Message Type");
    //private JLabeledTextField replyToQueue = new JLabeledTextField("Reply-To Queue");
    private JLabeledTextField correlationId = new JLabeledTextField("Correlation Id");
    
    private final JCheckBox autoAck = new JCheckBox("Auto ACK", true);
    protected JLabeledTextField receiveTimeout = new JLabeledTextField("Receive Timeout");

    private JCheckBox persistent = new JCheckBox("Persistent?", AMQPPublisher.DEFAULT_PERSISTENT);
    private JCheckBox useTx = new JCheckBox("Use Transactions?", AMQPPublisher.DEFAULT_USE_TX);

    public AMQPPublisherGui(){
        init();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLabelResource() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String getStaticLabel() {
        return "AMQP RPC Sampler";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (!(element instanceof AMQPPublisher)) return;
        AMQPPublisher sampler = (AMQPPublisher) element;
        exchangePublisher.setText(sampler.getPublisherExchange());
        exchangePublisherType.setText(sampler.getPublisherExchangeType());
        exchangePublisherDurable.setSelected(sampler.getPublisherExchangeDurable());
        exchangePublisherRedeclare.setSelected(sampler.getPublisherExchangeRedeclare());
        
        persistent.setSelected(sampler.getPersistent());
        useTx.setSelected(sampler.getUseTx());

        messageRoutingKey.setText(sampler.getMessageRoutingKey());
        messageType.setText(sampler.getMessageType());
        //replyToQueue.setText(sampler.getReplyToQueue());
        correlationId.setText(sampler.getCorrelationId());
        message.setText(sampler.getMessage());
        messageheader.setText(sampler.getMessageHeadersAsString());
        autoAck.setSelected(sampler.autoAck());
        receiveTimeout.setText(sampler.getReceiveTimeout());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestElement createTestElement() {
    	log.info("CREATE TEST ELEMENT IS CALLED");
        AMQPPublisher sampler = new AMQPPublisher();
        modifyTestElement(sampler);
        return sampler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyTestElement(TestElement te) {
        AMQPPublisher sampler = (AMQPPublisher) te;
        sampler.clear();
        configureTestElement(sampler);

        super.modifyTestElement(sampler);
        sampler.setPublisherExchangeType(exchangePublisherType.getText());
        sampler.setPublisherExchange(exchangePublisher.getText());
        sampler.setPublisherExchangeDurable(exchangePublisherDurable.isSelected());
        sampler.setPublisherExchangeRedeclare(exchangePublisherRedeclare.isSelected());
        
        
        sampler.setPersistent(persistent.isSelected());
        sampler.setUseTx(useTx.isSelected());

        sampler.setMessageRoutingKey(messageRoutingKey.getText());
        sampler.setMessage(message.getText());
        sampler.setMessageHeaders(messageheader.getText());
        sampler.setMessageType(messageType.getText());
        //sampler.setReplyToQueue(replyToQueue.getText());
        sampler.setCorrelationId(correlationId.getText());
        sampler.setAutoAck(autoAck.isSelected());
        sampler.setReceiveTimeout(receiveTimeout.getText());
    }

    @Override
    protected void setMainPanel(JPanel panel){
        mainPanel = panel;
    }

    /*
     * Helper method to set up the GUI screen
     */
    @Override
    protected final void init() {
        super.init();
        persistent.setPreferredSize(new Dimension(100, 25));
        useTx.setPreferredSize(new Dimension(100, 25));
        messageRoutingKey.setPreferredSize(new Dimension(100, 25));
        messageType.setPreferredSize(new Dimension(100, 25));
        //replyToQueue.setPreferredSize(new Dimension(100, 25));
        correlationId.setPreferredSize(new Dimension(100, 25));
        message.setPreferredSize(new Dimension(400, 150));
        messageheader.setPreferredSize(new Dimension(400, 70));
        JPanel publisher = new VerticalPanel();
        publisher.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Publisher Setting"));
       
        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(2, 2, 2, 2);
        gridBagConstraints.fill = GridBagConstraints.NONE;
        gridBagConstraints.anchor = GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.5;       
        JPanel exchangeSettings = new JPanel(new GridBagLayout());
        exchangeSettings.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Exchange"));
      
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        exchangeSettings.add(exchangePublisher, gridBagConstraints);
        
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        exchangeSettings.add(exchangePublisherType, gridBagConstraints);
        
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        exchangeSettings.add(exchangePublisherDurable, gridBagConstraints);

        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        exchangeSettings.add(exchangePublisherRedeclare, gridBagConstraints);
        
        publisher.add(exchangeSettings);
        publisher.add(persistent);
        publisher.add(useTx);
        publisher.add(messageRoutingKey);
        publisher.add(messageType);
        //publisher.add(replyToQueue);       
        publisher.add(message);
        publisher.add(messageheader);
        
        mainPanel.add(correlationId);
        mainPanel.add(receiveTimeout);
        mainPanel.add(autoAck);
        mainPanel.add(publisher);    
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearGui() {
        super.clearGui();
        exchangePublisher.setText("jmeterExchangePublisher");
        exchangePublisherType.setText("direct");
        exchangePublisherDurable.setSelected(AMQPSampler.DEFAULT_EXCHANGE_DURABLE);
        exchangePublisherRedeclare.setSelected(AMQPSampler.DEFAULT_EXCHANGE_REDECLARE);
        
        
        persistent.setSelected(AMQPPublisher.DEFAULT_PERSISTENT);
        useTx.setSelected(AMQPPublisher.DEFAULT_USE_TX);
        messageRoutingKey.setText("");
        messageType.setText("");
        //replyToQueue.setText("");
        correlationId.setText("");
        message.setText("");
        messageheader.setText("");
        autoAck.setSelected(true);
        receiveTimeout.setText("");
    }
}